using System.IO;
using System.Windows;
using System.Windows.Controls;

namespace Bwdacc.Desktop;

public partial class MainWindow
{
    private int setupStep;
    private string sourceChoice = "managed", externalDraft = "http://127.0.0.1:8188";
    private bool switchingBackend;
    private Border? tailscaleGuide;
    private void StartSetup(object sender, RoutedEventArgs e) => BeginSetup();
    private void BeginSetup(string? previewChoice = null)
    {
        sourceChoice = previewChoice ?? settings.Backend; externalDraft = settings.ExternalUrl;
        setupStep = previewChoice != null ? 1 : Math.Clamp(settings.SetupStep, 1, 3);
        if (setupStep > 1 && !BackendReady) setupStep = 1;
        ShowSetupStep();
    }
    private void ShowSetupStep()
    {
        settings = settings with { SetupStep = setupStep }; SaveSettings();
        SetupHeader.Visibility = SetupFooter.Visibility = Visibility.Visible;
        if (setupStep == 1) { ShowPage("source"); BuildSetupSource(); }
        else {
            ShowPage("devices");
            if (tailscaleGuide != null) {
                tailscaleGuide.Visibility = setupStep == 3 ? Visibility.Visible : Visibility.Collapsed;
                DevicesPage.Children.Remove(tailscaleGuide);
                if (setupStep == 3) DevicesPage.Children.Insert(0, tailscaleGuide); else DevicesPage.Children.Add(tailscaleGuide);
            }
        }
        PageTitle.Text = setupStep switch { 1 => "Seu estúdio começa aqui.", 2 => "Agora, seu celular.", _ => "Crie também fora de casa." };
        PageSubtitle.Text = setupStep switch { 1 => "Escolha como usar o ComfyUI neste computador.", 2 => "Conecte pelo Wi-Fi, leia o QR e confirme neste PC.", _ => "Tailscale é opcional. Você pode configurar depois." };
        PageScroll.ScrollToTop(); RefreshSetupControls();
    }
    private void RefreshSetupControls()
    {
        if (setupStep == 0) return;
        SetupStepLabel.Text = setupStep switch { 1 => "1 / 3   ComfyUI e modelos", 2 => "2 / 3   Conectar celular", _ => "3 / 3   Tailscale · opcional" };
        SetupBack.Content = setupStep == 1 ? "Continuar depois" : "Voltar";
        SetupSkip.Visibility = setupStep == 3 ? Visibility.Visible : Visibility.Collapsed;
        SetupSkip.IsEnabled = operation == null;
        SetupNext.Content = setupStep switch { 1 => "Conectar celular", 2 => "Continuar", _ => "Concluir" };
        SetupBack.IsEnabled = operation == null;
        SetupNext.IsEnabled = operation == null && (setupStep switch {
            1 => sourceChoice == settings.Backend && (IsExternal ? external.Ready && DraftMatches() : Installer.State != null),
            2 => desktopIdentity?.Devices.Length > 0,
            _ => true
        });
    }
    private bool DraftMatches() { try { return ExternalComfy.Validate(externalDraft) == ExternalComfy.Validate(settings.ExternalUrl); } catch { return false; } }
    private void SkipTailscale(object sender, RoutedEventArgs e) { LeaveSetup(true); ShowPage("overview"); }
    private async void NextSetup(object sender, RoutedEventArgs e)
    {
        if (operation != null || !SetupNext.IsEnabled) return;
        if (setupStep == 1 && !IsExternal && !engine.Running)
        {
            await Work((_, t) => engine.StartAsync(Installer, t), "Motor ativo");
            if (!engine.Running) return;
        }
        if (setupStep == 3) { LeaveSetup(true); ShowPage("overview"); StatusTitle.Text = "Estúdio configurado"; StatusDetail.Text = "ComfyUI e celular prontos. Tailscale pode ser configurado em Dispositivos."; return; }
        setupStep++; ShowSetupStep();
    }
    private void PreviousSetup(object sender, RoutedEventArgs e)
    {
        if (setupStep == 1) { LeaveSetup(false); ShowPage("overview"); return; }
        setupStep--; ShowSetupStep();
    }
    private void LeaveSetup(bool complete)
    {
        settings = settings with { SetupStep = complete ? 0 : setupStep, SetupComplete = complete || settings.SetupComplete }; SaveSettings();
        setupStep = 0; SetupHeader.Visibility = SetupFooter.Visibility = Visibility.Collapsed;
        if (tailscaleGuide != null) { tailscaleGuide.Visibility = Visibility.Visible; DevicesPage.Children.Remove(tailscaleGuide); DevicesPage.Children.Add(tailscaleGuide); }
    }
    private Button SetupAction(string title, RoutedEventHandler action, bool primary = false)
    {
        var button = new Button { Content = title, HorizontalAlignment = HorizontalAlignment.Left, Margin = new(0, 8, 12, 6), IsEnabled = operation == null };
        if (primary) button.Style = (Style)FindResource("Primary");
        button.Click += action; return button;
    }
    private void BuildSetupSource()
    {
        SetupPage.Children.Clear();
        var choices = new Grid { Margin = new(0, 0, 0, 16) };
        choices.ColumnDefinitions.Add(new()); choices.ColumnDefinitions.Add(new());
        foreach (var (mode, title, detail, column) in new[] {
            ("managed", "Um ComfyUI separado", "Instale o motor e escolha seus modelos. Tudo numa pasta própria.", 0),
            ("external", "Já tenho ComfyUI", "Conecte sua instalação e aproveite os modelos e LoRAs que já tem.", 1) })
        {
            var content = new StackPanel(); content.Children.Add(Label(title, 17, false)); content.Children.Add(Label(detail));
            var button = new Button { Content = content, Padding = new(18), HorizontalContentAlignment = HorizontalAlignment.Stretch,
                Background = Brush(sourceChoice == mode ? "#303136" : "#202125"), Margin = new(column == 0 ? 0 : 6, 0, column == 0 ? 6 : 0, 0), IsEnabled = operation == null };
            button.Click += (_, _) => { sourceChoice = mode; BuildSetupSource(); RefreshSetupControls(); };
            Grid.SetColumn(button, column); choices.Children.Add(button);
        }
        SetupPage.Children.Add(choices);
        var body = new StackPanel();
        if (sourceChoice == "external")
        {
            body.Children.Add(Label("Abra seu ComfyUI como de costume", 19, false));
            body.Children.Add(Label("Cole o endereço que ele usa neste PC. Aceita instalação manual, portable ou Desktop com API local HTTP. O ComfyUI precisa continuar aberto."));
            var address = new TextBox { Text = externalDraft, Margin = new(0, 8, 0, 8), IsEnabled = operation == null };
            address.TextChanged += (_, _) => { externalDraft = address.Text; };
            System.Windows.Automation.AutomationProperties.SetName(address, "Endereço local do ComfyUI"); body.Children.Add(address);
            body.Children.Add(SetupAction("Verificar e usar este ComfyUI", async (_, _) => await ApplySourceAsync("external", externalDraft), true));
            body.Children.Add(Label("O conector não instala nodes, baixa modelos nem inicia ou encerra essa instalação. Instale os recursos que faltarem pelo seu próprio ComfyUI."));
            if (IsExternal && external.Inventory != null)
            {
                body.Children.Add(Label(InventorySummary(), 14, false));
                AddCompatibility(body);
            }
        }
        else
        {
            body.Children.Add(Label("Uma instalação independente", 19, false));
            body.Children.Add(Label(gpu?.Summary ?? "O instalador gerenciado requer GPU NVIDIA com pelo menos 8 GB."));
            body.Children.Add(Label(RootBox.Text, 12));
            if (IsExternal) body.Children.Add(SetupAction("Usar instalação separada", async (_, _) => await ApplySourceAsync("managed", settings.ExternalUrl), true));
            else
            {
                var folder = SetupAction("Escolher pasta", ChooseFolder); folder.IsEnabled &= !engine.Running; body.Children.Add(folder);
                if (Installer.State == null)
                {
                    body.Children.Add(Label("Reserve 25 GB para o motor, além dos modelos escolhidos. Downloads podem ser cancelados e retomados."));
                    var install = SetupAction("Instalar ComfyUI separado", InstallRuntime, true); install.IsEnabled &= gpu?.Supported == true; body.Children.Add(install);
                }
                else body.Children.Add(Label("Motor instalado. Escolha abaixo os modelos que deseja baixar; também pode fazer isso depois em Modelos.", 14, false));
                var models = new StackPanel(); RenderModels(models);
                body.Children.Add(new Expander { Header = "Modelos disponíveis · perfil " + (gpu?.Profile ?? "8") + " GB", IsExpanded = Installer.State != null, Content = models, Foreground = Brush("#F3F3F7"), Margin = new(0, 14, 0, 0) });
            }
        }
        if (engine.Running && sourceChoice != settings.Backend) body.Children.Add(Label("Para trocar de ComfyUI, pare o motor atual na Visão geral. Nenhuma geração será encerrada automaticamente."));
        SetupPage.Children.Add(Card(body));
    }
    private string InventorySummary() => external.Inventory is { } info
        ? $"ComfyUI {info.Version} · {info.Nodes.Length} nodes · {info.Models.Length} modelos · {info.Loras.Length} LoRAs informados pela API."
        : "Verifique a conexão para consultar os recursos disponíveis.";
    private void AddCompatibility(StackPanel panel)
    {
        if (external.Inventory is not { } info) return;
        var content = new StackPanel(); var available = info.Nodes.ToHashSet(StringComparer.Ordinal);
        foreach (var wf in catalog.Document.RootElement.GetProperty("workflows").EnumerateArray())
        {
            var profile = wf.GetProperty("profiles").GetProperty(gpu?.Profile ?? "8");
            if (!profile.TryGetProperty("node_types", out var nodes)) continue;
            var missing = nodes.EnumerateArray().Select(n => n.GetString()!).Where(n => !available.Contains(n)).ToArray();
            content.Children.Add(Label(wf.GetProperty("name").GetString() + ": " + (missing.Length == 0 ? "nodes encontrados; pesos e memória ainda precisam ser compatíveis." : "nodes ausentes: " + string.Join(", ", missing))));
        }
        foreach (var (node, feature) in new[] { ("BwdaccVisualExtract", "Extração de composição e objetos"), ("MomentosVideoRange", "Corte de vídeo") })
            content.Children.Add(Label(feature + ": " + (available.Contains(node) ? "node encontrado; depende dos modelos necessários." : "requer o node " + node + ".")));
        panel.Children.Add(new Expander { Header = "Compatibilidade dos recursos", Content = content, Foreground = Brush("#F3F3F7"), Margin = new(0, 12, 0, 6) });
    }
    private void RenderExternalInventory(StackPanel panel, bool loras)
    {
        panel.Children.Add(Label("Recursos do ComfyUI existente", 19, false));
        panel.Children.Add(Label("Lista informada pela API. Gerencie downloads, arquivos e atualizações na sua própria instalação."));
        panel.Children.Add(SetupAction("Atualizar lista", async (_, _) => await CheckExternalAsync()));
        if (external.Inventory is not { } info) { panel.Children.Add(Label(InventorySummary())); return; }
        if (!external.Ready) panel.Children.Add(Label("Última consulta. ComfyUI desconectado; verifique novamente para continuar."));
        if (!loras) AddCompatibility(panel);
        var files = loras ? info.Loras : info.Models;
        foreach (var name in files.Take(500)) panel.Children.Add(Label(name, 13, false));
        if (files.Length == 0) panel.Children.Add(Label("Nenhum arquivo informado pela API nesta categoria."));
        if (files.Length > 500) panel.Children.Add(Label($"Mostrando 500 de {files.Length} arquivos."));
    }
    private async Task CheckExternalAsync() => await ApplySourceAsync("external", settings.ExternalUrl);
    private async Task ApplySourceAsync(string mode, string url)
    {
        await Work(async (p, token) =>
        {
            if (engine.Running) throw new IOException("Pare o motor gerenciado na Visão geral antes de mudar a conexão.");
            ExternalComfy? candidate = null;
            try
            {
                if (mode == "external")
                {
                    candidate = new ExternalComfy(); p.Report(new("Verificando ComfyUI", "Consultando API, modelos e nodes. Nenhum arquivo será alterado."));
                    await candidate.ConnectAsync(url, token);
                }
                var sameSource = settings.Backend == mode && (mode == "managed" || candidate!.Endpoint == ExternalComfy.Validate(settings.ExternalUrl));
                if (sameSource)
                {
                    external.Dispose(); external = candidate ?? new ExternalComfy(); candidate = null;
                    externalDraft = settings.ExternalUrl; return;
                }
                switchingBackend = true;
                if (privateConnection != null) { await privateConnection.DisposeAsync(); privateConnection = null; }
                if (IsExternal && await external.BusyAsync(token)) throw new IOException("A instalação atual tem gerações na fila. Aguarde antes de trocar ou atualizar a conexão.");
                var previous = settings;
                settings = settings with { Backend = mode, ExternalUrl = candidate?.Endpoint!.GetLeftPart(UriPartial.Authority) ?? url };
                try { SaveSettings(); } catch { settings = previous; throw; }
                external.Dispose(); external = candidate ?? new ExternalComfy(); candidate = null;
                sourceChoice = mode; externalDraft = settings.ExternalUrl;
            }
            finally
            {
                candidate?.Dispose(); switchingBackend = false;
                if (desktopIdentity != null && privateConnection == null)
                    privateConnection = new PrivateConnection(Paths.SettingsRoot, desktopIdentity, () => BackendReady, ConfirmDevice, backendPort: BackendPort);
                TickDevices();
            }
        }, mode == "external" ? "ComfyUI existente conectado" : "Instalação separada selecionada");
    }
}
