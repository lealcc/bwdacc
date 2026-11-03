using System.IO;
using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;

namespace Bwdacc.Desktop;

/// <summary>Opt-in real inference in a marker-owned commercial runtime, using synthetic media only.</summary>
public static class VisualReferenceTests
{
    public static async Task RunAsync(string root, string fixture, string report)
    {
        var installer = new RuntimeInstaller(root);
        Paths.AssertRoot(root);
        using var timeout = new CancellationTokenSource(TimeSpan.FromMinutes(6));
        using var engine = new RuntimeController();
        await engine.StartAsync(installer, timeout.Token);
        using var client = new HttpClient { BaseAddress = new Uri("http://127.0.0.1:8189"), Timeout = TimeSpan.FromSeconds(30) };
        try
        {
            await engine.ValidateNodesAsync(timeout.Token);
            var bytes = await File.ReadAllBytesAsync(fixture, timeout.Token);
            if (bytes.Length < 8 || bytes.Length > 8_000_000 || !bytes.AsSpan(0, 8).SequenceEqual(new byte[] {137,80,78,71,13,10,26,10}))
                throw new IOException("The visual fixture must be a bounded synthetic PNG.");
            using var upload = new MultipartFormDataContent();
            upload.Add(new ByteArrayContent(bytes), "image", "bwdacc-visual-test-" + Guid.NewGuid().ToString("N") + ".png");
            using var uploaded = await client.PostAsync("/upload/image", upload, timeout.Token);
            uploaded.EnsureSuccessStatusCode();
            using var uploadedJson = JsonDocument.Parse(await uploaded.Content.ReadAsStringAsync(timeout.Token));
            var name = uploadedJson.RootElement.GetProperty("name").GetString()!;
            var reports = new List<object>();
            foreach (var operation in new[] { "composition", "segment" })
            {
                var graph = new Dictionary<string, object>
                {
                    ["image"] = new { class_type = "LoadImage", inputs = new { image = name } },
                    ["result"] = new { class_type = "BwdaccVisualExtract", inputs = new { image = new object[] { "image", 0 }, operation,
                        points = "[{\"x\":0.5,\"y\":0.5,\"label\":1}]" } }
                };
                var watch = System.Diagnostics.Stopwatch.StartNew();
                using var response = await client.PostAsJsonAsync("/prompt", new { prompt = graph }, timeout.Token);
                response.EnsureSuccessStatusCode();
                using var receipt = JsonDocument.Parse(await response.Content.ReadAsStringAsync(timeout.Token));
                var id = receipt.RootElement.GetProperty("prompt_id").GetString();
                while (true)
                {
                    await Task.Delay(500, timeout.Token);
                    using var history = JsonDocument.Parse(await client.GetStringAsync("/history/" + id, timeout.Token));
                    if (!history.RootElement.TryGetProperty(id!, out var entry)) continue;
                    if (entry.GetProperty("status").GetProperty("status_str").GetString() == "error")
                        throw new IOException("Visual inference failed: " + entry.GetProperty("status").GetRawText());
                    if (!entry.TryGetProperty("outputs", out var outputs) || !outputs.TryGetProperty("result", out var node)) continue;
                    var text = node.GetProperty("text")[0].GetString()!;
                    using var result = JsonDocument.Parse(text);
                    if (operation == "composition")
                    {
                        if(result.RootElement.GetProperty("model").GetString()!="Florence-2-base-ft") throw new IOException("Composition did not use Florence-2.");
                        var boxes = result.RootElement.GetProperty("boxes");
                        if (boxes.GetArrayLength() is < 1 or > 12) throw new IOException("Invalid composition box count.");
                        foreach (var box in boxes.EnumerateArray())
                        {
                            var x = box.GetProperty("x").GetDouble();var y = box.GetProperty("y").GetDouble();
                            var w = box.GetProperty("w").GetDouble();var h = box.GetProperty("h").GetDouble();
                            if (x < 0 || y < 0 || w <= 0 || h <= 0 || x+w > 1.00001 || y+h > 1.00001) throw new IOException("Invalid normalized box.");
                            if(string.IsNullOrWhiteSpace(box.GetProperty("desc").GetString())) throw new IOException("Missing region description.");
                        }
                    }
                    else
                    {
                        var mask = Convert.FromBase64String(result.RootElement.GetProperty("mask").GetString()!);
                        if (mask.Length < 8 || !mask.AsSpan(0,8).SequenceEqual(bytes.AsSpan(0,8))) throw new IOException("Invalid mask PNG.");
                        await File.WriteAllBytesAsync(Path.Combine(Path.GetDirectoryName(report)!, "visual-mask.png"), mask, timeout.Token);
                    }
                    await File.WriteAllTextAsync(Path.Combine(Path.GetDirectoryName(report)!, "visual-" + operation + ".json"), text, timeout.Token);
                    reports.Add(new { operation, Passed = true, Seconds = watch.Elapsed.TotalSeconds });
                    break;
                }
            }
            Data.Save(report, new { Passed = true, Runtime = installer.State!.Active, Cases = reports });
        }
        finally { await engine.StopAsync(); }
    }
}
