package com.leal.comfyremote;

import android.app.*;
import android.os.*;
import android.graphics.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Disposable, synthetic fixtures. Tests the real vault and real visual UI components. */
public final class VisualProbe extends Activity {
    public static final class PairingPreview extends PairingActivity {@Override public void onCreate(Bundle state){super.onCreate(state);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);}}
    ReferenceStudio studio;ReferencePreset.Store store;ReferenceJobs jobs;int checks;
    void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    @Override public void onCreate(Bundle state){super.onCreate(state);String language=getIntent().getStringExtra("language");if(language!=null)getSharedPreferences("interface",0).edit().putString("language",language).commit();UiLanguage.init(this);new Thread(()->{
        try{VaultFiles.prepare(this);store=new ReferencePreset.Store(this,VaultFiles.preferences());
            testRoundtrip();testControlFragments();testHelpContent();testLipsync();testAudioFragments();testVideoLoras();testSfwPromptFilter();testImageTools();testReferenceCaption();runOnUiThread(()->show());
        }catch(Throwable error){report(false,error);}
    }).start();}
    void testReferenceCaption()throws Exception{
        check(ReferenceGraphs.captionText("[empty 'content' field - reasoning_content was:] unfinished draft").isEmpty(),"VLM diagnostics never become fragment descriptions");
        check(ReferenceGraphs.captionText("<think>unfinished draft").isEmpty(),"unfinished reasoning is rejected");
        check(ReferenceGraphs.captionText("<think>draft</think>Oval face.").equals("Oval face."),"only the completed answer is saved");
        JSONObject required=new JSONObject().put("model_path",new JSONArray().put(new JSONArray().put("vision.gguf")))
            .put("mmproj_path",new JSONArray().put(new JSONArray().put("mmproj.gguf")));
        JSONObject schema=new JSONObject().put("LlamaServerVisionCaption",new JSONObject().put("input",new JSONObject().put("required",required)))
            .put("PreviewAny",new JSONObject()).put("UnloadAllModels",new JSONObject());
        JSONObject ordinary=ReferenceGraphs.caption(schema,"source.png","character");
        check(!ordinary.has("focus"),"older servers retain usable captions without optional segmentation");
        schema.put("BwdaccReferenceFocus",new JSONObject()).put("RemoveBackground",new JSONObject())
            .put("LoadBackgroundRemovalModel",new JSONObject().put("input",new JSONObject().put("required",new JSONObject()
                .put("bg_removal_name",new JSONArray().put("COMBO").put(new JSONObject().put("options",new JSONArray().put("birefnet.safetensors")))))));
        for(String kind:new String[]{"character","object","style","place"}){
            JSONObject graph=ReferenceGraphs.caption(schema,"source.png",kind);boolean isolate=kind.equals("character")||kind.equals("object");
            check(graph.has("focus")==isolate,"foreground isolation applies only to subjects: "+kind);
            check(graph.getJSONObject("release").getJSONObject("inputs").getJSONArray("value").getString(0).equals(isolate?"focus":"image"),"VLM receives prepared image after model release: "+kind);
            check(graph.getJSONObject("caption").getJSONObject("inputs").getString("user_prompt").equals(ReferenceGraphs.captionPrompt(kind)),"category instruction reaches VLM: "+kind);
            if(isolate)check(graph.getJSONObject("focus").getJSONObject("inputs").getJSONArray("transparent").getInt(1)==1,"source alpha reaches focus node: "+kind);
        }
        check(ReferenceGraphs.captionPrompt("character").contains("Omit clothing")&&ReferenceGraphs.captionPrompt("character").contains("Never guess anatomy"),"character identity excludes outfit and hidden anatomy");
        schema.getJSONObject("LoadBackgroundRemovalModel").getJSONObject("input").getJSONObject("required")
            .put("bg_removal_name",new JSONArray().put(new JSONArray()));
        check(!ReferenceGraphs.caption(schema,"source.png","character").has("focus"),"installed nodes without BiRefNet weights do not break captions");
    }
    void testRoundtrip()throws Exception{
        Bitmap image=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);image.eraseColor(Color.MAGENTA);
        Bitmap mask=Bitmap.createBitmap(20,10,Bitmap.Config.ARGB_8888);mask.eraseColor(Color.BLACK);
        for(int y=2;y<8;y++)for(int x=3;x<11;x++)if(!(x==3&&y==2))mask.setPixel(x,y,Color.WHITE);
        Bitmap crop=ReferenceExtractor.cutout(image,mask);check(crop.getWidth()==8&&crop.getHeight()==6,"tight cutout bounds");
        check(Color.alpha(crop.getPixel(0,0))==0&&Color.alpha(crop.getPixel(1,1))==255,"transparent cutout, no black background");
        String name=UUID.randomUUID()+".png";File file=store.file(name);try(OutputStream out=VaultFiles.output(file)){crop.compress(Bitmap.CompressFormat.PNG,100,out);}
        byte[] raw=Files.readAllBytes(file.toPath());check(raw[0]!=(byte)137,"reference encrypted at rest");
        Bitmap restored=VaultFiles.bitmap(file.getAbsolutePath());check(Color.alpha(restored.getPixel(0,0))==0,"alpha survives vault roundtrip");
        ReferencePreset object=new ReferencePreset();object.kind="object";object.name="Synthetic object";object.text="small magenta object";object.images.add(new ReferencePreset.Picture(name,"synthetic-origin",true));object.cover=name;
        store.save(object);ReferencePreset read=store.get(object.id);check(read.hidden()&&read.prefix()=='&'&&read.cover.equals(name),"object kind, privacy and PNG survive reload");
        Map<String,String> tags=new HashMap<>();tags.put("&object",object.text);check(PromptTags.expand("&object near @person",tags).equals("small magenta object near @person"),"object tag expands once");
        check(PromptTags.tokens("&object A&object \\&escaped").size()==1,"object tag boundaries");
        check(ReferenceBindings.pictures(Arrays.asList(object),false,"").size()==1,"object participates in compatible image references");
        JSONObject layout=new JSONObject("{\"enabled\":true,\"aspect\":2,\"boxes\":[{\"x\":0.15,\"y\":0.2,\"w\":0.4,\"h\":0.6,\"desc\":\"object\",\"type\":\"obj\"}]}");
        ReferencePreset composition=new ReferencePreset();composition.kind="composition";composition.name="Synthetic layout";composition.layout=layout;composition.useImages=false;composition.images.add(new ReferencePreset.Picture(name,"synthetic-origin",true));store.save(composition);
        ReferencePreset loaded=store.get(composition.id);check(BoxLayout.regions(loaded.layout).length()==1&&loaded.layout.getDouble("aspect")==2,"composition stores normalized boxes and original aspect");
        loaded.layout.getJSONArray("boxes").getJSONObject(0).put("x",.2);check(store.get(composition.id).layout.getJSONArray("boxes").getJSONObject(0).getDouble("x")==.15,"draft changes do not mutate saved composition");
        check(ReferenceBindings.pictures(Arrays.asList(composition),false,"").isEmpty(),"composition cover never becomes image conditioning");
        check(VaultBackup.validPath("references/"+name),"PNG included in portable backup");check(!VaultBackup.validPath("references/../"+name),"backup traversal rejected");
        ReferencePreset.Picture full=new ReferencePreset.Picture("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg","synthetic-image",false);
        try(InputStream input=getAssets().open("covers/default/cover-1.png");OutputStream output=VaultFiles.output(store.file(full.file))){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}
    }
    void show(){
        try{
            testHelpFlow();
            jobs=new ReferenceJobs(store,new ReferenceJobs.Server(){public JSONObject json(String method,String url,JSONObject body)throws Exception{
                if(url.contains("object_info"))return new JSONObject().put("BwdaccVisualExtract",new JSONObject());
                if(method.equals("POST"))return new JSONObject().put("prompt_id","synthetic-analysis");
                try(InputStream in=getAssets().open("visual-segment.json")){String data=new String(VaultFiles.readAll(in,8_000_000),"UTF-8");return new JSONObject().put("synthetic-analysis",new JSONObject().put("status",new JSONObject().put("completed",true)).put("outputs",new JSONObject().put("result",new JSONObject().put("text",new JSONArray().put(data)))));}
            }public String upload(String address,File file){return "synthetic.png";}});
            studio=new ReferenceStudio(this,store,jobs,new ReferenceStudio.Host(){public String tag(ReferencePreset p){return p.prefix()+p.name.replace(' ','_');}public void insert(String tag){}public String address(){return "fixture";}public boolean nsfw(){return true;}
                public Bitmap preview(ReferencePreset p,ReferencePreset.Picture image,boolean reveal){return image==null?null:VaultFiles.bitmap(store.file(image.file).getAbsolutePath());}
                public void choose(boolean library,int remaining,ReferenceStudio.Pick callback){callback.accept(Collections.singletonList(new ReferencePreset.Picture("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg","synthetic-image",false)));}
                public void composition(ReferencePreset p){}
            });
            String phase=getIntent().getStringExtra("phase");
            if(phase!=null&&phase.startsWith("modal-")){modalPreview(phase);return;}
            if("negative-settings".equals(phase)){mediaPreview(phase);return;}
            if(phase!=null&&(phase.equals("animate-loras")||phase.equals("video-loras")||phase.equals("gallery-menu")||phase.equals("private-menu")||phase.equals("outpaint-tools")||phase.equals("background-tools")||phase.equals("background-result"))){mediaPreview(phase);return;}
            else if("audio".equals(phase)){setContentView(studio.library(6));}
            else if("audio-editor".equals(phase)){ReferencePreset p=new ReferencePreset();p.kind="audio";studio.edit(p);allowSyntheticScreenshot(studio.editor);}
            else if("record".equals(phase)||"recordtest".equals(phase)){studio.audio.record(a->LipsyncAudio.discard(a));AudioTools.Capture capture=studio.audio.capture;allowSyntheticScreenshot(capture.sheet.dialog);if("recordtest".equals(phase)){capture.start();new Handler().postDelayed(capture::finish,650);new Handler().postDelayed(()->{try{check(capture.audio!=null&&capture.audio.millis>=200&&capture.audio.millis<5000,"microphone capture produces bounded encrypted audio");capture.start();new Handler().postDelayed(()->{studio.audio.pause();new Handler().postDelayed(()->{try{check(!capture.running&&capture.recorder==null&&studio.audio.capture==null,"leaving app stops and releases microphone");report(true,null);}catch(Throwable e){report(false,e);}},800);},400);}catch(Throwable e){report(false,e);}},1800);return;}}
            else if("connection".equals(phase)){allowSyntheticScreenshot(ConnectionGuide.show(this).dialog);}
            else if("pairing".equals(phase)){startActivity(new android.content.Intent(this,PairingPreview.class));}
            else if("lipsync".equals(phase)){LipsyncSheet sheet=new LipsyncSheet(this,false,null,1024,Runnable::run,()->{},(audio,size)->false);allowSyntheticScreenshot(sheet.dialog);}
            else if("intro".equals(phase)){HelpCenter help=new HelpCenter(this,()->{});help.introduction(true);allowSyntheticScreenshot(help.dialog);}
            else if("tutorials".equals(phase)){HelpCenter help=new HelpCenter(this,()->{});help.library();allowSyntheticScreenshot(help.dialog);}
            else if("navigation".equals(phase)){startActivity(new android.content.Intent(this,MainActivity.class));}
            else if("select".equals(phase)){studio.extractor.open(new ReferencePreset.Picture("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg","synthetic-image",false),false);new Handler().postDelayed(()->{try{allowSyntheticScreenshot(studio.extractor.active.dialog);}catch(Throwable error){report(false,error);}},1500);}
            else if("boxes".equals(phase)){try(InputStream in=getAssets().open("visual-composition.json")){
                JSONObject layout=VisualAnalysis.layout(new JSONObject(new String(VaultFiles.readAll(in,100000),"UTF-8")));
                Class<?> type=Class.forName("com.leal.comfyremote.BoxEditor$Editor");
                java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor(Activity.class,JSONObject.class,double.class,Bitmap.class,BoxEditor.Saved.class);constructor.setAccessible(true);
                Object editor=constructor.newInstance(this,layout,layout.getDouble("aspect"),VaultFiles.bitmap(store.file("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg").getAbsolutePath()),(BoxEditor.Saved)(v->{}));
                java.lang.reflect.Method show=type.getDeclaredMethod("show");show.setAccessible(true);show.invoke(editor);
                java.lang.reflect.Field dialog=type.getDeclaredField("dialog");dialog.setAccessible(true);allowSyntheticScreenshot((Dialog)dialog.get(editor));
            }}
            else if("fragments".equals(phase)){setContentView(studio.library(5));}
            else if("control".equals(phase)){for(ReferencePreset p:store.all())if(p.control()){studio.edit(p);allowSyntheticScreenshot(studio.editor);break;}}
            else studio.manage(3);
            report(true,null);
        }catch(Throwable error){report(false,error);}
    }
    void modalPreview(String phase){
        android.widget.LinearLayout background=new android.widget.LinearLayout(this);background.setBackground(Appearance.atmosphere());setContentView(background);
        final boolean[] called={false};FlowDialogs.Builder builder=new FlowDialogs.Builder(this);
        if(phase.equals("modal-choice"))builder.setTitle(UiLanguage.t("Idioma do app")).setSingleChoiceItems(UiLanguage.NAMES,UiLanguage.index(this),(d,w)->called[0]=true).setNegativeButton(UiLanguage.t("Cancelar"),null);
        else if(phase.equals("modal-input")){android.widget.EditText field=new android.widget.EditText(this);field.setHint(UiLanguage.t("Nome do projeto"));builder.setTitle(UiLanguage.t("Novo projeto")).setView(field).setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Salvar"),null);}
        else if(phase.equals("modal-delete"))builder.setTitle(UiLanguage.t("Limpar feed do Prompter?")).setMessage(UiLanguage.t("Remove este fragmento. Gerações e arquivos do ComfyUI permanecem.")).setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Apagar"),(d,w)->called[0]=true);
        else builder.setTitle(UiLanguage.t("Lote interrompido")).setMessage(UiLanguage.t("Confira a fila antes de reenviar para evitar duplicatas.")).setPositiveButton(UiLanguage.t("Entendi"),null);
        AlertDialog dialog=builder.create();dialog.setOnShowListener(d->called[0]=true);dialog.show();check((dialog.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0,"alerts protect content");dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        new Handler().postDelayed(()->{try{
            check(called[0],"styling preserves caller onShow handler");check(dialog.isShowing(),"styled modal displayed");
            check(dialog.getWindow().getAttributes().width<=FlowDesign.dp(this,440),"alert width bounded");
            android.widget.Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if(positive!=null&&positive.getVisibility()==android.view.View.VISIBLE){check(positive.getHeight()>=FlowDesign.dp(this,48),"alert action touch target");check(positive.getBackground() instanceof android.graphics.drawable.RippleDrawable,"alert uses app rounded ripple");}
            report(true,null);
        }catch(Throwable error){report(false,error);}},500);
    }
    void testControlFragments()throws Exception{
        String file="aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa.jpg";
        ReferencePreset.Picture image=new ReferencePreset.Picture(file,"synthetic-image",false);
        ReferencePreset block=new ReferencePreset();block.kind="control";block.name="Estrutura da cena";
        ControlFragments.put(block,"depth",image,null,"");ControlFragments.put(block,"pose",image,image,"DWPreprocessor");ControlFragments.put(block,"lineart",image,null,"");
        store.save(block);ReferencePreset loaded=store.get(block.id);
        check(loaded.controls.length()==3&&loaded.images.size()==1,"multiple typed maps persist and deduplicate shared images");
        check(loaded.promptText().isEmpty()&&!loaded.useImages&&!loaded.useLoras&&!loaded.useRefmod,"control blocks never masquerade as text or visual references");
        check(ReferenceBindings.pictures(Arrays.asList(loaded),false,"").isEmpty()&&!ReferenceBindings.hasVisual(Arrays.asList(loaded),""),"control block cannot silently switch or condition an unsupported workflow");
        loaded.controls.getJSONObject("depth").put("processor","changed");check(store.get(block.id).controls.getJSONObject("depth").optString("processor").isEmpty(),"control edits isolate saved snapshots");
        ControlFragments.remove(loaded,"depth");check(loaded.controls.length()==2&&loaded.images.size()==1,"removal preserves images used by other maps");
        ControlFragments.remove(loaded,"pose");ControlFragments.remove(loaded,"lineart");check(loaded.images.isEmpty()&&loaded.cover.isEmpty(),"empty block clears stale image references");
        ReferencePreset privateBlock=new ReferencePreset();privateBlock.kind="control";privateBlock.name="Private synthetic map";ControlFragments.put(privateBlock,"canny",new ReferencePreset.Picture(file,"private-origin",true),null,"");
        check(privateBlock.copy().hidden(),"control map inherits privacy");
        JSONObject invalid=block.json();invalid.getJSONObject("controls").getJSONObject("depth").put("file","../outside.png");boolean rejected=false;try{ReferencePreset.parse(invalid);}catch(JSONException expected){rejected=true;}check(rejected,"control map traversal rejected");
        JSONObject legacy=block.json();legacy.remove("controls");legacy.put("kind","character");check(ReferencePreset.parse(legacy).controls.length()==0,"legacy references remain readable");
        JSONObject schema=new JSONObject("{\"Canny\":{\"output\":[\"IMAGE\"],\"input\":{\"required\":{\"image\":[\"IMAGE\"],\"low_threshold\":[\"FLOAT\",{\"default\":0.1}],\"high_threshold\":[\"FLOAT\",{\"default\":0.4}]}}}}");
        check(ControlFragments.processor(schema,"canny").equals("Canny"),"preprocessor discovery uses real output type");
        JSONObject graph=ControlFragments.graph(schema,"Canny","fixture.png");
        check(graph.length()==3&&graph.getJSONObject("map").getJSONObject("inputs").getDouble("low_threshold")==.1,"extract graph preserves node defaults");
        check(graph.getJSONObject("result").getString("class_type").equals("PreviewImage"),"extraction produces a temporary map rather than a gallery generation");
        JSONObject aux=new JSONObject("{\"LineArtPreprocessor\":{\"input\":{\"required\":{\"image\":[\"IMAGE\"]},\"optional\":{\"coarse\":[[\"disable\",\"enable\"]],\"resolution\":[\"INT\",{\"default\":1024}]}}}}");
        JSONObject auxInput=ControlFragments.graph(aux,"LineArtPreprocessor","fixture.png").getJSONObject("map").getJSONObject("inputs");
        check(auxInput.getString("coarse").equals("disable")&&auxInput.getInt("resolution")==512,"aux optional widget values are sent, avoiding coarse/safe KeyErrors");
        final int[] posts={0},uploads={0};final boolean[] fail={false};
        ReferenceJobs.Server server=new ReferenceJobs.Server(){
            public String upload(String a,File f){uploads[0]++;return "fixture.png";}
            public byte[] download(String a,JSONObject image)throws Exception{check("temp".equals(image.getString("type")),"map download respects temp output");return new byte[]{1,2,3};}
            public JSONObject json(String method,String url,JSONObject body)throws Exception{
                if(url.contains("object_info"))return schema;
                if(method.equals("POST")){posts[0]++;if(fail[0])throw new IOException("uncertain");return new JSONObject().put("prompt_id","fixture-map");}
                return new JSONObject("{\"fixture-map\":{\"status\":{\"completed\":true},\"outputs\":{\"result\":{\"images\":[{\"filename\":\"map.png\",\"subfolder\":\"\",\"type\":\"temp\"}]}}}}");
            }
        };
        ControlFragments.Result result=ControlFragments.extract(server,"fixture",store.file(file),"canny",()->false,m->{});
        check(result.processor.equals("Canny")&&result.image.length==3&&posts[0]==1,"map extraction submits once and fetches completed result");
        fail[0]=true;boolean uncertain=false;try{ControlFragments.extract(server,"fixture",store.file(file),"canny",()->false,m->{});}catch(IllegalStateException expected){uncertain=true;}check(uncertain&&posts[0]==2,"uncertain submission is not retried");
        int before=uploads[0];boolean absent=false;try{ControlFragments.extract(server,"fixture",store.file(file),"depth",()->false,m->{});}catch(IllegalStateException expected){absent=true;}check(absent&&uploads[0]==before,"missing preprocessor fails before uploading media");
    }

    void testHelpContent()throws Exception{
        HelpContent baseline=new HelpContent(this,"pt-BR");
        for(String language:UiLanguage.TAGS){
            HelpContent translated=new HelpContent(this,language);boolean valid=translated.intro.length()==5&&translated.topics.length()==11&&translated.labels.length()==baseline.labels.length();
            for(Iterator<String> keys=baseline.labels.keys();keys.hasNext();)valid&=!translated.labels.optString(keys.next()).trim().isEmpty();
            for(int i=0;i<5;i++)for(String key:new String[]{"title","body","hint","icon"})valid&=!translated.intro.getJSONObject(i).optString(key).trim().isEmpty();
            for(int i=0;i<11;i++){JSONObject item=translated.topics.getJSONObject(i);valid&=item.getString("id").equals(baseline.topics.getJSONObject(i).getString("id"))&&!item.getString("title").isEmpty()&&!item.getString("summary").isEmpty();JSONArray steps=item.getJSONArray("steps");valid&=steps.length()>=3;for(int j=0;j<steps.length();j++)valid&=!steps.getString(j).trim().isEmpty();}
            check(valid,"complete offline help pack: "+language);
        }
        check(baseline.search("COMPOSICAO").stream().anyMatch(item->item.optString("id").equals("controls")),"help search ignores case and accents");
        check(baseline.search("no-such-tool-239239").isEmpty(),"help search empty state");
    }
    void testHelpFlow()throws Exception{
        HelpCenter help=new HelpCenter(this,()->{throw new AssertionError("reading a guide must not connect");});
        help.preferences.edit().remove(HelpCenter.COMPLETE).putInt(HelpCenter.PROGRESS,2).commit();help.start(null,false);
        check(help.screen.equals("intro")&&help.index==2,"unfinished introduction resumes saved page");
        check((help.dialog.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)==0,"introduction preserves system bars");
        Bundle state=new Bundle();help.save(state);help.dispose();check(HelpCenter.pending(help.preferences),"lifecycle dismissal does not complete introduction");
        help.start(state,false);check(help.index==2&&help.screen.equals("intro"),"introduction survives activity state recreation");
        help.back();check(help.index==1&&HelpCenter.progress(help.preferences,5)==1,"back moves to previous introduction page and persists it");
        help.back();help.back();check(!HelpCenter.pending(help.preferences)&&help.dialog==null,"skip/back exits introduction without connecting");
        help.start(null,false);check(help.dialog==null,"completed introduction does not reappear");
        help.introduction(false);help.back();check(help.screen.equals("library")&&!HelpCenter.pending(help.preferences),"replaying introduction returns to library without resetting completion");
        help.query="depth";help.topic="controls";help.screen="topic";Bundle detail=new Bundle();help.save(detail);help.dispose();help.start(detail,false);
        check(help.screen.equals("topic")&&help.topic.equals("controls")&&help.query.equals("depth"),"tutorial detail and search survive recreation");help.back();help.dispose();
        help.preferences.edit().remove(HelpCenter.COMPLETE).remove(HelpCenter.PROGRESS).commit();help.start(null,true);check(help.dialog==null,"language settings reopen is not covered by onboarding");
    }
    void testLipsync()throws Exception{
        JSONObject graph=LipsyncWorkflow.graph(false,"portrait.png","speech.wav",9999,2,42);
        check(graph.getJSONObject("lipsync").getJSONObject("inputs").getString("kind").equals("image")&&graph.getJSONObject("lipsync").getJSONObject("inputs").getInt("max_side")==1024,"lipsync submits image and bounded size");
        check(graph.getJSONObject("save").getJSONObject("inputs").getJSONArray("video").getString(0).equals("lipsync"),"lipsync result is encoded by Comfy SaveVideo");
        File draft=LipsyncAudio.file(getFilesDir(),"draft_lipsync_fixture"),saved=LipsyncAudio.file(getFilesDir(),"lipsync_fixture");
        // Synthetic one-second PCM WAV; exercises encrypted, seekable Android audio decoding.
        java.nio.ByteBuffer wav=java.nio.ByteBuffer.allocate(32044).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes("US-ASCII")).putInt(32036).put("WAVEfmt ".getBytes("US-ASCII")).putInt(16).putShort((short)1).putShort((short)1).putInt(16000).putInt(32000).putShort((short)2).putShort((short)16).put("data".getBytes("US-ASCII")).putInt(32000);
        for(int i=0;i<16000;i++)wav.putShort((short)(Math.sin(i*2*Math.PI*440/16000)*1000));VaultFiles.write(draft,wav.array());
        LipsyncAudio audio=LipsyncAudio.read(draft,"synthetic.wav");check(audio.millis==1000,"audio duration read through vault MediaDataSource");
        LipsyncAudio.copy(draft,saved);LipsyncAudio.discard(audio);check(!draft.exists()&&saved.exists()&&LipsyncAudio.read(saved,"saved").millis==1000,"result owns independent encrypted audio for reuse");
        check(VaultBackup.validPath("images/lipsync_fixture.audio")&&!VaultBackup.validPath("images/../outside.audio"),"audio backup allowlist rejects traversal");
        GenerationStorage storage=new GenerationStorage(getFilesDir());storage.removeFiles(Collections.singleton("lipsync_fixture"));check(!saved.exists(),"deleting result also deletes its recipe audio");
        check(GenerationRecipe.edit(new JSONObject().put("operation","image_lipsync")),"lipsync recipe returns to original source");
    }
    void testAudioFragments()throws Exception{
        byte[] pcm=new byte[32000];LipsyncAudio captured=AudioTools.savePcm(this,pcm);check(captured.millis==1000,"recorded PCM creates valid WAV through the encrypted vault");
        byte[] raw=Files.readAllBytes(captured.file.toPath());check(raw[0]!='R'||raw[1]!='I',"recording never writes plaintext WAV to storage");
        boolean rejected=false;try{AudioTools.savePcm(this,new byte[100]);}catch(Exception expected){rejected=true;}check(rejected,"too short microphone recordings rejected");
        ReferencePreset p=new ReferencePreset();p.kind="audio";p.name="Synthetic voice";p.audioFile=UUID.randomUUID()+".audio";p.audioName="fixture.wav";p.audioMillis=1000;p.privateCover=true;
        LipsyncAudio.copy(captured.file,store.file(p.audioFile));store.save(p);ReferencePreset loaded=store.get(p.id);check(loaded.audio()&&loaded.hidden()&&loaded.audioMillis==1000&&loaded.promptText().isEmpty(),"audio fragments roundtrip privacy and never expand as text prompts");
        check(VaultBackup.validPath("references/"+p.audioFile)&&!ReferencePreset.validFile(p.audioFile)&&!ReferencePreset.validAudioFile("../bad.audio"),"fragment audio backup and image/path validation remain distinct");
        ReferencePreset duplicate=p.copy();duplicate.id=UUID.randomUUID().toString();store.save(duplicate);store.delete(p.id);check(store.file(p.audioFile).isFile(),"deleting one fragment preserves shared audio still in use");store.delete(duplicate.id);check(!store.file(p.audioFile).exists(),"deleting final fragment removes its audio");
        p.id=UUID.randomUUID().toString();p.privateCover=false;LipsyncAudio.copy(captured.file,store.file(p.audioFile));store.save(p);LipsyncAudio.discard(captured);
    }
    // Only this disposable fixture package permits screenshots, after verifying production security.
    static java.lang.reflect.Field findField(Object object,String name)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static Object field(Object object,String name)throws Exception{java.lang.reflect.Field f=findField(object,name);return f.get(object);}
    static void field(Object object,String name,Object value)throws Exception{java.lang.reflect.Field f=findField(object,name);f.set(object,value);}
    static Object call(Object object,String name,Class<?>[] types,Object...args)throws Exception{java.lang.reflect.Method m=object.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(object,args);}
    void mediaPreview(String phase){
        getSharedPreferences("interface",0).edit().putBoolean(HelpCenter.COMPLETE,true).commit();
        android.app.Application.ActivityLifecycleCallbacks callback=new android.app.Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
            public void onActivityResumed(Activity a){if(!(a instanceof MainActivity)||!((MainActivity)a).libraryReady())return;getApplication().unregisterActivityLifecycleCallbacks(this);new Handler().postDelayed(()->{try{
                a.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                if("negative-settings".equals(phase)){
                    android.widget.LinearLayout controls=new android.widget.LinearLayout(a);controls.setOrientation(1);controls.setPadding(20,24,20,24);android.widget.ScrollView scroll=new android.widget.ScrollView(a);scroll.addView(controls);a.setContentView(scroll);
                    JSONObject graph=assetGraph("krea2_raw_api.json");call(a,"buildAdvancedSettings",new Class[]{android.widget.LinearLayout.class,JSONObject.class,String.class},controls,graph,"Krea 2 RAW");report(true,null);return;
                }
                Class<?> type=Class.forName("com.leal.comfyremote.MainActivity$Generation");java.lang.reflect.Constructor<?> constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object item=constructor.newInstance();
                field(item,"id","synthetic-media-preview");field(item,"state","done");field(item,"workflowName","Synthetic scene");field(item,"prompt","A quiet garden");field(item,"mediaWidth",768);field(item,"mediaHeight",1024);field(item,"createdAt",System.currentTimeMillis());
                field(item,"mediaKind",phase.equals("video-loras")?"video":"image");
                for(String method:new String[]{"imageFile","thumbFile"}){File file=(File)call(a,method,new Class[]{String.class},"synthetic-media-preview");try(InputStream in=getAssets().open("covers/default/cover-1.png");OutputStream out=VaultFiles.output(file)){out.write(VaultFiles.readAll(in,4_000_000));}}
                if(phase.equals("background-result")){try(InputStream in=getAssets().open("visual-cutout.png")){call(a,"saveImage",new Class[]{String.class,byte[].class},"synthetic-media-preview",VaultFiles.readAll(in,10_000_000));}field(item,"mediaWidth",1254);field(item,"mediaHeight",1254);for(String method:new String[]{"imageFile","thumbFile"}){File file=(File)call(a,method,new Class[]{String.class},"synthetic-media-preview");Bitmap bitmap=VaultFiles.bitmap(file.getAbsolutePath(),new BitmapFactory.Options());check(bitmap.hasAlpha()&&Color.alpha(bitmap.getPixel(0,0))==0,"real cutout transparency survives encrypted original and thumbnail storage");bitmap.recycle();}}
                ((List)field(a,"generations")).clear();((List)field(a,"generations")).add(item);
                field(a,"hideAllNsfw",!phase.equals("private-menu"));field(a,"selectedTab",1);call(a,"renderFeed",new Class[]{});
                if(phase.endsWith("menu")){call(a,"showLibraryActions",new Class[]{android.view.View.class,type,boolean.class},field(a,"dockPrompt"),item,true);report(true,null);return;}
                call(a,"openViewer",new Class[]{type},item);Object viewer=field(a,"versionViewer");
                if(phase.equals("background-result")){((Dialog)field(viewer,"dialog")).getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);report(true,null);return;}
                if(phase.endsWith("-tools")){call(a,"showImageTool",new Class[]{type,boolean.class,JSONObject.class},item,phase.equals("outpaint-tools"),null);new Handler().postDelayed(()->{try{for(android.view.View root:windowRoots()){android.view.ViewGroup.LayoutParams params=root.getLayoutParams();if(params instanceof android.view.WindowManager.LayoutParams){android.view.WindowManager.LayoutParams w=(android.view.WindowManager.LayoutParams)params;w.flags&=~android.view.WindowManager.LayoutParams.FLAG_SECURE;((android.view.WindowManager)a.getSystemService(WINDOW_SERVICE)).updateViewLayout(root,w);}}report(true,null);}catch(Throwable e){report(false,e);}},1800);return;}
                if(phase.equals("animate-loras"))call(viewer,"openEditor",new Class[]{boolean.class},true);
                call(a,"updateViewerLoras",new Class[]{viewer.getClass()},viewer);
                check(((android.view.View)field(viewer,"loras")).getVisibility()==android.view.View.VISIBLE,"video editor exposes LoRA chip");
                JSONObject template=(JSONObject)call(a,"editTemplate",new Class[]{viewer.getClass()},viewer);String name=(String)call(a,"viewerAdvancedName",new Class[]{viewer.getClass()},viewer);String key=(String)call(a,"loraKey",new Class[]{JSONObject.class,String.class},template,name);
                LoraPrivacy.load(new JSONObject().put("entries",new JSONObject().put("h3/synthetic-motion.safetensors",new JSONObject().put("status","sfw").put("name","Cinematic motion"))));
                call(a,"showLoraEditor",new Class[]{List.class,List.class,String.class,JSONObject.class,viewer.getClass()},LoraSupport.loaders(template),Collections.singletonList("h3/synthetic-motion.safetensors"),key,template,viewer);
                ((Dialog)field(viewer,"dialog")).getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
                // Only fixture windows are made capturable; production remains secure.
                new Handler().postDelayed(()->{try{for(android.view.View root:windowRoots()){android.view.ViewGroup.LayoutParams params=root.getLayoutParams();if(params instanceof android.view.WindowManager.LayoutParams){android.view.WindowManager.LayoutParams w=(android.view.WindowManager.LayoutParams)params;w.flags&=~android.view.WindowManager.LayoutParams.FLAG_SECURE;((android.view.WindowManager)a.getSystemService(WINDOW_SERVICE)).updateViewLayout(root,w);}}}catch(Exception ignored){}},350);
                report(true,null);
            }catch(Throwable error){report(false,error);}},700);}
        };getApplication().registerActivityLifecycleCallbacks(callback);startActivity(new android.content.Intent(this,MainActivity.class));
    }
    static List<android.view.View> windowRoots()throws Exception{Class<?> cls=Class.forName("android.view.WindowManagerGlobal");Object global=cls.getMethod("getInstance").invoke(null);java.lang.reflect.Field roots=cls.getDeclaredField("mViews");roots.setAccessible(true);return (List<android.view.View>)roots.get(global);}
    JSONObject assetGraph(String name)throws Exception{try(InputStream in=getAssets().open("workflows/"+name)){return new JSONObject(new String(VaultFiles.readAll(in,2_000_000),"UTF-8"));}}
    void testVideoLoras()throws Exception{
        for(String asset:new String[]{"minimax_h3_i2v_api.json","minimax_h3_turbo_api.json","minimax_h3_video_edit_api.json"}){
            JSONObject graph=LoraSupport.videoLoader(assetGraph(asset));List<String> ids=LoraSupport.loaders(graph);
            check(!ids.isEmpty(),asset+" exposes a compatible H3 LoRA slot");
            String before=graph.toString();LoraSupport.videoLoader(graph);check(before.equals(graph.toString()),"video slot setup is idempotent");
            String id=ids.get(0);JSONObject in=graph.getJSONObject(id).getJSONObject("inputs");Map<String,String> structural=new HashMap<>();
            for(Iterator<String> keys=in.keys();keys.hasNext();){String k=keys.next();JSONObject value=in.optJSONObject(k);if(k.startsWith("lora_")&&value!=null&&!LoraSupport.matches(value.optString("lora"),"h3"))structural.put(k,value.toString());}
            String path="h3/synthetic-motion.safetensors";JSONObject choices=new JSONObject().put(id,new JSONArray().put(new JSONObject().put("lora",path).put("on",true).put("strength",.7)));
            LoraSupport.apply(graph,choices,Collections.singletonList(path));JSONObject selected=LoraSupport.defaults(graph,ids).getJSONArray(id).getJSONObject(0);
            check(selected.optBoolean("on")&&selected.getDouble("strength")==.7,"video LoRA selection and weight reach submitted graph");
            for(String key:structural.keySet())check(structural.get(key).equals(in.getJSONObject(key).toString()),"structural turbo LoRA remains unchanged");
            check(LoraPrivacy.disableChoices(choices)==1&&!choices.getJSONArray(id).getJSONObject(0).getBoolean("on"),"private video LoRA is disabled outside private mode");
            choices.getJSONArray(id).getJSONObject(0).put("lora","moss/wrong-family.safetensors");boolean rejected=false;try{LoraSupport.apply(graph,choices,Collections.singletonList("moss/wrong-family.safetensors"));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"video rejects LoRAs from another model family");
        }
        JSONObject unrelated=new JSONObject().put("1",new JSONObject().put("class_type","UNETLoader").put("inputs",new JSONObject().put("unet_name","other.safetensors")));String original=unrelated.toString();LoraSupport.videoLoader(unrelated);check(original.equals(unrelated.toString()),"video adapter slot never rewrites unrelated models");
    }
    void testImageTools()throws Exception{
        JSONObject edges=new JSONObject().put("left",256).put("right",256).put("top",128).put("bottom",128);
        int[] size=ImageToolsWorkflow.dimensions(1254,1254,edges);check(size[0]==1766&&size[1]==1510,"outpainting retains exact source dimensions, including odd sizes");
        for(int[] input:new int[][]{{1766,1510},{7001,999},{999,7001},{64,64}}){int[] working=ImageToolsWorkflow.workingSize(input[0],input[1]);check(working[0]%16==0&&working[1]%16==0&&(long)working[0]*working[1]<=1_000_000,"working canvas aligns to Flux latents and stays within memory budget");}
        boolean rejected=false;try{ImageToolsWorkflow.dimensions(1000,1000,new JSONObject());}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"empty canvas expansion rejected");
        rejected=false;try{ImageToolsWorkflow.dimensions(4000,4000,edges);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"oversized output rejected before uploading");
        JSONObject bg=ImageToolsWorkflow.removeBackground("fixture.png");check(bg.getJSONObject("alpha").getString("class_type").equals("InvertMask")&&bg.getJSONObject("rgba").getJSONObject("inputs").getJSONArray("alpha").getString(0).equals("alpha"),"foreground mask uses Comfy inverted-alpha convention");
        check(bg.getJSONObject("keep").getJSONObject("inputs").getString("operation").equals("multiply"),"removal never restores pixels already transparent");
        for(boolean privateMode:new boolean[]{false,true}){
            JSONObject base=assetGraph(ImageEditWorkflow.asset(privateMode));String original=base.toString();JSONObject graph=ImageToolsWorkflow.outpaint(base,"fixture.png",1254,1254,edges,"Continue the wooden desk and window.",42);
            check(base.toString().equals(original),"outpainting does not mutate edit workflow");
            JSONObject preserve=graph.getJSONObject("outpaint_original").getJSONObject("inputs");check(preserve.getInt("x")==256&&preserve.getInt("y")==128&&!preserve.getBoolean("resize_source")&&preserve.getJSONArray("source").getString(0).equals("1"),"original pixels composite back at the chosen offset");
            check(graph.getJSONObject("17").getJSONObject("inputs").getJSONArray("latent_image").getString(0).equals("outpaint_latent"),"sampling receives masked image latent");
            check(graph.getJSONObject("outpaint_blend").getJSONObject("inputs").getInt("left")==64&&preserve.getJSONArray("mask").getString(0).equals("outpaint_blend"),"blend restores original interior without a hard rectangular seam");
            check(graph.getJSONObject("5").getJSONObject("inputs").getJSONArray("image").getString(0).equals("outpaint_visible")&&graph.getJSONObject("outpaint_combined_mask").getJSONObject("inputs").getString("operation").equals("add"),"transparent areas are cleared before conditioning and become fillable");
            for(Iterator<String> ids=graph.keys();ids.hasNext();){JSONObject in=graph.getJSONObject(ids.next()).getJSONObject("inputs");for(Iterator<String> keys=in.keys();keys.hasNext();){JSONArray link=in.optJSONArray(keys.next());if(link!=null)check(graph.has(link.getString(0)),"outpainting graph has no dangling inputs");}}
            check(ImageEditWorkflow.snofs(graph)==privateMode,"outpainting preserves selected edit model");
            try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),privateMode?"outpaint-private-graph.json":"outpaint-graph.json"))){out.write(graph.toString().getBytes("UTF-8"));}
        }
        check(GenerationRecipe.edit(new JSONObject().put("operation",ImageToolsWorkflow.REMOVE))&&GenerationRecipe.edit(new JSONObject().put("operation",ImageToolsWorkflow.EXPAND)),"image tools reuse original as edit source");
        try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"background-graph.json"))){out.write(bg.toString().getBytes("UTF-8"));}
    }
    void testSfwPromptFilter()throws Exception{
        check(SfwPromptFilter.clean("A NUDE person in a garden",true).equals("A person in a garden"),"SFW strips whole words without changing innocent text");
        check(SfwPromptFilter.clean("nua, nudez, erótica, pornografía",true).equals(", , ,"),"SFW recognizes case and accented list entries");
        String ordinary="A classical portrait in Sussex. Menu, nuance and sunset.";
        check(SfwPromptFilter.clean(ordinary,true).equals(ordinary),"SFW does not remove substrings inside ordinary words");
        check(SfwPromptFilter.clean("人物の全裸写真",true).equals("人物の写真"),"CJK list entries work without spaces");
        String raw="  NUDE, erotic portrait.\nKeep this spacing.  ";check(SfwPromptFilter.clean(raw,false).equals(raw),"private mode leaves prompt bytes unchanged");
        check(SfwPromptFilter.clean("nude",true).isEmpty(),"an entirely filtered prompt remains valid empty text");
        JSONObject graph=new JSONObject()
            .put("raw",ReferenceSupport.node("PrimitiveStringMultiline",new JSONObject().put("value","nude person")))
            .put("positive",ReferenceSupport.node("CLIPTextEncode",new JSONObject().put("text",ReferenceSupport.link("raw"))))
            .put("negative",ReferenceSupport.node("CLIPTextEncode",new JSONObject().put("text",ReferenceSupport.link("raw"))))
            .put("sample",ReferenceSupport.node("KSampler",new JSONObject().put("positive",ReferenceSupport.link("positive")).put("negative",ReferenceSupport.link("negative"))))
            .put("file",ReferenceSupport.node("LoadImage",new JSONObject().put("image","nude-reference.png")));
        String before=graph.toString(),negative=graph.getJSONObject("negative").toString();SfwPromptFilter.apply(graph,true);
        String filter=graph.getJSONObject("positive").getJSONObject("inputs").getJSONArray("text").getString(0);
        check(graph.getJSONObject(filter).getString("class_type").equals("RegexReplace"),"linked positive text is filtered at runtime");
        check(graph.getJSONObject("negative").toString().equals(negative)&&graph.getJSONObject("raw").getJSONObject("inputs").getString("value").equals("nude person"),"shared sources and negative conditioning remain unchanged");
        check(graph.getJSONObject("file").getJSONObject("inputs").getString("image").equals("nude-reference.png"),"filter never rewrites media filenames");
        String once=graph.toString();SfwPromptFilter.apply(graph,true);check(graph.toString().equals(once),"repeated submission does not accumulate filters");
        SfwPromptFilter.apply(graph,false);check(graph.toString().equals(before),"switching to private mode removes previous runtime filters");
        for(String asset:new String[]{"moss_t2i_api.json","klein_edit_api.json","minimax_h3_i2v_api.json","minimax_h3_video_edit_api.json"}){
            JSONObject fixture=assetGraph(asset);String[] target=PromptAgent.target(fixture);check(target!=null,"bundled workflow exposes its prompt: "+asset);
            fixture.getJSONObject(target[0]).getJSONObject("inputs").put(target[1],"nude person by a lake");String original=fixture.toString();
            SfwPromptFilter.apply(fixture,false);check(fixture.toString().equals(original),"private workflow is unchanged: "+asset);
            SfwPromptFilter.apply(fixture,true);check(fixture.getJSONObject(target[0]).getJSONObject("inputs").getString(target[1]).equals("person by a lake"),"bundled SFW prompt filtered: "+asset);
        }
        JSONObject dynamic=new JSONObject().put("llm",ReferenceSupport.node("LlamaServerVisionCaption",new JSONObject()))
            .put("guard",ReferenceSupport.node("MomentosPromptGuard",new JSONObject().put("text",ReferenceSupport.link("llm"))))
            .put("video",ReferenceSupport.node("MiniMaxH3ReferenceToVideo",new JSONObject().put("prompt",ReferenceSupport.link("guard"))));
        SfwPromptFilter.apply(dynamic,true);String output=dynamic.getJSONObject("video").getJSONObject("inputs").getJSONArray("prompt").getString(0);
        check(dynamic.getJSONObject(output).getJSONObject("inputs").getJSONArray("string").getString(0).equals("guard"),"agent output is filtered after format validation and before video conditioning");
        JSONObject boxes=new JSONObject().put("layout",ReferenceSupport.node("MOSSImageLayoutPrompt",new JSONObject().put("prompt","a portrait")))
            .put("encode",ReferenceSupport.node("CLIPTextEncode",new JSONObject().put("text",ReferenceSupport.link("layout"))));
        SfwPromptFilter.apply(boxes,true);check(boxes.getJSONObject("encode").getJSONObject("inputs").getJSONArray("text").getString(0).startsWith("bwdacc_sfw_prompt_"),"dynamic box descriptions pass through the same final filter");
        SfwPromptFilter.strip(boxes);check(boxes.length()==2&&boxes.getJSONObject("encode").getJSONObject("inputs").getJSONArray("text").getString(0).equals("layout"),"recipe cleanup restores original text links");
        try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"sfw-filter-regex.json"))){out.write(new JSONObject().put("regex",SfwPromptFilter.REGEX).toString().getBytes("UTF-8"));}
    }
    void allowSyntheticScreenshot(Dialog dialog){check((dialog.getWindow().getAttributes().flags&android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0,"extractor protects screen capture");dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);new Handler().postDelayed(()->dialog.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE),250);report(true,null);}
    void report(boolean passed,Throwable error){try{JSONObject result=new JSONObject().put("passed",passed).put("checks",checks);if(error!=null){StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));result.put("error",trace.toString());}try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"visual-tests.json"))){out.write(result.toString().getBytes("UTF-8"));}}catch(Exception ignored){}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(studio!=null)studio.audio.permission(request,grants);}
    @Override protected void onPause(){if(studio!=null){studio.extractor.conceal();studio.audio.pause();}super.onPause();}
    @Override protected void onDestroy(){if(studio!=null){studio.extractor.close();studio.audio.close();}if(jobs!=null)jobs.close();super.onDestroy();}
}
