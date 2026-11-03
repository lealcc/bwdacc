package com.leal.comfyremote;
import org.json.*;
import java.util.*;
import java.nio.file.*;
public final class VideoWorkflowTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static JSONObject read(String path)throws Exception{return new JSONObject(new String(Files.readAllBytes(Paths.get(path)),"UTF-8"));}
    static void save(JSONObject graph,String path)throws Exception{Files.write(Paths.get(path),graph.toString().getBytes("UTF-8"));}
    public static void main(String[] args)throws Exception{
        JSONObject turbo=read(args[0]);check(VideoWorkflow.turbo(turbo),"Turbo profile");check(turbo.getJSONObject("21").getJSONObject("inputs").getJSONObject("lora_1").getDouble("strength")==1,"Structural turbo strength");
        VideoWorkflow.prepare(turbo,Collections.emptyList(),false,"A blue sphere floating above a quiet lake. Static camera.","16:9",.33,0,0,3,12345);
        JSONObject input=turbo.getJSONObject("8").getJSONObject("inputs");check(!input.has("first_frame")&&!input.has("last_frame"),"T2V has no hidden reference");
        check(!turbo.has("1")&&!turbo.has("2")&&!turbo.has("3"),"Unused image nodes removed");check(!input.getString("prompt").contains("<Picture"),"No invented picture tags");
        check(input.getInt("width")%32==0&&input.getInt("height")%32==0&&input.getInt("length")%17==5,"Model alignment");save(turbo,args[1]+"/h3-turbo-t2v-test.json");
        JSONObject fl=read(args[0]);VideoWorkflow.prepare(fl,Arrays.asList("Momentos/Tests/h3-first.png","Momentos/Tests/h3-last.png"),true,"A blue circle moves smoothly to the right and turns orange. Static camera.","16:9",.33,512,288,3,67890);
        input=fl.getJSONObject("8").getJSONObject("inputs");check(input.has("first_frame")&&input.has("last_frame"),"Two real keyframe links");check(input.getString("prompt").contains("<Picture 2>")&&input.getString("prompt").contains("3.00 seconds"),"Final frame at actual end");
        check(fl.getJSONObject("1").getJSONObject("inputs").getString("image").endsWith("h3-first.png"),"First role preserved");check(fl.getJSONObject("momentos_last_frame").getJSONObject("inputs").getString("image").endsWith("h3-last.png"),"Last role preserved");save(fl,args[1]+"/h3-turbo-fl-test.json");
        boolean rejected=false;try{VideoWorkflow.prepare(read(args[0]),Arrays.asList("last.png"),true,"Transition","16:9",.33,512,288,3,1);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"Incomplete keyframes rejected");
        JSONObject one=read(args[0]);VideoWorkflow.prepare(one,Arrays.asList("one.png"),false,"Slow camera pan","Original",.66,450,800,5,1);check(one.getJSONObject("8").getJSONObject("inputs").has("first_frame")&&!one.getJSONObject("8").getJSONObject("inputs").has("last_frame"),"Single image mode");
        VideoWorkflow.prepare(one,Collections.emptyList(),false,"Clouds moving","Original",.98,0,0,8,2);check(!one.has("1")&&!one.getJSONObject("8").getJSONObject("inputs").has("first_frame"),"Switch back to text removes stale inputs");
        for(String ratio:new String[]{"16:9","4:3","1:1","3:4","9:16"})for(double mp:new double[]{.33,.66,.98})for(int mode=0;mode<3;mode++){
            List<String> refs=mode==0?Collections.emptyList():mode==1?Arrays.asList("first.png"):Arrays.asList("first.png","last.png");
            JSONObject g=read(args[0]);VideoWorkflow.prepare(g,refs,mode==2,"Clouds moving",ratio,mp,800,600,3,42);
            JSONObject in=g.getJSONObject("8").getJSONObject("inputs");int w=in.getInt("width"),h=in.getInt("height");String[] parts=ratio.split(":");double expected=Double.parseDouble(parts[0])/Double.parseDouble(parts[1]);
            check(w%32==0&&h%32==0,"All aspect ratios retain model alignment");
            check(Math.abs((w/(double)h)/expected-1)<.07,"Requested aspect reaches T2V/I2V/FL2V");
            check(Math.abs(w*(double)h/1_000_000-mp)<.05,"New aspect ratios preserve pixel budget");
            check(in.has("first_frame")== (mode>0)&&in.has("last_frame")== (mode==2),"Ratio changes preserve reference roles");
        }
        System.out.println("VideoWorkflowTest passed: T2V, I2V, FL2V roles, mode changes, timing, resolution and missing-frame validation.");
    }
}
