package com.leal.comfyremote;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.*;
import java.util.*;

/** Durable reference library. Legacy tags keep their IDs, handles and private covers. */
final class ReferencePreset {
    String id=UUID.randomUUID().toString(),kind="character",name="",text="",ghost="",ghostSource="";
    String cover="",legacyCover="",legacySource="",refmod="",refmodServer="",refmodSource="";
    boolean privateCover, useGhost=true;
    boolean useText=true,useImages=true,useRefmod=true,useLoras=true;
    int usageVersion=1;
    final List<Picture> images=new ArrayList<>();
    JSONArray loras=new JSONArray();
    JSONObject jobs=new JSONObject();
    JSONObject layout=new JSONObject();
    JSONObject controls=new JSONObject();
    String audioFile="",audioName="";long audioMillis;
    boolean audio(){return kind.equals("audio");}
    static final class Picture {
        String file,source; boolean hidden;
        Picture(String file,String source,boolean hidden){this.file=file;this.source=source;this.hidden=hidden;}
        JSONObject json() throws JSONException{return new JSONObject().put("file",file).put("source",source).put("private",hidden);}
    }
    boolean control(){return kind.equals("control");}
    char prefix(){return control()?'!':kind.equals("character")?'@':kind.equals("style")?'$':kind.equals("object")?'&':kind.equals("composition")?'%':'#';}
    String label(){return control()?UiLanguage.t("bloco"):kind.equals("character")?UiLanguage.t("personagem"):kind.equals("style")?UiLanguage.t("estilo"):kind.equals("object")?UiLanguage.t("objeto"):kind.equals("composition")?UiLanguage.t("composição"):UiLanguage.t("local");}
    String icon(){return audio()?"wave":control()?"layers":kind.equals("character")?"person":kind.equals("style")?"style":kind.equals("object")?"object":kind.equals("composition")?"boxes":"location";}
    String description(){return text.trim()+(useGhost&&!ghost.isEmpty()?(text.trim().isEmpty()?"":"\n")+ghost.trim():"");}
    String promptText(){return !audio()&&!control()&&useText?(description().isEmpty()?name:description()):"";}
    void usePermissions(ReferencePreset current){
        if(current.usageVersion<1)return;
        useText=current.useText;useGhost=current.useGhost;useImages=current.useImages;useRefmod=current.useRefmod;useLoras=current.useLoras;usageVersion=current.usageVersion;
    }
    boolean hidden(){if(privateCover)return true;for(Picture p:images)if(p.hidden)return true;return false;}
    String signature(){StringBuilder s=new StringBuilder();for(Picture p:images)s.append(p.file).append('|');return s.toString();}
    Picture coverPicture(){for(Picture p:images)if(p.file.equals(cover))return p;return images.isEmpty()?null:images.get(0);}
    ReferencePreset copy(){try{return parse(new JSONObject(json().toString()));}catch(Exception e){throw new IllegalStateException(e);}}
    JSONObject json() throws JSONException {
        JSONArray pictures=new JSONArray();for(Picture p:images)pictures.put(p.json());
        return new JSONObject().put("id",id).put("kind",kind).put("name",name).put("text",text).put("ghost",ghost)
            .put("ghostSource",ghostSource).put("useGhost",useGhost).put("cover",cover).put("legacyCover",legacyCover).put("legacySource",legacySource)
            .put("private",privateCover).put("images",pictures).put("loras",loras).put("jobs",jobs)
            .put("usageVersion",usageVersion).put("useText",useText).put("useImages",useImages).put("useRefmod",useRefmod).put("useLoras",useLoras)
            .put("refmod",refmod).put("refmodServer",refmodServer).put("refmodSource",refmodSource).put("layout",layout).put("controls",controls)
            .put("audioFile",audioFile).put("audioName",audioName).put("audioMillis",audioMillis);
    }
    static ReferencePreset parse(JSONObject j) throws JSONException {
        ReferencePreset p=new ReferencePreset();p.id=j.getString("id");p.kind=j.getString("kind");p.name=j.optString("name");p.text=j.optString("text");
        p.ghost=j.optString("ghost");p.ghostSource=j.optString("ghostSource");p.useGhost=j.optBoolean("useGhost",true);p.cover=j.optString("cover");
        p.usageVersion=j.optInt("usageVersion",0);p.useText=j.optBoolean("useText",true);p.useImages=j.optBoolean("useImages",true);p.useRefmod=j.optBoolean("useRefmod",true);p.useLoras=j.optBoolean("useLoras",true);
        p.legacyCover=j.optString("legacyCover");p.legacySource=j.optString("legacySource");p.privateCover=j.optBoolean("private");p.refmod=j.optString("refmod");p.refmodServer=j.optString("refmodServer");p.refmodSource=j.optString("refmodSource");
        p.loras=j.optJSONArray("loras");if(p.loras==null)p.loras=new JSONArray();p.jobs=j.optJSONObject("jobs");if(p.jobs==null)p.jobs=new JSONObject();
        p.layout=j.optJSONObject("layout");if(p.layout==null)p.layout=new JSONObject();
        p.controls=j.optJSONObject("controls");if(p.controls==null)p.controls=new JSONObject();
        p.audioFile=j.optString("audioFile");if(!p.audioFile.isEmpty()&&!validAudioFile(p.audioFile))throw new JSONException(UiLanguage.t("Invalid fragment audio path"));p.audioName=j.optString("audioName");p.audioMillis=j.optLong("audioMillis");
        if(p.audio())p.useText=p.useImages=p.useRefmod=p.useLoras=false;
        JSONArray a=j.optJSONArray("images");if(a!=null)for(int i=0;i<a.length();i++){JSONObject r=a.getJSONObject(i);String f=r.getString("file");if(validFile(f))p.images.add(new Picture(f,r.optString("source"),r.optBoolean("private")));}
        if(p.control()){p.useText=p.useImages=p.useRefmod=p.useLoras=false;ControlFragments.validate(p);}
        return p;
    }
    static boolean validFile(String name){return name.matches("[a-fA-F0-9-]+\\.(jpg|png)");}
    static boolean validAudioFile(String name){return name.matches("[a-fA-F0-9-]+\\.audio");}
    static final class Store {
        final SharedPreferences prefs;final File directory,files;
        Store(Context context,SharedPreferences prefs){this.prefs=prefs;files=context.getFilesDir();directory=new File(files,"references");directory.mkdirs();migrate();}
        File file(String name){if(!validFile(name)&&!validAudioFile(name))throw new IllegalArgumentException(UiLanguage.t("Arquivo inválido."));return new File(directory,name);}
        synchronized List<ReferencePreset> all(){
            List<ReferencePreset> result=new ArrayList<>();try{JSONArray list=new JSONArray(prefs.getString("reference_library_v1","[]"));for(int i=0;i<list.length();i++)result.add(parse(list.getJSONObject(i)));}
            catch(Exception e){throw new IllegalStateException(UiLanguage.t("Falha ao ler referências salvas."),e);}return result;
        }
        synchronized ReferencePreset get(String id){for(ReferencePreset p:all())if(p.id.equals(id))return p;return null;}
        synchronized void save(ReferencePreset p){List<ReferencePreset> list=all();int at=-1;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(p.id))at=i;if(at<0)list.add(p);else list.set(at,p);write(list);}
        synchronized void delete(String id){ReferencePreset old=get(id);List<ReferencePreset> list=all();list.removeIf(p->p.id.equals(id));write(list);if(old!=null)removeUnusedAudio(old.audioFile);}
        synchronized void removeUnusedAudio(String name){if(!validAudioFile(name))return;for(ReferencePreset p:all())if(p.audioFile.equals(name))return;VaultFiles.delete(file(name));}
        synchronized void protect(String source){List<ReferencePreset> list=all();boolean changed=false;for(ReferencePreset p:list){if(source.equals(p.legacySource)&&!p.privateCover){p.privateCover=true;changed=true;}for(Picture image:p.images)if(source.equals(image.source)&&!image.hidden){image.hidden=true;p.privateCover=true;changed=true;}}if(changed)write(list);}
        void cleanup(){Set<String> used=GenerationRecipe.presetFiles(files);for(ReferencePreset p:all()){for(Picture image:p.images)used.add(image.file);if(!p.audioFile.isEmpty())used.add(p.audioFile);}File[] entries=directory.listFiles();if(entries==null)return;long cutoff=System.currentTimeMillis()-86_400_000L;for(File file:entries)if(file.isFile()&&(validFile(file.getName())||validAudioFile(file.getName()))&&!used.contains(file.getName())&&file.lastModified()<cutoff)VaultFiles.delete(file);}
        private void write(List<ReferencePreset> list){try{JSONArray a=new JSONArray();for(ReferencePreset p:list)a.put(p.json());if(!prefs.edit().putString("reference_library_v1",a.toString()).commit())throw new IOException();}catch(Exception e){throw new IllegalStateException(UiLanguage.t("Não foi possível salvar referências."),e);}}
        private void migrate(){
            if(prefs.contains("reference_library_v1"))return;
            List<ReferencePreset> list=new ArrayList<>();
            try{
                for(String kind:new String[]{"style","character","location"}){
                    String key=kind.equals("style")?"prompt_styles":kind.equals("character")?"characters":"prompt_locations";
                    JSONArray a=new JSONArray(prefs.getString(key,"[]"));for(int i=0;i<a.length();i++){
                        JSONObject j=a.getJSONObject(i);ReferencePreset p=new ReferencePreset();p.id=j.getString("id");p.kind=kind;p.name=j.getString("name");p.text=j.optString(kind.equals("character")?"description":"text");p.privateCover=j.optBoolean("private");
                        String cover=j.optString("cover");if(cover.matches("[a-fA-F0-9-]+"))p.legacyCover=cover;
                        // Prefer the full original image. A former thumbnail remains a cover, never an invented reference.
                        String source=j.optString("source");p.legacySource=source;if(source.matches("[a-fA-F0-9-]+")){
                            File full=new File(files,"images/"+source+".img");
                            if(full.exists()){
                                String file=UUID.randomUUID()+".jpg";
                                try(InputStream in=VaultFiles.open(full);OutputStream out=VaultFiles.output(file(file))){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
                                p.images.add(new Picture(file,source,p.privateCover));p.cover=file;
                            }
                        }
                        list.add(p);
                    }
                }write(list);
            }catch(Exception e){throw new IllegalStateException(UiLanguage.t("Não foi possível migrar referências. Dados antigos preservados."),e);}
        }
    }
}
