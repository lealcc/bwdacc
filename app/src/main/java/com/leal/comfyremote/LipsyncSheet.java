package com.leal.comfyremote;

import android.app.*;
import android.graphics.Color;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import java.util.concurrent.Executor;

final class LipsyncSheet {
    interface Submit {boolean run(LipsyncAudio audio,int size);}
    final Activity activity;final Dialog dialog;final Executor worker;final Submit submit;final Runnable choose;
    final TextView detail,status;final Button send,pick;final Button[] sizes=new Button[4];
    LipsyncAudio audio;int size;boolean closed,transferred,loading;
    LipsyncSheet(Activity a,boolean video,LipsyncAudio audio,int size,Executor worker,Runnable choose,Submit submit){
        activity=a;this.audio=audio;this.size=LipsyncWorkflow.size(size);this.worker=worker;this.choose=choose;this.submit=submit;dialog=new Dialog(a);
        LinearLayout page=new LinearLayout(a);page.setOrientation(1);FlowDesign.surface(page);page.setPadding(dp(20),dp(18),dp(20),dp(12));
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=text(UiLanguage.t("Lipsync"),21,true);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button close=button("");FlowDesign.symbol(close,"close",UiLanguage.t("Fechar"));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));close.setOnClickListener(v->dialog.dismiss());page.addView(header);
        page.addView(text(UiLanguage.t("MuseTalk 1.5 · ")+UiLanguage.t("Local"),13,false));gap(page,16);
        page.addView(text(video?UiLanguage.t("Troca a fala e cria uma versão do vídeo. O áudio precisa caber no clipe."):UiLanguage.t("Anima a boca da imagem com seu áudio. O resultado vira um novo vídeo."),14,false));gap(page,14);
        pick=button(UiLanguage.t("Escolher áudio"));FlowDesign.labelIcon(pick,"sound");pick.setPadding(dp(16),0,dp(16),0);pick.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);page.addView(pick,new LinearLayout.LayoutParams(-1,dp(52)));pick.setOnClickListener(v->choose.run());
        detail=text("",13,false);detail.setMaxLines(2);page.addView(detail);gap(page,16);
        page.addView(text(UiLanguage.t("Tamanho máximo"),13,false));LinearLayout row=new LinearLayout(a);page.addView(row);
        int[] values={512,768,1024,1280};for(int n=0;n<4;n++){final int value=values[n];Button button=button(String.valueOf(value));sizes[n]=button;row.addView(button,new LinearLayout.LayoutParams(0,dp(48),1));button.setOnClickListener(v->{this.size=value;refresh();});}
        gap(page,12);page.addView(text(UiLanguage.t("Até 30 s · um rosto frontal e visível. O áudio substitui o som original; proporção preservada, sem ampliar.")+" "+UiLanguage.t("O resultado tem a duração do áudio."),12,false));
        status=text("",13,false);status.setPadding(0,dp(8),0,dp(8));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);page.addView(status);
        send=button(UiLanguage.t("Sincronizar lábios"));page.addView(send,new LinearLayout.LayoutParams(-1,dp(52)));send.setOnClickListener(v->{if(audio!=null&&!loading&&submit.run(audio,this.size)){transferred=true;dialog.dismiss();}});
        dialog.setOnDismissListener(d->{closed=true;if(!transferred)LipsyncAudio.discard(this.audio);});
        ScrollView scroll=new ScrollView(a);scroll.addView(page);dialog.setContentView(scroll);UiMotion.dialog(dialog);dialog.show();Window w=dialog.getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);w.setNavigationBarColor(FlowDesign.SURFACE);
        scroll.post(()->{if(scroll.getHeight()>a.getResources().getDisplayMetrics().heightPixels*.85f)w.setLayout(-1,(int)(a.getResources().getDisplayMetrics().heightPixels*.85f));});refresh();
    }
    void picked(Uri uri){if(closed)return;loading=true;status.setText(UiLanguage.t("Preparando áudio…"));refresh();worker.execute(()->{
        try{LipsyncAudio selected=LipsyncAudio.importFile(activity,uri);activity.runOnUiThread(()->{if(closed){LipsyncAudio.discard(selected);return;}LipsyncAudio.discard(audio);audio=selected;loading=false;status.setText("");refresh();});}
        catch(Exception error){activity.runOnUiThread(()->{if(closed)return;loading=false;status.setText(error.getMessage());refresh();});}
    });}
    void accept(LipsyncAudio selected){if(closed){LipsyncAudio.discard(selected);return;}LipsyncAudio.discard(audio);audio=selected;loading=false;status.setText("");refresh();}
    void refresh(){detail.setText(audio==null?UiLanguage.t("MP3, WAV ou outro áudio reconhecido pelo aparelho."):audio.name+" · "+String.format(java.util.Locale.getDefault(),UiLanguage.t("%.1f s"),audio.millis/1000.0));pick.setEnabled(!loading);send.setEnabled(audio!=null&&!loading);send.setAlpha(send.isEnabled()?1f:.4f);for(int n=0;n<4;n++)sizes[n].setBackground(FlowDesign.pill(activity,new int[]{512,768,1024,1280}[n]==size?FlowDesign.CONTROL:Color.TRANSPARENT,18,false));}
    int dp(int n){return FlowDesign.dp(activity,n);}
    TextView text(String value,int size,boolean title){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(title?FlowDesign.INK:FlowDesign.MUTED);v.setTypeface(title?Branding.secondary(activity,600):Branding.body(activity,400));return v;}
    Button button(String label){Button b=new FlowDesign.IconButton(activity);b.setText(label);b.setTextSize(13);b.setTextColor(FlowDesign.INK);b.setTypeface(Branding.secondary(activity,500));b.setAllCaps(false);b.setBackground(FlowDesign.pill(activity,FlowDesign.CONTROL,20,true));return b;}
    void gap(LinearLayout p,int n){p.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(n)));}
}
