using Microsoft.Win32;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace Bwdacc.Desktop;

public partial class MainWindow : Window
{
    private readonly RuntimeController engine = new();
    private ExternalComfy external = new();
    private DesktopSettings settings = new(Paths.DefaultRoot);
    private bool IsExternal => settings.Backend == "external";
    private bool BackendReady => !switchingBackend && (IsExternal ? external.Ready : engine.OwnsListener);
    private int BackendPort => IsExternal ? ExternalComfy.Validate(settings.ExternalUrl).Port : 8189;
    private readonly Catalog catalog = new();
    private readonly bool fixture;
    private CancellationTokenSource? operation;
    private Gpu? gpu;
    private bool ready, closing;
    private RuntimeInstaller Installer => new(RootBox.Text);
    private static Brush Brush(string color) => (Brush)new BrushConverter().ConvertFromString(color)!;
    [DllImport("dwmapi.dll")] private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attribute, ref int value, int size);
    public MainWindow(bool fixture = false)
    {
        this.fixture = fixture; InitializeComponent();
        DesktopVisuals.Navigation(OverviewNav, "Visão geral", "M3,4 L21,4 L21,20 L3,20 Z M3,9 L21,9 M8,9 L8,20");
        DesktopVisuals.Navigation(ModelsNav, "Modelos", "M3,3 L10,3 L10,10 L3,10 Z M14,3 L21,3 L21,10 L14,10 Z M3,14 L10,14 L10,21 L3,21 Z M14,14 L21,14 L21,21 L14,21 Z");
        DesktopVisuals.Navigation(LorasNav, "LoRAs", "M12,2 L15,9 L22,12 L15,15 L12,22 L9,15 L2,12 L9,9 Z");
        DesktopVisuals.Navigation(DevicesNav, "Dispositivos", "M3,3 L21,3 L21,16 L3,16 Z M8,21 L16,21 M12,16 L12,21");
        DesktopVisuals.Navigation(SettingsNav, "Ambiente", "M3,6 L21,6 M3,12 L21,12 M3,18 L21,18 M8,3 L8,9 M16,9 L16,15 M10,15 L10,21");
        VersionBadge.Text = typeof(App).Assembly.GetName().Version!.ToString(3) + " · desenvolvimento";
        var saved = Path.Combine(Paths.SettingsRoot, "desktop.json");
        try {
            settings = !fixture && File.Exists(saved) ? Data.Read<DesktopSettings>(saved) : new(Paths.DefaultRoot);
            if (settings.Backend is not ("managed" or "external")) throw new IOException("Modo inválido.");
            if (IsExternal) _ = ExternalComfy.Validate(settings.ExternalUrl);
            RootBox.Text = settings.Root; Paths.NoLinks(RootBox.Text);
        }
        catch { RootBox.Text = Paths.DefaultRoot; StatusDetail.Text = "Configuração anterior indisponível. Escolha novamente a pasta do estúdio."; }
        SourceInitialized += (_, _) => { var dark = 1; DwmSetWindowAttribute(new WindowInteropHelper(this).Handle, 20, ref dark, sizeof(int)); };
        Loaded += async (_, _) => {
            gpu = fixture ? new("NVIDIA GeForce RTX 3090", 24576, "581.80", 0, "8.6") : (await Hardware.DetectAsync()).FirstOrDefault();
            GpuTitle.Text = gpu?.Name ?? "GPU NVIDIA não encontrada";
            GpuDetail.Text = gpu == null ? "Esta prévia requer GPU NVIDIA com pelo menos 8 GB. Confira o driver antes de instalar." : gpu.Summary;
            if (gpu != null && !gpu.Supported) GpuDetail.Text += " · Requer CUDA 7.5+, driver 580+ e pelo menos 8 GB.";
            ProfileBox.SelectedIndex = gpu?.Profile switch { "24plus" => 3, "24" => 2, "16" => 1, _ => 0 };
            for (var i = 0; i < ProfileBox.Items.Count; i++) ((ComboBoxItem)ProfileBox.Items[i]).IsEnabled = gpu == null || i <= ProfileBox.SelectedIndex;
            ready = true; Refresh();
            if (!fixture) {
                BuildDevicesPage(); // Restores only explicitly enabled LAN/Tailscale access.
                if (IsExternal) await CheckExternalAsync();
                if (!settings.SetupComplete && (settings.SetupStep > 0 || Installer.State == null && desktopIdentity?.Devices.Length == 0)) BeginSetup();
            }
        };
        Closing += OnClosing;
        var timer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromSeconds(3) };
        timer.Tick += (_, _) => { if (ready && operation == null) SafeRefreshStatus(); TickDevices(); RefreshSetupControls(); }; timer.Start();
        Closed += (_, _) => { timer.Stop(); engine.Dispose(); external.Dispose(); };
    }
    private void RefreshStatus()
    {
        ManagedRootCard.Visibility = IsExternal ? Visibility.Collapsed : Visibility.Visible;
        ManagedLoraToolbar.Visibility = ManagedVersionHelp.Visibility = OpenRootButton.Visibility = IsExternal ? Visibility.Collapsed : Visibility.Visible;
        ModelListTitle.Text = IsExternal ? "Modelos informados pelo ComfyUI" : "Modelos compatíveis com seu perfil de memória";
        EnvironmentTitle.Text = IsExternal ? "Seu ComfyUI existente" : "Um ambiente independente";
        ExternalOverview.Visibility = IsExternal ? Visibility.Visible : Visibility.Collapsed;
        ProfileBox.IsEnabled = !IsExternal && operation == null;
        FamilyBox.IsEnabled = !IsExternal && operation == null;
        if (IsExternal) {
            GpuDetail.Text = "Hardware e configurações administrados pela sua instalação do ComfyUI.";
            EngineStatus.Text = external.Ready ? "●  ComfyUI existente conectado" : "○  Abra seu ComfyUI e verifique a conexão";
            EngineButton.Content = "Verificar conexão"; EngineButton.IsEnabled = operation == null;
            InstallButton.IsEnabled = BrowseButton.IsEnabled = ImportButton.IsEnabled = RollbackButton.IsEnabled = false;
            ExternalOverviewText.Text = settings.ExternalUrl + "\n" + InventorySummary();
            VersionDetail.Text = "Instalação existente · " + settings.ExternalUrl + "\n" + InventorySummary() + "\nAbra e encerre o ComfyUI pelo atalho que você já usa.";
            return;
        }
        GpuDetail.Text = gpu?.Summary ?? "A instalação gerenciada requer GPU NVIDIA com pelo menos 8 GB.";
        var state = Installer.State;
        EngineStatus.Text = engine.Running ? "●  Motor ativo · acesso local" : state == null ? "●  Ambiente ainda não instalado" : "○  Motor pronto para iniciar";
        EngineButton.Content = engine.Running ? "Parar motor" : "Iniciar motor";
        EngineButton.IsEnabled = operation == null && state != null;
        InstallButton.Content = state == null ? "Preparar meu PC" : "Reparar / atualizar motor";
        InstallButton.IsEnabled = operation == null && !engine.Running && gpu?.Supported == true;
        BrowseButton.IsEnabled = operation == null && !engine.Running;
        ImportButton.IsEnabled = operation == null && state != null;
        RollbackButton.IsEnabled = operation == null && !engine.Running && state?.Previous != null;
        VersionDetail.Text = state == null ? "Nenhum motor instalado. O ambiente será preparado na pasta escolhida." : "Versão ativa: " + state.Active + "\nVersão anterior: " + (state.Previous ?? "ainda não disponível") + "\nComfyUI restrito a 127.0.0.1:8189.";
    }
    private void SafeRefreshStatus()
    {
        try { RefreshStatus(); }
        catch { EngineButton.IsEnabled = InstallButton.IsEnabled = ImportButton.IsEnabled = RollbackButton.IsEnabled = false; BrowseButton.IsEnabled = operation == null && !engine.Running; StatusTitle.Text = "Configuração do ambiente indisponível"; StatusDetail.Text = "Escolha uma pasta válida. A instalação anterior foi preservada."; }
    }
    private void Refresh()
    {
        SafeRefreshStatus();
        try { RenderModels(); RenderLoras(); }
        catch { ModelCards.Children.Clear(); LoraCards.Children.Clear(); }
        if (setupStep == 1) BuildSetupSource();
        RefreshSetupControls();
    }
    private void Navigate(object sender, RoutedEventArgs e) { if (setupStep != 0) LeaveSetup(false); ShowPage(((Button)sender).Tag.ToString()!); }
    public void ShowPage(string page)
    {
        if (page.StartsWith("setup")) {
            BeginSetup(page == "setup-existing" ? "external" : "managed");
            if (fixture && page is "setup-phone" or "setup-tailscale") { setupStep = page == "setup-phone" ? 2 : 3; ShowSetupStep(); }
            return;
        }
        foreach (var (panel, name) in new[] { (OverviewPage, "overview"), (ModelsPage, "models"), (LorasPage, "loras"), (SettingsPage, "settings"), (DevicesPage, "devices"), (SetupPage, "source") }) panel.Visibility = name == page ? Visibility.Visible : Visibility.Collapsed;
        foreach (var button in new[] { OverviewNav, ModelsNav, LorasNav, SettingsNav, DevicesNav }) { bool selected = button.Tag.ToString() == page; button.Background = Brush(selected ? "#2B2C30" : "#111214"); button.Foreground = Brush(selected ? "#C6B2F3" : "#A7A8AE"); }
        PageTitle.Text = page switch { "models" => "Escolha seu motor criativo.", "loras" => "Detalhes que fazem diferença.", "settings" => "Seu ambiente, organizado.", _ => "Seu estúdio, pronto para criar." };
        PageSubtitle.Text = page switch { "models" => "Baixe apenas o que você pretende usar.", "loras" => "Uma biblioteca própria, organizada por modelo.", "settings" => "Instalação, versões e recuperação em um só lugar.", _ => "Prepare o computador uma vez. Gerencie tudo por aqui." };
        if (page == "devices") { PageTitle.Text = "Conexões sob seu controle."; PageSubtitle.Text = "Na sua rede ou de onde você estiver."; BuildDevicesPage(); }
        if (SystemParameters.ClientAreaAnimation && !fixture) Pages.BeginAnimation(OpacityProperty, new DoubleAnimation(.4, 1, TimeSpan.FromMilliseconds(180)));
    }
    private async Task Work(Func<IProgress<InstallProgress>, CancellationToken, Task> work, string success)
    {
        if (operation != null) return;
        operation = new(); var current = operation;
        CancelButton.Visibility = Progress.Visibility = Visibility.Visible; Progress.IsIndeterminate = true; Refresh();
        var progress = new Progress<InstallProgress>(p => { if (operation != current) return; StatusTitle.Text = p.Stage; StatusDetail.Text = p.Detail; Progress.IsIndeterminate = p.Fraction == null; if (p.Fraction != null) Progress.Value = p.Fraction.Value * 100; });
        try { await work(progress, current.Token); StatusTitle.Text = success; StatusDetail.Text = "Pronto."; }
        catch (OperationCanceledException) { StatusTitle.Text = "Operação cancelada"; StatusDetail.Text = "A versão ativa foi preservada. Downloads parciais podem ser retomados."; }
        catch (Exception ex) { StatusTitle.Text = "Não foi possível concluir"; StatusDetail.Text = ex.Message.Length > 240 ? ex.Message[..240] : ex.Message; }
        finally { operation = null; current.Dispose(); CancelButton.Visibility = Progress.Visibility = Visibility.Collapsed; Refresh(); }
    }
    private void ChooseFolder(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFolderDialog { Title = "Escolha uma pasta vazia para o estúdio" };
        if (dialog.ShowDialog(this) != true) return;
        try { Paths.NoLinks(dialog.FolderName); RootBox.Text = dialog.FolderName; SaveSettings(); Refresh(); }
        catch (Exception ex) { MessageBox.Show(this, ex.Message, "Pasta indisponível"); }
    }
    private void SaveSettings() { settings = settings with { Root = RootBox.Text }; if (!fixture) Data.Save(Path.Combine(Paths.SettingsRoot, "desktop.json"), settings); }
    private async void InstallRuntime(object sender, RoutedEventArgs e)
    {
        if (IsExternal || gpu?.Supported != true) return;
        SaveSettings(); await Work(async (p, t) => { p.Report(new("Instalando bwdacc Desktop", "Criando instalação e atalho para este usuário.")); await DesktopSetup.InstallAsync(t); await Installer.InstallAsync(p, t); }, "Ambiente preparado");
    }
    private async void ToggleEngine(object sender, RoutedEventArgs e)
    {
        if (IsExternal) { await CheckExternalAsync(); return; }
        if (engine.Running) await Work(async (_, _) => await engine.StopAsync(), "Motor parado");
        else await Work(async (p, t) => { p.Report(new("Iniciando motor", "Carregando componentes da instalação local.")); await engine.StartAsync(Installer, t); }, "Motor ativo");
    }
    private void Cancel(object sender, RoutedEventArgs e) { operation?.Cancel(); StatusDetail.Text = "Cancelando com segurança…"; }
    private void ProfileChanged(object sender, SelectionChangedEventArgs e) { if (ready) RenderModels(); }
    private Border Card(StackPanel content) => new() { Background = (Brush)FindResource("Card"), BorderBrush = Brush("#303135"), BorderThickness = new(1), CornerRadius = new(28), Padding = new(24), Margin = new(0, 0, 0, 16), Child = content };
    private TextBlock Label(string value, int size = 12, bool muted = true) => new() { Text = value, FontSize = size, FontFamily = (FontFamily)FindResource(size >= 15 ? "TitleFont" : "BodyFont"), Foreground = Brush(muted ? "#A6A7AD" : "#F3F3F7"), Margin = new(0, 0, 0, 10), TextWrapping = TextWrapping.Wrap };
    private void RenderModels(StackPanel? target = null)
    {
        if (!ready) return; var cards = target ?? ModelCards; cards.Children.Clear();
        if (IsExternal) { RenderExternalInventory(cards, false); return; }
        var profile = ((ComboBoxItem)ProfileBox.SelectedItem).Tag.ToString()!;
        foreach (var choice in catalog.Choices(profile, RootBox.Text))
        {
            var stack = new StackPanel(); stack.Children.Add(Label(choice.Name, 17, false));
            var present = choice.Files.Count > 0 && choice.Files.All(f => File.Exists(Paths.Under(RootBox.Text, f.Destination)));
            stack.Children.Add(Label(choice.Available ? $"{choice.Files.Count} arquivos · {Data.Size(choice.Bytes)} · SHA-256 verificado ao baixar" : choice.Reason));
            if (choice.Available)
            {
                var button = new Button { Content = present ? "Verificar arquivos" : "Baixar modelo", HorizontalAlignment = HorizontalAlignment.Left, Padding = new(14, 8, 14, 8), IsEnabled = operation == null && Installer.State != null };
                button.Click += async (_, _) => {
                    if (!present && MessageBox.Show(this, $"Baixar {choice.Name}?\n\nPacote: {Data.Size(choice.Bytes)}. Arquivos já verificados serão reutilizados. Consulte a licença do modelo antes de uso comercial.", "Baixar modelo", MessageBoxButton.OKCancel) != MessageBoxResult.OK) return;
                    await Work((p, t) => Installer.DownloadModelsAsync(choice, p, t), choice.Name + " disponível");
                }; stack.Children.Add(button);
            }
            cards.Children.Add(Card(stack));
        }
    }
    private void RenderLoras()
    {
        if (IsExternal) { LoraCards.Children.Clear(); RenderExternalInventory(LoraCards, true); return; }
        LoraCards.Children.Clear(); var root = Paths.Under(RootBox.Text, "models/loras");
        var files = Directory.Exists(root) ? Directory.EnumerateFiles(root, "*.safetensors", new EnumerationOptions { RecurseSubdirectories = true, AttributesToSkip = FileAttributes.ReparsePoint }).Take(500).ToList() : [];
        if (files.Count == 0) { LoraCards.Children.Add(Label("Sua biblioteca começa aqui. Importe um LoRA para personalizar os modelos instalados.", 14)); return; }
        foreach (var file in files)
        {
            var stack = new StackPanel(); stack.Children.Add(Label(Path.GetFileNameWithoutExtension(file), 15, false)); stack.Children.Add(Label(Path.GetRelativePath(root, file) + " · " + Data.Size(new FileInfo(file).Length)));
            var remove = new Button { Content = "Mover para lixeira", HorizontalAlignment = HorizontalAlignment.Left, Padding = new(12, 7, 12, 7), IsEnabled = operation == null && !engine.Running };
            remove.Click += (_, _) => {
                try { Paths.AssertRoot(RootBox.Text); Paths.NoLinks(file); var trash = Paths.Under(RootBox.Text, "trash/loras/" + Guid.NewGuid().ToString("N") + "/" + Path.GetRelativePath(root, file)); Directory.CreateDirectory(Path.GetDirectoryName(trash)!); File.Move(file, trash); StatusTitle.Text = "LoRA movido para a lixeira"; StatusDetail.Text = "O arquivo pode ser recuperado na pasta trash do estúdio."; Refresh(); }
                catch (Exception ex) { MessageBox.Show(this, ex.Message, "Não foi possível mover"); }
            }; stack.Children.Add(remove); LoraCards.Children.Add(Card(stack));
        }
    }
    private async void ImportLora(object sender, RoutedEventArgs e)
    {
        if (IsExternal) return;
        var dialog = new OpenFileDialog { Filter = "LoRA safetensors|*.safetensors", Multiselect = true };
        if (dialog.ShowDialog(this) != true) return;
        var family = ((ComboBoxItem)FamilyBox.SelectedItem).Tag.ToString()!;
        await Work(async (p, t) => { foreach (var file in dialog.FileNames) { p.Report(new("Importando LoRA", Path.GetFileName(file))); await Loras.ImportAsync(RootBox.Text, file, family, t); } }, "LoRAs importados");
    }
    private async void Rollback(object sender, RoutedEventArgs e) { if (!IsExternal) await Work((_, _) => { Installer.Rollback(); return Task.CompletedTask; }, "Versão anterior restaurada"); }
    private void OpenRoot(object sender, RoutedEventArgs e) { if (Directory.Exists(RootBox.Text)) Process.Start(new ProcessStartInfo("explorer.exe") { UseShellExecute = true, ArgumentList = { RootBox.Text } }); }
    private async void InstallManager(object sender, RoutedEventArgs e) => await Work(async (_, t) => { await DesktopSetup.InstallAsync(t); }, "Manager instalado no menu Iniciar");
    private async void OnClosing(object? sender, CancelEventArgs e)
    {
        if (closing) return;
        if (operation != null) { e.Cancel = true; MessageBox.Show(this, "Cancele a operação antes de fechar o manager.", "Operação em andamento"); return; }
        if (engine.Running && MessageBox.Show(this, "Fechar o manager encerra o motor e qualquer geração em andamento. Fechar mesmo assim?", "Encerrar motor", MessageBoxButton.YesNo) != MessageBoxResult.Yes) { e.Cancel = true; return; }
        e.Cancel = true; closing = true;
        try { await StopDevicesAsync(); } catch { /* Process exit also closes remaining sockets. */ }
        finally { _ = Dispatcher.BeginInvoke(new Action(Close)); }
    }
}
