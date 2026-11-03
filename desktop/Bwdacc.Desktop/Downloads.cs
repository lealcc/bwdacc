using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;

namespace Bwdacc.Desktop;

public sealed class Downloads : IDisposable
{
    private readonly HttpClient client;
    public Downloads(HttpMessageHandler? handler = null)
    {
        client = handler == null ? new HttpClient(new HttpClientHandler { AllowAutoRedirect = true }) : new HttpClient(handler);
        client.Timeout = Timeout.InfiniteTimeSpan; client.DefaultRequestHeaders.UserAgent.ParseAdd("bwdacc-desktop/0.1");
    }
    public static async Task<string> HashAsync(string file, CancellationToken token = default)
    {
        using var stream = File.OpenRead(file);
        return Convert.ToHexString(await SHA256.HashDataAsync(stream, token)).ToLowerInvariant();
    }
    public async Task FetchAsync(Artifact artifact, string target, IProgress<double>? progress, CancellationToken token)
    {
        if (new Uri(artifact.Url).Scheme != "https" || artifact.Bytes <= 0 || artifact.Sha256.Length != 64 || !artifact.Sha256.All(Uri.IsHexDigit))
            throw new IOException("Download sem manifesto de integridade válido.");
        Paths.NoLinks(target); Directory.CreateDirectory(Path.GetDirectoryName(target)!);
        if (File.Exists(target))
        {
            if (new FileInfo(target).Length == artifact.Bytes && await HashAsync(target, token) == artifact.Sha256.ToLowerInvariant()) { progress?.Report(1); return; }
            throw new IOException("Arquivo existente não corresponde ao catálogo. Ele foi preservado; escolha outra pasta ou remova-o manualmente.");
        }
        var partial = target + ".partial"; Paths.NoLinks(partial);
        if (File.Exists(partial) && new FileInfo(partial).Length > artifact.Bytes) File.Delete(partial);
        for (var attempt = 0; attempt < 3; attempt++)
        {
            token.ThrowIfCancellationRequested();
            var offset = File.Exists(partial) ? new FileInfo(partial).Length : 0;
            if (offset == artifact.Bytes) break;
            try
            {
                using var request = new HttpRequestMessage(HttpMethod.Get, artifact.Url);
                if (offset > 0) request.Headers.Range = new RangeHeaderValue(offset, null);
                using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token); timeout.CancelAfter(TimeSpan.FromMinutes(3));
                using var response = await client.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, timeout.Token);
                if (response.RequestMessage?.RequestUri?.Scheme != "https") throw new IOException("Redirecionamento sem HTTPS recusado.");
                response.EnsureSuccessStatusCode();
                var resumed = offset > 0 && response.StatusCode == HttpStatusCode.PartialContent;
                if (response.StatusCode == HttpStatusCode.PartialContent && (!resumed || response.Content.Headers.ContentRange?.From != offset || response.Content.Headers.ContentRange?.Length != artifact.Bytes))
                    throw new IOException("Resposta parcial incompatível com o download.");
                if (!resumed) offset = 0;
                var remaining = artifact.Bytes - offset;
                if (response.Content.Headers.ContentLength is long length && length != remaining) throw new IOException("Tamanho remoto diferente do manifesto.");
                await using var output = new FileStream(partial, resumed ? FileMode.Append : FileMode.Create, FileAccess.Write, FileShare.None, 131072, true);
                await using var input = await response.Content.ReadAsStreamAsync(timeout.Token);
                var buffer = new byte[131072]; var received = offset; long reportedAt = 0;
                while (true)
                {
                    timeout.CancelAfter(TimeSpan.FromMinutes(3));
                    var read = await input.ReadAsync(buffer, timeout.Token); if (read == 0) break;
                    received += read; if (received > artifact.Bytes) throw new IOException("Download excedeu o tamanho autorizado.");
                    await output.WriteAsync(buffer.AsMemory(0, read), token);
                    if (Environment.TickCount64 - reportedAt >= 100 || received == artifact.Bytes) { progress?.Report((double)received / artifact.Bytes); reportedAt = Environment.TickCount64; }
                }
                await output.FlushAsync(token);
                if (received != artifact.Bytes) throw new IOException("Download incompleto; poderá ser retomado.");
                break;
            }
            catch (Exception e) when (attempt < 2 && !token.IsCancellationRequested && (e is HttpRequestException or IOException or OperationCanceledException))
            { await Task.Delay(TimeSpan.FromSeconds(attempt + 1), token); }
        }
        token.ThrowIfCancellationRequested();
        if (!File.Exists(partial) || new FileInfo(partial).Length != artifact.Bytes || await HashAsync(partial, token) != artifact.Sha256.ToLowerInvariant())
        { if (File.Exists(partial)) File.Delete(partial); throw new IOException("Verificação SHA-256 falhou. Arquivo descartado."); }
        Paths.NoLinks(target); File.Move(partial, target); progress?.Report(1);
    }
    public void Dispose() => client.Dispose();
}

public static class Archives
{
    public static void Extract(string archive, string destination, bool stripRoot, long limit = 768L * 1024 * 1024)
    {
        Paths.NoLinks(destination); Directory.CreateDirectory(destination);
        using var zip = ZipFile.OpenRead(archive);
        if (zip.Entries.Count > 40000 || zip.Entries.Sum(e => e.Length) > limit) throw new IOException("Pacote excede o limite de extração.");
        var names = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        string? root = null;
        // Validate the entire archive before creating files.
        var files = new List<(ZipArchiveEntry Entry, string Target)>();
        foreach (var entry in zip.Entries)
        {
            if (((entry.ExternalAttributes >> 16) & 0xF000) == 0xA000) throw new IOException("Links não são permitidos em pacotes.");
            var name = entry.FullName.Replace('\\', '/').TrimEnd('/'); if (name.Length == 0) continue;
            Paths.Under(destination, name);
            if (stripRoot)
            {
                var slash = name.IndexOf('/'); var current = slash < 0 ? name : name[..slash];
                root ??= current; if (current != root) throw new IOException("Raízes inconsistentes no pacote.");
                if (slash < 0) { if (entry.Name.Length > 0) throw new IOException("Arquivo fora da raiz do pacote."); continue; }
                name = name[(slash + 1)..];
            }
            var target = Paths.Under(destination, name);
            if (entry.FullName.EndsWith('/')) continue;
            if (!names.Add(target)) throw new IOException("Caminho duplicado no pacote.");
            files.Add((entry, target));
        }
        foreach (var (entry, target) in files)
        {
            Directory.CreateDirectory(Path.GetDirectoryName(target)!); Paths.NoLinks(target);
            using var input = entry.Open(); using var output = new FileStream(target, FileMode.CreateNew);
            var buffer = new byte[65536]; long written = 0; int read;
            while ((read = input.Read(buffer)) > 0) { written += read; if (written > entry.Length) throw new IOException("Entrada excedeu tamanho declarado."); output.Write(buffer, 0, read); }
            if (written != entry.Length) throw new IOException("Entrada incompleta no pacote.");
        }
    }
}
