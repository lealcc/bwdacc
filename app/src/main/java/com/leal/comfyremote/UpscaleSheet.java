package com.leal.comfyremote;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

final class UpscaleSheet {
    interface Submit {void run(boolean compact,int size);}
    static void show(Activity a,boolean video,boolean compact,int size,String unavailable,Submit submit){
        Dialog dialog=new Dialog(a);LinearLayout page=new LinearLayout(a);page.setOrientation(1);FlowDesign.surface(page);
        TextView title=text(a,video?UiLanguage.t("Upscale de vídeo"):UiLanguage.t("Upscale de imagem"),21);page.addView(title);
        TextView detail=text(a,video?UiLanguage.t("Refina detalhes e preserva proporção, duração e áudio. Até 15 segundos."):UiLanguage.t("4x-UltraSharp · preserva a proporção. Cria uma versão no mesmo conjunto."),14);page.addView(detail);
        final boolean[] mode={compact};final int[] resolution={size};
        LinearLayout models=new LinearLayout(a);page.addView(models);
        Button small=button(a,UiLanguage.t("Kijai compacto")),full=button(a,UiLanguage.t("Completo"));
        if(video){models.addView(small,new LinearLayout.LayoutParams(0,dp(a,52),1));models.addView(full,new LinearLayout.LayoutParams(0,dp(a,52),1));}
        TextView warning=text(a,"",12);page.addView(warning);if(!video&&unavailable.isEmpty())warning.setVisibility(View.GONE);
        LinearLayout sizes=new LinearLayout(a);page.addView(sizes);
        int[] values=video?new int[]{1920,2560}:new int[]{2,4};Button[] choices=new Button[2];
        for(int i=0;i<2;i++){choices[i]=button(a,video?(i==0?UiLanguage.t("1920 px"):UiLanguage.t("2560 px · 2K")):values[i]+"×");sizes.addView(choices[i],new LinearLayout.LayoutParams(0,dp(a,52),1));}
        Runnable refresh=()->{small.setBackground(FlowDesign.shape(a,mode[0]?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));full.setBackground(FlowDesign.shape(a,!mode[0]?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));
            warning.setText(!unavailable.isEmpty()?unavailable:video?(mode[0]?UiLanguage.t("Experimental · refiner Kijai + Turbo, sem LMS Sharp. Tamanho no lado maior. Pode alterar rostos e detalhes."):UiLanguage.t("Refiner, Turbo e LMS Sharp. Tamanho no lado maior. Pode alterar rostos e detalhes.")):"");
            for(int i=0;i<2;i++)choices[i].setBackground(FlowDesign.shape(a,resolution[0]==values[i]?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));};
        small.setOnClickListener(v->{mode[0]=true;refresh.run();});full.setOnClickListener(v->{mode[0]=false;refresh.run();});
        for(int i=0;i<2;i++){final int index=i;choices[i].setOnClickListener(v->{resolution[0]=values[index];refresh.run();});}
        Button send=button(a,UiLanguage.t("Criar upscale"));send.setEnabled(unavailable.isEmpty());send.setBackground(FlowDesign.shape(a,FlowDesign.CONTROL,24,true));LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(a,52));action.topMargin=dp(a,10);page.addView(send,action);
        send.setOnClickListener(v->{dialog.dismiss();submit.run(mode[0],resolution[0]);});
        Button close=button(a,UiLanguage.t("Cancelar"));page.addView(close,new LinearLayout.LayoutParams(-1,dp(a,48)));close.setOnClickListener(v->dialog.dismiss());
        refresh.run();dialog.setContentView(page);dialog.show();Window w=dialog.getWindow();w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);w.setNavigationBarColor(FlowDesign.SURFACE);
    }
    private static int dp(Activity a,int n){return FlowDesign.dp(a,n);}
    private static TextView text(Activity a,String text,int size){TextView v=new TextView(a);v.setText(text);v.setTextColor(size==21?FlowDesign.INK:FlowDesign.MUTED);v.setTextSize(size);v.setPadding(dp(a,16),dp(a,12),dp(a,16),dp(a,12));return v;}
    private static Button button(Activity a,String text){Button b=new Button(a);b.setText(text);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(FlowDesign.INK);b.setBackgroundColor(Color.TRANSPARENT);return b;}
}
