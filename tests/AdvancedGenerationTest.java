package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public final class AdvancedGenerationTest {
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        JSONObject graph=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),"UTF-8"));
        JSONObject options=new JSONObject().put("randomSeed",false);AdvancedGeneration.Control step=null,seed=null;
        for(AdvancedGeneration.Control c:AdvancedGeneration.controls(graph)){if(c.seed()){options.put(c.id(),12345);seed=c;}if(c.key.equals("steps")){options.put(c.id(),12);step=c;}if(c.key.equals("sampler_name"))options.put(c.id(),"euler");}
        check(step!=null&&seed!=null,"sampler fields discovered through linked seed");
        String link=graph.getJSONObject("18:7").getJSONObject("inputs").getJSONArray("seed").toString();String model=graph.getJSONObject("18:7").getJSONObject("inputs").getJSONArray("model").toString();
        AdvancedGeneration.apply(graph,options);
        check(graph.getJSONObject("29").getJSONObject("inputs").getLong("value")==12345,"fixed seed reaches primitive");
        check(graph.getJSONObject("18:7").getJSONObject("inputs").getInt("steps")==12,"steps applied");
        check(graph.getJSONObject("18:7").getJSONObject("inputs").getString("sampler_name").equals("euler"),"sampler applied");
        check(link.equals(graph.getJSONObject("18:7").getJSONObject("inputs").getJSONArray("seed").toString())&&model.equals(graph.getJSONObject("18:7").getJSONObject("inputs").getJSONArray("model").toString()),"model and seed links remain intact");
        AdvancedGeneration.apply(graph,new JSONObject());long first=graph.getJSONObject("29").getJSONObject("inputs").getLong("value");AdvancedGeneration.apply(graph,new JSONObject());long second=graph.getJSONObject("29").getJSONObject("inputs").getLong("value");check(first!=second,"random seed changes between generations");
        try{AdvancedGeneration.validate(step,"4.5");throw new AssertionError("fractional steps accepted");}catch(IllegalArgumentException expected){}
        try{AdvancedGeneration.validate(seed,"-1");throw new AssertionError("negative fixed seed accepted");}catch(IllegalArgumentException expected){}
        for(int index=1;index<args.length;index++){
            JSONObject profile=new JSONObject(new String(Files.readAllBytes(Paths.get(args[index])),"UTF-8"));
            JSONObject overrides=new JSONObject().put("randomSeed",false);int seeds=0,steps=0;
            for(AdvancedGeneration.Control c:AdvancedGeneration.controls(profile)){if(c.seed()){seeds++;overrides.put(c.id(),67890);}if(c.key.equals("steps")){steps++;overrides.put(c.id(),6);}}
            check(seeds>0&&steps>0,"live seed and steps in "+args[index]);AdvancedGeneration.apply(profile,overrides);
            for(AdvancedGeneration.Control c:AdvancedGeneration.controls(profile)){if(c.seed())check(((Number)c.value).longValue()==67890,"profile fixed seed");if(c.key.equals("steps"))check(((Number)c.value).intValue()==6,"profile steps");}
        }
        System.out.println("PASS: fixed/random seed, actual steps/sampler, link preservation and validation");
    }
}
