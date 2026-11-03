using System.IO;
using System.Net;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;

namespace Bwdacc.Desktop;

public sealed record PairedDevice(string Id, string Name, string TokenHash, DateTimeOffset Created);
public sealed record PairingInvite(string Uri, string Ticket, DateTimeOffset Expires);
public sealed record PairingCredential(string DeviceId, string Token);

/// <summary>One desktop identity, protected by Windows DPAPI; only credential hashes are persisted.</summary>
public sealed class PairingIdentity : IDisposable
{
    private readonly string root;
    private readonly RSA key;
    private readonly object sync = new();
    private readonly Dictionary<string, (PairedDevice Device, CancellationTokenSource Revoked)> devices = new();
    private PairingInvite? invite;
    private readonly Func<DateTimeOffset> now;
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("bwdacc-commercial-pairing-v1");
    public string Pin => Convert.ToHexStringLower(SHA256.HashData(key.ExportSubjectPublicKeyInfo()));

    public PairingIdentity(string directory, Func<DateTimeOffset>? clock = null)
    {
        root = Path.GetFullPath(directory); now = clock ?? (() => DateTimeOffset.UtcNow);
        Paths.NoLinks(root); Directory.CreateDirectory(root);
        var marker = Paths.Under(root, ".bwdacc-pairing");
        if (!File.Exists(marker))
        {
            if (Directory.EnumerateFileSystemEntries(root).Any()) throw new IOException("Pasta de identidade não reconhecida.");
            File.WriteAllText(marker, "bwdacc-pairing-v1");
        }
        if (File.ReadAllText(marker) != "bwdacc-pairing-v1") throw new IOException("Identidade não reconhecida.");
        var keyPath = Paths.Under(root, "identity.dpapi");
        key = RSA.Create(3072);
        if (File.Exists(keyPath))
        {
            var clear = Unprotect(File.ReadAllBytes(keyPath));
            try { key.ImportPkcs8PrivateKey(clear, out _); } finally { CryptographicOperations.ZeroMemory(clear); }
        }
        else
        {
            // A lost key must never silently replace an identity that already has paired devices.
            if (File.Exists(Paths.Under(root, "devices.dpapi"))) throw new IOException("Identidade perdida. Não foi criado um novo pareamento automaticamente.");
            var clear = key.ExportPkcs8PrivateKey();
            try { SaveProtected(keyPath, clear); } finally { CryptographicOperations.ZeroMemory(clear); }
        }
        var records = Paths.Under(root, "devices.dpapi");
        if (File.Exists(records))
        {
            var clear = Unprotect(File.ReadAllBytes(records));
            try
            {
                foreach (var device in JsonSerializer.Deserialize<PairedDevice[]>(clear, Data.Json) ?? [])
                {
                    if (!Guid.TryParseExact(device.Id, "N", out _) || device.TokenHash.Length != 64) throw new IOException("Registro de dispositivos inválido.");
                    devices.Add(device.Id, (device, new()));
                }
            }
            finally { CryptographicOperations.ZeroMemory(clear); }
        }
    }
    private static byte[] Unprotect(byte[] value) => ProtectedData.Unprotect(value, Entropy, DataProtectionScope.CurrentUser);
    private static void SaveProtected(string path, byte[] clear)
    {
        var bytes = ProtectedData.Protect(clear, Entropy, DataProtectionScope.CurrentUser);
        var temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try { File.WriteAllBytes(temp, bytes); if (File.Exists(path)) File.Replace(temp, path, null); else File.Move(temp, path); }
        finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    public X509Certificate2 Certificate(IPAddress address)
    {
        var request = new CertificateRequest("CN=bwdacc Desktop", key, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
        var san = new SubjectAlternativeNameBuilder(); san.AddIpAddress(address);
        if (!IPAddress.IsLoopback(address)) san.AddIpAddress(IPAddress.Loopback);
        request.CertificateExtensions.Add(san.Build());
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, true));
        request.CertificateExtensions.Add(new X509KeyUsageExtension(X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, true));
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(new OidCollection { new("1.3.6.1.5.5.7.3.1") }, true));
        using var generated = request.CreateSelfSigned(now().AddMinutes(-5), now().AddYears(1));
        // Windows Schannel cannot use an ephemeral CNG key. The user-key container
        // created by this import is temporary and owned by the returned certificate.
        var pfx = generated.Export(X509ContentType.Pkcs12);
        try { return X509CertificateLoader.LoadPkcs12(pfx, null, X509KeyStorageFlags.UserKeySet); }
        finally { CryptographicOperations.ZeroMemory(pfx); }
    }
    public PairingInvite CreateInvite(string endpoint)
    {
        lock (sync)
        {
            var ticket = Secret(); var expires = now().AddMinutes(2);
            var uri = "bwdacc://pair?v=1&endpoint=" + Uri.EscapeDataString(endpoint) + "&pin=" + Pin + "&ticket=" + ticket + "&expires=" + expires.ToUnixTimeSeconds();
            return invite = new(uri, ticket, expires);
        }
    }
    public void CancelInvite() { lock (sync) invite = null; }
    public PairingInvite CreateRemoteInvite(string endpoint, string relay, string relayKey, bool permanent = false)
    {
        if (permanent) RemoteAccessProfile.ValidateEndpoint(relay);
        if ((!permanent && !System.Text.RegularExpressions.Regex.IsMatch(relay, @"\Ahttps://[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com\z")) ||
            !System.Text.RegularExpressions.Regex.IsMatch(relayKey, "^[A-Za-z0-9_-]{43}$") || new Uri(endpoint).Host != "127.0.0.1")
            throw new IOException("Endereço remoto inválido.");
        lock (sync)
        {
            var local = CreateInvite(endpoint);
            return invite = local with { Uri = local.Uri.Replace("pair?v=1&", permanent ? "pair?v=3&" : "pair?v=2&") +
                "&relay=" + Uri.EscapeDataString(relay) + "&relayKey=" + relayKey };
        }
    }
    public bool ConsumeInvite(string ticket)
    {
        lock (sync)
        {
            if (invite == null || now() >= invite.Expires || ticket.Length != 43 || !CryptographicOperations.FixedTimeEquals(Encoding.ASCII.GetBytes(ticket), Encoding.ASCII.GetBytes(invite.Ticket))) return false;
            invite = null; return true;
        }
    }
    private static string Secret() => Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    private static string Hash(string value) => Convert.ToHexStringLower(SHA256.HashData(Encoding.UTF8.GetBytes(value)));
    public PairingCredential Add(string name)
    {
        lock (sync)
        {
            if (devices.Count >= 16) throw new IOException("Limite de 16 dispositivos. Remova um pareamento antigo.");
            var token = Secret(); var id = Guid.NewGuid().ToString("N");
            var cleanName = string.Concat(name.Where(c => !char.IsControl(c))).Trim();
            var device = new PairedDevice(id, cleanName[..Math.Min(60, cleanName.Length)], Hash(token), now());
            SaveDevices(devices.Values.Select(x => x.Device).Append(device)); devices.Add(id, (device, new()));
            return new(id, token);
        }
    }
    public (string Id, CancellationToken Revoked)? Authenticate(string authorization)
    {
        if (!authorization.StartsWith("Bearer ", StringComparison.Ordinal) || authorization.Length != 50) return null;
        var hash = Encoding.ASCII.GetBytes(Hash(authorization[7..]));
        lock (sync) foreach (var entry in devices.Values)
            if (CryptographicOperations.FixedTimeEquals(hash, Encoding.ASCII.GetBytes(entry.Device.TokenHash))) return (entry.Device.Id, entry.Revoked.Token);
        return null;
    }
    public PairedDevice[] Devices { get { lock (sync) return devices.Values.Select(x => x.Device).ToArray(); } }
    public void Revoke(string id)
    {
        lock (sync)
        {
            if (!devices.TryGetValue(id, out var entry)) return;
            SaveDevices(devices.Values.Where(x => x.Device.Id != id).Select(x => x.Device));
            devices.Remove(id); entry.Revoked.Cancel(); entry.Revoked.Dispose();
        }
    }
    private void SaveDevices(IEnumerable<PairedDevice> values) => SaveProtected(Paths.Under(root, "devices.dpapi"), JsonSerializer.SerializeToUtf8Bytes(values.ToArray(), Data.Json));
    public void Dispose()
    {
        lock (sync) { invite = null; foreach (var entry in devices.Values) { entry.Revoked.Cancel(); entry.Revoked.Dispose(); } devices.Clear(); key.Dispose(); }
    }
}
