package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
public final class GenerationOptionsTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    public static void main(String[] args)throws Exception {
        JSONObject graph=read(args[0]);String original=graph.toString();
        GenerationOptions.varySeed(graph,0);check(original.equals(graph.toString()),"first seed preserved");
        long first=graph.getJSONObject("29").getJSONObject("inputs").getLong("value");
        GenerationOptions.varySeed(graph,1);check(first!=graph.getJSONObject("29").getJSONObject("inputs").getLong("value"),"linked seed varied");
        JSONObject edit=read(args[1]);String reference=edit.getJSONObject("5").toString();
        String latentReference=edit.getJSONObject("8").toString();
        GenerationOptions.editRatio(edit,"16:9",false);
        JSONObject size=edit.getJSONObject("16").getJSONObject("inputs");
        check(size.getInt("width")>size.getInt("height"),"landscape output");
        check(reference.equals(edit.getJSONObject("5").toString())&&latentReference.equals(edit.getJSONObject("8").toString()),"reference unmodified");
        check(edit.getJSONObject("14").getJSONObject("inputs").getInt("width")==size.getInt("width"),"scheduler matches latent");
        JSONObject video=read(args[2]);GenerationOptions.editRatio(video,"9:16",true);
        JSONObject vi=video.getJSONObject("8").getJSONObject("inputs");
        check(vi.getInt("height")>vi.getInt("width")&&vi.getInt("width")%32==0,"video dimensions aligned");
        for(int quality:new int[]{33,66,98}){
            double mp=GenerationOptions.videoMegapixels(quality);
            int[] portrait=GenerationOptions.videoDimensions("9:16",mp,800,600);
            int[] landscape=GenerationOptions.videoDimensions("16:9",mp,800,600);
            check(portrait[0]<portrait[1]&&portrait[0]%32==0&&portrait[1]%32==0,"portrait and model alignment");
            check(portrait[0]==landscape[1]&&portrait[1]==landscape[0],"orientation preserves pixel budget");
            check(Math.abs(portrait[0]*portrait[1]/1000000.0-mp)<.05,"output stays near selected megapixels");
            int[] square=GenerationOptions.videoDimensions("Original",mp,1200,1200);
            check(square[0]==square[1],"original reference aspect");
            GenerationOptions.videoOutput(video,"9:16",mp,800,600);
            check(vi.getInt("width")==portrait[0]&&vi.getInt("height")==portrait[1],"chosen video dimensions sent to model");
            check(video.getJSONObject("2").getJSONObject("inputs").getDouble("megapixels")==mp,"reference receives exact selected megapixels");
        }
        try{GenerationOptions.videoDimensions("Original",.33,0,0);throw new AssertionError("invalid reference accepted");}catch(IllegalArgumentException expected){}
        check(GenerationOptions.count(99)==4&&GenerationOptions.count(0)==1,"batch bounds");
        for(int mp:new int[]{1,2,4}){
            JSONObject fresh=read(args[1]);String referenceScale=fresh.getJSONObject("5").toString();
            GenerationOptions.imageOutput(fresh,"Original",mp,900,1600);
            JSONObject latent=fresh.getJSONObject("16").getJSONObject("inputs");int w=latent.getInt("width"),h=latent.getInt("height");
            check(w<h&&w%16==0&&h%16==0,"original portrait kept at each resolution");
            check(Math.abs(w*(double)h/1_000_000-mp)<.06,"selected image pixel budget");
            check(referenceScale.equals(fresh.getJSONObject("5").toString()),"reference encoding budget unchanged");
            check(fresh.getJSONObject("14").getJSONObject("inputs").getInt("width")==w,"scheduler uses output dimensions");
            JSONObject main=read(args[0]);GenerationOptions.imageOutput(main,"4:3",mp,0,0);
            check(main.getJSONObject("19").getJSONObject("inputs").getInt("megapixels")==mp,"main selector receives image MP");
        }
        check(GenerationOptions.supportsImageResolution(read(args[0]))&&GenerationOptions.supportsImageResolution(read(args[1])),"image profiles support resolution");
        JSONObject unsupported=new JSONObject("{\"1\":{\"class_type\":\"EmptyLatentImage\",\"inputs\":{\"width\":[\"unknown\",0],\"height\":[\"unknown\",1]}}}");
        check(!GenerationOptions.supportsImageResolution(unsupported),"unknown linked dimensions do not expose ineffective controls");
        System.out.println("PASS: seeds, reference preservation, dimensions, batch bounds");
    }
}
