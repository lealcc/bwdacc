package com.leal.comfyremote;

import org.json.JSONObject;
import java.util.Locale;

final class ImageEditWorkflow {
    static final String KLEIN="Klein · Referências", SNOFS="Snofs Klein 9B · Edit NSFW";
    static boolean snofs(JSONObject graph){
        if(graph==null||graph.optJSONObject("2")==null)return false;
        JSONObject input=graph.optJSONObject("2").optJSONObject("inputs");
        return input!=null&&input.optString("unet_name").toLowerCase(Locale.ROOT).contains("snofs");
    }
    static String name(JSONObject graph){return snofs(graph)?SNOFS:KLEIN;}
    static String asset(boolean nsfw){return nsfw?"snofs_klein_edit_api.json":"klein_edit_api.json";}
}
