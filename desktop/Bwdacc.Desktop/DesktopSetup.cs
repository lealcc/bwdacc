using System.Diagnostics;
using System.IO;
using Microsoft.Win32;

namespace Bwdacc.Desktop;

public static class DesktopSetup
{
    public static string InstallRoot => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", "bwdacc Desktop Preview");
    private static string Shortcut => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), "bwdacc Desktop Preview.lnk");
    public static async Task InstallAsync(CancellationToken token)
    {
        var source = Environment.ProcessPath!;
        if (Path.GetFileName(source).Equals("dotnet.exe", StringComparison.OrdinalIgnoreCase) || new FileInfo(source).Length < 10 * 1024 * 1024) throw new IOException("Use o executável publicado para instalar o manager.");
        Paths.NoLinks(InstallRoot);
        var marker = Path.Combine(InstallRoot, ".bwdacc-desktop");
        if (Directory.Exists(InstallRoot) && Directory.EnumerateFileSystemEntries(InstallRoot).Any() && (!File.Exists(marker) || File.ReadAllText(marker) != Paths.Identity)) throw new IOException("A pasta de destino já pertence a outra instalação.");
        Directory.CreateDirectory(InstallRoot); File.WriteAllText(marker, Paths.Identity);
        var hash = await Downloads.HashAsync(source, token);
        var folder = Paths.Under(InstallRoot, "releases/" + hash[..16]); Directory.CreateDirectory(folder);
        var target = Paths.Under(folder, "bwdacc-desktop.exe");
        if (!File.Exists(target))
        {
            var partial = target + ".partial";
            await using (var input = File.OpenRead(source)) await using (var output = new FileStream(partial, FileMode.Create)) await input.CopyToAsync(output, token);
            if (await Downloads.HashAsync(partial, token) != hash) throw new IOException("Falha ao verificar o manager copiado.");
            File.Move(partial, target);
        }
        else if (await Downloads.HashAsync(target, token) != hash) throw new IOException("Executável instalado não corresponde a esta versão.");
        // WScript creates a standard Windows shortcut; it does not execute shell text.
        var shellType = Type.GetTypeFromProgID("WScript.Shell") ?? throw new IOException("Criação de atalho indisponível.");
        dynamic shell = Activator.CreateInstance(shellType)!; dynamic link = shell.CreateShortcut(Shortcut);
        link.TargetPath = target; link.WorkingDirectory = folder; link.Description = "bwdacc Desktop · prévia de desenvolvimento"; link.Save();
        using var registry = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\bwdaccDesktopPreview");
        registry.SetValue("DisplayName", "bwdacc Desktop Preview"); registry.SetValue("DisplayVersion", typeof(App).Assembly.GetName().Version!.ToString(3)); registry.SetValue("Publisher", "bwdacc (development build)");
        registry.SetValue("InstallLocation", InstallRoot); registry.SetValue("DisplayIcon", target);
        registry.SetValue("UninstallString", "\"" + target + "\" --uninstall"); registry.SetValue("NoModify", 1); registry.SetValue("NoRepair", 1);
    }
    public static void BeginUninstall()
    {
        var stage = Path.Combine(Path.GetTempPath(), "bwdacc-uninstall-" + Guid.NewGuid().ToString("N")); Directory.CreateDirectory(stage);
        var copy = Path.Combine(stage, "bwdacc-uninstall.exe"); File.Copy(Environment.ProcessPath!, copy);
        Process.Start(new ProcessStartInfo(copy) { UseShellExecute = false, CreateNoWindow = true, ArgumentList = { "--remove-installed", Environment.ProcessId.ToString() } });
    }
    public static void RemoveInstalled(int parentPid)
    {
        try { using var parent = Process.GetProcessById(parentPid); if (!parent.WaitForExit(15000)) throw new IOException("O desinstalador original ainda está aberto."); } catch (ArgumentException) { }
        Paths.NoLinks(InstallRoot);
        var marker = Path.Combine(InstallRoot, ".bwdacc-desktop");
        if (!File.Exists(marker) || File.ReadAllText(marker) != Paths.Identity) throw new IOException("Instalação não reconhecida.");
        if (Directory.EnumerateFileSystemEntries(InstallRoot, "*", SearchOption.AllDirectories).Any(p => (File.GetAttributes(p) & FileAttributes.ReparsePoint) != 0)) throw new IOException("Links encontrados na instalação; remoção automática interrompida.");
        // Only the fixed, marker-verified program directory is removed. Runtime/media remain.
        Directory.Delete(InstallRoot, true); if (File.Exists(Shortcut)) File.Delete(Shortcut);
        Registry.CurrentUser.DeleteSubKeyTree(@"Software\Microsoft\Windows\CurrentVersion\Uninstall\bwdaccDesktopPreview", false);
    }
}
