package com.leal.comfyremote;
import org.json.JSONObject;
import java.util.regex.*;

/** Read the immutable submitted dimensions, rather than whichever options are selected now. */
final class GenerationAspect {
    private static final Pattern DIMENSIONS=Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*[×x:]\\s*(\\d+(?:\\.\\d+)?)");
    static double requested(JSONObject settings){
        if(settings==null)return 0;
        for(String key:new String[]{"Dimensões solicitadas","Proporção"}){
            Matcher match=DIMENSIONS.matcher(settings.optString(key));
            if(match.find())try{double width=Double.parseDouble(match.group(1)),height=Double.parseDouble(match.group(2));double ratio=width/height;if(width>0&&height>0&&ratio>=.05&&ratio<=20)return ratio;}catch(NumberFormatException ignored){}
        }return 0;
    }
}
