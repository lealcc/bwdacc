package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Queue and bounded terminal output; only GET requests, only while visible. */
final class ServerMonitor {
    interface Api {JSONObject get(String url)throws Exception;}
    interface Titles {String name(String id);}
    private final Activity activity;private final String address;private final Api api;private final Titles titles;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Dialog dialog;private final LinearLayout queueRows;private final ScrollView queueScroll,logScroll;
    private final TextView status,logText;private final Button queueTab,logTab,live;private final CheckBox follow;
    private boolean terminal,paused,busy,closed,active=true,dragging;private final Runnable tick=()->fetch(false);
    private String lastQueue="";
    ServerMonitor(Activity activity,String address,Api api,Titles titles){
        this.activity=activity;this.address=address;this.api=api;this.titles=titles;dialog=new Dialog(activity);
        LinearLayout page=column();FlowDesign.surface(page);
        LinearLayout header=row();TextView title=text(UiLanguage.t("Servidor"),21,true);header.addView(title,new LinearLayout.LayoutParams(0,dp(52),1));title.setGravity(Gravity.CENTER_VERTICAL);
        Button refresh=icon("refresh",UiLanguage.t("Atualizar fila ou terminal")),close=icon("close",UiLanguage.t("Fechar monitor do servidor"));header.addView(refresh,new LinearLayout.LayoutParams(dp(48),dp(48)));header.addView(close,new LinearLayout.LayoutParams(dp(48),dp(48)));page.addView(header);
        refresh.setOnClickListener(v->fetch(true));close.setOnClickListener(v->close());
        LinearLayout tabs=row();queueTab=button(UiLanguage.t("Fila"));logTab=button(UiLanguage.t("Terminal"));FlowDesign.labelIcon(queueTab,"queue");FlowDesign.labelIcon(logTab,"terminal");tabs.addView(queueTab,new LinearLayout.LayoutParams(0,dp(48),1));tabs.addView(logTab,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(tabs);
        status=text(UiLanguage.t("Atualizando…"),12,false);status.setTextColor(FlowDesign.MUTED);status.setPadding(dp(4),dp(12),dp(4),dp(12));page.addView(status);
        if(address.equals(DesktopConnection.endpoint(activity)))logTab.setVisibility(View.GONE);
        queueScroll=new ScrollView(activity);queueRows=column();queueScroll.addView(queueRows);page.addView(queueScroll,new LinearLayout.LayoutParams(-1,0,1));
        logScroll=new ScrollView(activity);logScroll.setBackground(FlowDesign.shape(activity,0xFF090A0B,20,false));logText=text(UiLanguage.t("Carregando log…"),12,false);logText.setTypeface(Typeface.MONOSPACE);logText.setTextIsSelectable(true);logText.setPadding(dp(12),dp(14),dp(12),dp(14));logScroll.addView(logText);page.addView(logScroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout controls=row();controls.setPadding(0,dp(8),0,0);live=button(UiLanguage.t("Pausar"));FlowDesign.labelIcon(live,"pause");controls.addView(live,new LinearLayout.LayoutParams(dp(132),dp(48)));
        follow=new CheckBox(activity);follow.setText(UiLanguage.t("Seguir final"));follow.setTextColor(FlowDesign.INK);follow.setChecked(true);controls.addView(follow,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(controls);
        follow.setOnCheckedChangeListener((v,on)->{if(on)logScroll.post(()->logScroll.fullScroll(View.FOCUS_DOWN));});
        logScroll.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN)dragging=true;if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)dragging=false;return false;});
        logScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(dragging&&y<oldY&&follow.isChecked())follow.setChecked(false);});
        live.setOnClickListener(v->{paused=!paused;live.setText(paused?UiLanguage.t("Retomar"):UiLanguage.t("Pausar"));FlowDesign.labelIcon(live,paused?"play":"pause");ui.removeCallbacks(tick);if(paused)status.setText(UiLanguage.t("Atualização pausada"));else fetch(true);});
        queueTab.setOnClickListener(v->select(false));logTab.setOnClickListener(v->select(true));
        dialog.setContentView(page);dialog.setOnDismissListener(v->dispose());dialog.show();
        Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setGravity(Gravity.BOTTOM);window.setLayout(-1,(int)(activity.getResources().getDisplayMetrics().heightPixels*.84f));
        window.setNavigationBarColor(FlowDesign.SURFACE);window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(true);
        select(false);
    }
    private void select(boolean logs){terminal=logs;queueScroll.setVisibility(logs?View.GONE:View.VISIBLE);logScroll.setVisibility(logs?View.VISIBLE:View.GONE);follow.setVisibility(logs?View.VISIBLE:View.GONE);queueTab.setBackground(FlowDesign.shape(activity,!logs?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));logTab.setBackground(FlowDesign.shape(activity,logs?FlowDesign.CONTROL:Color.TRANSPARENT,20,false));status.setText(UiLanguage.t("Atualizando…"));fetch(true);}
    private void fetch(boolean force){
        if(closed||!active||busy||paused&&!force)return;ui.removeCallbacks(tick);busy=true;final boolean logs=terminal;
        worker.execute(()->{JSONObject snapshot=null;String error=null;try{snapshot=api.get(address+(logs?"/internal/logs/raw":"/queue"));}catch(Exception e){error=e.getMessage()==null?"Sem resposta do servidor":e.getMessage();}
            final JSONObject data=snapshot;final String failure=error;
            ui.post(()->{busy=false;if(closed||!active)return;if(logs!=terminal){fetch(true);return;}
                boolean unsupported=logs&&(failure!=null&&(failure.contains("HTTP 404")||failure.contains("HTTP 405"))||data!=null&&data.optJSONArray("entries")==null);
                if(unsupported){logText.setText(UiLanguage.t("Este ComfyUI não disponibiliza o terminal remoto. A fila continua disponível."));status.setText(UiLanguage.t("Terminal indisponível neste servidor"));return;}
                if(failure!=null){status.setText(UiLanguage.t("Falha ao atualizar · toque em atualizar para tentar novamente"));if(logs&&logText.getText().toString().equals("Carregando log…"))logText.setText(UiLanguage.t("Não foi possível ler o log do servidor."));}
                else{
                    if(logs){String value=ServerMonitorData.logs(data);if(value.isEmpty())value="Servidor ainda sem mensagens de log.";if(!value.contentEquals(logText.getText())){logText.setText(value);if(follow.isChecked())logScroll.post(()->logScroll.fullScroll(View.FOCUS_DOWN));}}
                    else renderQueue(data);
                    status.setText((paused?UiLanguage.t("Pausado · "):logs?UiLanguage.t("Log do ComfyUI · "):UiLanguage.t("Atualizado · "))+new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date()));
                }
                if(!paused)ui.postDelayed(tick,4000);
            });
        });
    }
    private void renderQueue(JSONObject data){
        List<ServerMonitorData.Job> jobs=ServerMonitorData.jobs(data);StringBuilder fingerprint=new StringBuilder();for(ServerMonitorData.Job job:jobs)fingerprint.append(job.id).append(job.running).append(job.order).append(job.title);
        if(fingerprint.toString().equals(lastQueue)&&queueRows.getChildCount()>0)return;lastQueue=fingerprint.toString();queueRows.removeAllViews();
        if(jobs.isEmpty()){TextView empty=text(UiLanguage.t("Fila livre"),20,true);empty.setPadding(dp(12),dp(28),dp(12),dp(8));queueRows.addView(empty);TextView detail=text(UiLanguage.t("Nenhuma geração em andamento ou aguardando."),14,false);detail.setTextColor(FlowDesign.MUTED);detail.setPadding(dp(12),0,dp(12),0);queueRows.addView(detail);return;}
        int running=0;for(ServerMonitorData.Job job:jobs)if(job.running)running++;int pending=jobs.size()-running,position=0;
        TextView count=text(running+UiLanguage.t(" em execução · ")+pending+UiLanguage.t(" na fila"),13,false);count.setTextColor(FlowDesign.MUTED);count.setPadding(dp(6),dp(4),dp(6),dp(12));queueRows.addView(count);
        boolean group=false;
        for(ServerMonitorData.Job job:jobs){
            if(!job.running&&!group){group=true;TextView label=text(UiLanguage.t("A seguir"),14,true);label.setPadding(dp(6),dp(12),dp(6),dp(8));queueRows.addView(label);}
            LinearLayout card=row();card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(FlowDesign.shape(activity,FlowDesign.CONTROL,20,false));
            ImageView icon=new ImageView(activity);icon.setImageDrawable(FlowDesign.icon(activity,job.running?"play":"queue",22));card.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(40)));
            LinearLayout labels=column();labels.setPadding(dp(10),0,0,0);String known=titles.name(job.id);TextView name=text(known==null||known.trim().isEmpty()?job.title:known,15,true);name.setMaxLines(2);labels.addView(name);
            String shortId=job.id.substring(0,Math.min(8,job.id.length()));TextView info=text((job.running?UiLanguage.t("Em execução"):UiLanguage.t("Posição ")+(++position))+" · #"+shortId,12,false);info.setTextColor(FlowDesign.MUTED);info.setPadding(0,dp(5),0,0);labels.addView(info);card.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(8);queueRows.addView(card,lp);
        }
    }
    void pause(){active=false;ui.removeCallbacks(tick);}
    void resume(){if(closed)return;active=true;if(!paused)fetch(false);}
    void close(){dialog.dismiss();dispose();}
    private void dispose(){if(closed)return;closed=true;ui.removeCallbacksAndMessages(null);worker.shutdownNow();}
    private int dp(int n){return FlowDesign.dp(activity,n);}
    private LinearLayout column(){LinearLayout view=new LinearLayout(activity);view.setOrientation(1);return view;}
    private LinearLayout row(){LinearLayout view=new LinearLayout(activity);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private TextView text(String value,int size,boolean bold){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(FlowDesign.INK);if(bold)view.setTypeface(null,Typeface.BOLD);return view;}
    private Button button(String name){Button b=new Button(activity);b.setText(name);b.setAllCaps(false);b.setTextSize(14);b.setTextColor(FlowDesign.INK);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(FlowDesign.shape(activity,FlowDesign.CONTROL,20,false));return b;}
    private Button icon(String symbol,String label){Button b=new FlowDesign.IconButton(activity);FlowDesign.symbol(b,symbol,label);b.setMinWidth(0);b.setMinimumWidth(0);b.setBackgroundColor(Color.TRANSPARENT);return b;}
}
