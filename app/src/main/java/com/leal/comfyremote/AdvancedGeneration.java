package com.leal.comfyremote;
import org.json.*;
import java.util.*;

/** Small set of real sampler controls. Linked primitive seeds remain linked. */
final class AdvancedGeneration {
    static final class Control {
        String node,key,label,type,sourceNode,sourceKey;Object value;
        String id(){return node+"/"+key;}
        boolean seed(){return key.equals("seed")||key.equals("noise_seed");}
        boolean choice(){return key.equals("sampler_name")||key.equals("scheduler");}
    }
    static List<Control> controls(JSONObject graph){
        List<Control> result=new ArrayList<>();if(graph==null)return result;
        Set<String> live=new HashSet<>();Iterator<String> nodes=graph.keys();while(nodes.hasNext()){String id=nodes.next();JSONObject n=graph.optJSONObject(id);if(n!=null&&(n.optString("class_type").startsWith("Save")||n.optString("class_type").equals("VHS_VideoCombine")||n.optString("class_type").equals("PreviewImage")))LoraSupport.ancestors(graph,id,live);}
        Set<String> seen=new HashSet<>();nodes=graph.keys();while(nodes.hasNext()){String id=nodes.next();if(id.startsWith("momentos_agent_"))continue;if(!live.isEmpty()&&!live.contains(id))continue;JSONObject n=graph.optJSONObject(id);if(n==null)continue;JSONObject in=n.optJSONObject("inputs");if(in==null)continue;
            for(String[] field:new String[][]{{"seed","Seed"},{"noise_seed","Seed"},{"steps","Passos"},{"sampler_name","Sampler"},{"scheduler","Scheduler"},{"cfg","CFG"},{"guidance","Guidance"},{"denoise","Denoise"}}){
                Object value=in.opt(field[0]);String target=id,key=field[0];
                if(value instanceof JSONArray){JSONArray link=(JSONArray)value;JSONObject primitive=graph.optJSONObject(link.optString(0));if(primitive==null||!primitive.optString("class_type").startsWith("Primitive"))continue;target=link.optString(0);key="value";value=primitive.optJSONObject("inputs")==null?null:primitive.optJSONObject("inputs").opt(key);}
                if(!(value instanceof Number||value instanceof String)||!seen.add(target+"/"+key))continue;
                Control c=new Control();c.node=target;c.key=field[0];c.sourceNode=id;c.sourceKey=key;c.label=UiLanguage.t(field[1]);c.type=n.optString("class_type");c.value=value;result.add(c);
            }
        }
        result.sort((a,b)->Integer.compare(rank(a),rank(b)));return result;
    }
    private static int rank(Control c){return c.seed()?0:c.key.equals("steps")?1:c.key.equals("sampler_name")?2:c.key.equals("scheduler")?3:4;}
    static Object validate(Control control,String input)throws Exception{
        if(control.choice()){if(input.trim().isEmpty())throw new IllegalArgumentException(UiLanguage.t("Escolha ")+control.label);return input.trim();}
        if(control.seed()){long value=Long.parseLong(input.trim());if(value<0)throw new IllegalArgumentException(UiLanguage.t("Seed deve ser positiva."));return value;}
        double value=Double.parseDouble(input.trim());double max=control.key.equals("steps")?200:control.key.equals("denoise")?1:100;
        double min=control.key.equals("steps")?1:0;
        if(!Double.isFinite(value)||value<min||value>max||(control.key.equals("steps")&&value!=Math.rint(value)))throw new IllegalArgumentException(control.label+UiLanguage.t(": use ")+(int)min+UiLanguage.t(" a ")+(int)max);
        return control.key.equals("steps")?(Object)(int)value:value;
    }
    static void apply(JSONObject graph,JSONObject settings)throws Exception{
        for(Control c:controls(graph)){
            Object value=settings.opt(c.id());if(c.seed()&&settings.optBoolean("randomSeed",true))value=new Random().nextLong()&0x1FFFFFFFFFFFFFL;
            if(value!=null)graph.getJSONObject(c.node).getJSONObject("inputs").put(c.sourceKey,validate(c,String.valueOf(value)));
        }
        if(settings.has(NegativePrompt.KEY))NegativePrompt.apply(graph,settings.optString(NegativePrompt.KEY));
    }
}
