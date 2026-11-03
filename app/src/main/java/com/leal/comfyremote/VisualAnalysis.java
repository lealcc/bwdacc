package com.leal.comfyremote;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import org.json.*;

/** Auxiliary queue jobs use the same pinned transport as generation. No public vision API. */
final class VisualAnalysis {
    static JSONObject run(ReferenceJobs.Server server, String address, File image, String operation,
                          JSONArray points, Callable<Boolean> cancelled, Consumer<String> status) throws Exception {
        if (address == null || address.isEmpty()) throw new IllegalStateException(UiLanguage.t("Conecte ao servidor em Ajustes."));
        JSONObject info = server.json("GET", address + "/object_info/BwdaccVisualExtract", null);
        if (!info.has("BwdaccVisualExtract")) throw new IllegalStateException(UiLanguage.t("Atualize o motor no Desktop para instalar o suporte SAM."));
        status.accept(UiLanguage.t("Enviando imagem…"));
        String uploaded = server.upload(address, image);
        if (cancelled.call()) throw new InterruptedException();
        JSONObject graph = new JSONObject();
        graph.put("image", ReferenceSupport.node("LoadImage", new JSONObject().put("image", uploaded)));
        graph.put("result", ReferenceSupport.node("BwdaccVisualExtract", new JSONObject()
            .put("image", ReferenceSupport.link("image")).put("operation", operation).put("points", points.toString())));
        String id;
        try {
            id = server.json("POST", address + "/prompt", new JSONObject().put("prompt", graph)
                .put("client_id", UUID.randomUUID().toString())).getString("prompt_id");
        } catch (Exception error) {
            throw new IllegalStateException(UiLanguage.t("Envio não confirmado. Confira a fila antes de tentar novamente."), error);
        }
        if (!id.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalStateException(UiLanguage.t("Servidor não retornou ID."));
        status.accept(UiLanguage.t("Na fila · analisando imagem…"));
        long deadline = android.os.SystemClock.elapsedRealtime() + 300_000;
        while (!cancelled.call() && android.os.SystemClock.elapsedRealtime() < deadline) {
            JSONObject history = server.json("GET", address + "/history/" + id, null).optJSONObject(id);
            if (history != null) {
                JSONObject state = history.optJSONObject("status");
                if (state != null && "error".equals(state.optString("status_str"))) {
                    String detail = UiLanguage.t("Não foi possível analisar. Confira o modelo SAM no Desktop.");
                    JSONArray messages = state.optJSONArray("messages");
                    if (messages != null) for (int i = 0; i < messages.length(); i++) {
                        JSONArray message = messages.optJSONArray(i);
                        if (message != null && "execution_error".equals(message.optString(0)) && message.optJSONObject(1) != null)
                            detail = message.getJSONObject(1).optString("exception_message", detail);
                    }
                    throw new IllegalStateException(detail.length() > 400 ? detail.substring(0, 400) : detail);
                }
                if (state != null && state.optBoolean("completed")) {
                    String result = ReferenceGraphs.outputText(history);
                    if (result.length() > 8_000_000) throw new IllegalStateException(UiLanguage.t("Resultado de análise inválido."));
                    return new JSONObject(result);
                }
            }
            Thread.sleep(700);
        }
        if (cancelled.call()) throw new InterruptedException();
        throw new IllegalStateException(UiLanguage.t("Análise demorou demais. Confira a fila antes de tentar novamente."));
    }

    static JSONObject layout(JSONObject result) throws Exception {
        double aspect = result.getDouble("aspect");
        if (!Double.isFinite(aspect) || aspect < .05 || aspect > 20) throw new JSONException(UiLanguage.t("Invalid aspect ratio"));
        JSONObject layout = new JSONObject().put("enabled", true).put("boxes", result.getJSONArray("boxes"))
            .put("aspect", aspect).put("model", result.optString("model"));
        BoxLayout.regions(layout);
        return layout;
    }
}
