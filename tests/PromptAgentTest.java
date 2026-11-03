package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;

public final class PromptAgentTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    static void save(String directory,String name,JSONObject graph)throws Exception{Files.write(Paths.get(directory,name+".json"),graph.toString(2).getBytes("UTF-8"));}
    public static void main(String[] args)throws Exception{
        String dir=args[0];JSONObject schema=read(args[1]);
        for(String asset:new String[]{"kromoss_t2i_api.json","klein_edit_api.json","minimax_h3_i2v_api.json","minimax_h3_turbo_api.json","minimax_h3_video_edit_api.json"}){
            JSONObject base=read(dir+"/"+asset);String original=base.toString();boolean video=asset.startsWith("minimax");
            for(boolean hidden:new boolean[]{false,true}){
                JSONObject graph=GenerationRecipe.copy(base);String raw="Um barco de papel azul flutua devagar num lago. Sem fala ou música.";
                if(VideoWorkflow.matches(graph))VideoWorkflow.prepare(graph,Collections.emptyList(),false,raw,"16:9",.33,0,0,3,42);
                if(ReferenceBindings.ref2va(graph))VideoEditWorkflow.prepare(graph,"source.mp4",Arrays.asList("ref.png"),true,true,raw,"16:9",.33,640,360,3000,3,42);
                String clip=video?graph.getJSONObject("5").toString():ReferenceSupport.klein(graph)?graph.getJSONObject("3").toString():graph.getJSONObject("18:2").toString();
                PromptAgent.apply(graph,true,hidden,raw,asset.contains("edit")?"video_edit":video?"video":"image",schema);
                JSONObject llm=graph.getJSONObject(PromptAgent.LLM);check(llm.getJSONObject("inputs").getString("model_path").contains(hidden?"RVN-":"Qwen3.8-27B-UD"),"Route must follow explicit mode");
                check(clip.equals(video?graph.getJSONObject("5").toString():ReferenceSupport.klein(graph)?graph.getJSONObject("3").toString():graph.getJSONObject("18:2").toString()),"Original encoder unchanged");
                check(graph.getJSONObject("momentos_agent_raw").getJSONObject("inputs").getString("value").equals(raw),"Raw prompt preserved");
                String execution=graph.getJSONObject("momentos_agent_release").getJSONObject("inputs").getString("value");
                JSONObject gate=graph.getJSONObject(PromptAgent.GATE).getJSONObject("inputs");
                check(gate.getString("find").equals(execution)&&gate.getJSONArray("replace").getString(0).equals(PromptAgent.REQUEST)&&llm.getJSONObject("inputs").getJSONArray("user_prompt").getString(0).equals(PromptAgent.GATE),"Execution token is replaced by the complete request before the LLM");
                check(!llm.getJSONObject("inputs").getBoolean("keep_server_alive"),"LLM must release VRAM");
                check(llm.getJSONObject("inputs").getString("extra_server_args").contains("--reasoning off --reasoning-budget 0"),"Qwen thinking is disabled at the server even when the caption node uses an incompatible template flag");
                String settings=llm.toString();GenerationOptions.varySeed(graph,1);check(settings.equals(llm.toString()),"Batch must reuse the same LLM output");
                check(execution.equals(graph.getJSONObject("momentos_agent_release").getJSONObject("inputs").getString("value")),"Batch must retain the release token");
                check(!GenerationDetails.snapshot(graph).optString("Seed").contains("\n0"),"LLM seed not exposed as generation seed");
                check(AdvancedGeneration.controls(graph).stream().noneMatch(c->c.node.equals(PromptAgent.LLM)),"No LLM controls in sampler settings");
                String[] target=PromptAgent.target(graph);check(graph.getJSONObject(target[0]).getJSONObject("inputs").getJSONArray(target[1]).getString(0).equals("momentos_agent_guard"),"Conditioning must use LLM output");
                PromptAgent.apply(graph,true,hidden,raw,video?"video":"image",schema);check(graph.has(PromptAgent.LLM),"Repeated apply remains valid");
                check(!execution.equals(graph.getJSONObject("momentos_agent_release").getJSONObject("inputs").getString("value")),"A new submission must release VRAM even when its prompt is unchanged");
                if(args.length>2)save(args[2],asset.replace("_api.json","")+(hidden?"_nsfw":"_sfw"),graph);
                JSONObject recipe=new JSONObject().put("template",base).put("graph",graph).put("options",new JSONObject().put("agent",true));
                check(PromptAgent.requested(recipe),"Reuse restores mode");JSONObject legacy=GenerationRecipe.legacyTemplate(graph);check(!legacy.has(PromptAgent.LLM)&&PromptAgent.target(legacy)!=null,"Legacy reuse restores a clean prompt target");JSONObject restored=GenerationRecipe.configured(recipe);check(!restored.has(PromptAgent.LLM),"Prepared branch must not enter base template");
                PromptAgent.apply(graph,false,hidden,raw,"image",new JSONObject());check(!graph.has(PromptAgent.LLM)&&!graph.has(PromptAgent.PREVIEW),"Agent off removes execution branch");
                if(PromptAgent.moss(graph))check(!graph.getJSONObject("18:16").getJSONObject("inputs").getBoolean("switch"),"Legacy Moss enhancer bypassed");
                check(original.equals(base.toString()),"Base template immutable");
            }
        }
        JSONObject video=read(dir+"/minimax_h3_i2v_api.json");VideoWorkflow.prepare(video,Arrays.asList("first.png","last.png"),true,"A smooth transition","9:16",.66,720,1280,8,42);
        String system=PromptAgent.instructions(video,"video");check(system.contains(String.format(Locale.ROOT,"%.2f-second",(VideoWorkflow.frames(8)-1)/24.0)),"First/last uses real frame time");
        check(system.contains("Picture 2")&&system.contains("integrated_multimodal_description"),"First/last structure");
        check(system.contains("opening and ending instants only"),"Keyframes constrain endpoints, not every intermediate frame");
        String frameRequest=PromptAgent.request(video,"Turn slowly","video"),alignment=PromptAgent.alignment(video,"video");
        check(frameRequest.contains(alignment+"\n\nintegrated_multimodal_description:"),"The request template puts keyframe alignment before the visual field");
        if(args.length>2){JSONObject staged=GenerationRecipe.copy(video);PromptAgent.apply(staged,true,false,"A pessoa gira lentamente da pose inicial até a pose final. Sem fala nem música.","video",schema);save(args[2],"motion_first_last",staged);}
        for(boolean hidden:new boolean[]{false,true}){
            JSONObject moving=read(dir+"/minimax_h3_i2v_api.json");String raw="A pessoa da imagem se levanta, dá dois passos para a direita e olha por cima do ombro. A câmera acompanha lateralmente. Sem fala nem música.";
            VideoWorkflow.prepare(moving,Arrays.asList("first.png"),false,raw,"9:16",.33,512,768,5,42);
            String first=moving.getJSONObject("8").getJSONObject("inputs").getJSONArray("first_frame").toString();
            PromptAgent.apply(moving,true,hidden,raw,"video",schema);system=moving.getJSONObject(PromptAgent.SYSTEM).getJSONObject("inputs").getString("value");
            check(system.contains("alignment applies only at 0.00 seconds")&&system.contains("Honor explicit stillness"),"I2V has time-scoped alignment and respects requested stillness");
            check(first.equals(moving.getJSONObject("8").getJSONObject("inputs").getJSONArray("first_frame").toString()),"Prompt changes preserve the real first-frame connection");
            check(moving.getJSONObject("momentos_agent_guard").getJSONObject("inputs").getInt("pictures")==1,"I2V guard still enforces one actual image");
            if(args.length>2)save(args[2],"motion_i2v_"+(hidden?"nsfw":"sfw"),moving);
        }
        VideoWorkflow.prepare(video,Collections.emptyList(),false,"Paper boat","16:9",.33,0,0,3,42);check(PromptAgent.instructions(video,"video").contains("No images connected"),"T2V must not invent refs");
        JSONObject ref=read(dir+"/minimax_h3_video_edit_api.json");VideoEditWorkflow.prepare(ref,"source.mp4",Arrays.asList("ref.png"),false,true,"Change color","16:9",.33,640,360,3000,3,42);
        system=PromptAgent.instructions(ref,"video_edit");check(system.contains("<Video 1>")&&system.contains("<Picture 1>")&&!system.contains("<Audio 1>"),"Reference labels follow actual wiring");
        check(system.contains("retention_analysis:")&&system.contains("detailed_description:"),"Ref2VA uses six sections");
        check(PromptAgent.request(ref,"Change color","video_edit").contains("Actual connected inputs: <Picture 1>, <Video 1>.")&&!PromptAgent.request(ref,"Change color","video_edit").contains("<Audio 1>"),"Request names only real visual/edit inputs, reinforcing reference roles");
        JSONObject missing=GenerationRecipe.copy(schema);missing.remove("LlamaServerVisionCaption");boolean failed=false;try{PromptAgent.profile(missing,true);}catch(IllegalArgumentException e){failed=true;}check(failed,"Missing model never silently falls back");
        for(String asset:new String[]{"minimax_h3_i2v_api.json","minimax_h3_turbo_api.json"})for(boolean hidden:new boolean[]{false,true}){
            JSONObject extend=read(dir+"/"+asset);String raw="he falls on the ground";
            VideoExtendWorkflow.prepare(extend,"source.mp4",raw,VideoExtendWorkflow.range(0,3000),416,768,3000,.33,5,42);
            String source=extend.getJSONObject("momentos_extend_source").toString(),guide=extend.getJSONObject("momentos_extend_guide").toString(),merge=extend.getJSONObject("momentos_extend_merge").toString();
            PromptAgent.apply(extend,true,hidden,raw,"video_extend",schema);
            String request=extend.getJSONObject(PromptAgent.REQUEST).getJSONObject("inputs").getString("value");
            system=extend.getJSONObject(PromptAgent.SYSTEM).getJSONObject("inputs").getString("value");
            check(request.contains(raw)&&request.contains("continuation")&&request.contains("\noverall_soundscape:")&&request.contains("\nnon_diegetic_music:"),"Short continuation includes the complete output template");
            check(system.contains("22-frame")&&system.contains("0.917")&&system.contains("No cuts")&&system.contains("Unless the user explicitly requests speech"),"Continuation preserves guide, timing and audio rules");
            check(source.equals(extend.getJSONObject("momentos_extend_source").toString())&&guide.equals(extend.getJSONObject("momentos_extend_guide").toString())&&merge.equals(extend.getJSONObject("momentos_extend_merge").toString()),"Agent must preserve trim, guide and join");
            JSONObject guard=extend.getJSONObject("momentos_agent_guard").getJSONObject("inputs");
            check(guard.getString("format").equals("h3")&&guard.getInt("pictures")==0&&guard.getInt("videos")==0,"Motion guide is not a numbered reference asset");
            if(args.length>2)save(args[2],"extension_"+(asset.contains("turbo")?"turbo":"normal")+(hidden?"_nsfw":"_sfw"),extend);
            JSONObject stripped=GenerationRecipe.copy(extend);PromptAgent.strip(stripped);
            check(!stripped.has(PromptAgent.REQUEST)&&stripped.getJSONObject("8").getJSONObject("inputs").getString("prompt").equals(raw),"Reuse strips the contract and preserves the user's short prompt");
            PromptAgent.apply(extend,true,hidden,"He stands up again.","video_extend",schema);
            check(!extend.getJSONObject(PromptAgent.REQUEST).toString().contains(raw),"Reapplying the agent refreshes the creative request");
            PromptAgent.apply(extend,false,hidden,raw,"video_extend",schema);check(!extend.has(PromptAgent.REQUEST),"Agent off removes the request contract");
        }
        System.out.println("PASS PromptAgentTest: five generation workflows, normal/Turbo extension, both profiles, off, reuse, batching and reference contracts");
    }
}
