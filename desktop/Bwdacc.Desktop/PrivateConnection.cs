using System.IO;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace Bwdacc.Desktop;

public sealed record PrivateAddress(IPAddress Address, string Label)
{
    public override string ToString() => Label + " · " + Address;
}

public sealed record PrivateConnectionProfile(string Address, bool Enabled)
{
    public IPAddress Validate()
    {
        if (!IPAddress.TryParse(Address, out var ip) || ip.ToString() != Address ||
            IPAddress.IsLoopback(ip) || !PrivateNetwork.IsAllowed(ip))
            throw new IOException("Escolha um IPv4 da rede local ou do Tailscale.");
        return ip;
    }
}

public static class PrivateNetwork
{
    public static bool IsTailscaleRange(IPAddress address)
    {
        if (address.AddressFamily != AddressFamily.InterNetwork) return false;
        var b = address.GetAddressBytes();
        return b[0] == 100 && b[1] >= 64 && b[1] <= 127;
    }
    public static bool IsAllowed(IPAddress address) => PairingGateway.IsLocalAddress(address) || IsTailscaleRange(address);
    // CGNAT addresses alone do not prove that an adapter belongs to Tailscale.
    public static bool IsTailscaleAdapter(string name, string description) =>
        name.Equals("Tailscale", StringComparison.OrdinalIgnoreCase) ||
        description.StartsWith("Tailscale", StringComparison.OrdinalIgnoreCase);
    public static PrivateAddress[] Addresses() => NetworkInterface.GetAllNetworkInterfaces()
        .Where(n => n.OperationalStatus == OperationalStatus.Up)
        .SelectMany(n => n.GetIPProperties().UnicastAddresses
            .Where(a => !IPAddress.IsLoopback(a.Address) &&
                (IsTailscaleAdapter(n.Name, n.Description) ? IsTailscaleRange(a.Address) :
                    n.NetworkInterfaceType is NetworkInterfaceType.Ethernet or NetworkInterfaceType.Wireless80211 && PairingGateway.IsLocalAddress(a.Address)))
            .Select(a => new PrivateAddress(a.Address, IsTailscaleRange(a.Address) ? "Tailscale" : n.Name)))
        .DistinctBy(a => a.Address).OrderByDescending(a => IsTailscaleRange(a.Address)).ToArray();
    public static bool IsAssigned(IPAddress address) => Addresses().Any(a => a.Address.Equals(address));
}

/// <summary>Restores only an explicitly enabled private listener, never a public carrier.</summary>
public sealed class PrivateConnection : IAsyncDisposable
{
    private readonly string path;
    private readonly PairingIdentity identity;
    private readonly Func<bool> backendReady;
    private readonly Func<string, CancellationToken, Task<bool>> approve;
    private readonly int port;
    private readonly int backendPort;
    private readonly SemaphoreSlim serial = new(1, 1);
    private bool disposed;
    public PrivateConnectionProfile? Profile { get; private set; }
    public PairingGateway? Gateway { get; private set; }
    public string Detail { get; private set; } = "Conexão desativada. Nenhum acesso pela rede.";
    public bool Enabled => Profile?.Enabled == true;
    public PrivateConnection(string directory, PairingIdentity identity, Func<bool> backendReady, Func<string, CancellationToken, Task<bool>> approve, int port = 8190, int backendPort = 8189)
    {
        Paths.NoLinks(directory); Directory.CreateDirectory(directory);
        path = Paths.Under(directory, "private-connection.json");
        this.identity = identity; this.backendReady = backendReady; this.approve = approve; this.port = port;
        this.backendPort = backendPort;
        if (File.Exists(path))
        {
            if (new FileInfo(path).Length > 4096) throw new IOException("Configuração de conexão inválida.");
            Profile = Data.Read<PrivateConnectionProfile>(path) ?? throw new IOException("Configuração de conexão inválida.");
            Profile.Validate();
        }
    }
    public async Task EnableAsync(string address)
    {
        await serial.WaitAsync();
        try
        {
            ObjectDisposedException.ThrowIf(disposed, this);
            var profile = new PrivateConnectionProfile(address, true);
            if (!PrivateNetwork.IsAssigned(profile.Validate())) throw new IOException("Rede indisponível. Conecte o Tailscale ou Wi-Fi e atualize a lista.");
            if (Enabled) throw new IOException("Desative a conexão atual antes de trocar a rede.");
            Data.Save(path, profile); Profile = profile;
            await ConnectCore();
        }
        finally { serial.Release(); }
    }
    public async Task DisableAsync()
    {
        await serial.WaitAsync();
        try
        {
            ObjectDisposedException.ThrowIf(disposed, this);
            if (Profile != null) Profile = Profile with { Enabled = false };
            try { if (Profile != null) Data.Save(path, Profile); Detail = "Conexão desativada. Não será reativada ao reabrir Desktop."; }
            catch { Detail = "Sessão encerrada, mas não foi possível salvar. Ao reabrir Desktop, a conexão pode reativar."; }
            finally { await StopCore(); }
        }
        finally { serial.Release(); }
    }
    public async Task RefreshAsync()
    {
        if (!await serial.WaitAsync(0)) return;
        try { if (!disposed && Enabled) await ConnectCore(); }
        finally { serial.Release(); }
    }
    private async Task ConnectCore()
    {
        var address = Profile!.Validate();
        if (!PrivateNetwork.IsAssigned(address))
        {
            await StopCore();
            Detail = PrivateNetwork.IsTailscaleRange(address)
                ? "Aguardando Tailscale neste PC. Reconexão automática no mesmo endereço; não precisa gerar outro QR."
                : "Aguardando a rede local salva. Se o IP mudou, desative, escolha o novo endereço e pareie novamente.";
            return;
        }
        if (Gateway != null) return;
        var candidate = new PairingGateway(identity, backendReady, approve, backendPort);
        try
        {
            await candidate.StartAsync(address, port); Gateway = candidate;
            Detail = (PrivateNetwork.IsTailscaleRange(address) ? "Tailscale" : "Rede local") + " conectado · " + address + "\nHTTPS · somente celulares pareados. Retoma ao reabrir Desktop.";
        }
        catch
        {
            await candidate.DisposeAsync();
            Detail = "Não foi possível abrir a conexão. Confira a rede e se a porta 8190 está livre. Tentando novamente…";
        }
    }
    private async Task StopCore()
    {
        var previous = Gateway; Gateway = null;
        identity.CancelInvite();
        if (previous != null) await previous.DisposeAsync();
    }
    public async ValueTask DisposeAsync()
    {
        await serial.WaitAsync();
        try { if (disposed) return; disposed = true; await StopCore(); }
        finally { serial.Release(); }
    }
}
