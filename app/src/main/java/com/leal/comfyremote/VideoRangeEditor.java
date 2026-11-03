package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Locale;
import org.json.*;

/** Non-destructive trim; the selected endpoint is where continuation begins. */
final class VideoRangeEditor {
    interface Saved {void accept(JSONObject range);}
    static void show(Activity a,File file,long duration,JSONObject initial,Bitmap poster,boolean protectedMedia,boolean sensitive,double aspect,Saved saved){
        Dialog dialog=new Dialog(a);Handler handler=new Handler();InlineVideoPlayer player=new InlineVideoPlayer(a);ImageView cover=new ImageView(a);cover.setImageBitmap(poster);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);cover.setBackground(FlowDesign.shape(a,0xFF18191D,24,false));cover.setClipToOutline(true);
        LinearLayout page=new LinearLayout(a){@Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus){handler.removeCallbacksAndMessages(null);if(protectedMedia){player.stop();cover.setAlpha(1);}else player.freezeForGesture();}else if(!protectedMedia)player.play(file,"trim-preview");}};page.setOrientation(1);page.setPadding(dp(a,16),dp(a,8),dp(a,16),dp(a,20));FlowDesign.surface(page);
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);Button back=BoxEditor.icon(a,"close",UiLanguage.t("Cancelar corte do vídeo"));header.addView(back,new LinearLayout.LayoutParams(dp(a,44),dp(a,48)));TextView title=BoxEditor.label(a,UiLanguage.t("Trecho para extensão"),19);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);back.setOnClickListener(v->dialog.dismiss());
        FrameLayout preview=new FrameLayout(a);preview.setBackground(FlowDesign.shape(a,0xFF18191D,24,false));preview.setClipToOutline(true);preview.addView(player,new FrameLayout.LayoutParams(-1,-1));preview.addView(cover,new FrameLayout.LayoutParams(-1,-1));page.addView(preview,new LinearLayout.LayoutParams(-1,dp(a,290)));
        preview.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{int width=r-l,height=b-t;if(width<=0||height<=0)return;double ratio=Double.isFinite(aspect)&&aspect>0?aspect:1;int w=width,h=(int)Math.round(w/ratio);if(h>height){h=height;w=(int)Math.round(h*ratio);}FrameLayout.LayoutParams cp=(FrameLayout.LayoutParams)cover.getLayoutParams();if(cp.width!=w||cp.height!=h){cp.width=w;cp.height=h;cp.gravity=Gravity.CENTER;cover.setLayoutParams(cp);}});
        TextView times=BoxEditor.label(a,"",14);times.setGravity(Gravity.CENTER);page.addView(times,new LinearLayout.LayoutParams(-1,dp(a,44)));
        RangeBar bar=new RangeBar(a,duration);bar.start=Math.max(0,initial.optLong("start"));bar.end=initial.optLong("end",duration);if(bar.end==0)bar.end=duration;bar.end=Math.min(duration,bar.end);if(bar.end-bar.start>120000)bar.start=bar.end-120000;
        Runnable label=()->times.setText(clock(bar.start)+"   —   "+clock(bar.end)+"   ·   "+String.format(Locale.ROOT,UiLanguage.t("%.1fs"),(bar.end-bar.start)/1000.0));label.run();
        bar.changed=()->{label.run();player.setRange(bar.start,bar.end,true,bar.draggingEnd);};page.addView(bar,new LinearLayout.LayoutParams(-1,dp(a,64)));
        TextView detail=BoxEditor.label(a,UiLanguage.t("Arraste as pontas para cortar. A continuação começa no fim deste trecho."),13);page.addView(detail);Button all=new Button(a);all.setAllCaps(false);all.setText(UiLanguage.t("Usar vídeo inteiro"));all.setTextColor(0xFFEAEAF0);all.setBackgroundColor(Color.TRANSPARENT);all.setEnabled(duration<=120000);page.addView(all,new LinearLayout.LayoutParams(-1,dp(a,44)));all.setOnClickListener(v->{bar.start=0;bar.end=duration;bar.invalidate();bar.changed.run();});
        Button apply=new Button(a);apply.setAllCaps(false);apply.setText(UiLanguage.t("Usar trecho e continuar"));apply.setTextColor(0xFF111216);apply.setBackground(FlowDesign.shape(a,0xFFE7E7EE,24,false));page.addView(apply,new LinearLayout.LayoutParams(-1,dp(a,54)));
        apply.setOnClickListener(v->{try{JSONObject range=VideoExtendWorkflow.range(bar.start,bar.end);VideoExtendWorkflow.validate(range,duration);saved.accept(range);dialog.dismiss();}catch(Exception e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}});
        Runnable reveal=()->{if(!dialog.isShowing())return;dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);cover.setAlpha(0);player.play(file,"trim-preview");player.setRange(bar.start,bar.end,false,false);};
        if(protectedMedia){cover.setContentDescription(UiLanguage.t("Prévia protegida; mantenha pressionado para revelar"));cover.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){handler.postDelayed(reveal,400);return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(reveal);player.stop();cover.setAlpha(1);return true;}return true;});}
        else{cover.setVisibility(View.GONE);player.play(file,"trim-preview");player.setRange(bar.start,bar.end,false,false);}
        dialog.setOnDismissListener(d->{handler.removeCallbacksAndMessages(null);player.stop();});ScrollView scroll=new ScrollView(a);scroll.addView(page);dialog.setContentView(scroll);dialog.show();Window window=dialog.getWindow();window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(-1,Math.min(dp(a,700),a.getResources().getDisplayMetrics().heightPixels*9/10));window.setGravity(Gravity.BOTTOM);
        if(sensitive||protectedMedia)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }
    static String clock(long millis){return String.format(Locale.ROOT,"%d:%04.1f",millis/60000,(millis%60000)/1000.0);}
    static int dp(Activity a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static final class RangeBar extends View {
        final long duration;long start,end;boolean draggingEnd;Runnable changed;final Paint paint=new Paint(3);final int padding;
        RangeBar(Activity a,long duration){super(a);this.duration=duration;end=duration;padding=dp(a,18);setContentDescription(UiLanguage.t("Corte do vídeo: arraste a ponta esquerda ou direita"));}
        float position(long time){return padding+(getWidth()-padding*2)*time/(float)Math.max(1,duration);}
        protected void onDraw(Canvas c){float top=getHeight()*.25f,bottom=getHeight()*.75f,left=position(start),right=position(end);paint.setColor(0xFF292B33);c.drawRoundRect(padding,top,getWidth()-padding,bottom,8,8,paint);paint.setColor(0xFF65637E);c.drawRoundRect(left,top,right,bottom,8,8,paint);paint.setColor(0xFFE0DCF9);c.drawRoundRect(left-6,top-8,left+6,bottom+8,5,5,paint);c.drawRoundRect(right-6,top-8,right+6,bottom+8,5,5,paint);}
        public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);draggingEnd=Math.abs(e.getX()-position(end))<Math.abs(e.getX()-position(start));}if(e.getActionMasked()==MotionEvent.ACTION_MOVE||e.getActionMasked()==MotionEvent.ACTION_UP){long time=Math.round(Math.max(0,Math.min(1,(e.getX()-padding)/(getWidth()-2.0*padding)))*duration/100.0)*100;if(draggingEnd)end=Math.max(start+1000,Math.min(Math.min(duration,start+120000),time));else start=Math.min(end-1000,Math.max(Math.max(0,end-120000),time));invalidate();if(changed!=null)changed.run();setContentDescription(UiLanguage.t("Trecho de ")+clock(start)+UiLanguage.t(" até ")+clock(end));}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)getParent().requestDisallowInterceptTouchEvent(false);return true;}
    }
}
