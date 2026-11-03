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

internal static class PairingTests
{
    private static void Check(bool value, string message) { if (!value) throw new IOException(message); }
    private static int Port() { using var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start(); return ((IPEndPoint)listener.LocalEndpoint).Port; }
    private static bool Validate(string pin, X509Certificate? certificate, SslPolicyErrors errors)
    {
        if (certificate is not X509Certificate2 cert || errors.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch)) return false;
        return cert.NotAfter.ToUniversalTime() > DateTime.UtcNow && cert.NotBefore.ToUniversalTime() < DateTime.UtcNow && Convert.ToHexStringLower(SHA256.HashData(cert.PublicKey.ExportSubjectPublicKeyInfo())) == pin;
    }
    private static HttpClient Client(string pin) => new(new HttpClientHandler { UseProxy = false, AllowAutoRedirect = false, ServerCertificateCustomValidationCallback = (_, cert, _, errors) => Validate(pin, cert, errors) }) { Timeout = TimeSpan.FromSeconds(10) };
    internal static WebApplication Backend(int port, Action<HttpContext>? observe = null)
    {
        var builder = WebApplication.CreateEmptyBuilder(new WebApplicationOptions { ApplicationName = typeof(PairingTests).Assembly.FullName });
        builder.Logging.ClearProviders(); builder.WebHost.UseKestrel(o => o.Listen(IPAddress.Loopback, port));
        var app = builder.Build(); app.UseWebSockets();
        app.Run(async context =>
        {
            observe?.Invoke(context);
            if (context.WebSockets.IsWebSocketRequest)
            {
                using var socket = await context.WebSockets.AcceptWebSocketAsync();
                await socket.SendAsync(Encoding.UTF8.GetBytes("{\"type\":\"status\",\"data\":{\"status\":{\"exec_info\":{\"queue_remaining\":0}}}}"), WebSocketMessageType.Text, true, context.RequestAborted);
                var bytes = new byte[1024]; try { await socket.ReceiveAsync(bytes.AsMemory(), context.RequestAborted); } catch { } return;
            }
            if (context.Request.Path == "/view")
            {
                if (context.Request.Query["filename"] == "redirect") { context.Response.Redirect("https://example.invalid/secret"); return; }
                if (context.Request.Query["filename"] == "slow")
                {
                    context.Response.ContentType = "application/octet-stream";
                    for (var i = 0; i < 24; i++) { await context.Response.Body.WriteAsync(new byte[16384], context.RequestAborted); await context.Response.Body.FlushAsync(context.RequestAborted); await Task.Delay(500, context.RequestAborted); }
                    return;
                }
                context.Response.ContentType = "application/octet-stream"; await context.Response.Body.WriteAsync(new byte[] { 0, 1, 2, 3, 128, 255 }); return;
            }
            if (context.Request.Path == "/prompt")
            { using var input = await JsonDocument.ParseAsync(context.Request.Body); await context.Response.WriteAsJsonAsync(new { prompt_id = "synthetic-test", accepted = input.RootElement.GetProperty("prompt").GetProperty("label").GetString() }); return; }
            if (context.Request.Path == "/upload/image")
            { using var stream = new MemoryStream(); await context.Request.Body.CopyToAsync(stream); await context.Response.WriteAsJsonAsync(new { bytes = stream.Length, name = "synthetic.png", subfolder = "", type = "input" }); return; }
            if (context.Request.Path == "/queue") { await context.Response.WriteAsJsonAsync(new { queue_running = Array.Empty<object>(), queue_pending = Array.Empty<object>() }); return; }
            await context.Response.WriteAsJsonAsync(new { system = new { os = "synthetic" }, devices = Array.Empty<object>() });
        });
        return app;
    }
    public static async Task RunAsync(string report)
    {
        var root = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "pairing-" + Guid.NewGuid().ToString("N"));
        var clock = DateTimeOffset.UtcNow;
        using var identity = new PairingIdentity(root, () => clock);
        var backendPort = Port(); var port = Port(); var requests = 0; var leakedCredential = false; var allowed = true; var ready = true;
        await using var backend = Backend(backendPort, context => { Interlocked.Increment(ref requests); if (context.Request.Headers.ContainsKey("Authorization")) leakedCredential = true; });
        await backend.StartAsync();
        await using var gateway = new PairingGateway(identity, () => ready, (_, _) => Task.FromResult(allowed), backendPort);
        await gateway.StartAsync(IPAddress.Loopback, port);
        var address = gateway.Endpoint!; var passed = new List<string>();
        using var client = Client(identity.Pin);
        using (var unknown = await client.GetAsync(address + "/system_stats")) Check(unknown.StatusCode == HttpStatusCode.Unauthorized && requests == 0, "Unpaired request reached backend");
        passed.Add("unpaired HTTPS cannot reach backend");
        using (var wrongPin = Client(new string('0', 64)))
        {
            var rejected = false; try { await wrongPin.GetAsync(address + "/system_stats"); } catch (HttpRequestException) { rejected = true; }
            Check(rejected, "Wrong TLS identity accepted");
        }
        passed.Add("incorrect TLS identity rejected before authentication");
        var expired = identity.CreateInvite(address); clock = clock.AddMinutes(3);
        using (var response = await client.PostAsJsonAsync(address + "/bwdacc/pair", new { ticket = expired.Ticket, name = "test" })) Check(response.StatusCode == HttpStatusCode.Forbidden, "Expired invite accepted");
        clock = DateTimeOffset.UtcNow;
        var invitation = identity.CreateInvite(address);
        using (var invalid = await client.PostAsJsonAsync(address + "/bwdacc/pair", new { ticket = new string('x', 43), name = "test" })) Check(invalid.StatusCode == HttpStatusCode.Forbidden, "Unknown ticket accepted");
        using var paired = await client.PostAsJsonAsync(address + "/bwdacc/pair", new { ticket = invitation.Ticket, name = "Synthetic Android" }); paired.EnsureSuccessStatusCode();
        using var credential = JsonDocument.Parse(await paired.Content.ReadAsStringAsync());
        var token = credential.RootElement.GetProperty("token").GetString()!; var deviceId = credential.RootElement.GetProperty("deviceId").GetString()!;
        using (var replay = await client.PostAsJsonAsync(address + "/bwdacc/pair", new { ticket = invitation.Ticket, name = "replay" })) Check(replay.StatusCode == HttpStatusCode.Forbidden, "Pairing replay accepted");
        passed.Add("invalid, expired and consumed pairing tickets rejected");
        allowed = false; var declined = identity.CreateInvite(address);
        using (var denied = await client.PostAsJsonAsync(address + "/bwdacc/pair", new { ticket = declined.Ticket, name = "denied" })) Check(denied.StatusCode == HttpStatusCode.Forbidden && identity.Devices.Length == 1, "Desktop denial ignored");
        passed.Add("desktop approval required before issuing credential");
        client.DefaultRequestHeaders.Authorization = new("Bearer", token);
        using (var response = await client.GetAsync(address + "/system_stats")) Check(response.IsSuccessStatusCode, "Paired HTTPS failed");
        Check((await client.GetByteArrayAsync(address + "/view?filename=synthetic")).SequenceEqual(new byte[] { 0, 1, 2, 3, 128, 255 }), "Binary media corrupted");
        using (var response = await client.PostAsJsonAsync(address + "/prompt", new { prompt = new { label = "synthetic request" } })) { response.EnsureSuccessStatusCode(); Check((await response.Content.ReadAsStringAsync()).Contains("synthetic request"), "Prompt body not forwarded"); }
        using (var upload = await client.PostAsync(address + "/upload/image", new ByteArrayContent(new byte[4096]))) { upload.EnsureSuccessStatusCode(); Check((await upload.Content.ReadAsStringAsync()).Contains("4096"), "Upload body not forwarded"); }
        Check(!leakedCredential, "Bearer forwarded to ComfyUI");
        passed.Add("authenticated JSON, binary media and upload pass without forwarding bearer");
        foreach (var path in new[] { "/internal/logs/raw", "/manager/install", "/userdata", "/api/system_stats", "/history/../../internal/logs/raw" })
        { using var response = await client.GetAsync(address + path); Check(response.StatusCode == HttpStatusCode.NotFound, "Unexpected route exposed: " + path); }
        using (var response = await client.PostAsJsonAsync(address + "/queue", new { clear = true })) Check(response.StatusCode == HttpStatusCode.NotFound, "Queue mutation exposed");
        using (var response = await client.GetAsync(address + "/view?filename=redirect")) Check(response.StatusCode == HttpStatusCode.BadGateway && response.Headers.Location == null, "Upstream redirect exposed");
        using (var browser = new HttpRequestMessage(HttpMethod.Get, address + "/queue")) { browser.Headers.Add("Origin", "https://example.invalid"); using var response = await client.SendAsync(browser); Check(response.StatusCode == HttpStatusCode.Forbidden, "Browser origin accepted"); }
        passed.Add("admin routes, unsupported mutations, browser origins and redirects blocked");
        ready = false; using (var response = await client.GetAsync(address + "/queue")) Check(response.StatusCode == HttpStatusCode.ServiceUnavailable, "Foreign backend adopted"); ready = true;
        passed.Add("gateway refuses backend it does not own");
        using var socket = new ClientWebSocket(); socket.Options.Proxy = null; socket.Options.SetRequestHeader("Authorization", "Bearer " + token);
        socket.Options.RemoteCertificateValidationCallback = (_, cert, _, errors) => Validate(identity.Pin, cert, errors);
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(10));
        await socket.ConnectAsync(new Uri(address.Replace("https:", "wss:") + "/ws?clientId=test-client"), deadline.Token);
        var message = new byte[4096]; var received = await socket.ReceiveAsync(message.AsMemory(), deadline.Token); Check(received.Count > 0, "Progress events unavailable");
        passed.Add("authenticated WSS progress delivered");
        using (var reopened = new PairingIdentity(root)) Check(reopened.Pin == identity.Pin && reopened.Authenticate("Bearer " + token) != null, "Pairing not persistent");
        Check(!Encoding.UTF8.GetString(File.ReadAllBytes(Path.Combine(root, "devices.dpapi"))).Contains(token), "Plaintext credential stored");
        passed.Add("DPAPI identity and credential registry survive reopening");
        identity.Revoke(deviceId);
        using (var response = await client.GetAsync(address + "/queue")) Check(response.StatusCode == HttpStatusCode.Unauthorized, "Revoked HTTP credential accepted");
        var disconnected = false; try { var close = await socket.ReceiveAsync(message.AsMemory(), deadline.Token); disconnected = close.MessageType == WebSocketMessageType.Close; } catch (WebSocketException) { disconnected = true; }
        Check(disconnected, "Revoked WebSocket remained connected");
        using (var reopened = new PairingIdentity(root)) Check(reopened.Authenticate("Bearer " + token) == null, "Revocation not persistent");
        passed.Add("revocation persists and terminates active WSS");
        Data.Save(report, new { Passed = passed.Count, Cases = passed });
    }

    public static async Task RealRuntimeAsync(string runtimeRoot, string report, bool remote = false)
    {
        var installer = new RuntimeInstaller(runtimeRoot);
        using var deadline = new CancellationTokenSource(TimeSpan.FromMinutes(4));
        using var engine = new RuntimeController();
        await engine.StartAsync(installer, deadline.Token);
        var identityRoot = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "real-connection-" + Guid.NewGuid().ToString("N"));
        using var identity = new PairingIdentity(identityRoot);
        await using var gateway = new PairingGateway(identity, () => engine.OwnsListener, (_, _) => Task.FromResult(true));
        await gateway.StartAsync(IPAddress.Loopback, Port());
        await using var bridge = remote ? new RemoteBridge(new Uri(gateway.Endpoint!).Port) : null;
        await using var tunnel = remote ? new CloudflareTunnel() : null;
        if (bridge != null && tunnel != null) { await bridge.StartAsync(0); await tunnel.StartAsync(bridge.Port, bridge.AccessKey, null, deadline.Token); }
        using var client = remote ? RemoteTests.InternetClient(tunnel!.Endpoint!, bridge!.AccessKey, identity.Pin) : Client(identity.Pin);
        var invite = identity.CreateInvite(gateway.Endpoint!);
        using var paired = await client.PostAsJsonAsync(gateway.Endpoint + "/bwdacc/pair", new { ticket = invite.Ticket, name = "Synthetic runtime validation" }, deadline.Token);
        paired.EnsureSuccessStatusCode();
        using var credentials = JsonDocument.Parse(await paired.Content.ReadAsStringAsync(deadline.Token));
        client.DefaultRequestHeaders.Authorization = new("Bearer", credentials.RootElement.GetProperty("token").GetString());
        using (var health = await client.GetAsync(gateway.Endpoint + "/system_stats", deadline.Token)) health.EnsureSuccessStatusCode();
        var graph = new Dictionary<string, object>
        {
            ["1"] = new { class_type = "EmptyImage", inputs = new { width = 64, height = 64, batch_size = 1, color = 3359829 } },
            ["2"] = new { class_type = "SaveImage", inputs = new { images = new object[] { "1", 0 }, filename_prefix = "bwdacc-test/connection" } }
        };
        using var submitted = await client.PostAsJsonAsync(gateway.Endpoint + "/prompt", new { prompt = graph, client_id = Guid.NewGuid().ToString() }, deadline.Token);
        submitted.EnsureSuccessStatusCode();
        using var submittedJson = JsonDocument.Parse(await submitted.Content.ReadAsStringAsync(deadline.Token));
        var promptId = submittedJson.RootElement.GetProperty("prompt_id").GetString()!;
        JsonElement? image = null;
        for (var i = 0; i < 45; i++)
        {
            using var history = JsonDocument.Parse(await client.GetStringAsync(gateway.Endpoint + "/history/" + promptId, deadline.Token));
            if (history.RootElement.TryGetProperty(promptId, out var entry) && entry.TryGetProperty("outputs", out var outputs) && outputs.TryGetProperty("2", out var node) && node.TryGetProperty("images", out var images)) { image = images[0].Clone(); break; }
            if (history.RootElement.TryGetProperty(promptId, out entry) && entry.TryGetProperty("status", out var status) && status.TryGetProperty("status_str", out var outcome) && outcome.GetString() == "error") throw new IOException("Synthetic EmptyImage workflow failed.");
            await Task.Delay(500, deadline.Token);
        }
        Check(image != null, "Synthetic output was not reported in history.");
        var media = image!.Value;
        var query = "/view?type=output&filename=" + Uri.EscapeDataString(media.GetProperty("filename").GetString()!) + "&subfolder=" + Uri.EscapeDataString(media.GetProperty("subfolder").GetString()!);
        var png = await client.GetByteArrayAsync(gateway.Endpoint + query, deadline.Token);
        Check(png.Length > 8 && png.AsSpan(0, 8).SequenceEqual(new byte[] { 137, 80, 78, 71, 13, 10, 26, 10 }), "Generated media is not a PNG.");
        using var upload = new MultipartFormDataContent();
        var content = new ByteArrayContent(png); content.Headers.ContentType = new("image/png"); upload.Add(content, "image", "bwdacc-synthetic-" + Guid.NewGuid().ToString("N") + ".png"); upload.Add(new StringContent("input"), "type");
        using var uploaded = await client.PostAsync(gateway.Endpoint + "/upload/image", upload, deadline.Token); uploaded.EnsureSuccessStatusCode();
        using var uploadedJson = JsonDocument.Parse(await uploaded.Content.ReadAsStringAsync(deadline.Token)); Check(uploadedJson.RootElement.TryGetProperty("name", out _), "Upload not accepted by ComfyUI.");
        await engine.StopAsync();
        Data.Save(report, new { Passed = true, RealManagedComfy = true, InternetTransport = remote, AuthenticatedPairing = true, SyntheticGeneration = "64x64 EmptyImage", PngDownloaded = true, ReferenceUpload = true, Bytes = png.Length, PersonalPortUntouched = 8188 });
    }

    // Synthetic-only public fixture. A Quick Tunnel keeps its address while the
    // gateway/bridge are recreated; this is not a named-provider deployment test.
    public static async Task FixtureAsync(string root, bool remote = false, bool persistent = false, IPAddress? privateAddress = null)
    {
        root = Path.GetFullPath(root); Directory.CreateDirectory(root);
        var store = new RemoteAccessStore(Path.Combine(root, "settings"));
        RemoteAccessProfile? profile = null;
        if (persistent)
        {
            profile = store.Load() ?? new RemoteAccessProfile("https://synthetic.bwdacc.app", new string('Z', 128), RemoteAccessProfile.NewAdmissionKey(), true);
            store.Save(profile);
        }
        int backendPort = Port(); await using var backend = Backend(backendPort); await backend.StartAsync();
        PairingIdentity? identity = null; PairingGateway? gateway = null; RemoteBridge? bridge = null;
        WebApplication? wrongHostname = null; X509Certificate2? wrongCertificate = null;
        int wrongHostnamePort = Port();
        await using var tunnel = remote ? new CloudflareTunnel() : null;
        async Task StopGateway()
        {
            if (wrongHostname != null) { await wrongHostname.DisposeAsync(); wrongHostname = null; }
            wrongCertificate?.Dispose(); wrongCertificate = null;
            if (bridge != null) { await bridge.DisposeAsync(); bridge = null; }
            if (gateway != null) { await gateway.DisposeAsync(); gateway = null; }
            identity?.Dispose(); identity = null;
        }
        async Task StartGateway()
        {
            identity = new PairingIdentity(Path.Combine(root, "identity"));
            gateway = new PairingGateway(identity, () => true, (_, _) => Task.FromResult(true), backendPort);
            await gateway.StartAsync(privateAddress ?? IPAddress.Loopback);
            // Same trusted key, wrong IP SAN: the Android HTTPS client must reject
            // this reachable server before sending the device credential.
            wrongCertificate = identity.Certificate(IPAddress.Parse("127.0.0.2"));
            var wrongBuilder = WebApplication.CreateEmptyBuilder(new WebApplicationOptions { ApplicationName = typeof(PairingTests).Assembly.FullName });
            wrongBuilder.Logging.ClearProviders();
            wrongBuilder.WebHost.UseKestrel(o => o.Listen(privateAddress ?? IPAddress.Loopback, wrongHostnamePort, listen => listen.UseHttps(wrongCertificate)));
            wrongHostname = wrongBuilder.Build(); wrongHostname.Run(context => context.Response.WriteAsync("hostname validation was bypassed"));
            await wrongHostname.StartAsync();
            if (remote)
            {
                bridge = new RemoteBridge(8190, persistent ? store.Load()!.AdmissionKey : null);
                await bridge.StartAsync();
            }
        }
        void Invite()
        {
            var invite = remote ? identity!.CreateRemoteInvite(gateway!.Endpoint!, tunnel!.Endpoint!, bridge!.AccessKey, persistent) : identity!.CreateInvite(gateway!.Endpoint!);
            Data.Save(Path.Combine(root, "invite.json"), new { code = invite.Uri, endpoint = remote ? $"https://{identity.Pin[..32]}.{identity.Pin[32..]}.desktop.bwdacc.invalid" : gateway.Endpoint, pin = identity.Pin, remote, permanent = persistent, privateNetwork = privateAddress != null, wrongHostnameEndpoint = $"https://{privateAddress ?? IPAddress.Loopback}:{wrongHostnamePort}" });
            File.WriteAllBytes(Path.Combine(root, "invite.png"), QRCoder.PngByteQRCodeHelper.GetQRCode(invite.Uri, QRCoder.QRCodeGenerator.ECCLevel.M, 8));
        }
        try
        {
            await StartGateway();
            if (tunnel != null) { using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(5)); await tunnel.StartAsync(bridge!.Port, bridge.AccessKey, null, timeout.Token); }
            Invite();
            var until = DateTime.UtcNow.AddMinutes(15);
            while (DateTime.UtcNow < until && !File.Exists(Path.Combine(root, "stop")))
            {
                if (File.Exists(Path.Combine(root, "renew"))) { File.Delete(Path.Combine(root, "renew")); Invite(); }
                if ((persistent || privateAddress != null) && File.Exists(Path.Combine(root, "restart")))
                {
                    File.Delete(Path.Combine(root, "restart")); await StopGateway(); await StartGateway();
                    File.WriteAllText(Path.Combine(root, "restarted"), "true");
                }
                if (File.Exists(Path.Combine(root, "revoke"))) { foreach (var device in identity!.Devices) identity.Revoke(device.Id); File.Delete(Path.Combine(root, "revoke")); File.WriteAllText(Path.Combine(root, "revoked"), "true"); }
                await Task.Delay(100);
            }
        }
        finally { await StopGateway(); }
    }
}
