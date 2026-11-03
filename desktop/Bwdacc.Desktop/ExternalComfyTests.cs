using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Json;
using System.Net.Security;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace Bwdacc.Desktop;

internal static class ExternalComfyTests
{
    private static void Check(bool condition, string message) { if (!condition) throw new IOException(message); }
    private static int Port() { using var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start(); return ((IPEndPoint)listener.LocalEndpoint).Port; }
    public static async Task RunAsync(string report)
    {
        var passed = new List<string>();
        foreach (var value in new[] { "https://127.0.0.1:8188", "http://192.168.1.9:8188", "http://example.com:8188", "http://127.0.0.1:8190", "http://127.0.0.1:80", "http://u:p@localhost:8188", "http://localhost:8188/admin", "http://localhost:8188/?q=x", "http://localhost:8188/#x" })
        {
            var rejected = false; try { ExternalComfy.Validate(value); } catch (IOException) { rejected = true; }
            Check(rejected, "Unsafe endpoint accepted: " + value);
        }
        Check(ExternalComfy.Validate("http://localhost:8388").ToString() == "http://127.0.0.1:8388/", "Localhost not normalized");
        passed.Add("local-only upstream validation rejects remote hosts, reserved gateway port, credentials and paths");
        var old = JsonSerializer.Deserialize<DesktopSettings>("{\"Root\":\"synthetic-root\"}", Data.Json)!;
        Check(old.Backend == "managed" && old.ExternalUrl == "http://127.0.0.1:8188" && !old.SetupComplete && old.SetupStep == 0, "Settings migration changed backend");
        passed.Add("legacy settings retain managed mode without changing root");
        var backendPort = Port(); var gatewayPort = Port();
        var builder = WebApplication.CreateEmptyBuilder(new WebApplicationOptions { ApplicationName = typeof(ExternalComfyTests).Assembly.FullName });
        builder.Logging.ClearProviders(); builder.WebHost.UseKestrel(o => o.Listen(IPAddress.Loopback, backendPort));
        await using var backend = builder.Build(); backend.UseWebSockets();
        bool busy = false, redirect = false, invalid = false, leaked = false; var writes = 0;
        backend.Run(async context =>
        {
            if (context.Request.Headers.ContainsKey("Authorization")) leaked = true;
            if (context.Request.Method != "GET") writes++;
            switch (context.Request.Path.Value)
            {
                case "/system_stats":
                    if (redirect) { context.Response.Redirect("http://example.invalid/"); return; }
                    await context.Response.WriteAsJsonAsync(invalid ? new { server = "different" } : (object)new { system = new { comfyui_version = "synthetic-test" }, devices = Array.Empty<object>() }); return;
                case "/object_info":
                    context.Response.ContentType = "application/json";
                    await context.Response.WriteAsync("""{"LoadImage":{"input":{"required":{"image":[["private-photo.png"]]}}},"UNETLoader":{"input":{"required":{"unet_name":[["synthetic/model.safetensors"]]}}},"LoraLoader":{"input":{"required":{"lora_name":[["synthetic/style.safetensors"]]}}}}"""); return;
                case "/queue": await context.Response.WriteAsJsonAsync(new { queue_running = busy ? new[] { 1 } : [], queue_pending = Array.Empty<int>() }); return;
                case "/prompt": await context.Response.WriteAsJsonAsync(new { prompt_id = "synthetic-external" }); return;
                case "/ws":
                    using (var socket = await context.WebSockets.AcceptWebSocketAsync())
                    {
                        await socket.SendAsync(Encoding.UTF8.GetBytes("synthetic-progress"), WebSocketMessageType.Text, true, context.RequestAborted);
                        try { await socket.ReceiveAsync(new byte[256].AsMemory(), context.RequestAborted); } catch { }
                    } return;
                default: context.Response.StatusCode = 404; return;
            }
        });
        await backend.StartAsync();
        using var external = new ExternalComfy(); var url = $"http://127.0.0.1:{backendPort}";
        await external.ConnectAsync(url, CancellationToken.None);
        Check(external.Ready && external.Inventory!.Models.SequenceEqual(new[] { "synthetic/model.safetensors" }) && external.Inventory.Loras.Length == 1 && !external.Inventory.Models.Contains("private-photo.png") && writes == 0, "Discovery invalid or mutated backend");
        passed.Add("read-only API inventory separates models, LoRAs and private image names");
        Check(!await external.BusyAsync(CancellationToken.None), "Empty queue reported busy"); busy = true;
        Check(await external.BusyAsync(CancellationToken.None), "Running queue not detected"); busy = false;
        passed.Add("queue inspection detects active work without clearing it");
        foreach (var fail in new[] { "redirect", "invalid" })
        {
            redirect = fail == "redirect"; invalid = fail == "invalid";
            var rejected = false; try { await external.ConnectAsync(url, CancellationToken.None); } catch (IOException) { rejected = true; }
            Check(rejected && !external.Ready, "Invalid server accepted or retained readiness");
        }
        redirect = invalid = false; await external.ConnectAsync(url, CancellationToken.None);
        passed.Add("redirects and non-Comfy APIs fail closed");
        var root = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "external-tests-" + Guid.NewGuid().ToString("N"));
        using var identity = new PairingIdentity(Path.Combine(root, "identity"));
        using var client = new HttpClient(new HttpClientHandler { UseProxy = false, AllowAutoRedirect = false,
            ServerCertificateCustomValidationCallback = (_, cert, _, errors) => cert != null && !errors.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch) && Convert.ToHexStringLower(SHA256.HashData(cert.PublicKey.ExportSubjectPublicKeyInfo())) == identity.Pin });
        var address = PrivateNetwork.Addresses().FirstOrDefault()?.Address ?? throw new IOException("Private adapter required for integration test");
        string token;
        await using (var connection = new PrivateConnection(root, identity, () => external.Ready, (_, _) => Task.FromResult(true), gatewayPort, backendPort))
        {
            await connection.EnableAsync(address.ToString()); var endpoint = connection.Gateway!.Endpoint!;
            using (var denied = await client.GetAsync(endpoint + "/system_stats")) Check(denied.StatusCode == HttpStatusCode.Unauthorized, "Unpaired external access allowed");
            var invite = identity.CreateInvite(endpoint);
            using var paired = await client.PostAsJsonAsync(endpoint + "/bwdacc/pair", new { ticket = invite.Ticket, name = "Synthetic external client" }); paired.EnsureSuccessStatusCode();
            using var response = JsonDocument.Parse(await paired.Content.ReadAsStringAsync()); token = response.RootElement.GetProperty("token").GetString()!;
            client.DefaultRequestHeaders.Authorization = new("Bearer", token);
            Check((await client.GetStringAsync(endpoint + "/system_stats")).Contains("synthetic-test"), "Configured upstream ignored");
            using var submitted = await client.PostAsJsonAsync(endpoint + "/prompt", new { prompt = new { } }); submitted.EnsureSuccessStatusCode();
            Check((await submitted.Content.ReadAsStringAsync()).Contains("synthetic-external") && !leaked, "External prompt failed or token leaked");
            using var admin = await client.GetAsync(endpoint + "/manager/install"); Check(admin.StatusCode == HttpStatusCode.NotFound, "Admin route exposed");
            passed.Add("approved pinned HTTPS reaches selected external port without leaking credentials or exposing admin routes");
            using var socket = new ClientWebSocket(); socket.Options.Proxy = null; socket.Options.SetRequestHeader("Authorization", "Bearer " + token);
            socket.Options.RemoteCertificateValidationCallback = (_, cert, _, errors) => cert is X509Certificate2 certificate && !errors.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch) && Convert.ToHexStringLower(SHA256.HashData(certificate.PublicKey.ExportSubjectPublicKeyInfo())) == identity.Pin;
            using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(10));
            await socket.ConnectAsync(new Uri(endpoint.Replace("https:", "wss:") + "/ws?clientId=external-test"), deadline.Token);
            var message = new byte[256]; var received = await socket.ReceiveAsync(message.AsMemory(), deadline.Token);
            Check(Encoding.UTF8.GetString(message, 0, received.Count) == "synthetic-progress", "External progress missing"); socket.Abort();
            passed.Add("WSS progress uses the same external backend");
            external.Disconnect(); using var stopped = await client.GetAsync(endpoint + "/queue"); Check(stopped.StatusCode == HttpStatusCode.ServiceUnavailable, "Disconnected backend exposed");
        }
        using var localClient = new HttpClient(new HttpClientHandler { UseProxy = false });
        Check((await localClient.GetStringAsync(url + "/system_stats")).Contains("synthetic-test"), "External server was stopped by connector");
        passed.Add("disconnect and gateway disposal leave external ComfyUI running");
        await external.ConnectAsync(url, CancellationToken.None);
        await using (var restored = new PrivateConnection(root, identity, () => external.Ready, (_, _) => throw new IOException("Unexpected approval"), gatewayPort, backendPort))
        {
            await restored.RefreshAsync(); Check((await client.GetStringAsync(restored.Gateway!.Endpoint + "/system_stats")).Contains("synthetic-test"), "Credential or upstream lost after reopening");
        }
        passed.Add("saved private listener and paired credentials survive controller reopening with external port");
        await backend.StopAsync(); Check(!external.Ready, "Dead external listener retained readiness");
        passed.Add("backend readiness expires when selected process stops listening");
        Data.Save(report, new { Passed = passed.Count, Cases = passed });
    }
}
