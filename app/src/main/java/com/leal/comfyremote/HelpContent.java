package com.leal.comfyremote;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.util.*;

/** Bundled, offline guides. No library media or network requests are used. */
final class HelpContent {
    final JSONObject labels;
    final JSONArray intro, topics;
    HelpContent(Context context)throws Exception {this(context,UiLanguage.TAGS[UiLanguage.index(context)]);}
    HelpContent(Context context,String language)throws Exception {
        if(!Arrays.asList(UiLanguage.TAGS).contains(language))language="pt-BR";
        try(InputStream input=context.getAssets().open("tutorials/"+language+".json")){
            JSONObject data=new JSONObject(new String(VaultFiles.readAll(input,300_000),"UTF-8"));
            labels=data.getJSONObject("labels");intro=data.getJSONArray("intro");topics=data.getJSONArray("topics");
        }
    }
    String text(String key){return labels.optString(key,key);}
    JSONObject topic(String id){for(int n=0;n<topics.length();n++){JSONObject item=topics.optJSONObject(n);if(item.optString("id").equals(id))return item;}return null;}
    List<JSONObject> search(String query){
        String needle=normalized(query);List<JSONObject> result=new ArrayList<>();
        for(int n=0;n<topics.length();n++){JSONObject item=topics.optJSONObject(n);
            if(normalized(item.optString("title")+" "+item.optString("summary")+" "+item.optJSONArray("steps")).contains(needle))result.add(item);
        }return result;
    }
    private static String normalized(String text){return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).trim();}
}
