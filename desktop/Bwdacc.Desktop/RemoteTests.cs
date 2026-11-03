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

namespace Bwdacc.Desktop;

internal static class RemoteTests
{
    private static void Check(bool condition, string text) { if (!condition) throw new IOException(text); }
    private static int Port() { using var server = new TcpListener(IPAddress.Loopback, 0); server.Start(); return ((IPEndPoint)server.LocalEndpoint).Port; }
    // Test-only raw byte stream; production Android uses a loopback socket for its native TLS stack.
    private sealed class Carrier(ClientWebSocket socket, MemoryStream? observed) : Stream
    {
        public override bool CanRead => true; public override bool CanWrite => true; public override bool CanSeek => false;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }
        public override int Read(byte[] buffer, int offset, int count) => ReadAsync(buffer.AsMemory(offset, count)).AsTask().GetAwaiter().GetResult();
        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken token = default)
        {
            if (buffer.IsEmpty) return 0;
            while (true)
            {
                var result = await socket.ReceiveAsync(buffer, token);
                if (result.MessageType == WebSocketMessageType.Close) return 0;
                if (result.Count > 0) return result.Count;
            }
        }
        public override void Write(byte[] buffer, int offset, int count) => WriteAsync(buffer.AsMemory(offset, count)).AsTask().GetAwaiter().GetResult();
        public override async ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken token = default)
        {
            if (observed != null) lock (observed) if (observed.Length < 2_000_000) observed.Write(buffer.Span);
            for (int offset = 0; offset < buffer.Length; offset += 16384)
                await socket.SendAsync(buffer.Slice(offset, Math.Min(16384, buffer.Length - offset)), WebSocketMessageType.Binary, true, token);
        }
        public override void Flush() { }
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long length) => throw new NotSupportedException();
        protected override void Dispose(bool disposing) { socket.Abort(); socket.Dispose(); base.Dispose(disposing); }
    }
    internal static HttpClient InternetClient(string relay, string admission, string pin)
    {
        var handler = new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false };
        handler.ConnectCallback = async (_, token) =>
        {
            var ws = new ClientWebSocket(); ws.Options.Proxy = null; ws.Options.SetRequestHeader("Authorization", "Bearer " + admission);
            try { await ws.ConnectAsync(new Uri(relay.Replace("https:", "wss:").Replace("http:", "ws:") + "/bwdacc/tunnel"), token); return new Carrier(ws, null); }
            catch { ws.Dispose(); throw; }
        };
        handler.SslOptions.RemoteCertificateValidationCallback = (_, cert, _, errors) =>
            cert is X509Certificate2 leaf && !errors.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch) &&
            leaf.NotAfter.ToUniversalTime() > DateTime.UtcNow && Convert.ToHexStringLower(SHA256.HashData(leaf.PublicKey.ExportSubjectPublicKeyInfo())) == pin;
        return new HttpClient(handler) { Timeout = TimeSpan.FromSeconds(40) };
    }
    public static async Task RunAsync(string report)
    {
        var root = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "remote-" + Guid.NewGuid().ToString("N"));
        using var identity = new PairingIdentity(root);
        var backendPort = Port(); var gatewayPort = Port();
        await using var backend = PairingTests.Backend(backendPort); await backend.StartAsync();
        await using var gateway = new PairingGateway(identity, () => true, (_, _) => Task.FromResult(true), backendPort);
        await gateway.StartAsync(IPAddress.Loopback, gatewayPort);
        await using var bridge = new RemoteBridge(gatewayPort); await bridge.StartAsync(0);
        var uri = new Uri($"ws://127.0.0.1:{bridge.Port}/bwdacc/tunnel");
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(50));
        var passed = new List<string>();
        using (var anonymous = new ClientWebSocket())
        {
            bool denied = false; try { await anonymous.ConnectAsync(uri, deadline.Token); } catch (WebSocketException) { denied = true; }
            Check(denied, "Anonymous tunnel accepted");
        }
        passed.Add("tunnel admission requires a separate capability");
        foreach (var bad in new[] { uri + "?target=8188", uri.ToString().Replace("/bwdacc/tunnel", "/manager/install") })
        {
            using var socket = new ClientWebSocket(); socket.Options.SetRequestHeader("Authorization", "Bearer " + bridge.AccessKey);
            bool denied = false; try { await socket.ConnectAsync(new Uri(bad), deadline.Token); } catch (WebSocketException) { denied = true; }
            Check(denied, "Dynamic destination or route accepted");
        }
        passed.Add("arbitrary routes and destinations refused");
        using (var socket = new ClientWebSocket())
        {
            socket.Options.SetRequestHeader("Authorization", "Bearer " + bridge.AccessKey); await socket.ConnectAsync(uri, deadline.Token);
            await socket.SendAsync(Encoding.ASCII.GetBytes("GET / HTTP/1.1\r\n\r\n"), WebSocketMessageType.Binary, true, deadline.Token);
            bool closed = false; try { var r = await socket.ReceiveAsync(new byte[32], deadline.Token); closed = r.MessageType == WebSocketMessageType.Close; } catch (WebSocketException) { closed = true; }
            Check(closed, "Plaintext tunnel accepted");
        }
        passed.Add("plaintext rejected before TLS gateway");
        using var observed = new MemoryStream();
        using var handler = new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false };
        handler.ConnectCallback = async (_, token) =>
        {
            var socket = new ClientWebSocket(); socket.Options.Proxy = null; socket.Options.SetRequestHeader("Authorization", "Bearer " + bridge.AccessKey);
            try { await socket.ConnectAsync(uri, token); return new Carrier(socket, observed); }
            catch { socket.Dispose(); throw; }
        };
        handler.SslOptions.RemoteCertificateValidationCallback = (_, cert, _, errors) =>
            cert is X509Certificate2 leaf && !errors.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch) &&
            Convert.ToHexStringLower(SHA256.HashData(leaf.PublicKey.ExportSubjectPublicKeyInfo())) == identity.Pin;
        using var client = new HttpClient(handler, false) { Timeout = TimeSpan.FromSeconds(15) };
        var invite = identity.CreateInvite(gateway.Endpoint!);
        using var response = await client.PostAsJsonAsync(gateway.Endpoint + "/bwdacc/pair", new { ticket = invite.Ticket, name = "Synthetic remote" });
        response.EnsureSuccessStatusCode();
        using var credential = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        var token = credential.RootElement.GetProperty("token").GetString()!; var id = credential.RootElement.GetProperty("deviceId").GetString()!;
        client.DefaultRequestHeaders.Authorization = new("Bearer", token);
        Check((await client.GetAsync(gateway.Endpoint + "/queue")).IsSuccessStatusCode, "Nested TLS request failed");
        const string canary = "REMOTE_SYNTHETIC_PROMPT_ONLY_98aaf1";
        using (var prompt = await client.PostAsJsonAsync(gateway.Endpoint + "/prompt", new { prompt = new { label = canary } }))
            Check((await prompt.Content.ReadAsStringAsync()).Contains(canary), "Nested prompt failed");
        using (var upload = await client.PostAsync(gateway.Endpoint + "/upload/image", new ByteArrayContent(new byte[1048576])))
            Check((await upload.Content.ReadAsStringAsync()).Contains("1048576"), "Large tunneled upload failed");
        Check((await client.GetByteArrayAsync(gateway.Endpoint + "/view?filename=synthetic")).Length == 6, "Media download failed");
        passed.Add("pairing, prompt, 1MiB upload and media through inner TLS");
        var wire = Encoding.Latin1.GetString(observed.ToArray());
        Check(!wire.Contains(canary) && !wire.Contains(token) && !wire.Contains(invite.Ticket), "Inner secrets leaked to relay");
        passed.Add("captured carrier bytes contain no plaintext prompt, pairing ticket or device token");
        using var progress = new ClientWebSocket(); progress.Options.SetRequestHeader("Authorization", "Bearer " + token);
        using var invoker = new HttpMessageInvoker(handler, false);
        await progress.ConnectAsync(new Uri(gateway.Endpoint!.Replace("https:", "wss:") + "/ws?clientId=remote-test"), invoker, deadline.Token);
        var message = new byte[4096]; Check((await progress.ReceiveAsync(message, deadline.Token)).Count > 0, "Progress over nested WSS unavailable");
        passed.Add("progress websocket works inside opaque carrier");
        identity.Revoke(id);
        Check((await client.GetAsync(gateway.Endpoint + "/queue")).StatusCode == HttpStatusCode.Unauthorized, "Revocation lost through relay");
        bool disconnected = false; try { disconnected = (await progress.ReceiveAsync(message, deadline.Token)).MessageType == WebSocketMessageType.Close; } catch (WebSocketException) { disconnected = true; }
        Check(disconnected, "Revoked progress stayed open");
        passed.Add("revocation rejects requests and aborts existing progress through relay");
        Data.Save(report, new { Passed = passed.Count, Cases = passed });
    }
}
