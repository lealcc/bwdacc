package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Per-request memory strategy. Core ComfyUI still owns weight offloading to system RAM. */
final class MemoryProfiles {
    static final String[] IDS={"24plus","24","16","8"};
    static final String[] LABELS={"Mais de 24 GB","24 GB","16 GB","8 GB"};
    static String normalize(String value){return Arrays.asList(IDS).contains(value)?value:"24";}
    static String label(String value){return UiLanguage.t(LABELS[Arrays.asList(IDS).indexOf(normalize(value))]);}
    static String description(String value){switch(normalize(value)){
        case "8":return UiLanguage.t("H3 normal e Turbo: perfis baseados no FlowForgeLabAi, com atenção dividida, INT8 e Turbo T8. Comece em 0,33 MP / 5s. Modelos e nodes necessários ficam no catálogo; o download será feito pelo futuro instalador. Extensão e outros modelos ainda sem perfil incluído.");
        case "16":return UiLanguage.t("MOSS/Kromoss INT8 e H3 com VAE em partes e atenção segmentada. Comece em 1 MP ou vídeo de 0,33 MP / 5s. Ideogram FP8 indisponível: rota de 16 GB encontrada usa outros pesos.");
        case "24plus":return UiLanguage.t("Prioriza velocidade, com menos divisão de cálculos. Usa os checkpoints quantizados do catálogo; os pesos têm instalação separada.");
        default:return UiLanguage.t("Perfil de 24 GB: reduz picos de atenção e divide a decodificação. Comece com 1–2 MP ou vídeo de 0,66 MP.");
    }}
    static String unavailable(JSONObject graph,String selected){
        if(UpscaleWorkflow.matches(graph))return UpscaleWorkflow.unavailable(graph.has("momentos_upscale_source"),selected);
        String p=normalize(selected),f=family(graph);if(p.equals("24")||p.equals("24plus"))return "";
        if(f.equals("krea2"))return UiLanguage.t("Krea 2: perfil disponível para 24 GB ou mais.");
        if(f.equals("klein9b"))return UiLanguage.t("Snofs Klein 9B BF16 requer perfil de 24 GB ou mais. Ainda sem perfil validado para ")+p+UiLanguage.t(" GB.");
        if(f.equals("ideogram"))return UiLanguage.t("Indisponível em ")+p+UiLanguage.t(" GB: este pacote inclui Ideogram FP8 para 24 GB ou mais; a rota GGUF menor ainda exige integração e validação.");
        if(p.equals("8")){
            if(f.equals("moss"))return UiLanguage.t("Indisponível em 8 GB: a rota GGUF publicada validou MOSS Base, mas ainda não MOSS SFT incluído neste pacote.");
            if(f.equals("kromoss"))return UiLanguage.t("Indisponível em 8 GB: nenhum workflow validado encontrado para este Kromoss INT8.");
            if(f.equals("h3")){
                if(ReferenceBindings.ref2va(graph))return UiLanguage.t("Edição Ref2VA indisponível em 8 GB: o perfil pesquisado cobre FL2VA.");
                if(graph.has("momentos_extend_source"))return extensionUnavailable(p);
                return "";
            }
            return UiLanguage.t("Indisponível em 8 GB: este workflow ainda não tem perfil de memória validado.");
        }
        return "";
    }
    static String extensionUnavailable(String selected){return normalize(selected).equals("8")?UiLanguage.t("Extensão indisponível em 8 GB: não há perfil validado para o guia de múltiplos frames."):"";}
    static String family(JSONObject graph){
        if(graph==null)return "";
        for(Iterator<String> ids=graph.keys();ids.hasNext();){JSONObject n=graph.optJSONObject(ids.next());if(n==null)continue;JSONObject in=n.optJSONObject("inputs");if(in==null)continue;
            String model=in.optString("unet_name").toLowerCase(Locale.ROOT);
            if(model.contains("krea2")||model.contains("krea_2"))return "krea2";
            if(model.contains("snofs")&&model.contains("klein9b"))return "klein9b";
            if(model.contains("ideogram4"))return "ideogram";
            if(model.contains("kromoss"))return "kromoss";
            if(model.contains("moss_image"))return "moss";
            if(model.contains("minimax")||n.optString("class_type").startsWith("MiniMaxH3"))return "h3";
        }return "";
    }
    static void apply(JSONObject graph,String selected)throws Exception{
        String blocked=unavailable(graph,selected);if(!blocked.isEmpty())throw new IllegalArgumentException(blocked);
        strip(graph);
        String profile=normalize(selected),family=family(graph);if(family.isEmpty())return;
        int level=Arrays.asList(IDS).indexOf(profile);List<String> original=new ArrayList<>();for(Iterator<String> it=graph.keys();it.hasNext();)original.add(it.next());
        // Repair saved MOSS recipes from catalog v1 as well as newly bundled graphs.
        // SD3 forces timestep * 1000; MOSS expects the native flow multiplier of 1.
        if(family.equals("moss")||family.equals("kromoss"))for(String id:original){JSONObject n=graph.getJSONObject(id);if(n.optString("class_type").equals("ModelSamplingSD3"))n.put("class_type","ModelSamplingAuraFlow");}
        if(level==3&&family.equals("h3"))for(String id:original){JSONObject n=graph.getJSONObject(id),in=n.getJSONObject("inputs");String type=n.optString("class_type");
            boolean sage=type.equals("MiniMaxH3MemoryEfficientSageAttentionPatch"),vae=type.equals("VAELoader")&&in.optString("vae_name").contains("video_vae_fp16"),turbo=false;
            for(Iterator<String> keys=in.keys();keys.hasNext();){JSONObject lora=in.optJSONObject(keys.next());if(lora!=null&&lora.optString("lora").contains("fl2v")&&lora.optString("lora").contains("turbo"))turbo=true;}
            if(sage||vae||turbo){JSONObject meta=n.optJSONObject("_meta");if(meta==null)meta=new JSONObject();n.put("_meta",meta.put("momentos_original_profile_node",new JSONObject().put("class_type",type).put("inputs",GenerationRecipe.copy(in))));}
            if(sage){n.put("class_type","ModelAttentionBackend");in.put("attention","pytorch attention");}
            if(vae)in.put("vae_name","minimax_h3_video_vae_int8_convrot.safetensors");
            if(turbo)for(Iterator<String> keys=in.keys();keys.hasNext();){JSONObject lora=in.optJSONObject(keys.next());if(lora!=null&&lora.optString("lora").contains("fl2v")&&lora.optString("lora").contains("turbo"))lora.put("lora","minimax_h3_turbo_v4_step600_comfyui_T8-convert.safetensors");}
        }
        // Wrap every diffusion loader, including Ideogram's unconditional model. LoRAs and
        // guides remain downstream, so their meaning does not change with the memory profile.
        for(String id:original){JSONObject n=graph.getJSONObject(id);if(!n.optString("class_type").equals("UNETLoader"))continue;
            String tail=id;int index=0;
            if(family.equals("ideogram")){
                // Current ComfyUI passes matrix-form RoPE; KJ's experimental low-precision
                // patch expects the older (cos, sin, -sin) tuple and crashes at sampling.
                // Keep native attention and only chunk the feed-forward activations.
                String next="momentos_memory_"+id+"_"+(index++);graph.put(next,ReferenceSupport.node("Ideogram4OptimizationsKJ",new JSONObject().put("model",ReferenceSupport.link(tail)).put("chunk_ffn",level>0).put("ffn_chunks",level==3?16:level==2?8:2).put("ffn_seq_threshold",1024).put("bf16_rope",false)));tail=next;
            }
            if(family.equals("h3")){
                String next="momentos_memory_"+id+"_"+(index++);graph.put(next,ReferenceSupport.node("MiniMaxChunkFeedForward",new JSONObject().put("model",ReferenceSupport.link(tail)).put("chunks",level==3?4:level==2?8:level==1?4:2).put("seq_threshold",level==3?4096:2048)));tail=next;
                if(level==3){next="momentos_memory_"+id+"_"+(index++);graph.put(next,ReferenceSupport.node("MiniMaxH3SigmaShift",new JSONObject().put("model",ReferenceSupport.link(tail)).put("shift_video",12.0).put("shift_audio",3.0)));tail=next;}
                // Existing Sage patches can override attention if wrapped before them. FFN
                // chunking is always applied; low-VRAM attention is attached to sampler models below.
            }
            if(level==2){String next="momentos_memory_"+id+"_"+(index++);graph.put(next,ReferenceSupport.node("ModelMemoryUsageFactorOverride",new JSONObject().put("model",ReferenceSupport.link(tail)).put("memory_usage_factor",3)));tail=next;}
            if(!tail.equals(id))for(String other:original){JSONObject in=graph.getJSONObject(other).optJSONObject("inputs");if(in==null)continue;for(Iterator<String> keys=in.keys();keys.hasNext();){String key=keys.next();JSONArray link=in.optJSONArray(key);if(link!=null&&id.equals(link.optString(0))&&link.optInt(1)==0)in.put(key,ReferenceSupport.link(tail));}}
        }
        for(String id:original){JSONObject node=graph.getJSONObject(id),in=node.optJSONObject("inputs");String type=node.optString("class_type");if(in==null)continue;
            if(level>0&&type.equals("VAEDecode")){JSONObject meta=node.optJSONObject("_meta");if(meta==null)meta=new JSONObject();node.put("_meta",meta.put("momentos_original_decode",GenerationRecipe.copy(in)));node.put("class_type","VAEDecodeTiled");in.put("tile_size",level==3?256:level==2?384:512).put("overlap",64).put("temporal_size",level==3?16:level==2?32:64).put("temporal_overlap",8);}
            if(level>=2&&family.equals("h3")&&(type.equals("BasicGuider")||type.equals("CFGGuider")||type.equals("KSampler"))){JSONArray model=in.optJSONArray("model");if(model!=null){String key="momentos_memory_attention_"+id;graph.put(key,ReferenceSupport.node("MiniMaxLowVRAMAttention",new JSONObject().put("model",model).put("head_chunks",level==3?10:4)));in.put("model",ReferenceSupport.link(key));
                if(level==3){String backend=key+"_backend";graph.put(backend,ReferenceSupport.node("ModelAttentionBackend",new JSONObject().put("model",ReferenceSupport.link(key)).put("attention","comfy kitchen attention")));in.put("model",ReferenceSupport.link(backend));}
            }}
        }
        JSONObject first=graph.getJSONObject(original.get(0)),meta=first.optJSONObject("_meta");if(meta==null)meta=new JSONObject();first.put("_meta",meta.put("momentos_memory_profile",profile));
    }
    static String saved(JSONObject graph){for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject meta=graph.optJSONObject(it.next()).optJSONObject("_meta");if(meta!=null&&meta.has("momentos_memory_profile"))return normalize(meta.optString("momentos_memory_profile"));}return "";}
    static void strip(JSONObject graph)throws Exception{
        List<String> remove=new ArrayList<>();for(Iterator<String> it=graph.keys();it.hasNext();){String id=it.next();if(id.startsWith("momentos_memory_"))remove.add(id);}
        for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject node=graph.getJSONObject(it.next()),in=node.optJSONObject("inputs");if(in==null)continue;
            JSONObject meta=node.optJSONObject("_meta");if(meta!=null&&meta.has("momentos_original_decode")){node.put("inputs",GenerationRecipe.copy(meta.getJSONObject("momentos_original_decode"))).put("class_type","VAEDecode");meta.remove("momentos_original_decode");in=node.getJSONObject("inputs");}
            if(meta!=null&&meta.has("momentos_original_profile_node")){JSONObject prior=meta.getJSONObject("momentos_original_profile_node");node.put("class_type",prior.getString("class_type")).put("inputs",GenerationRecipe.copy(prior.getJSONObject("inputs")));meta.remove("momentos_original_profile_node");in=node.getJSONObject("inputs");}
            for(Iterator<String> keys=in.keys();keys.hasNext();){String key=keys.next();JSONArray link=in.optJSONArray(key);int guard=0;
                while(link!=null&&remove.contains(link.optString(0))&&guard++<30){JSONObject wrapped=graph.getJSONObject(link.getString(0));link=wrapped.getJSONObject("inputs").optJSONArray("model");if(link!=null)in.put(key,link);}
            }
        }for(String id:remove)graph.remove(id);
    }
}
