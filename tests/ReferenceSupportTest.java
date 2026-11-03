package com.leal.comfyremote;
import org.json.*;
import java.util.*;
import java.nio.file.*;

public final class ReferenceSupportTest {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject klein=read(args[0]),h3=read(args[1]),moss=read(args[2]);
        check(ReferenceSupport.capacity(klein)==3,"Klein capacity");
        check(ReferenceSupport.capacity(h3)==2,"H3 first/last frames");
        check(ReferenceSupport.capacity(moss)==0,"Text workflow must not accept images");
        ReferenceSupport.bind(klein,Arrays.asList("one.png","two.png","three.png"));
        check(klein.getJSONObject("1").getJSONObject("inputs").getString("image").equals("one.png"),"Base preserved");
        check(klein.getJSONObject("momentos_ref_2_load").getJSONObject("inputs").getString("image").equals("three.png"),"Third image uploaded separately");
        String positive=klein.getJSONObject("15").getJSONObject("inputs").getJSONArray("positive").getString(0);
        check(positive.equals("momentos_ref_2_positive"),"All positive reference latents reach guider");
        check(klein.getJSONObject(positive).getJSONObject("inputs").getJSONArray("conditioning").getString(0).equals("momentos_ref_1_positive"),"References chain in order");
        check(klein.getJSONObject("15").getJSONObject("inputs").getJSONArray("negative").getString(0).equals("momentos_ref_2_negative"),"Negative conditioning also updated");
        ReferenceSupport.bind(h3,Arrays.asList("first.png","last.png"));
        check(h3.getJSONObject("8").getJSONObject("inputs").getJSONArray("last_frame").getString(0).equals("momentos_last_frame"),"Final frame connected");
        boolean rejected=false;try{ReferenceSupport.bind(read(args[0]),Arrays.asList("1","2","3","4"));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Four images rejected");
        rejected=false;try{ReferenceSupport.bind(moss,Arrays.asList("1"));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"Unsupported workflow rejected");
        JSONObject custom=new JSONObject("{\"1\":{\"class_type\":\"LoadImage\",\"inputs\":{\"image\":\"old\"}},\"2\":{\"class_type\":\"LoadImage\",\"inputs\":{\"image\":\"unused\"}},\"3\":{\"class_type\":\"SaveImage\",\"inputs\":{\"images\":[\"1\",0]}}}");
        check(ReferenceSupport.capacity(custom)==1,"Only live image inputs");ReferenceSupport.bind(custom,Arrays.asList("new"));
        check(custom.getJSONObject("2").getJSONObject("inputs").getString("image").equals("unused"),"Unused image node unchanged");
        System.out.println("ReferenceSupportTest passed: Klein 3, H3 2, ordered conditioning, overflow, unsupported and inactive inputs.");
    }
}
