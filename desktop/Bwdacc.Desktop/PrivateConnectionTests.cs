using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Json;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text.Json;

namespace Bwdacc.Desktop;

internal static class PrivateConnectionTests
{
    private static void Check(bool value, string message) { if (!value) throw new IOException(message); }
    public static async Task RunAsync(string report)
    {
        var root = Path.Combine(Path.GetDirectoryName(Path.GetFullPath(report))!, "private-tests-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(root);
        var passed = new List<string>();
        foreach (var address in new[] { "100.64.0.0", "100.127.255.255", "192.168.1.20", "10.0.0.1", "172.31.0.1" })
            new PrivateConnectionProfile(address, true).Validate();
        foreach (var address in new[] { "100.63.255.255", "100.128.0.0", "8.8.8.8", "0.0.0.0", "127.0.0.1", "::1", "pc.local", "192.168.001.2" })
        {
            bool rejected = false; try { new PrivateConnectionProfile(address, true).Validate(); } catch (IOException) { rejected = true; }
            Check(rejected, "Unsafe or noncanonical address accepted: " + address);
        }
        Check(PrivateNetwork.IsTailscaleAdapter("Tailscale", "Tunnel") && !PrivateNetwork.IsTailscaleAdapter("Wi-Fi", "Generic CGNAT"), "CGNAT adapter misidentified");
        passed.Add("private IPv4 boundaries and Tailscale adapter restriction");
        using var identity = new PairingIdentity(Path.Combine(root, "identity"));
        var legacyRoot = Path.Combine(root, "legacy");
        new RemoteAccessStore(Path.Combine(legacyRoot, "remote")).Save(new("https://legacy.bwdacc.app", new string('Z', 128), RemoteAccessProfile.NewAdmissionKey(), true));
        await using (var legacy = new PrivateConnection(legacyRoot, identity, () => false, (_, _) => Task.FromResult(false)))
        {
            await legacy.RefreshAsync(); Check(!legacy.Enabled && legacy.Gateway == null, "Old public profile activated");
        }
        passed.Add("enabled legacy Cloudflare profile never restores public access");
        var offlineRoot = Path.Combine(root, "offline");
        var absent = new[] { "100.64.0.1", "100.127.255.254", "100.101.102.103" }.First(a => !PrivateNetwork.IsAssigned(IPAddress.Parse(a)));
        Data.Save(Path.Combine(offlineRoot, "private-connection.json"), new PrivateConnectionProfile(absent, true));
        await using (var offline = new PrivateConnection(offlineRoot, identity, () => false, (_, _) => Task.FromResult(false)))
        {
            await offline.RefreshAsync(); Check(offline.Enabled && offline.Gateway == null && offline.Profile!.Address == absent, "Missing Tailscale address fell back to another interface");
            await offline.DisableAsync();
        }
        await using (var stopped = new PrivateConnection(offlineRoot, identity, () => false, (_, _) => Task.FromResult(false)))
        {
            await stopped.RefreshAsync(); Check(!stopped.Enabled && stopped.Gateway == null, "Disabled access restored");
        }
        passed.Add("missing saved interface waits without fallback; disable survives restart");
        File.WriteAllText(Path.Combine(offlineRoot, "private-connection.json"), "{broken");
        bool corrupt = false;
        try { await using var invalid = new PrivateConnection(offlineRoot, identity, () => false, (_, _) => Task.FromResult(false)); } catch (JsonException) { corrupt = true; }
        Check(corrupt, "Corrupted connection profile accepted"); passed.Add("corrupted private profile fails closed");

        var available = PrivateNetwork.Addresses().FirstOrDefault();
        if (available != null)
        {
            int port; using (var listener = new TcpListener(IPAddress.Loopback, 0)) { listener.Start(); port = ((IPEndPoint)listener.LocalEndpoint).Port; }
            var settings = Path.Combine(root, "live");
            string endpoint, token, device;
            using var client = new HttpClient(new HttpClientHandler { UseProxy = false, AllowAutoRedirect = false,
                ServerCertificateCustomValidationCallback = (_, cert, _, errors) => cert != null && !errors.HasFlag(SslPolicyErrors.RemoteCertificateNameMismatch) &&
                    Convert.ToHexStringLower(SHA256.HashData(cert.PublicKey.ExportSubjectPublicKeyInfo())) == identity.Pin }) { Timeout = TimeSpan.FromSeconds(10) };
            await using (var first = new PrivateConnection(settings, identity, () => false, (_, _) => Task.FromResult(true), port))
            {
                await first.EnableAsync(available.Address.ToString()); Check(first.Gateway != null, "Assigned interface did not start"); endpoint = first.Gateway!.Endpoint!;
                using var denied = await client.GetAsync(endpoint + "/bwdacc/status"); Check(denied.StatusCode == HttpStatusCode.Unauthorized, "Unpaired access accepted");
                var invite = identity.CreateInvite(endpoint);
                using var paired = await client.PostAsJsonAsync(endpoint + "/bwdacc/pair", new { ticket = invite.Ticket, name = "Synthetic private connection" }); paired.EnsureSuccessStatusCode();
                using var body = JsonDocument.Parse(await paired.Content.ReadAsStringAsync()); token = body.RootElement.GetProperty("token").GetString()!; device = body.RootElement.GetProperty("deviceId").GetString()!;
                passed.Add("assigned private interface requires HTTPS pin and approved pairing");
            }
            using var reopenedIdentity = new PairingIdentity(Path.Combine(root, "identity"));
            await using (var second = new PrivateConnection(settings, reopenedIdentity, () => false, (_, _) => throw new IOException("Unexpected new approval"), port))
            {
                await second.RefreshAsync(); Check(second.Gateway?.Endpoint == endpoint, "Saved endpoint not restored");
                client.DefaultRequestHeaders.Authorization = new("Bearer", token);
                using var status = await client.GetAsync(endpoint + "/bwdacc/status"); status.EnsureSuccessStatusCode();
                passed.Add("private listener and phone credential survive full controller and identity recreation");
                reopenedIdentity.Revoke(device);
                using var denied = await client.GetAsync(endpoint + "/bwdacc/status"); Check(denied.StatusCode == HttpStatusCode.Unauthorized, "Revoked phone accepted");
                await second.DisableAsync(); Check(second.Gateway == null, "Disabled listener still active");
                passed.Add("private access revocation and explicit stop enforced");
            }
        }
        Data.Save(report, new { Passed = passed.Count, Cases = passed, LiveAssignedAdapter = available?.Label, CrossDeviceTailscaleTested = false });
    }
}
