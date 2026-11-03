package com.leal.comfyremote;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** A compact, grouped workflow sheet shared by generation and editing. */
final class WorkflowPicker {
    interface Selected {void choose(int index);}
    static void show(Activity activity,List<String> names,List<JSONObject> graphs,String current,boolean video,Selected selected,Runnable importer){
        show(activity,names,graphs,current,video,"24",selected,importer);
    }
    static void show(Activity activity,List<String> names,List<JSONObject> graphs,String current,boolean video,String profile,Selected selected,Runnable importer){
        Dialog dialog=new Dialog(activity);LinearLayout page=new LinearLayout(activity);page.setOrientation(1);FlowDesign.surface(page);
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label(activity,UiLanguage.t("Workflows"),21,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(activity,52),1));title.setGravity(Gravity.CENTER_VERTICAL);
        Button close=icon(activity,"close",UiLanguage.t("Fechar workflows"));header.addView(close,new LinearLayout.LayoutParams(dp(activity,48),dp(activity,48)));close.setOnClickListener(v->dialog.dismiss());page.addView(header);
        boolean hasImages=false,hasVideos=false;for(JSONObject graph:graphs){if(isVideo(graph))hasVideos=true;else hasImages=true;}
        final boolean[] mode={video};LinearLayout tabs=new LinearLayout(activity);page.addView(tabs);Button images=button(activity,UiLanguage.t("Imagens")),videos=button(activity,UiLanguage.t("Vídeos"));tabs.addView(images,new LinearLayout.LayoutParams(0,dp(activity,48),1));tabs.addView(videos,new LinearLayout.LayoutParams(0,dp(activity,48),1));tabs.setVisibility(hasImages&&hasVideos?View.VISIBLE:View.GONE);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);LinearLayout list=new LinearLayout(activity);list.setOrientation(1);scroll.addView(list);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        Runnable render=()->{list.removeAllViews();images.setBackground(FlowDesign.shape(activity,!mode[0]?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));videos.setBackground(FlowDesign.shape(activity,mode[0]?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));
            for(int i=0;i<graphs.size();i++){JSONObject graph=graphs.get(i);if(isVideo(graph)!=mode[0])continue;final int index=i;boolean active=names.get(i).equals(current);String reason=MemoryProfiles.unavailable(graph,profile);boolean available=reason.isEmpty();
                LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(activity,12),dp(activity,10),dp(activity,12),dp(activity,10));row.setBackground(FlowDesign.shape(activity,active?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));
                ImageView image=new ImageView(activity);image.setImageDrawable(FlowDesign.icon(activity,isVideo(graph)?"video":"image",22));row.addView(image,new LinearLayout.LayoutParams(dp(activity,32),dp(activity,44)));
                LinearLayout text=new LinearLayout(activity);text.setOrientation(1);text.setPadding(dp(activity,10),0,dp(activity,8),0);TextView name=label(activity,names.get(i),15,true);name.setMaxLines(2);text.addView(name);
                TextView detail=label(activity,available?description(graph)+" · "+MemoryProfiles.label(profile):reason,12,false);detail.setTextColor(FlowDesign.MUTED);detail.setPadding(0,dp(activity,4),0,0);text.addView(detail);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                ImageView check=new ImageView(activity);check.setImageDrawable(FlowDesign.icon(activity,active?"check":"right",20));row.addView(check,new LinearLayout.LayoutParams(dp(activity,24),dp(activity,44)));
                row.setContentDescription(names.get(i)+(active?UiLanguage.t(", selecionado"):"")+". "+(available?description(graph):reason));row.setAlpha(available?1f:.5f);row.setEnabled(available);row.setOnClickListener(v->{dialog.dismiss();selected.choose(index);});LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(activity,6);list.addView(row,rp);
            }
            if(dialog.isShowing()){int visible=0;for(JSONObject graph:graphs)if(isVideo(graph)==mode[0])visible++;
                int height=dp(activity,86+visible*88+(tabs.getVisibility()==View.VISIBLE?48:0)+(importer!=null?52:0));dialog.getWindow().setLayout(-1,Math.min(height,(int)(activity.getResources().getDisplayMetrics().heightPixels*.72f)));}
        };
        images.setOnClickListener(v->{boolean changed=mode[0];mode[0]=false;render.run();if(changed)UiMotion.enter(list);});videos.setOnClickListener(v->{boolean changed=!mode[0];mode[0]=true;render.run();if(changed)UiMotion.enter(list);});
        if(importer!=null){Button add=button(activity,UiLanguage.t("Importar workflow"));FlowDesign.labelIcon(add,"plus");page.addView(add,new LinearLayout.LayoutParams(-1,dp(activity,52)));add.setOnClickListener(v->{dialog.dismiss();importer.run();});}
        render.run();dialog.setContentView(page);UiMotion.dialog(dialog);dialog.show();Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.65f));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);window.setDimAmount(.45f);window.setNavigationBarColor(FlowDesign.SURFACE);render.run();
    }
    static boolean isVideo(JSONObject graph){return VideoWorkflow.matches(graph)||ReferenceBindings.ref2va(graph);}
    static String description(JSONObject graph){
        String text;
        if(ReferenceBindings.ref2va(graph))text=UiLanguage.t("Edição de vídeo · referências");
        else if(VideoWorkflow.matches(graph))text=String.format(Locale.ROOT,UiLanguage.t("%d passos · texto, imagem e first/last"),VideoWorkflow.turbo(graph)?4:20);
        else if(ReferenceSupport.klein(graph))text=UiLanguage.t("Editar e combinar até 3 imagens");
        else if(MemoryProfiles.family(graph).equals("krea2")){int steps=0;for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject n=graph.optJSONObject(it.next());if(n!=null&&"KSampler".equals(n.optString("class_type"))&&n.optJSONObject("inputs")!=null)steps=n.optJSONObject("inputs").optInt("steps");}text=String.format(Locale.ROOT,UiLanguage.t("Texto · %d passos"),steps);}
        else {String family=MemoryProfiles.family(graph);int steps=family.equals("ideogram")?ideogramSteps(graph):family.equals("moss")?50:family.equals("kromoss")?8:0;
            text=steps>0?String.format(Locale.ROOT,UiLanguage.t("Texto e boxes · %d passos"),steps):UiLanguage.t("Workflow importado");}
        return text+(PromptAgent.target(graph)!=null?UiLanguage.t(" · Agente disponível"):"");
    }
    private static int ideogramSteps(JSONObject graph){for(Iterator<String> it=graph.keys();it.hasNext();){JSONObject n=graph.optJSONObject(it.next());if(n!=null&&n.optString("class_type").equals("Ideogram4Scheduler")&&n.optJSONObject("inputs")!=null)return n.optJSONObject("inputs").optInt("steps",20);}return 20;}
    private static int dp(Activity a,int n){return FlowDesign.dp(a,n);}
    private static TextView label(Activity a,String s,int size,boolean bold){TextView v=new TextView(a);v.setText(s);v.setTextSize(size);v.setTextColor(FlowDesign.INK);if(bold)v.setTypeface(null,Typeface.BOLD);return v;}
    private static Button button(Activity a,String s){Button b=new Button(a);b.setAllCaps(false);b.setText(s);b.setTextColor(FlowDesign.INK);b.setTextSize(14);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private static Button icon(Activity a,String kind,String desc){Button b=new FlowDesign.IconButton(a);FlowDesign.symbol(b,kind,desc);b.setBackgroundColor(Color.TRANSPARENT);return b;}
}
