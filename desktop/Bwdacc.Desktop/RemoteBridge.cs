using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Text;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging;

namespace Bwdacc.Desktop;

/// <summary>Opaque TLS byte transport. Never decrypts the inner Desktop connection.</summary>
public sealed class RemoteBridge : IAsyncDisposable
{
    private readonly int targetPort;
    private readonly CancellationTokenSource stop = new();
    private readonly SemaphoreSlim slots = new(24);
    private WebApplication? app;
    private readonly object rateLock = new();
    private DateTimeOffset window = DateTimeOffset.UtcNow;
    private int attempts;
    public string AccessKey { get; }
    public int Port { get; private set; }
    public RemoteBridge(int targetPort, string? accessKey = null)
    {
        this.targetPort = targetPort; AccessKey = accessKey ?? RemoteAccessProfile.NewAdmissionKey();
        if (!System.Text.RegularExpressions.Regex.IsMatch(AccessKey, @"\A[A-Za-z0-9_-]{43}\z")) throw new IOException("Chave de transporte inválida.");
    }

    public async Task StartAsync(int port = 8191)
    {
        if (app != null || TcpOwner.Listener(targetPort) != Environment.ProcessId)
            throw new IOException("Gateway seguro indisponível. Ative a conexão do Desktop.");
        var builder = WebApplication.CreateEmptyBuilder(new WebApplicationOptions { ApplicationName = typeof(RemoteBridge).Assembly.FullName });
        builder.Logging.ClearProviders();
        builder.WebHost.UseKestrel(o =>
        {
            o.AddServerHeader = false; o.Limits.MaxConcurrentConnections = 32; o.Limits.MaxConcurrentUpgradedConnections = 24;
            o.Limits.MaxRequestBodySize = 0; o.Limits.RequestHeadersTimeout = TimeSpan.FromSeconds(10);
            o.Listen(IPAddress.Loopback, port);
        });
        var candidate = builder.Build();
        candidate.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20), KeepAliveTimeout = TimeSpan.FromSeconds(30) });
        candidate.Run(Handle);
        try { await candidate.StartAsync(); app = candidate; Port = new Uri(candidate.Urls.Single()).Port; }
        catch { await candidate.DisposeAsync(); throw; }
    }
    private async Task Handle(HttpContext context)
    {
        context.Response.Headers.CacheControl = "no-store";
        if (context.Request.Path != "/bwdacc/tunnel" || context.Request.QueryString.HasValue ||
            context.Request.Headers.ContainsKey("Origin") || !context.WebSockets.IsWebSocketRequest)
        { context.Response.StatusCode = 404; return; }
        var expected = Encoding.ASCII.GetBytes("Bearer " + AccessKey);
        var actual = Encoding.ASCII.GetBytes(context.Request.Headers.Authorization.ToString());
        if (context.Request.Headers.Authorization.Count != 1 || !CryptographicOperations.FixedTimeEquals(expected, actual))
        { context.Response.StatusCode = 401; return; }
        lock (rateLock)
        {
            if (DateTimeOffset.UtcNow - window > TimeSpan.FromMinutes(1)) { window = DateTimeOffset.UtcNow; attempts = 0; }
            if (++attempts > 120) { context.Response.StatusCode = 429; return; }
        }
        if (!await slots.WaitAsync(0)) { context.Response.StatusCode = 429; return; }
        try
        {
            // Fixed, owned destination: no URL, host or port is accepted from a client.
            if (TcpOwner.Listener(targetPort) != Environment.ProcessId) { context.Response.StatusCode = 503; return; }
            using var lifetime = CancellationTokenSource.CreateLinkedTokenSource(stop.Token, context.RequestAborted);
            lifetime.CancelAfter(TimeSpan.FromMinutes(30));
            using var tcp = new TcpClient { NoDelay = true };
            using (var handshake = CancellationTokenSource.CreateLinkedTokenSource(lifetime.Token))
            { handshake.CancelAfter(TimeSpan.FromSeconds(10)); await tcp.ConnectAsync(IPAddress.Loopback, targetPort, handshake.Token); }
            using var ws = await context.WebSockets.AcceptWebSocketAsync();
            using var abort = lifetime.Token.Register(() => { tcp.Dispose(); ws.Abort(); });
            var stream = tcp.GetStream();
            async Task ToDesktop()
            {
                var bytes = new byte[16384]; var first = true; long messageSize = 0;
                while (true)
                {
                    var read = await ws.ReceiveAsync(bytes.AsMemory(), lifetime.Token);
                    if (read.MessageType == WebSocketMessageType.Close) return;
                    if (read.MessageType != WebSocketMessageType.Binary || (messageSize += read.Count) > 65536)
                        throw new IOException("Invalid tunnel frame.");
                    if (read.Count > 0)
                    {
                        // Refuse plaintext HTTP before it can reach the TLS listener.
                        if (first && bytes[0] != 0x16) throw new IOException("TLS required.");
                        first = false; await stream.WriteAsync(bytes.AsMemory(0, read.Count), lifetime.Token);
                    }
                    if (read.EndOfMessage) messageSize = 0;
                }
            }
            async Task ToPhone()
            {
                var bytes = new byte[16384]; int count;
                while ((count = await stream.ReadAsync(bytes, lifetime.Token)) > 0)
                    await ws.SendAsync(bytes.AsMemory(0, count), WebSocketMessageType.Binary, true, lifetime.Token);
            }
            var up = ToDesktop(); var down = ToPhone();
            await Task.WhenAny(up, down); await lifetime.CancelAsync();
            try { await Task.WhenAll(up, down); } catch { }
        }
        catch (Exception) { context.Abort(); }
        finally { slots.Release(); }
    }
    public async ValueTask DisposeAsync()
    {
        await stop.CancelAsync();
        if (app != null) { await app.StopAsync(); await app.DisposeAsync(); app = null; }
        stop.Dispose();
    }
}
