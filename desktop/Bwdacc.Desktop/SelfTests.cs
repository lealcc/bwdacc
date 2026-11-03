using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace Bwdacc.Desktop;

public static class SelfTests
{
    public static async Task RunSetupAsync(string report)
    {
        const string key = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\bwdaccDesktopPreview";
        var shortcut = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "bwdacc Desktop Preview.lnk");
        using (var existing = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(key))
            Check(!Directory.Exists(DesktopSetup.InstallRoot) && existing == null && !File.Exists(shortcut), "Setup smoke requires no existing Desktop Preview installation");
        // Exercise the real per-user installer only when no prior installation exists.
        // Runtime/model/media storage is outside this fixed program directory.
        await DesktopSetup.InstallAsync(default);
        var sourceHash = await Downloads.HashAsync(Environment.ProcessPath!, default);
        var target = Paths.Under(DesktopSetup.InstallRoot, "releases/" + sourceHash[..16] + "/bwdacc-desktop.exe");
        Check(await Downloads.HashAsync(target, default) == sourceHash, "Installed executable hash mismatch");
        using (var installed = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(key))
            Check(installed?.GetValue("UninstallString") as string == "\"" + target + "\" --uninstall", "Uninstall registration mismatch");
        var shellType = Type.GetTypeFromProgID("WScript.Shell")!;
        dynamic shell = Activator.CreateInstance(shellType)!; dynamic link = shell.CreateShortcut(shortcut);
        Check(File.Exists(shortcut) && string.Equals((string)link.TargetPath, target, StringComparison.OrdinalIgnoreCase), "Start shortcut target mismatch");
        System.Runtime.InteropServices.Marshal.FinalReleaseComObject(link); System.Runtime.InteropServices.Marshal.FinalReleaseComObject(shell);
        DesktopSetup.RemoveInstalled(int.MaxValue); // No parent process exists in this in-process test.
        Check(!Directory.Exists(DesktopSetup.InstallRoot) && !File.Exists(shortcut), "Manager removal left program files or shortcut");
        using (var removed = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(key)) Check(removed == null, "Uninstall registration left behind");
        await DesktopSetup.InstallAsync(default);
        Check(await Downloads.HashAsync(target, default) == sourceHash, "Reinstalled executable hash mismatch");
        Data.Save(report, new { Passed = true, ShortcutVerified = true, RegistrationVerified = true, RemovalPassed = true, ReinstallationPassed = true, InstalledExecutable = target });
    }
    private static void Check(bool value, string message) { if (!value) throw new Exception(message); }
    private static void Reject(Action action) { try { action(); } catch (IOException) { return; } throw new Exception("Expected rejection"); }
    private static async Task RejectAsync(Func<Task> action) { try { await action(); } catch (IOException) { return; } throw new Exception("Expected async rejection"); }
    private sealed class Handler(Func<HttpRequestMessage, HttpResponseMessage> action) : HttpMessageHandler
    { protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken token) { token.ThrowIfCancellationRequested(); var response = action(request); response.RequestMessage = request; return Task.FromResult(response); } }
    public static async Task RunAsync(string report)
    {
        var root = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "cases-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(root);
        var passed = new List<string>();
        var visuals=new Catalog().Choices("8",root).Single(m=>m.Family=="vision");
        Check(visuals.Files.Any(f=>f.Destination=="models/segmentation/efficientsam3_efficientvit.pt"),"Missing SAM checkpoint");
        Check(visuals.Files.Any(f=>f.Destination=="models/vision/florence-2-base-ft/model.safetensors"),"Missing Florence checkpoint");
        foreach(var file in visuals.Files){Check(file.Destination.StartsWith("models/",StringComparison.Ordinal),"Visual resource outside model root");Paths.Under(root,file.Destination);}
        passed.Add("visual resources use the registered model roots");
        foreach (var path in new[] { "../outside", "folder/../../outside", "C:/outside", "file:stream", "CON.txt", "folder/name.", "\\\\host\\share", "a//b" }) Reject(() => Paths.Under(root, path));
        passed.Add("path traversal, ADS, reserved names and absolute paths rejected");
        var foreign = Path.Combine(root, "foreign"); Directory.CreateDirectory(foreign); File.WriteAllText(Path.Combine(foreign, "sentinel"), "keep"); Reject(() => Paths.PrepareRoot(foreign));
        Check(File.ReadAllText(Path.Combine(foreign, "sentinel")) == "keep", "Foreign directory mutated"); passed.Add("existing unmanaged directory preserved");
        var zipPath = Path.Combine(root, "bad.zip");
        using (var zip = ZipFile.Open(zipPath, ZipArchiveMode.Create)) { using var writer = new StreamWriter(zip.CreateEntry("../escaped.txt").Open()); writer.Write("unsafe"); }
        Reject(() => Archives.Extract(zipPath, Path.Combine(root, "extract"), false)); Check(!File.Exists(Path.Combine(root, "escaped.txt")), "Zip slip"); passed.Add("zip traversal rejected before extraction");
        var links = Path.Combine(root, "links.zip");
        using (var zip = ZipFile.Open(links, ZipArchiveMode.Create)) { var entry = zip.CreateEntry("link"); entry.ExternalAttributes = unchecked((int)0xA1FF0000); }
        Reject(() => Archives.Extract(links, Path.Combine(root, "link-out"), false)); passed.Add("archive symbolic links rejected");
        var good = Path.Combine(root, "good.zip");
        using (var zip = ZipFile.Open(good, ZipArchiveMode.Create)) { zip.CreateEntry("repo/"); using var writer = new StreamWriter(zip.CreateEntry("repo/file.txt").Open()); writer.Write("good"); }
        Archives.Extract(good, Path.Combine(root, "good-out"), true); Check(File.ReadAllText(Path.Combine(root, "good-out/file.txt")) == "good", "Root strip failed");
        Reject(() => Archives.Extract(good, Path.Combine(root, "too-large"), true, 1)); passed.Add("archive root stripping and expansion limit");
        var bytes = Encoding.UTF8.GetBytes("verified payload"); var hash = Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant(); var artifact = new Artifact("https://example.invalid/model", hash, bytes.Length);
        HttpResponseMessage Full() => new(HttpStatusCode.OK) { Content = new ByteArrayContent(bytes) };
        using (var downloads = new Downloads(new Handler(_ => Full()))) { var file = Path.Combine(root, "fresh"); await downloads.FetchAsync(artifact, file, null, default); Check(File.ReadAllBytes(file).SequenceEqual(bytes), "Fresh content"); }
        passed.Add("HTTPS download verified before publication");
        var resumed = Path.Combine(root, "resumed"); File.WriteAllBytes(resumed + ".partial", bytes[..4]);
        using (var downloads = new Downloads(new Handler(request => { Check(request.Headers.Range?.Ranges.First().From == 4, "Missing Range"); var response = new HttpResponseMessage(HttpStatusCode.PartialContent) { Content = new ByteArrayContent(bytes[4..]) }; response.Content.Headers.ContentRange = new ContentRangeHeaderValue(4, bytes.Length - 1, bytes.Length); return response; }))) await downloads.FetchAsync(artifact, resumed, null, default);
        Check(File.ReadAllBytes(resumed).SequenceEqual(bytes), "Resume content"); passed.Add("partial download resumes with verified range");
        var ignored = Path.Combine(root, "ignored-range"); File.WriteAllBytes(ignored + ".partial", bytes[..4]);
        using (var downloads = new Downloads(new Handler(_ => Full()))) await downloads.FetchAsync(artifact, ignored, null, default);
        Check(File.ReadAllBytes(ignored).SequenceEqual(bytes), "Range ignored concatenated"); passed.Add("server ignoring range restarts without concatenation");
        var bad = Path.Combine(root, "bad-hash");
        using (var downloads = new Downloads(new Handler(_ => Full()))) await RejectAsync(() => downloads.FetchAsync(artifact with { Sha256 = new string('0', 64) }, bad, null, default));
        Check(!File.Exists(bad) && !File.Exists(bad + ".partial"), "Corrupt output published"); passed.Add("hash failure removes partial and never publishes");
        var existing = Path.Combine(root, "existing"); File.WriteAllText(existing, "keep");
        using (var downloads = new Downloads(new Handler(_ => throw new Exception("Should not connect")))) await RejectAsync(() => downloads.FetchAsync(artifact, existing, null, default));
        Check(File.ReadAllText(existing) == "keep", "Existing output replaced"); passed.Add("unrecognized existing output preserved");
        var cancelled = Path.Combine(root, "cancelled"); File.WriteAllBytes(cancelled + ".partial", bytes[..4]); using var cancel = new CancellationTokenSource(); cancel.Cancel();
        using (var downloads = new Downloads(new Handler(_ => Full()))) { try { await downloads.FetchAsync(artifact, cancelled, null, cancel.Token); throw new Exception("Cancellation ignored"); } catch (OperationCanceledException) { } }
        Check(!File.Exists(cancelled) && File.ReadAllBytes(cancelled + ".partial").Length == 4, "Cancelled partial lost"); passed.Add("cancellation retains resumable partial only");
        var runtime = Path.Combine(root, "runtime"); Paths.PrepareRoot(runtime); var installer = new RuntimeInstaller(runtime);
        Directory.CreateDirectory(installer.ReleasePath("v1")); Data.Save(Path.Combine(installer.ReleasePath("v1"), "complete.json"), new { Ok = true });
        Data.Save(Path.Combine(runtime, "active.json"), new RuntimeState("v2", "v1")); installer.Rollback(); Check(installer.State?.Active == "v1", "Rollback failed");
        Data.Save(Path.Combine(runtime, "active.json"), new RuntimeState("v1", "incomplete")); Reject(installer.Rollback); Check(installer.State?.Active == "v1", "Invalid rollback changed active"); passed.Add("rollback requires validated prior version and preserves pointer on failure");
        var model = Path.Combine(root, "test.safetensors"); var header = Encoding.UTF8.GetBytes("{\"weight\":{\"dtype\":\"F32\",\"shape\":[1],\"data_offsets\":[0,4]}}");
        using (var stream = File.Create(model)) { stream.Write(BitConverter.GetBytes((ulong)header.Length)); stream.Write(header); stream.Write(new byte[4]); }
        await RejectAsync(() => Loras.ImportAsync(runtime, model, "../../outside", default));
        await Loras.ImportAsync(runtime, model, "krea2", default); await RejectAsync(() => Loras.ImportAsync(runtime, model, "krea2", default)); passed.Add("LoRA family containment and no overwrite");
        var signal = Path.Combine(root, "child-pid");
        using (var probe = Process.Start(new ProcessStartInfo(Environment.ProcessPath!) { UseShellExecute = false, CreateNoWindow = true, ArgumentList = { "--job-probe", signal } })!)
        {
            try {
                for (var i = 0; i < 100 && !File.Exists(signal); i++) await Task.Delay(100);
                Check(File.Exists(signal), "Probe failed to start"); var pid = int.Parse(File.ReadAllText(signal));
                probe.Kill(); await probe.WaitForExitAsync();
                try { using var child = Process.GetProcessById(pid); Check(child.WaitForExit(5000), "Owned child survived manager crash"); } catch (ArgumentException) { }
            } finally { if (!probe.HasExited) { probe.Kill(true); await probe.WaitForExitAsync(); } }
        }
        passed.Add("owned child terminates after abrupt manager crash");
        var listener = new System.Net.Sockets.TcpListener(IPAddress.Loopback, 0); listener.Start();
        try { var port = ((System.Net.IPEndPoint)listener.LocalEndpoint).Port; Check(TcpOwner.Listener(port) == Environment.ProcessId, "TCP owner identification failed"); }
        finally { listener.Stop(); }
        passed.Add("local TCP listener ownership is verified");
        Data.Save(report, new { Passed = passed.Count, Cases = passed, Artifacts = root });
    }
}
