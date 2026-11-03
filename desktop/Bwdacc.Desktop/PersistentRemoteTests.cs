using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Json;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace Bwdacc.Desktop;

internal static class PersistentRemoteTests
{
    private static void Check(bool condition, string message) { if (!condition) throw new IOException(message); }
    private static int Port() { using var listener = new TcpListener(IPAddress.Loopback, 0); listener.Start(); return ((IPEndPoint)listener.LocalEndpoint).Port; }
    public static async Task RunAsync(string report)
    {
        var root = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "persistent-" + Guid.NewGuid().ToString("N"));
        var store = new RemoteAccessStore(Path.Combine(root, "settings"));
        var profile = new RemoteAccessProfile("https://studio.bwdacc.app", new string('Z', 128), RemoteAccessProfile.NewAdmissionKey(), true);
        store.Save(profile);
        var loaded = new RemoteAccessStore(Path.Combine(root, "settings")).Load()!;
        Check(loaded == profile, "Remote profile did not survive reopening");
        var disk = Encoding.Latin1.GetString(File.ReadAllBytes(Path.Combine(root, "settings", "remote.dpapi")));
        Check(!disk.Contains(profile.TunnelToken) && !disk.Contains(profile.AdmissionKey) && !disk.Contains(profile.Endpoint), "Plaintext remote configuration on disk");
        var passed = new List<string> { "fixed endpoint, provider credential and admission persist encrypted" };
        foreach (var endpoint in new[] { "http://studio.bwdacc.app", "https://127.0.0.1", "https://localhost", "https://pc.local", "https://studio.bwdacc.app:443", "https://studio.bwdacc.app/path", "https://user@studio.bwdacc.app", "https://studio.bwdacc.app?key=x", "https://temporary.trycloudflare.com" })
        {
            bool rejected = false; try { (profile with { Endpoint = endpoint }).Validate(); } catch { rejected = true; }
            Check(rejected, "Unsafe or temporary fixed profile accepted");
        }
        passed.Add("fixed profiles reject temporary addresses and non-public URL forms");
        var info = CloudflareTunnel.StartInfo("test.exe", "preview.yml", root, 8191, profile);
        Check(info.ArgumentList.Contains("run") && !info.ArgumentList.Contains("--url") && !info.ArgumentList.Contains("--token") &&
            !string.Join(" ", info.ArgumentList).Contains(profile.TunnelToken) && info.Environment["TUNNEL_TOKEN"] == profile.TunnelToken,
            "Provider credential exposed in command arguments");
        passed.Add("named tunnel uses provider token only in child environment");
        using var deadline = new CancellationTokenSource(TimeSpan.FromSeconds(45));
        var backendPort = Port(); var bridgePort = Port();
        await using var backend = PairingTests.Backend(backendPort); await backend.StartAsync();
        string credential, pin, id;
        using (var identity = new PairingIdentity(Path.Combine(root, "identity")))
        {
            pin = identity.Pin;
            await using var gateway = new PairingGateway(identity, () => true, (_, _) => Task.FromResult(true), backendPort);
            await gateway.StartAsync(IPAddress.Loopback, Port());
            await using var bridge = new RemoteBridge(new Uri(gateway.Endpoint!).Port, loaded.AdmissionKey); await bridge.StartAsync(bridgePort);
            using var client = RemoteTests.InternetClient("http://127.0.0.1:" + bridgePort, loaded.AdmissionKey, pin);
            var invite = identity.CreateRemoteInvite(gateway.Endpoint!, profile.Endpoint, loaded.AdmissionKey, true);
            Check(invite.Uri.Contains("v=3&") && !invite.Uri.Contains(profile.TunnelToken), "Provider credential leaked to QR");
            using var response = await client.PostAsJsonAsync(gateway.Endpoint + "/bwdacc/pair", new { ticket = invite.Ticket, name = "Synthetic persistent Android" }, deadline.Token);
            response.EnsureSuccessStatusCode(); using var json = JsonDocument.Parse(await response.Content.ReadAsStringAsync(deadline.Token));
            credential = json.RootElement.GetProperty("token").GetString()!; id = json.RootElement.GetProperty("deviceId").GetString()!;
        }
        passed.Add("one-time v3 pairing excludes provider credential from invitation");
        // New identities, certificates, gateway and carrier: only saved files survive.
        using (var identity = new PairingIdentity(Path.Combine(root, "identity")))
        {
            loaded = new RemoteAccessStore(Path.Combine(root, "settings")).Load()!;
            Check(identity.Pin == pin && identity.Devices.Length == 1, "Desktop identity changed on restart");
            await using var gateway = new PairingGateway(identity, () => true, (_, _) => throw new IOException("Unexpected second pairing approval"), backendPort);
            await gateway.StartAsync(IPAddress.Loopback, Port());
            await using var bridge = new RemoteBridge(new Uri(gateway.Endpoint!).Port, loaded.AdmissionKey); await bridge.StartAsync(bridgePort);
            using var client = RemoteTests.InternetClient("http://127.0.0.1:" + bridgePort, loaded.AdmissionKey, pin);
            client.DefaultRequestHeaders.Authorization = new("Bearer", credential);
            using (var response = await client.GetAsync(gateway.Endpoint + "/queue", deadline.Token)) response.EnsureSuccessStatusCode();
            Check(identity.Devices.Length == 1, "Restart created another device");
            passed.Add("saved phone credential reconnects after gateway and carrier recreation without QR");
            identity.Revoke(id);
            using var refused = await client.GetAsync(gateway.Endpoint + "/queue", deadline.Token);
            Check(refused.StatusCode == HttpStatusCode.Unauthorized, "Persistent carrier bypassed revocation");
        }
        using (var reopened = new PairingIdentity(Path.Combine(root, "identity"))) Check(reopened.Authenticate("Bearer " + credential) == null, "Revocation lost after restart");
        passed.Add("revocation remains effective across restart");
        store.Save(profile with { Enabled = false }); Check(store.Load()!.Enabled == false && store.Load()!.AdmissionKey == profile.AdmissionKey, "Disabling reset pairing or lost preference");
        passed.Add("explicit stop persists without rotating admission or phone identity");
        var file = Path.Combine(root, "settings", "remote.dpapi"); var corrupt = File.ReadAllBytes(file); corrupt[^1] ^= 1; File.WriteAllBytes(file, corrupt);
        bool failedClosed = false; try { store.Load(); } catch { failedClosed = true; }
        Check(failedClosed && File.ReadAllBytes(file).SequenceEqual(corrupt), "Corrupted remote profile silently replaced");
        passed.Add("corrupted remote settings fail closed without generating replacement secrets");
        Data.Save(report, new { Passed = passed.Count, Cases = passed, PublicNamedTunnelTested = false });
    }
}
