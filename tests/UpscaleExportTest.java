package com.leal.comfyremote;
import org.json.*;
import java.nio.file.*;

public final class UpscaleExportTest {
    public static void main(String[] args)throws Exception{
        boolean compact=Boolean.parseBoolean(args[2]);JSONObject graph=MediaUpdatesTest.read(args[0]+"/"+UpscaleWorkflow.asset(true,compact));
        UpscaleWorkflow.prepare(graph,"Momentos/QA/upscale-source.mp4","integrated_multimodal_description: [Shot 1] An adult man with short black hair wearing a black button-up shirt and black trousers moves toward the camera outdoors on a tree-lined path. Preserve his face, clothing, pose, camera motion, framing, background, colors and lighting exactly as in the source clip. Refine natural detail only.\noverall_soundscape: Preserve the original soundtrack.\nnon_diegetic_music: N/A.",768,1418,2000,Integer.parseInt(args[3]),777);
        MemoryProfiles.apply(graph,"24");WorkflowDependencies.verify(graph,MediaUpdatesTest.read(args[1]));WorkflowProfilesTest.valid(graph);
        graph.getJSONObject("save").getJSONObject("inputs").put("filename_prefix","Momentos/QA/Upscale/"+(compact?"compact":"full"));
        Files.write(Paths.get(args[4]),graph.toString(2).getBytes("UTF-8"));System.out.println("Upscale graph exported and dependencies checked");
    }
}
