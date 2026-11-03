using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Server.Kestrel.Core;
using Microsoft.AspNetCore.Http.Features;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging;

namespace Bwdacc.Desktop;

/// <summary>A narrow, authenticated TLS gateway. ComfyUI itself remains bound to loopback.</summary>
public sealed class PairingGateway : IAsyncDisposable
{
    public PairingIdentity Identity { get; }
    private readonly Func<bool> ownsBackend;
    private readonly int backendPort;
    private readonly Func<string, CancellationToken, Task<bool>> approve;
    private readonly HttpClient client = new(new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false }) { Timeout = Timeout.InfiniteTimeSpan };
    private readonly CancellationTokenSource stopped = new();
    private WebApplication? app;
    private X509Certificate2? certificate;
    private readonly object attemptsLock = new();
    private DateTimeOffset attemptWindow = DateTimeOffset.UtcNow;
    private readonly Dictionary<IPAddress, int> attempts = new();
    private bool disposed;
    public string? Endpoint { get; private set; }
    public bool Running => app != null;
    public PairingGateway(PairingIdentity identity, Func<bool> ownsBackend, Func<string, CancellationToken, Task<bool>> approve, int backendPort = 8189)
    { Identity = identity; this.ownsBackend = ownsBackend; this.approve = approve; this.backendPort = backendPort; }

    public static bool IsLocalAddress(IPAddress address)
    {
        if (address.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = address.GetAddressBytes();
        return IPAddress.IsLoopback(address) || b[0] == 10 || b[0] == 192 && b[1] == 168 || b[0] == 172 && b[1] >= 16 && b[1] <= 31;
    }
    public static IPAddress[] LanAddresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up && n.NetworkInterfaceType is NetworkInterfaceType.Ethernet or NetworkInterfaceType.Wireless80211)
        .SelectMany(n => n.GetIPProperties().UnicastAddresses.Select(a => a.Address))
        .Where(a => IsLocalAddress(a) && !IPAddress.IsLoopback(a)).Distinct().ToArray();

    public async Task StartAsync(IPAddress address, int port = 8190)
    {
        if (app != null) throw new InvalidOperationException("Conexão já iniciada.");
        if (!PrivateNetwork.IsAllowed(address) || port is < 1024 or > 65535 ||
            PrivateNetwork.IsTailscaleRange(address) && !PrivateNetwork.IsAssigned(address))
            throw new IOException("Escolha um IPv4 local ou atribuído ao adaptador Tailscale deste PC.");
        certificate = Identity.Certificate(address);
        var builder = WebApplication.CreateEmptyBuilder(new WebApplicationOptions { ApplicationName = typeof(PairingGateway).Assembly.FullName });
        builder.Logging.ClearProviders(); // Never record pairing tickets, credentials, query strings or media.
        builder.WebHost.UseKestrel(options =>
        {
            options.AddServerHeader = false;
            options.Limits.MaxConcurrentConnections = 32;
            options.Limits.MaxConcurrentUpgradedConnections = 16;
            options.Limits.MaxRequestBodySize = 512L * 1024 * 1024;
            options.Limits.MaxRequestLineSize = 8192;
            options.Limits.RequestHeadersTimeout = TimeSpan.FromSeconds(15);
            options.Limits.KeepAliveTimeout = TimeSpan.FromMinutes(1);
            void Configure(Microsoft.AspNetCore.Server.Kestrel.Core.ListenOptions listen)
            {
                listen.Protocols = HttpProtocols.Http1;
                listen.UseHttps(tls => { tls.ServerCertificate = certificate; tls.SslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13; tls.HandshakeTimeout = TimeSpan.FromSeconds(10); });
            }
            options.Listen(address, port, Configure);
            if (!IPAddress.IsLoopback(address)) options.Listen(IPAddress.Loopback, port, Configure);
        });
        var candidate = builder.Build(); candidate.UseWebSockets(new WebSocketOptions { KeepAliveInterval = TimeSpan.FromSeconds(20) });
        candidate.Run(HandleAsync);
        try { await candidate.StartAsync(); app = candidate; Endpoint = $"https://{address}:{port}"; }
        catch { await candidate.DisposeAsync(); certificate.Dispose(); certificate = null; throw; }
    }
    private static async Task Error(HttpContext context, int code, string message)
    { context.Response.StatusCode = code; await context.Response.WriteAsJsonAsync(new { error = message }, context.RequestAborted); }
    private async Task HandleAsync(HttpContext context)
    {
        context.Response.Headers.CacheControl = "no-store";
        context.Response.Headers["X-Content-Type-Options"] = "nosniff";
        try
        {
            if (context.Request.Headers.ContainsKey("Origin")) { await Error(context, 403, "Browser access is disabled."); return; }
            if (context.Request.Path == "/bwdacc/pair" && HttpMethods.IsPost(context.Request.Method)) { await PairAsync(context); return; }
            var credential = context.Request.Headers.Authorization.Count == 1 ? Identity.Authenticate(context.Request.Headers.Authorization.ToString()) : null;
            if (credential == null) { await Error(context, 401, "Pair this device with Desktop."); return; }
            using var lifetime = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted, stopped.Token, credential.Value.Revoked);
            using var abort = lifetime.Token.Register(context.Abort);
            if (context.Request.Path == "/bwdacc/status" && HttpMethods.IsGet(context.Request.Method))
            { await context.Response.WriteAsJsonAsync(new { paired = true, engineReady = ownsBackend(), deviceId = credential.Value.Id }, lifetime.Token); return; }
            if (!Allowed(context.Request.Method, context.Request.Path.Value ?? "")) { await Error(context, 404, "Route unavailable."); return; }
            if (!ownsBackend()) { await Error(context, 503, "Check the selected ComfyUI connection in Desktop."); return; }
            if (context.Request.Path == "/ws") { await BridgeSocket(context, lifetime.Token); return; }
            await Forward(context, lifetime.Token);
        }
        catch (OperationCanceledException) { context.Abort(); }
        catch (JsonException) { if (!context.Response.HasStarted) await Error(context, 400, "Invalid JSON."); else context.Abort(); }
        catch (Microsoft.AspNetCore.Http.BadHttpRequestException ex) { if (!context.Response.HasStarted) await Error(context, ex.StatusCode, "Request rejected."); else context.Abort(); }
        catch (Exception) { if (!context.Response.HasStarted) await Error(context, 502, "Desktop could not complete this request."); else context.Abort(); }
    }
    private async Task PairAsync(HttpContext context)
    {
        lock (attemptsLock)
        {
            var now = DateTimeOffset.UtcNow;
            if (now - attemptWindow > TimeSpan.FromMinutes(1)) { attempts.Clear(); attemptWindow = now; }
            var source = context.Connection.RemoteIpAddress;
            if (source == null || !attempts.ContainsKey(source) && attempts.Count >= 128) { context.Response.StatusCode = 429; return; }
            var count = attempts.GetValueOrDefault(source) + 1; attempts[source] = count;
            if (count > 10) { context.Response.StatusCode = 429; return; }
        }
        context.Features.Get<IHttpMaxRequestBodySizeFeature>()!.MaxRequestBodySize = 4096;
        if (!context.Request.HasJsonContentType()) { await Error(context, 415, "Expected JSON."); return; }
        using var body = await JsonDocument.ParseAsync(context.Request.Body, new JsonDocumentOptions { MaxDepth = 4 }, context.RequestAborted);
        if (!body.RootElement.TryGetProperty("ticket", out var ticket) || ticket.ValueKind != JsonValueKind.String || !Identity.ConsumeInvite(ticket.GetString()!))
        { await Error(context, 403, "Pairing code expired or already used."); return; }
        var name = body.RootElement.TryGetProperty("name", out var n) && n.ValueKind == JsonValueKind.String ? n.GetString()! : "Android";
        name = string.Concat(name.Where(c => !char.IsControl(c))).Trim(); name = name[..Math.Min(name.Length, 60)];
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(context.RequestAborted, stopped.Token); timeout.CancelAfter(TimeSpan.FromSeconds(60));
        if (!await approve(name, timeout.Token)) { await Error(context, 403, "Pairing declined on Desktop."); return; }
        timeout.Token.ThrowIfCancellationRequested();
        var credential = Identity.Add(name);
        await context.Response.WriteAsJsonAsync(new { deviceId = credential.DeviceId, token = credential.Token, desktopPin = Identity.Pin }, timeout.Token);
    }
    internal static bool Allowed(string method, string path)
    {
        if (method == "POST") return path is "/prompt" or "/upload/image";
        if (method != "GET") return false;
        if (path is "/system_stats" or "/queue" or "/object_info" or "/view" or "/momentos/loras/metadata" or "/rgthree/api/loras/info" or "/ws") return true;
        return Regex.IsMatch(path, "^/history/[a-zA-Z0-9_-]{1,128}$") || Regex.IsMatch(path, "^/object_info/[a-zA-Z0-9_ ().-]{1,160}$");
    }
    private async Task Forward(HttpContext context, CancellationToken token)
    {
        var path = context.Request.Path.Value!;
        if (path == "/prompt") context.Features.Get<IHttpMaxRequestBodySizeFeature>()!.MaxRequestBodySize = 10_000_000;
        using var upstream = new HttpRequestMessage(new HttpMethod(context.Request.Method), $"http://127.0.0.1:{backendPort}" + path + context.Request.QueryString);
        if (HttpMethods.IsPost(context.Request.Method))
        {
            upstream.Content = new StreamContent(context.Request.Body);
            if (context.Request.ContentType != null) upstream.Content.Headers.TryAddWithoutValidation("Content-Type", context.Request.ContentType);
        }
        if (Regex.IsMatch(context.Request.Headers.Range.ToString(), @"^bytes=\d+-\d*$")) upstream.Headers.TryAddWithoutValidation("Range", context.Request.Headers.Range.ToString());
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token); timeout.CancelAfter(TimeSpan.FromMinutes(10));
        using var response = await client.SendAsync(upstream, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
        // Never follow or return an upstream redirect to the authenticated client.
        if ((int)response.StatusCode is >= 300 and < 400) { await Error(context, 502, "Upstream redirect refused."); return; }
        context.Response.StatusCode = (int)response.StatusCode;
        context.Response.ContentType = response.Content.Headers.ContentType?.ToString() ?? "application/octet-stream";
        context.Response.ContentLength = response.Content.Headers.ContentLength;
        if (response.Content.Headers.ContentRange != null) context.Response.Headers.ContentRange = response.Content.Headers.ContentRange.ToString();
        if (response.Headers.AcceptRanges.Count > 0) context.Response.Headers.AcceptRanges = string.Join(",", response.Headers.AcceptRanges);
        await response.Content.CopyToAsync(context.Response.Body, timeout.Token);
    }
    private async Task BridgeSocket(HttpContext context, CancellationToken token)
    {
        if (!context.WebSockets.IsWebSocketRequest) { await Error(context, 400, "Expected WebSocket."); return; }
        var id = context.Request.Query["clientId"].ToString();
        if (!Regex.IsMatch(id, "^[a-zA-Z0-9_-]{1,128}$")) { await Error(context, 400, "Invalid client ID."); return; }
        using var upstream = new ClientWebSocket(); upstream.Options.Proxy = null;
        using var handshake = CancellationTokenSource.CreateLinkedTokenSource(token); handshake.CancelAfter(TimeSpan.FromSeconds(10));
        await upstream.ConnectAsync(new Uri($"ws://127.0.0.1:{backendPort}/ws?clientId=" + id), handshake.Token);
        using var downstream = await context.WebSockets.AcceptWebSocketAsync();
        using var linked = CancellationTokenSource.CreateLinkedTokenSource(token);
        var a = Pump(upstream, downstream, linked.Token); var b = Pump(downstream, upstream, linked.Token);
        await Task.WhenAny(a, b); linked.Cancel(); upstream.Abort(); downstream.Abort();
        try { await Task.WhenAll(a, b); } catch (OperationCanceledException) { } catch (WebSocketException) { }
    }
    private static async Task Pump(WebSocket input, WebSocket output, CancellationToken token)
    {
        var buffer = new byte[64 * 1024]; long messageBytes = 0;
        while (!token.IsCancellationRequested)
        {
            var part = await input.ReceiveAsync(buffer.AsMemory(), token);
            if (part.MessageType == WebSocketMessageType.Close) return;
            messageBytes += part.Count; if (messageBytes > 8L * 1024 * 1024) throw new IOException("WebSocket message limit.");
            await output.SendAsync(buffer.AsMemory(0, part.Count), part.MessageType, part.EndOfMessage, token);
            if (part.EndOfMessage) messageBytes = 0;
        }
    }
    public async ValueTask DisposeAsync()
    {
        if (disposed) return; disposed = true;
        Identity.CancelInvite(); stopped.Cancel();
        if (app != null) { using var timeout = new CancellationTokenSource(TimeSpan.FromSeconds(5)); await app.StopAsync(timeout.Token); await app.DisposeAsync(); app = null; }
        Endpoint = null; certificate?.Dispose(); certificate = null; client.Dispose(); stopped.Dispose();
    }
}
