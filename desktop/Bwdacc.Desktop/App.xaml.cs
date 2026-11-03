using System.Diagnostics;
using System.IO;
using System.Security.Principal;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace Bwdacc.Desktop;

public partial class App : Application
{
    private Mutex? instance;
    protected override async void OnStartup(StartupEventArgs e)
    {
        // Keep this 2D manager independent of driver/remote-session composition.
        // The generation engine is a separate process and still uses CUDA.
        RenderOptions.ProcessRenderMode = System.Windows.Interop.RenderMode.SoftwareOnly;
        base.OnStartup(e);
        if (e.Args.FirstOrDefault() == "--inspect-comfy")
        {
            if (e.Args.Length != 3) { Shutdown(2); return; }
            try {
                using var backend = new ExternalComfy(); await backend.ConnectAsync(e.Args[1], CancellationToken.None);
                var inventory = backend.Inventory!;
                Data.Save(e.Args[2], new { Ready = backend.Ready, inventory.Version, Nodes = inventory.Nodes.Length, Models = inventory.Models.Length, Loras = inventory.Loras.Length }); Shutdown(0);
            } catch (Exception error) { File.WriteAllText(e.Args[2] + ".error.txt", error.ToString()); Shutdown(1); }
            return;
        }
        if (e.Args.FirstOrDefault() == "--test-external")
        {
            if (e.Args.Length != 2) { Shutdown(2); return; }
            try { await ExternalComfyTests.RunAsync(e.Args[1]); Shutdown(0); }
            catch (Exception error) { File.WriteAllText(e.Args[1] + ".error.txt", error.ToString()); Shutdown(1); }
            return;
        }
        if(e.Args.FirstOrDefault()=="--download-visual-resources")
        {
            if(e.Args.Length!=3){Shutdown(2);return;}
            try {
                var installer=new RuntimeInstaller(e.Args[1]);Paths.PrepareRoot(installer.Root);
                var choice=new Catalog().Choices("8",installer.Root).Single(m=>m.Family=="vision");
                using var timeout=new CancellationTokenSource(TimeSpan.FromMinutes(30));
                var progress=new Progress<InstallProgress>(p=>File.WriteAllText(e.Args[2]+".progress",p.Stage+" | "+p.Detail));
                await installer.DownloadModelsAsync(choice,progress,timeout.Token);
                Data.Save(e.Args[2],new {Passed=true,installer.Root,choice.Name,choice.Bytes,Files=choice.Files.Count});Shutdown(0);
            }
            catch(Exception error){File.WriteAllText(e.Args[2]+".error.txt",error.ToString());Shutdown(1);}
            return;
        }
        if(e.Args.FirstOrDefault()=="--test-visual-runtime")
        {
            if(e.Args.Length!=4){Shutdown(2);return;}
            try { await VisualReferenceTests.RunAsync(e.Args[1],e.Args[2],e.Args[3]);Shutdown(0); }
            catch(Exception error) { File.WriteAllText(e.Args[3]+".error.txt",error.ToString());Shutdown(1); }
            return;
        }
        var headless = e.Args.Contains("--private-fixture") || e.Args.Contains("--test-private") || e.Args.Contains("--persistent-fixture") || e.Args.Contains("--test-persistent-remote") || e.Args.Contains("--smoke-remote") || e.Args.Contains("--test-remote") || e.Args.Contains("--remote-fixture") || e.Args.Contains("--self-test") || e.Args.Contains("--render") || e.Args.Contains("--job-probe") || e.Args.Contains("--smoke-install") || e.Args.Contains("--smoke-setup") || e.Args.Contains("--test-pairing") || e.Args.Contains("--pairing-fixture") || e.Args.Contains("--smoke-connection") || e.Args.Contains("--install-manager");
        try
        {
            if (e.Args.FirstOrDefault() == "--private-fixture") { await PairingTests.FixtureAsync(e.Args[1], privateAddress: System.Net.IPAddress.Parse(e.Args[2])); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--test-private") { await PrivateConnectionTests.RunAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--self-test") { await SelfTests.RunAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--test-pairing") { await PairingTests.RunAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--test-persistent-remote") { await PersistentRemoteTests.RunAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--test-remote") { await RemoteTests.RunAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--persistent-fixture") { await PairingTests.FixtureAsync(e.Args[1], true, true); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--remote-fixture") { await PairingTests.FixtureAsync(e.Args[1], true); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--smoke-connection") { await PairingTests.RealRuntimeAsync(e.Args[1], e.Args[2]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--smoke-remote") { await PairingTests.RealRuntimeAsync(e.Args[1], e.Args[2], true); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--pairing-fixture") { await PairingTests.FixtureAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--smoke-setup") { await SelfTests.RunSetupAsync(e.Args[1]); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--job-probe")
            {
                using var job = new ProcessJob(); using var child = Process.Start(Processes.Info(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "ping.exe"), ["-t", "127.0.0.1"], Path.GetTempPath(), null))!;
                job.Attach(child); File.WriteAllText(e.Args[1], child.Id.ToString()); await Task.Delay(Timeout.Infinite); return;
            }
            if (e.Args.FirstOrDefault() == "--smoke-install")
            {
                var root = Path.GetFullPath(e.Args[1]); var result = e.Args[2]; var installer = new RuntimeInstaller(root);
                using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(50));
                var progress = new Progress<InstallProgress>(p => File.WriteAllText(result + ".progress", p.Stage + " | " + p.Detail));
                await installer.InstallAsync(progress, timeout.Token);
                using var controller = new RuntimeController(); await controller.StartAsync(installer, timeout.Token);
                var busy = await controller.BusyAsync(); if (busy) throw new IOException("Fila de teste inesperada.");
                await controller.StopAsync();
                await controller.StartAsync(installer, timeout.Token); await controller.ValidateNodesAsync(timeout.Token); await controller.StopAsync();
                Data.Save(result, new { Passed = true, installer.State, QueueEmpty = true, ImmediateRestartPassed = true, NodeSchemasPassed = true, LoopbackPort = 8189 }); Shutdown(0); return;
            }
            if (e.Args.FirstOrDefault() == "--render")
            {
                var window = new MainWindow(true) { ShowActivated = false, ShowInTaskbar = false, WindowStartupLocation = WindowStartupLocation.Manual, Left = -10000, Top = -10000 }; MainWindow = window; window.Show();
                var page = e.Args.Length > 2 ? e.Args[2] : "overview";
                await Task.Delay(600); window.ShowPage(page); window.UpdateLayout();
                var target = window;
                if (target != window) target.Show(); target.UpdateLayout(); await Task.Delay(100);
                var bitmap = new RenderTargetBitmap((int)target.ActualWidth, (int)target.ActualHeight, 96, 96, PixelFormats.Pbgra32); bitmap.Render(target);
                var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap)); using (var output = File.Create(e.Args[1])) encoder.Save(output);
                if (target != window) target.Close(); window.Close(); Shutdown(0); return;
            }
            if (e.Args.FirstOrDefault() == "--remove-installed") { DesktopSetup.RemoveInstalled(int.Parse(e.Args[1])); MessageBox.Show("Manager removido. Ambiente, modelos e mídias foram preservados.", "bwdacc Desktop"); Shutdown(0); return; }
            instance = new Mutex(false, @"Local\bwdacc-commercial-desktop-" + WindowsIdentity.GetCurrent().User?.Value);
            try { if (!instance.WaitOne(0)) { MessageBox.Show("bwdacc Desktop já está aberto. Feche a outra janela antes de continuar."); Shutdown(0); return; } } catch (AbandonedMutexException) { }
            if (e.Args.FirstOrDefault() == "--install-manager") { await DesktopSetup.InstallAsync(default); Data.Save(e.Args[1], new { Installed = true, Directory = DesktopSetup.InstallRoot }); Shutdown(0); return; }
            if (e.Args.FirstOrDefault() == "--uninstall")
            {
                if (MessageBox.Show("Remover apenas o manager? Ambiente, modelos e mídias serão preservados.", "Desinstalar bwdacc Desktop", MessageBoxButton.YesNo) == MessageBoxResult.Yes) DesktopSetup.BeginUninstall();
                Shutdown(0); return;
            }
            MainWindow = new MainWindow(); MainWindow.Show();
        }
        catch (Exception ex)
        {
            if (headless) { var file = e.Args.FirstOrDefault() is "--smoke-install" or "--smoke-connection" or "--smoke-remote" && e.Args.Length > 2 ? e.Args[2] + ".error.txt" : e.Args.Length > 1 ? e.Args[1] + ".error.txt" : Path.Combine(Path.GetTempPath(), "bwdacc-test-error.txt"); File.WriteAllText(file, ex.ToString()); }
            else MessageBox.Show(ex.Message, "bwdacc Desktop");
            Shutdown(1);
        }
    }
    protected override void OnExit(ExitEventArgs e) { instance?.Dispose(); base.OnExit(e); }
}
