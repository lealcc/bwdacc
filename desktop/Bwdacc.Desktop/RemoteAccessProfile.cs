using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Bwdacc.Desktop;

public sealed record RemoteAccessProfile(string Endpoint, string TunnelToken, string AdmissionKey, bool Enabled)
{
    // Avoid accidental credential disclosure from record formatting.
    public override string ToString() => "Remote access: " + Endpoint;
    public void Validate()
    {
        ValidateEndpoint(Endpoint);
        if (new Uri(Endpoint).Host.EndsWith(".trycloudflare.com", StringComparison.Ordinal))
            throw new IOException("Use o domínio do túnel fixo, não o endereço temporário de teste.");
        if (!Regex.IsMatch(TunnelToken, @"\A[A-Za-z0-9+/=_-]{80,8192}\z") ||
            !Regex.IsMatch(AdmissionKey, @"\A[A-Za-z0-9_-]{43}\z"))
            throw new IOException("Token do túnel inválido. Cole somente o token, não o comando de instalação.");
    }
    public static void ValidateEndpoint(string endpoint)
    {
        if (endpoint.Length > 261 || !Regex.IsMatch(endpoint, @"\Ahttps://(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}\z"))
            throw new IOException("Use um endereço HTTPS público, sem porta, caminho ou parâmetros.");
        var host = new Uri(endpoint).Host;
        foreach (var suffix in new[] { "local", "localhost", "internal", "lan", "home", "test", "invalid", "example" })
            if (host.EndsWith("." + suffix, StringComparison.Ordinal)) throw new IOException("Use um domínio público para o acesso remoto.");
    }
    public static string NewAdmissionKey() => Convert.ToBase64String(RandomNumberGenerator.GetBytes(32)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
}

/// <summary>Explicit remote settings, encrypted for this Windows user. Never export to Android.</summary>
public sealed class RemoteAccessStore(string directory)
{
    private static readonly byte[] Entropy = Encoding.UTF8.GetBytes("bwdacc-commercial-remote-v1");
    private string FilePath => Paths.Under(directory, "remote.dpapi");
    public RemoteAccessProfile? Load()
    {
        var path = FilePath;
        if (!File.Exists(path)) return null;
        if (new FileInfo(path).Length > 32768) throw new IOException("Configuração remota inválida.");
        var clear = ProtectedData.Unprotect(File.ReadAllBytes(path), Entropy, DataProtectionScope.CurrentUser);
        try
        {
            var profile = JsonSerializer.Deserialize<RemoteAccessProfile>(clear, Data.Json) ?? throw new IOException("Configuração remota inválida.");
            profile.Validate(); return profile;
        }
        finally { CryptographicOperations.ZeroMemory(clear); }
    }
    public void Save(RemoteAccessProfile profile)
    {
        profile.Validate(); Paths.NoLinks(directory); Directory.CreateDirectory(directory);
        var path = FilePath; var temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        var clear = JsonSerializer.SerializeToUtf8Bytes(profile, Data.Json);
        try
        {
            File.WriteAllBytes(temp, ProtectedData.Protect(clear, Entropy, DataProtectionScope.CurrentUser));
            if (File.Exists(path)) File.Replace(temp, path, null); else File.Move(temp, path);
        }
        finally { CryptographicOperations.ZeroMemory(clear); if (File.Exists(temp)) File.Delete(temp); }
    }
}
