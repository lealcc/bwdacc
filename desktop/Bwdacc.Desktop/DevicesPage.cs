using System.IO;
using System.Net;
using System.Diagnostics;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using QRCoder;

namespace Bwdacc.Desktop;

public partial class MainWindow
{
    private PairingIdentity? desktopIdentity;
    private PrivateConnection? privateConnection;
    private PairingInvite? displayedInvite;
    private ComboBox? localAddresses;
    private Button? networkButton, inviteButton, refreshAddresses;
    private TextBlock? networkDetail, inviteDetail;
    private StackPanel? pairedRows, qrPanel;
    private Image? qr;
    private string deviceFingerprint = "";
    private bool networkBusy;

    private void BuildDevicesPage()
    {
        if (DevicesPage.Children.Count > 0) { TickDevices(); return; }
        var connection = new StackPanel(); connection.Children.Add(Label("Seu PC. Seu celular.", 19, false));
        connection.Children.Add(Label("Na mesma rede, use Wi-Fi. Tailscale é opcional para acessar fora de casa."));
        localAddresses = new ComboBox { HorizontalAlignment = HorizontalAlignment.Left, Width = 460, MaxWidth = 460, Margin = new(0, 12, 0, 14) };
        connection.Children.Add(localAddresses);
        var actions = new WrapPanel();
        networkButton = new Button { Content = "Ativar conexão", Margin = new(0, 0, 12, 10), Style = (Style)FindResource("Primary") };
        inviteButton = new Button { Content = "Parear celular", IsEnabled = false, Margin = new(0, 0, 12, 10) };
        refreshAddresses = new Button { Content = "Atualizar redes", Margin = new(0, 0, 0, 10) };
        networkButton.Click += async (_, _) => await ToggleNetwork();
        inviteButton.Click += (_, _) => ShowPairingInvite();
        refreshAddresses.Click += (_, _) => RefreshAddresses();
        actions.Children.Add(networkButton); actions.Children.Add(inviteButton); actions.Children.Add(refreshAddresses); connection.Children.Add(actions);
        networkDetail = Label("Conexão desativada. Nenhum acesso pela rede."); connection.Children.Add(networkDetail); DevicesPage.Children.Add(Card(connection));

        var guide = new StackPanel(); guide.Children.Add(Label("Fora de casa com Tailscale", 19, false));
        guide.Children.Add(DesktopVisuals.Step("1", "Conecte os dois aparelhos", "Use sua própria conta Tailscale no computador e no celular."));
        guide.Children.Add(DesktopVisuals.Step("2", "Escolha a rede privada", "Desative a conexão atual neste conector, selecione o endereço Tailscale e ative novamente."));
        guide.Children.Add(DesktopVisuals.Step("3", "Pareie com o novo QR", "Leia o QR no celular e confirme aqui. Mantenha Desktop, ComfyUI e Tailscale abertos para gerar."));
        var download = new Button { Content = "Instalar Tailscale", HorizontalAlignment = HorizontalAlignment.Left, Margin = new(0, 12, 0, 0) };
        download.Click += (_, _) => { try { Process.Start(new ProcessStartInfo("https://tailscale.com/download") { UseShellExecute = true }); } catch { networkDetail.Text = "Abra tailscale.com/download no navegador."; } };
        guide.Children.Add(download);
        var help = new StackPanel(); help.Children.Add(Label("Use a mesma conta pessoal nos dois aparelhos. Nenhum cliente precisa entrar numa rede compartilhada do bwdacc. O pareamento do app continua obrigatório."));
        help.Children.Add(Label("Não é necessário usar um nó de saída (exit node). Se a internet do celular parar, confira nó de saída e DNS no Tailscale. No Android, apenas uma VPN pode ficar ativa por vez."));
        help.Children.Add(Label("Se não conectar: confira Tailscale nos dois aparelhos, permissões da sua rede Tailscale e o firewall para o endereço escolhido, porta TCP 8190. Não libere a porta do ComfyUI."));
        guide.Children.Add(new Expander { Header = "Ajuda com a conexão", Foreground = Brush("#A6A6B0"), Content = help, Margin = new(0, 16, 0, 0) });
        tailscaleGuide = Card(guide);
        qrPanel = new StackPanel { Visibility = Visibility.Collapsed };
        qr = new Image { Width = 260, Height = 260, Stretch = Stretch.Uniform, HorizontalAlignment = HorizontalAlignment.Center };
        qrPanel.Children.Add(new Border { Child = qr, Background = Brushes.White, CornerRadius = new(16), Padding = new(12), HorizontalAlignment = HorizontalAlignment.Left, Margin = new(0, 0, 0, 14) });
        inviteDetail = Label(""); qrPanel.Children.Add(inviteDetail);
        qrPanel.Children.Add(Label("No celular: bwdacc dev > Ajustes > Conexão > Parear com Desktop. Confirme a solicitação neste PC."));
        var copy = new Button { Content = "Copiar código", HorizontalAlignment = HorizontalAlignment.Left, Margin = new(0, 0, 0, 16) };
        copy.Click += (_, _) => { if (displayedInvite != null && displayedInvite.Expires > DateTimeOffset.UtcNow) { try { Clipboard.SetText(displayedInvite.Uri); } catch { networkDetail.Text = "Não foi possível copiar. Use o QR."; } } }; qrPanel.Children.Add(copy); DevicesPage.Children.Add(qrPanel);
        pairedRows = new StackPanel(); DevicesPage.Children.Add(pairedRows);
        DevicesPage.Children.Add(tailscaleGuide);
        try
        {
            if (!fixture)
            {
                desktopIdentity = new PairingIdentity(Path.Combine(Paths.SettingsRoot, "pairing"));
                privateConnection = new PrivateConnection(Paths.SettingsRoot, desktopIdentity, () => BackendReady, ConfirmDevice, backendPort: BackendPort);
            }
        }
        catch { networkDetail.Text = "Configuração indisponível. Acesso mantido desativado; nenhum pareamento foi substituído."; }
        RefreshAddresses(); TickDevices();
    }
    private void RefreshAddresses()
    {
        if (localAddresses == null) return;
        string? selected = privateConnection?.Enabled == true ? privateConnection.Profile!.Address : (localAddresses.SelectedItem as PrivateAddress)?.Address.ToString() ?? privateConnection?.Profile?.Address;
        localAddresses.Items.Clear();
        try
        {
            var addresses = fixture ? new[] { new PrivateAddress(IPAddress.Parse("192.168.1.20"), "Wi-Fi"), new PrivateAddress(IPAddress.Parse("100.100.10.20"), "Tailscale") } : PrivateNetwork.Addresses().OrderBy(a => PrivateNetwork.IsTailscaleRange(a.Address)).ToArray();
            foreach (var address in addresses) localAddresses.Items.Add(address);
            localAddresses.SelectedItem = addresses.FirstOrDefault(a => a.Address.ToString() == selected);
            if (localAddresses.SelectedItem == null && privateConnection?.Enabled != true && addresses.Length > 0) localAddresses.SelectedIndex = 0;
        }
        catch { networkDetail!.Text = "Não foi possível consultar as redes. Tente atualizar a lista."; }
        RefreshNetworkControls();
    }
    private void RefreshNetworkControls()
    {
        bool enabled = privateConnection?.Enabled == true;
        networkButton!.Content = enabled ? "Desativar conexão" : "Ativar conexão";
        networkButton.IsEnabled = !networkBusy && (fixture || privateConnection != null) && (enabled || localAddresses!.SelectedItem != null);
        inviteButton!.IsEnabled = !networkBusy && privateConnection?.Gateway != null;
        localAddresses!.IsEnabled = refreshAddresses!.IsEnabled = !networkBusy && !enabled;
    }
    private async Task ToggleNetwork()
    {
        if (networkBusy || closing || fixture || privateConnection == null) return;
        networkBusy = true; RefreshNetworkControls(); ClearInvite();
        try
        {
            if (privateConnection.Enabled) await privateConnection.DisableAsync();
            else if (localAddresses!.SelectedItem is PrivateAddress selected) await privateConnection.EnableAsync(selected.Address.ToString());
            networkDetail!.Text = privateConnection.Detail;
        }
        catch (Exception ex) { networkDetail!.Text = ex.Message; }
        finally { networkBusy = false; RefreshNetworkControls(); }
    }
    private async Task RefreshNetworkAsync()
    {
        if (networkBusy || closing || privateConnection == null) return;
        networkBusy = true;
        try
        {
            await privateConnection.RefreshAsync();
            if (privateConnection.Enabled) networkDetail!.Text = privateConnection.Detail;
            if (privateConnection.Gateway == null && displayedInvite != null) ClearInvite();
        }
        catch { networkDetail!.Text = "Conexão indisponível. Confira Tailscale ou rede local."; }
        finally { networkBusy = false; if (!closing) RefreshNetworkControls(); }
    }
    private void ShowPairingInvite()
    {
        if (privateConnection?.Gateway?.Endpoint == null || desktopIdentity == null) return;
        try
        {
            displayedInvite = desktopIdentity.CreateInvite(privateConnection.Gateway.Endpoint);
            var bytes = PngByteQRCodeHelper.GetQRCode(displayedInvite.Uri, QRCodeGenerator.ECCLevel.M, 8);
            var bitmap = new BitmapImage(); bitmap.BeginInit(); bitmap.CacheOption = BitmapCacheOption.OnLoad; bitmap.StreamSource = new MemoryStream(bytes); bitmap.EndInit(); bitmap.Freeze();
            qr!.Source = bitmap; qrPanel!.Visibility = Visibility.Visible; TickDevices();
        }
        catch { networkDetail!.Text = "Não foi possível criar o QR. Gere um novo código."; ClearInvite(); }
    }
    private async Task<bool> ConfirmDevice(string name, CancellationToken token)
    {
        return await Dispatcher.InvokeAsync(() =>
        {
            if (token.IsCancellationRequested || closing) return false;
            ClearInvite();
            var dialog = new Window { Owner = this, Title = "Confirmar celular", Width = 440, SizeToContent = SizeToContent.Height, ResizeMode = ResizeMode.NoResize, WindowStartupLocation = WindowStartupLocation.CenterOwner, ShowInTaskbar = false, Background = (Brush)FindResource("Bg"), Foreground = Brushes.White };
            var page = new StackPanel { Margin = new(26) }; page.Children.Add(Label("Permitir este celular?", 23, false)); page.Children.Add(Label(string.IsNullOrWhiteSpace(name) ? "Android" : name, 17, false));
            page.Children.Add(Label("Confirme apenas seu celular. Ele poderá iniciar gerações e acessar os arquivos disponíveis ao motor neste computador."));
            var actions = new WrapPanel { Margin = new(0, 16, 0, 0) }; var no = new Button { Content = "Recusar", Margin = new(0, 0, 12, 0) }; var yes = new Button { Content = "Permitir celular", Style = (Style)FindResource("Primary") };
            no.Click += (_, _) => dialog.DialogResult = false; yes.Click += (_, _) => dialog.DialogResult = true; actions.Children.Add(no); actions.Children.Add(yes); page.Children.Add(actions); dialog.Content = page;
            using var timeout = token.Register(() => Dispatcher.BeginInvoke(() => { if (dialog.IsVisible) dialog.Close(); }));
            return dialog.ShowDialog() == true && !token.IsCancellationRequested;
        });
    }
    private void ClearInvite()
    {
        desktopIdentity?.CancelInvite(); displayedInvite = null;
        if (qrPanel != null) qrPanel.Visibility = Visibility.Collapsed;
        if (qr != null) qr.Source = null;
    }
    private void TickDevices()
    {
        if (pairedRows == null) return;
        if (closing) return;
        _ = RefreshNetworkAsync();
        if (displayedInvite != null)
        {
            var seconds = (int)(displayedInvite.Expires - DateTimeOffset.UtcNow).TotalSeconds;
            if (seconds <= 0) { ClearInvite(); if (networkDetail != null) networkDetail.Text = "QR expirado. Gere outro para parear."; }
            else inviteDetail!.Text = $"Uso único · expira em {seconds / 60}:{seconds % 60:00}";
        }
        var records = desktopIdentity?.Devices ?? [];
        var fingerprint = string.Join("|", records.Select(d => d.Id));
        if (deviceFingerprint == fingerprint && pairedRows.Children.Count > 0) return; deviceFingerprint = fingerprint;
        pairedRows.Children.Clear(); pairedRows.Children.Add(Label("Dispositivos pareados", 17, false));
        if (records.Length == 0) pairedRows.Children.Add(Label("Nenhum celular autorizado ainda."));
        foreach (var device in records)
        {
            var row = new StackPanel(); row.Children.Add(Label(device.Name, 15, false)); row.Children.Add(Label("Pareado em " + device.Created.ToLocalTime().ToString("dd/MM/yyyy HH:mm")));
            var revoke = new Button { Content = "Revogar acesso", HorizontalAlignment = HorizontalAlignment.Left };
            revoke.Click += (_, _) =>
            {
                if (MessageBox.Show(this, "Desconectar este celular e impedir novos acessos? Mídias já baixadas no aparelho permanecem.", "Revogar acesso", MessageBoxButton.YesNo) != MessageBoxResult.Yes) return;
                try { desktopIdentity!.Revoke(device.Id); TickDevices(); } catch { networkDetail!.Text = "Não foi possível salvar a revogação. Desative a conexão local até resolver."; }
            };
            row.Children.Add(revoke); pairedRows.Children.Add(Card(row));
        }
    }
    private async Task StopDevicesAsync()
    {
        ClearInvite();
        try { if (privateConnection != null) await privateConnection.DisposeAsync(); }
        finally { privateConnection = null; desktopIdentity?.Dispose(); desktopIdentity = null; }
    }
}
