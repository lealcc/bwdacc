package com.leal.comfyremote;

import android.os.Handler;
import org.json.*;
import java.io.File;
import java.util.*;
import java.util.concurrent.*;

/** Small persisted jobs: reconnect after process death; never resubmit an uncertain POST. */
final class ReferenceJobs {
    interface Server { JSONObject json(String method,String url,JSONObject body)throws Exception;String upload(String address,File file)throws Exception;
        default byte[] download(String address,JSONObject image)throws Exception{throw new UnsupportedOperationException(UiLanguage.t("Image transport unavailable"));} }
    final ReferencePreset.Store store;final Server server;final Handler handler=new Handler();
    final ExecutorService worker=Executors.newSingleThreadExecutor();final Set<String> launching=Collections.synchronizedSet(new HashSet<>());
    boolean closed,busy;Runnable changed=()->{};
    final Map<String,Integer> missing=new HashMap<>();
    ReferenceJobs(ReferencePreset.Store store,Server server){this.store=store;this.server=server;tick();}
    void describeIfNeeded(ReferencePreset p,String address){
        if(!p.useText||!p.useGhost)return;
        ReferencePreset.Picture cover=p.coverPicture();if(cover==null||cover.file.equals(p.ghostSource))return;
        JSONObject previous=p.jobs.optJSONObject("ghost");
        // A failed/uncertain submission needs the explicit retry button, not another unrelated save.
        if(previous!=null&&"error".equals(previous.optString("state"))&&cover.file.equals(previous.optString("source")))return;
        start(p,"ghost",address);
    }
    void start(ReferencePreset preset,String kind,String address){
        if(address==null||address.isEmpty()){error(preset.id,kind,"Conecte ao servidor em Ajustes.");return;}
        String key=preset.id+kind;JSONObject previous=preset.jobs.optJSONObject(kind);
        if(launching.contains(key)||previous!=null&&Arrays.asList("queued","submitting").contains(previous.optString("state")))return;
        if(preset.images.isEmpty()){error(preset.id,kind,"Adicione uma imagem de referência.");return;}
        ReferencePreset snapshot=preset.copy();String signature=kind.equals("ghost")?snapshot.coverPicture().file:snapshot.signature();
        String expected="momentos_"+preset.id.replace("-","").substring(0,12)+"_"+Long.toHexString(System.currentTimeMillis());
        launching.add(key);update(preset.id,kind,new JSONObject(),"preparing","Preparando…",address,"",signature,expected);
        worker.execute(()->{
            boolean posting=false;
            try{
                JSONObject schema=kind.equals("ghost")?ReferenceGraphs.schema(server,address,"LlamaServerVisionCaption","UnloadAllModels","ShowText|pysssss","PreviewAny","LoadBackgroundRemovalModel","RemoveBackground","BwdaccReferenceFocus")
                    :ReferenceGraphs.schema(server,address,"MiniMaxH3RefModExtract","VAELoader");JSONObject graph;
                if(kind.equals("ghost")){graph=ReferenceGraphs.caption(schema,server.upload(address,store.file(snapshot.coverPicture().file)),snapshot.kind);}
                else{List<String> images=new ArrayList<>();for(ReferencePreset.Picture image:snapshot.images)images.add(server.upload(address,store.file(image.file)));graph=ReferenceGraphs.refmod(schema,images,expected,snapshot.kind,snapshot.useText?snapshot.description():"");}
                update(snapshot.id,kind,new JSONObject(),"submitting","Enviando…",address,"",signature,expected);posting=true;
                JSONObject result=server.json("POST",address+"/prompt",new JSONObject().put("prompt",graph).put("client_id",UUID.randomUUID().toString()));String id=result.optString("prompt_id");
                if(id.isEmpty())throw new IllegalStateException(UiLanguage.t("Servidor não retornou ID."));
                update(snapshot.id,kind,new JSONObject(),"queued","Na fila do ComfyUI",address,id,signature,expected);
            }catch(Exception e){error(snapshot.id,kind,(posting?"Envio não confirmado. Confira a fila antes de tentar novamente. ":"")+e.getMessage());}
            finally{handler.post(()->{launching.remove(key);changed.run();});}
        });
    }
    void error(String id,String kind,String message){synchronized(store){ReferencePreset p=store.get(id);if(p==null)return;try{JSONObject job=p.jobs.optJSONObject(kind);if(job==null)job=new JSONObject();job.put("state","error").put("message",message);p.jobs.put(kind,job);store.save(p);}catch(Exception ignored){}}handler.post(()->changed.run());}
    void update(String id,String kind,JSONObject job,String state,String message,String address,String prompt,String signature,String name){
        synchronized(store){ReferencePreset p=store.get(id);if(p==null)return;try{job.put("state",state).put("message",message).put("server",address).put("prompt",prompt).put("source",signature).put("name",name).put("created",System.currentTimeMillis());p.jobs.put(kind,job);store.save(p);}catch(Exception ignored){}}
        handler.post(()->changed.run());
    }
    void tick(){if(closed)return;if(!busy){busy=true;worker.execute(()->{try{for(ReferencePreset p:store.all())for(String kind:new String[]{"ghost","refmod"})check(p,kind);}finally{handler.post(()->{busy=false;if(!closed)handler.postDelayed(this::tick,4000);});}});}}
    void check(ReferencePreset preset,String kind){
        JSONObject job=preset.jobs.optJSONObject(kind);if(job==null)return;String state=job.optString("state");
        if((state.equals("preparing")||state.equals("submitting"))&&!launching.contains(preset.id+kind)){error(preset.id,kind,"Operação interrompida. Confira a fila antes de tentar novamente.");return;}
        if(!state.equals("queued"))return;
        try{
            String id=job.getString("prompt"),address=job.getString("server");JSONObject h=server.json("GET",address+"/history/"+id,null).optJSONObject(id);
            if(h==null){
                if(System.currentTimeMillis()-job.optLong("created")>30_000){JSONObject queue=server.json("GET",address+"/queue",null);boolean found=false;
                    for(String key:new String[]{"queue_running","queue_pending"}){JSONArray items=queue.optJSONArray(key);if(items!=null)for(int i=0;i<items.length();i++){JSONArray item=items.optJSONArray(i);if(item!=null&&id.equals(item.optString(1)))found=true;}}
                    if(found)missing.remove(id);else{int attempts=missing.containsKey(id)?missing.get(id)+1:1;missing.put(id,attempts);if(attempts>=3){error(preset.id,kind,"Trabalho não encontrado no servidor. Tente novamente.");missing.remove(id);}}
                }return;
            }
            missing.remove(id);
            JSONObject status=h.optJSONObject("status");if(status==null)return;
            if("error".equals(status.optString("status_str"))){error(preset.id,kind,"ComfyUI não concluiu. Verifique modelos e memória do servidor; tente novamente.");return;}
            if(!status.optBoolean("completed"))return;
            String text=ReferenceGraphs.outputText(h);if(kind.equals("ghost"))text=ReferenceGraphs.captionText(text);if(text.isEmpty()){error(preset.id,kind,"Servidor concluiu sem resultado de referência.");return;}
            synchronized(store){ReferencePreset p=store.get(preset.id);if(p==null)return;JSONObject current=p.jobs.optJSONObject(kind);if(current==null||!id.equals(current.optString("prompt")))return;
                if(kind.equals("ghost")){
                    if(p.coverPicture()==null||!p.coverPicture().file.equals(job.optString("source"))){error(p.id,kind,"Capa mudou. Atualizando descrição visual.");if(p.coverPicture()!=null)handler.post(()->{ReferencePreset latest=store.get(p.id);if(latest!=null)start(latest,kind,address);});return;}
                    p.ghost=text;p.ghostSource=job.optString("source");
                }else{
                    JSONObject result=new JSONObject(text);JSONArray paths=result.optJSONArray("saved_paths");if(paths==null||paths.length()==0)throw new IllegalStateException(UiLanguage.t("RefMod não foi salvo."));
                    if(!p.signature().equals(job.optString("source"))){error(p.id,kind,"Imagens mudaram. Gere outro RefMod.");return;}
                    p.refmod="Momentos/"+job.getString("name");p.refmodServer=address;p.refmodSource=job.optString("source");
                }
                current.put("state","done").put("message",kind.equals("ghost")?"Descrição visual pronta":"RefMod H3 pronto");store.save(p);
            }handler.post(()->changed.run());
        }catch(JSONException e){error(preset.id,kind,"Resultado recebido em formato inválido. Tente atualizar a referência.");}
        catch(Exception e){/* Offline polling preserves the server job ID and retries without submitting. */}
    }
    void close(){closed=true;handler.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
