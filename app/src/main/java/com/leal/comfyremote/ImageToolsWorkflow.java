package com.leal.comfyremote;

import org.json.*;

/** Native ComfyUI image tools. Original interior is restored, with a narrow edge blend. */
final class ImageToolsWorkflow {
    static final String REMOVE="image_remove_background", EXPAND="image_outpaint";
    static boolean operation(String value){return REMOVE.equals(value)||EXPAND.equals(value);}
    static int[] dimensions(int width,int height,JSONObject edges){
        if(width<1||height<1)throw new IllegalArgumentException(UiLanguage.t("Dimensões da mídia indisponíveis."));
        long w=width,h=height;int total=0;
        for(String side:new String[]{"left","top","right","bottom"}){
            int n=edges.optInt(side);if(n<0||n>8192)throw new IllegalArgumentException(UiLanguage.t("Expansão inválida."));
            if(side.equals("left")||side.equals("right"))w+=n;else h+=n;total+=n;
        }
        if(total==0)throw new IllegalArgumentException(UiLanguage.t("Aumente pelo menos uma borda."));
        if(w>8192||h>8192||w*h>16_000_000L)throw new IllegalArgumentException(UiLanguage.t("Canvas limitado a 16 MP e 8192 px por lado."));
        return new int[]{(int)w,(int)h};
    }
    static int[] workingSize(int w,int h){
        double scale=Math.min(1,Math.sqrt(1_000_000.0/((double)w*h)));
        return new int[]{Math.max(64,(int)(w*scale/16)*16),Math.max(64,(int)(h*scale/16)*16)};
    }
    static JSONObject removeBackground(String file)throws Exception{
        JSONObject g=new JSONObject();
        g.put("source",node("LoadImage",new JSONObject().put("image",file)));
        g.put("model",node("LoadBackgroundRemovalModel",new JSONObject().put("bg_removal_name","birefnet.safetensors")));
        g.put("foreground",node("RemoveBackground",new JSONObject().put("image",link("source",0)).put("bg_removal_model",link("model",0))));
        g.put("size",node("GetImageSize",new JSONObject().put("image",link("source",0))));
        g.put("source_alpha_image",node("MaskToImage",new JSONObject().put("mask",link("source",1))));
        g.put("source_alpha_size",node("ImageScale",new JSONObject().put("image",link("source_alpha_image",0)).put("upscale_method","bilinear").put("width",link("size",0)).put("height",link("size",1)).put("crop","disabled")));
        g.put("source_alpha_mask",node("ImageToMask",new JSONObject().put("image",link("source_alpha_size",0)).put("channel","red")));
        g.put("source_alpha",node("InvertMask",new JSONObject().put("mask",link("source_alpha_mask",0))));
        g.put("keep",node("MaskComposite",new JSONObject().put("destination",link("foreground",0)).put("source",link("source_alpha",0)).put("x",0).put("y",0).put("operation","multiply")));
        // JoinImageWithAlpha expects an inverted alpha mask (white means transparent).
        g.put("alpha",node("InvertMask",new JSONObject().put("mask",link("keep",0))));
        g.put("rgba",node("JoinImageWithAlpha",new JSONObject().put("image",link("source",0)).put("alpha",link("alpha",0))));
        g.put("save",node("SaveImage",new JSONObject().put("images",link("rgba",0)).put("filename_prefix","bwdacc/Edits/Background")));
        return g;
    }
    static JSONObject outpaint(JSONObject base,String file,int width,int height,JSONObject edges,String prompt,long seed)throws Exception{
        int[] size=dimensions(width,height,edges),work=workingSize(size[0],size[1]);
        JSONObject g=new JSONObject(base.toString());
        int feather=Math.min(64,Math.max(1,Math.min(width,height)/8));
        g.getJSONObject("1").getJSONObject("inputs").put("image",file);
        g.getJSONObject("6").getJSONObject("inputs").put("text","Expand the canvas of the reference image. Continue the scene naturally beyond its existing borders, matching perspective, lighting, colors and texture. Fill the new area with a coherent continuation. Keep the existing subject unchanged; do not duplicate it or add a frame. "+prompt.trim());
        g.getJSONObject("12").getJSONObject("inputs").put("noise_seed",seed&Long.MAX_VALUE);
        // LoadImage retains RGB beneath alpha. Clear those pixels before any model sees them.
        g.put("outpaint_source_mask_image",node("MaskToImage",new JSONObject().put("mask",link("1",1))));
        g.put("outpaint_source_mask_size",scale(link("outpaint_source_mask_image",0),width,height));
        g.put("outpaint_source_mask",node("ImageToMask",new JSONObject().put("image",link("outpaint_source_mask_size",0)).put("channel","red")));
        g.put("outpaint_clear",node("EmptyImage",new JSONObject().put("width",width).put("height",height).put("batch_size",1).put("color",8421504)));
        g.put("outpaint_visible",node("ImageCompositeMasked",new JSONObject().put("destination",link("1",0)).put("source",link("outpaint_clear",0)).put("mask",link("outpaint_source_mask",0)).put("x",0).put("y",0).put("resize_source",false)));
        g.getJSONObject("5").getJSONObject("inputs").put("image",link("outpaint_visible",0)).put("megapixels",Math.max(.01,Math.min(1.0,(double)width*height/1_000_000)));
        g.put("outpaint_canvas",node("ImagePadForOutpaint",new JSONObject().put("image",link("outpaint_visible",0)).put("left",edges.optInt("left")).put("top",edges.optInt("top")).put("right",edges.optInt("right")).put("bottom",edges.optInt("bottom")).put("feathering",feather)));
        g.put("outpaint_combined_mask",node("MaskComposite",new JSONObject().put("destination",link("outpaint_canvas",1)).put("source",link("outpaint_source_mask",0)).put("x",edges.optInt("left")).put("y",edges.optInt("top")).put("operation","add")));
        g.put("outpaint_work",scale(link("outpaint_canvas",0),work[0],work[1]));
        g.put("outpaint_mask_image",node("MaskToImage",new JSONObject().put("mask",link("outpaint_combined_mask",0))));
        g.put("outpaint_mask_size",scale(link("outpaint_mask_image",0),work[0],work[1]));
        g.put("outpaint_mask",node("ImageToMask",new JSONObject().put("image",link("outpaint_mask_size",0)).put("channel","red")));
        g.put("outpaint_encode",node("VAEEncode",new JSONObject().put("pixels",link("outpaint_work",0)).put("vae",link("4",0))));
        g.put("outpaint_latent",node("SetLatentNoiseMask",new JSONObject().put("samples",link("outpaint_encode",0)).put("mask",link("outpaint_mask",0))));
        g.getJSONObject("11").getJSONObject("inputs").put("image",link("outpaint_work",0));
        g.getJSONObject("17").getJSONObject("inputs").put("latent_image",link("outpaint_latent",0));
        g.remove("16");
        g.put("outpaint_size",scale(link("18",0),size[0],size[1]));
        g.put("outpaint_keep",node("InvertMask",new JSONObject().put("mask",link("outpaint_source_mask",0))));
        JSONObject blend=new JSONObject().put("mask",link("outpaint_keep",0));for(String side:new String[]{"left","top","right","bottom"})blend.put(side,edges.optInt(side)>0?feather:0);
        g.put("outpaint_blend",node("FeatherMask",blend));
        g.put("outpaint_original",node("ImageCompositeMasked",new JSONObject().put("destination",link("outpaint_size",0)).put("source",link("1",0)).put("x",edges.optInt("left")).put("y",edges.optInt("top")).put("resize_source",false).put("mask",link("outpaint_blend",0))));
        g.getJSONObject("19").getJSONObject("inputs").put("images",link("outpaint_original",0)).put("filename_prefix","bwdacc/Edits/Outpaint");
        return g;
    }
    private static JSONObject scale(JSONArray image,int width,int height)throws Exception{return node("ImageScale",new JSONObject().put("image",image).put("upscale_method","lanczos").put("width",width).put("height",height).put("crop","disabled"));}
    private static JSONObject node(String type,JSONObject inputs)throws Exception{return new JSONObject().put("class_type",type).put("inputs",inputs);}
    private static JSONArray link(String id,int output){return new JSONArray().put(id).put(output);}
}
