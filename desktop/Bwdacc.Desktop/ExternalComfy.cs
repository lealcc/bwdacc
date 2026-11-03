using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json;

namespace Bwdacc.Desktop;

public sealed record ComfyInventory(string Version, string[] Nodes, string[] Models, string[] Loras);

/// <summary>Read-only discovery of an explicitly selected local ComfyUI. Never owns its process or files.</summary>
public sealed class ExternalComfy : IDisposable
{
    private readonly HttpClient client = new(new SocketsHttpHandler { UseProxy = false, AllowAutoRedirect = false }) { Timeout = TimeSpan.FromSeconds(12) };
    private int? pid;
    private DateTime started;
    public Uri? Endpoint { get; private set; }
    public ComfyInventory? Inventory { get; private set; }
    public bool Ready
    {
        get
        {
            try { return Endpoint != null && pid is int owner && TcpOwner.Listener(Endpoint.Port, loopbackOnly: true) == owner && ProcessStart(owner) == started; }
            catch { return false; }
        }
    }
    private static DateTime ProcessStart(int id) { using var process = Process.GetProcessById(id); return process.StartTime.ToUniversalTime(); }
    public static Uri Validate(string value)
    {
        if (!Uri.TryCreate(value.Trim(), UriKind.Absolute, out var uri) || uri.Scheme != "http" ||
            uri.Host is not ("127.0.0.1" or "localhost") || uri.UserInfo.Length != 0 ||
            uri.AbsolutePath != "/" || uri.Query.Length != 0 || uri.Fragment.Length != 0 ||
            uri.Port is < 1024 or > 65535 || uri.Port == 8190)
            throw new IOException("Use o endereço local do ComfyUI, como http://127.0.0.1:8188, sem caminhos ou senha. A porta 8190 pertence ao conector.");
        return new Uri($"http://127.0.0.1:{uri.Port}/");
    }
    public async Task ConnectAsync(string value, CancellationToken token)
    {
        Disconnect();
        var endpoint = Validate(value);
        var owner = TcpOwner.Listener(endpoint.Port, loopbackOnly: true) ?? throw new IOException("ComfyUI não está aberto nesta porta. Inicie sua instalação normalmente e verifique novamente.");
        var birth = ProcessStart(owner);
        using var stats = await ReadAsync(endpoint, "system_stats", token);
        if (!stats.RootElement.TryGetProperty("system", out var system) || system.ValueKind != JsonValueKind.Object ||
            !stats.RootElement.TryGetProperty("devices", out var devices) || devices.ValueKind != JsonValueKind.Array)
            throw new IOException("O endereço respondeu, mas não apresentou a API do ComfyUI.");
        using var nodes = await ReadAsync(endpoint, "object_info", token);
        var inventory = ParseInventory(stats.RootElement, nodes.RootElement);
        using var queue = await ReadAsync(endpoint, "queue", token);
        _ = QueueBusy(queue.RootElement);
        if (TcpOwner.Listener(endpoint.Port, loopbackOnly: true) != owner || ProcessStart(owner) != birth)
            throw new IOException("ComfyUI reiniciou durante a verificação. Verifique novamente.");
        Endpoint = endpoint; Inventory = inventory; started = birth; pid = owner;
    }
    public async Task<bool> BusyAsync(CancellationToken token)
    {
        if (!Ready) return false;
        using var queue = await ReadAsync(Endpoint!, "queue", token);
        if (!Ready) throw new IOException("ComfyUI reiniciou. Verifique a conexão novamente.");
        return QueueBusy(queue.RootElement);
    }
    private static bool QueueBusy(JsonElement queue) => queue.GetProperty("queue_running").GetArrayLength() > 0 | queue.GetProperty("queue_pending").GetArrayLength() > 0;
    private async Task<JsonDocument> ReadAsync(Uri endpoint, string path, CancellationToken token)
    {
        using var response = await client.GetAsync(new Uri(endpoint, path), HttpCompletionOption.ResponseHeadersRead, token);
        if (!response.IsSuccessStatusCode) throw new IOException("Não foi possível consultar o ComfyUI. Confira o endereço e se a API local está acessível sem login.");
        const int limit = 32 * 1024 * 1024;
        if (response.Content.Headers.ContentLength > limit) throw new IOException("Resposta do ComfyUI excedeu o limite de leitura.");
        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(token); timeout.CancelAfter(TimeSpan.FromSeconds(15));
        await using var input = await response.Content.ReadAsStreamAsync(timeout.Token);
        using var output = new MemoryStream(); var buffer = new byte[32768]; int count;
        while ((count = await input.ReadAsync(buffer, timeout.Token)) != 0)
        { if (output.Length + count > limit) throw new IOException("Resposta do ComfyUI excedeu o limite de leitura."); await output.WriteAsync(buffer.AsMemory(0, count), timeout.Token); }
        return JsonDocument.Parse(output.ToArray(), new JsonDocumentOptions { MaxDepth = 64 });
    }
    internal static ComfyInventory ParseInventory(JsonElement stats, JsonElement schema)
    {
        if (schema.ValueKind != JsonValueKind.Object || !schema.TryGetProperty("LoadImage", out _)) throw new IOException("API de nodes do ComfyUI indisponível.");
        var models = new HashSet<string>(StringComparer.OrdinalIgnoreCase); var loras = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var modelKeys = new HashSet<string>(["ckpt_name", "unet_name", "model_name", "clip_name", "clip_name1", "clip_name2", "clip_name3", "vae_name", "lora_name", "control_net_name"]);
        foreach (var node in schema.EnumerateObject())
            if (node.Value.TryGetProperty("input", out var input))
                foreach (var group in new[] { "required", "optional" })
                    if (input.TryGetProperty(group, out var fields) && fields.ValueKind == JsonValueKind.Object)
                        foreach (var field in fields.EnumerateObject())
                            if (modelKeys.Contains(field.Name) && field.Value.ValueKind == JsonValueKind.Array && field.Value.GetArrayLength() > 0 && field.Value[0].ValueKind == JsonValueKind.Array)
                                foreach (var item in field.Value[0].EnumerateArray())
                                    if (item.ValueKind == JsonValueKind.String && !string.IsNullOrWhiteSpace(item.GetString()))
                                        (field.Name == "lora_name" ? loras : models).Add(item.GetString()!);
        var version = stats.GetProperty("system").TryGetProperty("comfyui_version", out var v) && v.ValueKind == JsonValueKind.String ? v.GetString()! : "versão não informada";
        return new(version, schema.EnumerateObject().Select(n => n.Name).Order().ToArray(), models.Order().ToArray(), loras.Order().ToArray());
    }
    public void Disconnect() { pid = null; Endpoint = null; Inventory = null; }
    public void Dispose() { Disconnect(); client.Dispose(); }
}
