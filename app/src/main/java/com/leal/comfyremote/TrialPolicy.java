package com.leal.comfyremote;
import org.json.*;
import java.util.*;

final class TrialPolicy {
    static final long RETENTION=24*60*60*1000L;
    static boolean expired(boolean temporary,String state,long expiresAt,long now){
        return temporary&&("done".equals(state)||"error".equals(state))&&expiresAt>0&&now>=expiresAt;
    }
    static Set<String> expiredIds(JSONArray feed,long now,Set<String> pinned){
        Set<String> protectedIds=new HashSet<>(pinned),expired=new HashSet<>();
        for(int i=0;i<feed.length();i++){JSONObject item=feed.optJSONObject(i);if(item==null)continue;
            if("queued".equals(item.optString("state"))||"running".equals(item.optString("state"))){protectedIds.add(item.optString("sourceId"));protectedIds.add(item.optString("parentId"));}}
        for(int i=0;i<feed.length();i++){JSONObject item=feed.optJSONObject(i);if(item==null)continue;String id=item.optString("id");
            if(id.matches("[A-Za-z0-9_-]{1,128}")&&!protectedIds.contains(id)&&expired(item.optBoolean("temporary"),item.optString("state"),item.optLong("expiresAt"),now))expired.add(id);}
        return expired;
    }
}
