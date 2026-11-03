using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.Text.Json;

namespace Bwdacc.Desktop;

public sealed record InstallProgress(string Stage, string Detail, double? Fraction = null);

public sealed class RuntimeInstaller
{
    public string Root { get; }
    public RuntimeInstaller(string root) => Root = Path.GetFullPath(root);
    public RuntimeState? State => File.Exists(Path.Combine(Root, "active.json")) ? Data.Read<RuntimeState>(Path.Combine(Root, "active.json")) : null;
    public string ReleasePath(string name) => Paths.Under(Root, "releases/" + name);
    public static void CheckDisk(string directory, long required)
    {
        var drive = new DriveInfo(Path.GetPathRoot(Path.GetFullPath(directory))!);
        if (!drive.IsReady || drive.AvailableFreeSpace < required) throw new IOException($"São necessários pelo menos {Data.Size(required)} livres neste disco.");
    }
    public async Task InstallAsync(IProgress<InstallProgress> progress, CancellationToken token)
    {
        var gpu = (await Hardware.DetectAsync()).FirstOrDefault();
        if (gpu?.Supported != true) throw new IOException("Requer GPU NVIDIA com capacidade CUDA 7.5+, pelo menos 8 GB e driver 580 ou superior.");
        if (!File.Exists(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "vcruntime140.dll"))) throw new IOException("Instale o Microsoft Visual C++ Redistributable x64 antes de preparar o motor.");
        Paths.PrepareRoot(Root);
        using var installationLock = new FileStream(Paths.Under(Root, "operation.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
        CheckDisk(Root, 25L << 30);
        var manifest = RuntimeManifest.Load();
        using var downloads = new Downloads();
        var cache = Paths.Under(Root, "cache"); Directory.CreateDirectory(cache);
        var uvZip = Paths.Under(cache, "uv-" + manifest.Uv.Sha256 + ".zip");
        progress.Report(new("Preparando instalador", "Baixando ferramenta de instalação verificada."));
        await downloads.FetchAsync(manifest.Uv, uvZip, new Progress<double>(x => progress.Report(new("Preparando instalador", "Verificando ferramenta de instalação.", x))), token);
        var uvFolder = Paths.Under(Root, "tools/uv-" + Guid.NewGuid().ToString("N"));
        Archives.Extract(uvZip, uvFolder, false); var uv = Paths.Under(uvFolder, "uv.exe");
        var env = new Dictionary<string, string> { ["UV_CACHE_DIR"] = Paths.Under(cache, "python-packages"), ["UV_PYTHON_INSTALL_DIR"] = Paths.Under(Root, "python"), ["UV_NO_CONFIG"] = "1", ["UV_NO_PROGRESS"] = "1", ["PYTHONNOUSERSITE"] = "1", ["PYTHONDONTWRITEBYTECODE"] = "1" };
        progress.Report(new("Preparando Python", "Ambiente próprio, sem alterar o Python do Windows."));
        await Processes.RunAsync(uv, ["--no-config", "python", "install", manifest.Python, "--no-bin", "--no-registry"], Root, env, token);
        var python = (await Processes.RunAsync(uv, ["--no-config", "python", "find", "--managed-python", manifest.Python], Root, env, token)).Trim();
        var pythonRoot = Path.GetFullPath(env["UV_PYTHON_INSTALL_DIR"]).TrimEnd('\\') + "\\";
        if (!Path.GetFullPath(python).StartsWith(pythonRoot, StringComparison.OrdinalIgnoreCase)) throw new IOException("Python encontrado fora da instalação gerenciada.");
        var releaseName = manifest.Release + "-" + Guid.NewGuid().ToString("N")[..8];
        var release = ReleasePath(releaseName); Directory.CreateDirectory(release);
        var comfy = Paths.Under(release, "ComfyUI");
        foreach (var package in manifest.Packages)
        {
            progress.Report(new("Instalando componentes", package.Id));
            var zip = Paths.Under(cache, package.Id + "-" + package.Sha256 + ".zip");
            await downloads.FetchAsync(new(package.Url, package.Sha256, package.Bytes), zip, new Progress<double>(x => progress.Report(new("Instalando componentes", package.Id, x))), token);
            token.ThrowIfCancellationRequested();
            Archives.Extract(zip, package.Destination == "." ? comfy : Paths.Under(comfy, package.Destination), true);
        }
        var assembly = Assembly.GetExecutingAssembly();
        // Image inference only needs the backbone, not upstream autoencoder/training exports.
        var efficientInit = Paths.Under(comfy, "custom_nodes/momentos_workflows/vendor/efficientsam3/sam3/sam3/backbones/efficientvit/efficientvit/__init__.py");
        await File.WriteAllTextAsync(efficientInit, "from .backbone import *\n", token);
        foreach (var name in assembly.GetManifestResourceNames().Where(n => n.Contains(".nodes.momentos_workflows.")))
        {
            var filename = name[(name.IndexOf(".nodes.momentos_workflows.", StringComparison.Ordinal) + ".nodes.momentos_workflows.".Length)..];
            var target = Paths.Under(comfy, "custom_nodes/momentos_workflows/" + filename);
            Directory.CreateDirectory(Path.GetDirectoryName(target)!);
            await using var input = assembly.GetManifestResourceStream(name)!;
            await using var output = File.Create(target); await input.CopyToAsync(output, token);
        }
        var environment = Paths.Under(release, "environment");
        await Processes.RunAsync(uv, ["--no-config", "venv", "--python", python, environment], Root, env, token);
        var managedPython = Paths.Under(environment, "Scripts/python.exe");
        var requirements = Paths.Under(release, "requirements.lock"); await File.WriteAllBytesAsync(requirements, Data.Resource("requirements.lock"), token);
        progress.Report(new("Instalando motor de geração", "PyTorch e dependências. Esta etapa baixa vários GB; você pode cancelar."));
        await Processes.RunAsync(uv, ["--no-config", "pip", "sync", "--python", managedPython, "--require-hashes", "--only-binary", ":all:", "--default-index", "https://pypi.org/simple", "--index", manifest.TorchIndex, "--index-strategy", "unsafe-best-match", requirements], Root, env, token);
        progress.Report(new("Verificando instalação", "Conferindo Python, PyTorch e acesso à GPU."));
        await Processes.RunAsync(managedPython, ["-I", "-c", "import torch, av, aiohttp, safetensors, imageio_ffmpeg; assert torch.cuda.is_available(), 'GPU NVIDIA indisponivel'; torch.zeros(1, device='cuda'); print(torch.__version__)"], comfy, env, token, TimeSpan.FromMinutes(2));
        token.ThrowIfCancellationRequested();
        foreach (var folder in new[] { "models", "media/input", "media/output", "media/temp", "user" }) Directory.CreateDirectory(Paths.Under(Root, folder));
        var models = Paths.Under(Root, "models").Replace('\\', '/');
        var yaml = new StringBuilder("bwdacc:\n  base_path: " + JsonSerializer.Serialize(models) + "\n  is_default: true\n");
        foreach (var key in new[] { "checkpoints", "diffusion_models", "text_encoders", "clip", "vae", "loras", "upscale_models", "embeddings", "controlnet", "segmentation", "vision" })
        { yaml.Append("  ").Append(key).Append(": ").Append(key).Append('\n'); Directory.CreateDirectory(Paths.Under(Root, "models/" + key)); }
        await File.WriteAllTextAsync(Paths.Under(Root, "models.yaml"), yaml.ToString(), token);
        progress.Report(new("Verificando motor", "Iniciando ComfyUI e conferindo os nodes dos workflows."));
        using (var candidate = new RuntimeController())
        {
            await candidate.StartCandidateAsync(this, releaseName, token);
            await candidate.ValidateNodesAsync(token);
            await candidate.StopAsync();
        }
        token.ThrowIfCancellationRequested();
        Data.Save(Paths.Under(release, "complete.json"), new { manifest.Release, CreatedAt = DateTimeOffset.UtcNow });
        var prior = State; Data.Save(Paths.Under(Root, "active.json"), new RuntimeState(releaseName, prior?.Active));
        progress.Report(new("Ambiente instalado", "Agora escolha os modelos que deseja baixar.", 1));
    }
    public void Rollback()
    {
        Paths.AssertRoot(Root);
        using var operationLock = new FileStream(Paths.Under(Root, "operation.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
        var state = State ?? throw new IOException("Nenhuma instalação disponível.");
        if (state.Previous == null || !File.Exists(Paths.Under(ReleasePath(state.Previous), "complete.json"))) throw new IOException("Nenhuma versão anterior validada.");
        Data.Save(Paths.Under(Root, "active.json"), new RuntimeState(state.Previous, state.Active));
    }
    public async Task DownloadModelsAsync(ModelChoice choice, IProgress<InstallProgress> progress, CancellationToken token)
    {
        Paths.AssertRoot(Root); if (!choice.Available) throw new IOException(choice.Reason);
        using var operationLock = new FileStream(Paths.Under(Root, "operation.lock"), FileMode.OpenOrCreate, FileAccess.ReadWrite, FileShare.None);
        if (choice.Files.Any(f => f.Gated)) throw new IOException("Este modelo requer autorização do fornecedor. Download automático indisponível.");
        CheckDisk(Root, choice.Files.Where(f => !File.Exists(Paths.Under(Root, f.Destination))).Sum(f => f.Bytes) + (1L << 30));
        using var downloads = new Downloads();
        foreach (var file in choice.Files)
            await downloads.FetchAsync(new(file.Url, file.Sha256, file.Bytes), Paths.Under(Root, file.Destination),
                new Progress<double>(x => progress.Report(new("Baixando " + choice.Name, Path.GetFileName(file.Destination) + " · " + Data.Size(file.Bytes), x))), token);
    }
}

public sealed class RuntimeController : IDisposable
{
    private Process? process; private ProcessJob? job; private int? listenerPid;
    private readonly HttpClient client = new() { Timeout = TimeSpan.FromSeconds(5) };
    public bool Running => process != null && !process.HasExited;
    public int? Pid => Running ? process!.Id : null;
    public bool OwnsListener => Running && TcpOwner.Listener(8189) is int owner && job?.Owns(owner) == true;
    public Task StartAsync(RuntimeInstaller installer, CancellationToken token) => StartReleaseAsync(installer, installer.State?.Active ?? throw new IOException("Instale o ambiente primeiro."), true, token);
    internal Task StartCandidateAsync(RuntimeInstaller installer, string name, CancellationToken token) => StartReleaseAsync(installer, name, false, token);
    private async Task StartReleaseAsync(RuntimeInstaller installer, string name, bool requireComplete, CancellationToken token)
    {
        if (Running) return; DisposeProcess(); Paths.AssertRoot(installer.Root);
        var release = installer.ReleasePath(name);
        if (requireComplete && !File.Exists(Paths.Under(release, "complete.json"))) throw new IOException("Ambiente incompleto. Reinstale antes de iniciar.");
        // A bind probe with Windows exclusive-address semantics rejects TIME_WAIT sockets
        // after a clean shutdown. Inspect actual listeners instead; ComfyUI does its own bind.
        if (TcpOwner.Listener(8189) != null) throw new IOException("Porta 8189 ocupada. Nenhum processo foi encerrado.");
        var comfy = Paths.Under(release, "ComfyUI");
        var args = new List<string> { "-s", Paths.Under(comfy, "main.py"), "--listen", "127.0.0.1", "--port", "8189", "--disable-auto-launch", "--disable-partner-nodes", "--disable-metadata", "--extra-model-paths-config", Paths.Under(installer.Root, "models.yaml"), "--user-directory", Paths.Under(installer.Root, "user") };
        foreach (var key in new[] { "input", "output", "temp" }) { args.Add("--" + key + "-directory"); args.Add(Paths.Under(installer.Root, "media/" + key)); }
        job = new ProcessJob(); process = new Process { StartInfo = Processes.Info(Paths.Under(release, "environment/Scripts/python.exe"), args, comfy, new() { ["PYTHONNOUSERSITE"] = "1", ["PYTHONDONTWRITEBYTECODE"] = "1" }) };
        try
        {
            process.Start(); job.Attach(process);
            // Runtime output may contain private prompts. Do not retain or expose it.
            _ = Processes.DiscardAsync(process.StandardOutput); _ = Processes.DiscardAsync(process.StandardError);
            var until = DateTime.UtcNow.AddMinutes(3);
            while (DateTime.UtcNow < until)
            {
                token.ThrowIfCancellationRequested(); if (process.HasExited) throw new IOException("Motor encerrou durante a inicialização. Confira GPU e dependências.");
                try {
                    var owner = TcpOwner.Listener(8189);
                    if (owner is int pid && !job.Owns(pid)) throw new IOException("Porta 8189 pertence a outro processo. Conexão recusada.");
                    if (owner is int) { using var response = await client.GetAsync("http://127.0.0.1:8189/system_stats", token); if (response.IsSuccessStatusCode) { listenerPid = owner; return; } }
                }
                catch (HttpRequestException) { } catch (TaskCanceledException) when (!token.IsCancellationRequested) { }
                await Task.Delay(750, token);
            }
            throw new IOException("Motor não respondeu a tempo.");
        } catch { DisposeProcess(); throw; }
    }
    public async Task<bool> BusyAsync()
    {
        if (!Running) return false;
        if (TcpOwner.Listener(8189) is not int owner || job?.Owns(owner) != true) throw new IOException("Conexão local não pertence ao motor gerenciado.");
        using var response = await client.GetAsync("http://127.0.0.1:8189/queue"); response.EnsureSuccessStatusCode();
        using var queue = JsonDocument.Parse(await response.Content.ReadAsStreamAsync());
        return queue.RootElement.GetProperty("queue_running").GetArrayLength() > 0 || queue.RootElement.GetProperty("queue_pending").GetArrayLength() > 0;
    }
    public async Task ValidateNodesAsync(CancellationToken token)
    {
        if (!Running || TcpOwner.Listener(8189) is not int owner || job?.Owns(owner) != true) throw new IOException("Motor indisponível para validação.");
        using var response = await client.GetAsync("http://127.0.0.1:8189/object_info", token); response.EnsureSuccessStatusCode();
        using var schema = JsonDocument.Parse(await response.Content.ReadAsStreamAsync(token));
        var catalog = new Catalog(); var required = new HashSet<string>();
        foreach (var wf in catalog.Document.RootElement.GetProperty("workflows").EnumerateArray())
            foreach (var profile in wf.GetProperty("profiles").EnumerateObject())
                if (profile.Value.TryGetProperty("node_types", out var nodes)) foreach (var node in nodes.EnumerateArray()) required.Add(node.GetString()!);
        required.Add("MomentosVideoRange");
        required.Add("BwdaccVisualExtract");
        required.Add("BwdaccMuseTalk");
        var missing = required.Where(name => !schema.RootElement.TryGetProperty(name, out _)).ToList();
        if (missing.Count > 0) throw new IOException("Nodes ausentes na instalação: " + string.Join(", ", missing));
    }
    public async Task StopAsync() { if (await BusyAsync()) throw new IOException("Há gerações em andamento ou na fila. Aguarde antes de parar."); await Task.Run(DisposeProcess); }
    private void DisposeProcess()
    {
        Process? listener = null; var priorListener = listenerPid;
        try { if (job != null && TcpOwner.Listener(8189) is int owner && job.Owns(owner)) listener = Process.GetProcessById(owner); } catch { }
        job?.Dispose(); job = null;
        // The Windows venv launcher can exit before its real Python child releases the port.
        // Wait only for handles proven to belong to this job, never another listener.
        try { if (process != null && !process.HasExited) process.WaitForExit(10000); } catch (InvalidOperationException) { }
        try { if (listener != null && !listener.HasExited) listener.WaitForExit(10000); } finally { listener?.Dispose(); }
        var deadline = DateTime.UtcNow.AddSeconds(5);
        while (priorListener != null && TcpOwner.Listener(8189) == priorListener && DateTime.UtcNow < deadline) Thread.Sleep(25);
        if (priorListener != null && TcpOwner.Listener(8189) == priorListener) throw new IOException("O motor encerrou, mas a porta local ainda não foi liberada. Aguarde antes de iniciar novamente.");
        listenerPid = null;
        process?.Dispose(); process = null;
    }
    public void Dispose() { DisposeProcess(); client.Dispose(); }
}

public static class Loras
{
    public static async Task ImportAsync(string root, string source, string family, CancellationToken token)
    {
        Paths.AssertRoot(root);
        if (Path.GetExtension(source).ToLowerInvariant() != ".safetensors") throw new IOException("Importe um arquivo .safetensors.");
        await using var input = File.OpenRead(source); var prefix = new byte[8]; await input.ReadExactlyAsync(prefix, token);
        var length = System.Buffers.Binary.BinaryPrimitives.ReadUInt64LittleEndian(prefix);
        if (length < 2 || length > 16 * 1024 * 1024 || (ulong)input.Length < length + 8) throw new IOException("Cabeçalho safetensors inválido.");
        var header = new byte[(int)length]; await input.ReadExactlyAsync(header, token); using var parsed = JsonDocument.Parse(header);
        if (parsed.RootElement.ValueKind != JsonValueKind.Object) throw new IOException("Arquivo safetensors inválido.");
        var ranges = new List<(long Start, long End)>();
        foreach (var field in parsed.RootElement.EnumerateObject())
        {
            if (field.Name == "__metadata__") continue;
            if (field.Value.ValueKind != JsonValueKind.Object || !field.Value.TryGetProperty("data_offsets", out var offsets) || offsets.ValueKind != JsonValueKind.Array || offsets.GetArrayLength() != 2 || !offsets[0].TryGetInt64(out var start) || !offsets[1].TryGetInt64(out var end) || start < 0 || end < start || end > input.Length - 8 - (long)length)
                throw new IOException("Intervalos de dados safetensors inválidos.");
            ranges.Add((start, end));
        }
        if (ranges.Count == 0) throw new IOException("Arquivo não contém tensores.");
        long next = 0; foreach (var range in ranges.OrderBy(r => r.Start)) { if (range.Start != next) throw new IOException("Dados safetensors sobrepostos ou incompletos."); next = range.End; }
        if (next != input.Length - 8 - (long)length) throw new IOException("Dados safetensors incompletos.");
        var destination = Paths.Under(root, "models/loras/" + family + "/" + Path.GetFileName(source));
        if (File.Exists(destination)) throw new IOException("Já existe um LoRA com esse nome. O arquivo existente foi preservado.");
        RuntimeInstaller.CheckDisk(root, input.Length + 1048576); Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        var partial = destination + ".import-" + Guid.NewGuid().ToString("N");
        try { input.Position = 0; await using (var output = new FileStream(partial, FileMode.CreateNew)) await input.CopyToAsync(output, token); token.ThrowIfCancellationRequested(); File.Move(partial, destination); }
        finally { if (File.Exists(partial)) File.Delete(partial); }
    }
}
