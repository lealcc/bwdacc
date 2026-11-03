using System.Diagnostics;
using System.IO;
using System.Net.WebSockets;
using System.Text.RegularExpressions;

namespace Bwdacc.Desktop;

/// <summary>Named or temporary carrier. Pinned executable, owned child, no global service.</summary>
public sealed class CloudflareTunnel : IAsyncDisposable
{
    public const string Version = "2026.10.7";
    private static readonly Artifact Binary = new(
        "https://github.com/cloudflare/cloudflared/releases/download/2026.10.7/cloudflared-windows-amd64.exe",
        "2493dbc168923bb3e59e5e7716f71deced92992eea9acd6e9d74f84ac7751343", 55390512);
    private Process? child;
    private ProcessJob? job;
    private Task? stdout, stderr;
    public string? Endpoint { get; private set; }
    public bool Running => child is { HasExited: false };
    public async Task StartAsync(int bridgePort, string admissionKey, IProgress<double>? progress, CancellationToken token, RemoteAccessProfile? profile = null)
    {
        profile?.Validate();
        var directory = Path.Combine(Paths.SettingsRoot, "transport", Version);
        var executable = Path.Combine(directory, "cloudflared.exe");
        using (var download = new Downloads()) await download.FetchAsync(Binary, executable, progress, token);
        // Isolated empty config: do not adopt a user's cloudflared account/tunnels.
        var config = Path.Combine(directory, "preview.yml");
        Paths.NoLinks(config); await File.WriteAllTextAsync(config, "{}\n", token);
        var found = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);
        job = new ProcessJob();
        child = new Process { StartInfo = StartInfo(executable, config, directory, bridgePort, profile), EnableRaisingEvents = true };
        // Ignore inherited Cloudflare credentials/config overrides.
        child.Exited += (_, _) => { if (profile == null) found.TrySetException(new IOException("Conexão remota encerrou. Tente ativar novamente.")); };
        try
        {
            child.Start(); child.StartInfo.Environment.Remove("TUNNEL_TOKEN"); job.Attach(child);
            async Task Drain(StreamReader reader)
            {
                string? line;
                while ((line = await reader.ReadLineAsync()) != null)
                {
                    // Never retain logs, headers or the invitation. Only accept provider host syntax.
                    var match = Regex.Match(line, @"https://[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com\b");
                    if (profile == null && match.Success) found.TrySetResult(match.Value);
                }
            }
            stdout = Drain(child.StandardOutput); stderr = Drain(child.StandardError);
            Endpoint = profile?.Endpoint ?? await found.Task.WaitAsync(TimeSpan.FromSeconds(60), token);
            if (!Running) throw new IOException("Conexão remota indisponível.");
            // The URL is printed before DNS and the edge route are ready. Do not
            // issue a short-lived QR until the actual carrier accepts a socket.
            using var ready = CancellationTokenSource.CreateLinkedTokenSource(token);
            ready.CancelAfter(TimeSpan.FromSeconds(90));
            while (true)
            {
                ready.Token.ThrowIfCancellationRequested();
                if (!Running) throw new IOException("Conexão remota encerrou durante a ativação.");
                using var probe = new ClientWebSocket(); probe.Options.Proxy = null;
                probe.Options.SetRequestHeader("Authorization", "Bearer " + admissionKey);
                using var attempt = CancellationTokenSource.CreateLinkedTokenSource(ready.Token);
                attempt.CancelAfter(TimeSpan.FromSeconds(8));
                try
                {
                    await probe.ConnectAsync(new Uri(Endpoint.Replace("https:", "wss:") + "/bwdacc/tunnel"), attempt.Token);
                    probe.Abort(); break;
                }
                catch (Exception error) when (error is WebSocketException or OperationCanceledException)
                { await Task.Delay(2000, ready.Token); }
            }
        }
        catch { await DisposeAsync(); throw; }
    }
    internal static ProcessStartInfo StartInfo(string executable, string config, string directory, int bridgePort, RemoteAccessProfile? profile)
    {
        profile?.Validate();
        var args = new List<string> { "tunnel", "--config", config, "--no-autoupdate", "--protocol", "http2", "--metrics", "127.0.0.1:0" };
        if (profile == null) { args.Add("--url"); args.Add($"http://127.0.0.1:{bridgePort}"); }
        else args.Add("run");
        var info = Processes.Info(executable, args, directory, null);
        foreach (var name in info.Environment.Keys.Where(k => k.StartsWith("TUNNEL_", StringComparison.OrdinalIgnoreCase) || k.StartsWith("CLOUDFLARE_", StringComparison.OrdinalIgnoreCase)).ToArray())
            info.Environment.Remove(name);
        // Provider credential is never placed on the command line or in a plaintext file.
        if (profile != null) info.Environment["TUNNEL_TOKEN"] = profile.TunnelToken;
        return info;
    }
    public async ValueTask DisposeAsync()
    {
        job?.Dispose(); job = null;
        if (child != null)
        {
            try { if (!child.HasExited) child.Kill(true); await child.WaitForExitAsync(); } catch { }
            child.Dispose(); child = null;
        }
        if (stdout != null) try { await stdout; } catch { }
        if (stderr != null) try { await stderr; } catch { }
        Endpoint = null;
    }
}
