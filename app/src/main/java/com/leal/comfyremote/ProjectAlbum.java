package com.leal.comfyremote;
import org.json.*;
import java.util.*;

final class ProjectAlbum {
    String id=UUID.randomUUID().toString(),name="Projeto",coverId="",coverFile="";
    boolean privateCover;
    JSONObject toJson()throws Exception{return new JSONObject().put("id",id).put("name",name).put("coverId",coverId).put("coverFile",coverFile).put("privateCover",privateCover);}
    static ProjectAlbum fromJson(JSONObject value){ProjectAlbum p=new ProjectAlbum();p.id=value.optString("id");p.name=value.optString("name","Projeto");p.coverId=value.optString("coverId");p.coverFile=value.optString("coverFile");p.privateCover=value.optBoolean("privateCover");return p;}
}
