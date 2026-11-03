package com.leal.comfyremote;

import org.json.*;
import java.util.*;

/** Exports the same runtime composition used by the app for an actual server smoke test. */
public final class BoxesRegressionTest {
    public static void main(String[] args)throws Exception {
        JSONObject left=new JSONObject().put("x",.08).put("y",.2).put("w",.35).put("h",.6).put("type","obj").put("desc","A red ceramic mug");
        JSONObject right=new JSONObject().put("x",.57).put("y",.2).put("w",.35).put("h",.6).put("type","obj").put("desc","A blue ceramic vase");
        JSONObject layout=new JSONObject().put("enabled",true).put("boxes",new JSONArray().put(left).put(right));
        for(String name:new String[]{"ideogram4_t2i_api","moss_t2i_api","kromoss_t2i_api"}) {
            JSONObject g=WorkflowProfilesTest.read(args[0]+"/"+name+".json");
            GenerationOptions.imageOutput(g,"1:1",1,0,0);
            // Simulate reuse of a recipe from the previous release.
            g.put("momentos_layout_elements",ReferenceSupport.node("MomentosLayoutElements",new JSONObject().put("elements_json","[]")));
            BoxLayout.apply(g,layout,"A red ceramic mug on the left and a blue ceramic vase on the right, on a clean white table. Soft studio light, minimal product photograph.");
            if(name.equals("moss_t2i_api"))g.getJSONObject("8").put("class_type","ModelSamplingSD3");
            MemoryProfiles.apply(g,"24");
            if(name.equals("moss_t2i_api"))WorkflowProfilesTest.check(g.getJSONObject("8").getString("class_type").equals("ModelSamplingAuraFlow"),"Legacy MOSS timestep scale repaired");
            WorkflowProfilesTest.valid(g);
            WorkflowProfilesTest.check(!g.has("momentos_layout_elements"),"Stale bridge removed on reuse");
            for(Iterator<String> ids=g.keys();ids.hasNext();) {
                JSONObject node=g.getJSONObject(ids.next());
                if(node.optString("class_type").equals("SaveImage"))node.getJSONObject("inputs").put("filename_prefix","Momentos/QA/BboxFix/"+name);
            }
            WorkflowProfilesTest.save(g,args[1]+"/"+name+"_bbox_smoke.json");
        }
        System.out.println("PASS BoxesRegressionTest: 3 runtime graphs, native array inputs, old recipe migration");
    }
}
