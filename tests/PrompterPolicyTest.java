package com.leal.comfyremote;
import org.json.*;
import java.util.*;
public final class PrompterPolicyTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    static JSONObject item(String id,boolean temporary,String state,long expiry)throws Exception{return new JSONObject().put("id",id).put("temporary",temporary).put("state",state).put("expiresAt",expiry);}
    public static void main(String[] args)throws Exception{
        long now=1_000_000;JSONArray feed=new JSONArray().put(item("trial",true,"done",now-1)).put(item("saved",false,"done",now-1)).put(item("future",true,"done",now+1)).put(item("running",true,"running",now-1)).put(item("error",true,"error",now-1)).put(item("parent",true,"done",now-1)).put(item("child",true,"queued",now-1).put("parentId","parent")).put(item("active",true,"done",now-1)).put(item("../escape",true,"done",now-1));
        Set<String> expired=TrialPolicy.expiredIds(feed,now,new HashSet<>(Arrays.asList("active")));
        check(expired.equals(new HashSet<>(Arrays.asList("trial","error"))),"only expired unliked terminal tests; preserve active references and pending parents");
        check(!TrialPolicy.expired(true,"done",0,now),"unknown completion not expired immediately");
        check(TrialPolicy.RETENTION==86_400_000L,"24 hour retention");
        ProjectAlbum project=new ProjectAlbum();project.name="Teste";project.coverId="saved";project.privateCover=true;
        ProjectAlbum copy=ProjectAlbum.fromJson(project.toJson());check(copy.id.equals(project.id)&&copy.name.equals(project.name)&&copy.privateCover&&copy.coverId.equals("saved"),"project metadata round trip");
        System.out.println("PASS: retention, saved and pending protection, active refs, project persistence");
    }
}
