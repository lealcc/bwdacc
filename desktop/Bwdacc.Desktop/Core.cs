using System.IO;
using System.Reflection;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Diagnostics;
using System.Globalization;

namespace Bwdacc.Desktop;

public static class Data
{
    public static readonly JsonSerializerOptions Json = new() { PropertyNameCaseInsensitive = true, WriteIndented = true };
    public static byte[] Resource(string suffix)
    {
        var assembly = Assembly.GetExecutingAssembly();
        var name = assembly.GetManifestResourceNames().Single(n => n.EndsWith(suffix, StringComparison.Ordinal));
        using var stream = assembly.GetManifestResourceStream(name)!;
        using var output = new MemoryStream(); stream.CopyTo(output); return output.ToArray();
    }
    public static T Read<T>(string path) => JsonSerializer.Deserialize<T>(File.ReadAllBytes(path), Json)!;
    public static void Save<T>(string path, T value)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        var temp = path + "." + Guid.NewGuid().ToString("N") + ".tmp";
        try { File.WriteAllBytes(temp, JsonSerializer.SerializeToUtf8Bytes(value, Json));
            if (File.Exists(path)) File.Replace(temp, path, null); else File.Move(temp, path);
        } finally { if (File.Exists(temp)) File.Delete(temp); }
    }
    public static string Size(long bytes) => bytes >= 1L << 30 ? $"{bytes / (double)(1L << 30):0.0} GB" : $"{bytes / 1048576d:0.0} MB";
}

public static class Paths
{
    public const string Identity = "bwdacc-commercial-runtime-v1";
    public static string SettingsRoot => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "bwdacc-commercial");
    public static string DefaultRoot => Path.Combine(SettingsRoot, "runtime");
    public static string Under(string root, string relative)
    {
        if (string.IsNullOrWhiteSpace(relative) || Path.IsPathRooted(relative)) throw new IOException("Caminho relativo inválido.");
        var parts = relative.Replace('\\', '/').Split('/');
        foreach (var p in parts)
        {
            if (p is "" or "." or ".." || p.EndsWith('.') || p.EndsWith(' ') || p.IndexOfAny(Path.GetInvalidFileNameChars()) >= 0)
                throw new IOException("Caminho inválido no pacote.");
            var name = p.Split('.')[0].ToUpperInvariant();
            if (name is "CON" or "PRN" or "AUX" or "NUL" || (name.Length == 4 && (name.StartsWith("COM") || name.StartsWith("LPT")) && char.IsDigit(name[3])))
                throw new IOException("Nome reservado no pacote.");
        }
        var fullRoot = Path.GetFullPath(root).TrimEnd(Path.DirectorySeparatorChar);
        var result = Path.GetFullPath(Path.Combine(fullRoot, Path.Combine(parts)));
        if (!result.StartsWith(fullRoot + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase)) throw new IOException("Caminho fora da instalação.");
        NoLinks(result); return result;
    }
    public static void NoLinks(string path)
    {
        for (var item = new DirectoryInfo(Path.GetFullPath(path)); item != null; item = item.Parent)
            if ((Directory.Exists(item.FullName) || File.Exists(item.FullName)) && (File.GetAttributes(item.FullName) & FileAttributes.ReparsePoint) != 0)
                throw new IOException("Escolha uma pasta local sem links ou junções.");
    }
    public static void PrepareRoot(string root)
    {
        root = Path.GetFullPath(root); NoLinks(root);
        if (root.StartsWith("\\\\") || root.TrimEnd('\\') == Path.GetPathRoot(root)?.TrimEnd('\\')) throw new IOException("Escolha uma subpasta em disco local.");
        var marker = Path.Combine(root, ".bwdacc-runtime");
        if (File.Exists(marker)) { AssertRoot(root); return; }
        if (Directory.Exists(root) && Directory.EnumerateFileSystemEntries(root).Any()) throw new IOException("A pasta precisa estar vazia. Sua instalação existente será preservada.");
        Directory.CreateDirectory(root); File.WriteAllText(marker, Identity);
    }
    public static void AssertRoot(string root)
    {
        NoLinks(root);
        if (!File.Exists(Path.Combine(root, ".bwdacc-runtime")) || File.ReadAllText(Path.Combine(root, ".bwdacc-runtime")) != Identity)
            throw new IOException("Esta pasta não é uma instalação gerenciada pelo bwdacc.");
    }
}

public sealed record Artifact(string Url, string Sha256, long Bytes);
public sealed record SourcePackage(string Id, string Url, string Sha256, long Bytes, string Destination, string Revision);
public sealed record RuntimeManifest(int Schema, string Release, string Python, Artifact Uv, SourcePackage[] Packages, string TorchIndex, int MinDriverMajor)
{
    public static RuntimeManifest Load() => JsonSerializer.Deserialize<RuntimeManifest>(Data.Resource("runtime.json"), Data.Json)!;
}
public sealed record RuntimeState(string Active, string? Previous);
public sealed record DesktopSettings(string Root, string Backend = "managed", string ExternalUrl = "http://127.0.0.1:8188", int SetupStep = 0, bool SetupComplete = false);
public sealed record Gpu(string Name, long MemoryMb, string Driver, int Index, string ComputeCapability = "")
{
    public string Profile => MemoryMb > 25 * 1024 ? "24plus" : MemoryMb >= 23 * 1024 ? "24" : MemoryMb >= 15 * 1024 ? "16" : "8";
    public bool Supported => MemoryMb >= 8 * 1024 - 256 && int.TryParse(Driver.Split('.')[0], out var major) && major >= RuntimeManifest.Load().MinDriverMajor && double.TryParse(ComputeCapability, NumberStyles.Number, CultureInfo.InvariantCulture, out var capability) && capability >= 7.5;
    public string Summary => $"{Name} · {MemoryMb / 1024d:0.#} GB · driver {Driver}";
}
public static class Hardware
{
    public static async Task<List<Gpu>> DetectAsync()
    {
        var system = Environment.GetFolderPath(Environment.SpecialFolder.System);
        var candidate = new[] { Path.Combine(system, "nvidia-smi.exe"), Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "NVIDIA Corporation", "NVSMI", "nvidia-smi.exe") }.FirstOrDefault(File.Exists);
        if (candidate == null) return [];
        try {
            var result = await Processes.RunAsync(candidate, ["--query-gpu=index,name,memory.total,driver_version,compute_cap", "--format=csv,noheader,nounits"], system, null, CancellationToken.None, TimeSpan.FromSeconds(10));
            return result.Split('\n', StringSplitOptions.RemoveEmptyEntries).Select(line => line.Trim().Split(',').Select(x => x.Trim()).ToArray())
                .Where(x => x.Length == 5).Select(x => new Gpu(x[1], long.Parse(x[2], CultureInfo.InvariantCulture), x[3], int.Parse(x[0]), x[4])).ToList();
        } catch { return []; }
    }
}

public sealed class Catalog
{
    public JsonDocument Document { get; } = JsonDocument.Parse(Data.Resource("catalog.json"));
    public List<ModelChoice> Choices(string profile, string root)
    {
        var result = new List<ModelChoice>(); var all = Document.RootElement;
        foreach (var wf in all.GetProperty("workflows").EnumerateArray())
        {
            var p = wf.GetProperty("profiles").GetProperty(profile);
            var available = p.GetProperty("status").GetString() == "available";
            var entries = new List<ModelFile>();
            if (available) foreach (var key in p.GetProperty("model_dependencies").EnumerateArray())
            {
                var m = all.GetProperty("models").GetProperty(key.GetString()!);
                entries.Add(new(m.GetProperty("destination").GetString()!, m.GetProperty("url").GetString()!, m.GetProperty("sha256").GetString()!, m.GetProperty("bytes").GetInt64(), m.TryGetProperty("gated", out var g) && g.GetBoolean()));
            }
            result.Add(new(wf.GetProperty("name").GetString()!, wf.GetProperty("family").GetString()!, available,
                available ? "" : p.GetProperty("reason").GetString()!, entries));
        }
        using var visual = JsonDocument.Parse(Data.Resource("visual-models.json"));
        var model = visual.RootElement;
        result.Add(new(model.GetProperty("name").GetString()!, "vision", true, "",
            model.GetProperty("files").EnumerateArray().Select(f => new ModelFile(f.GetProperty("destination").GetString()!,
                f.GetProperty("url").GetString()!, f.GetProperty("sha256").GetString()!, f.GetProperty("bytes").GetInt64(), false)).ToList()));
        return result;
    }
}
public sealed record ModelFile(string Destination, string Url, string Sha256, long Bytes, bool Gated);
public sealed record ModelChoice(string Name, string Family, bool Available, string Reason, List<ModelFile> Files)
{
    public long Bytes => Files.Sum(x => x.Bytes);
}
