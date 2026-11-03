package com.leal.comfyremote;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Locale;

/** Local video player with silent autoplay, scrubbing and controls over the fitted video rectangle. */
final class InlineVideoPlayer extends FrameLayout implements TextureView.SurfaceTextureListener {
    private final FrameLayout box;
    private final TextureView texture;
    private final LinearLayout controls;
    private final Button sound,play;
    private final SeekBar seek;
    private final TextView time;
    private final Handler handler=new Handler();
    private MediaPlayer player;
    private android.media.MediaDataSource vaultSource;
    private Surface surface;
    private String key="",path="";
    private boolean prepared,muted=true,seeking,resumeAfterSeek,playWhenReady=true,dragFrozen,playingBeforeDrag,firstFrame;
    private int videoWidth=16,videoHeight=9;
    private long rangeStart,rangeEnd;
    Runnable onError,onFirstFrame;
    private final Runnable hideControls=this::hideControlsIfPlaying;
    private final Runnable tick=new Runnable(){public void run(){
        if(player==null||!prepared)return;
        try{
            int duration=player.getDuration(),position=player.getCurrentPosition();
            if(rangeEnd>rangeStart&&(position<rangeStart||position>=rangeEnd)){player.seekTo((int)rangeStart);position=(int)rangeStart;}
            seek.setMax(Math.max(1,duration));if(!seeking)seek.setProgress(position);
            time.setText(clock(seeking?seek.getProgress():position)+" / "+clock(duration));
            seek.setContentDescription(UiLanguage.t("Posição do vídeo, ")+clock(position)+UiLanguage.t(" de ")+clock(duration));
            FlowDesign.symbol(play,isPlaying()?"pause":"play",isPlaying()?UiLanguage.t("Pausar vídeo"):UiLanguage.t("Reproduzir vídeo"));
        }catch(IllegalStateException ignored){}
        handler.postDelayed(this,250);
    }};

    InlineVideoPlayer(Context context){
        super(context);setVisibility(GONE);setBackgroundColor(Color.TRANSPARENT);
        box=new FrameLayout(context);box.setBackground(FlowDesign.shape(context,Color.BLACK,24,false));box.setClipToOutline(true);addView(box);
        texture=new TextureView(context);texture.setSurfaceTextureListener(this);box.addView(texture,new FrameLayout.LayoutParams(-1,-1));
        sound=icon("muted",UiLanguage.t("Ativar som"));sound.setBackground(FlowDesign.shape(context,0xC9161719,24,true));
        FrameLayout.LayoutParams soundPosition=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.TOP|Gravity.LEFT);soundPosition.setMargins(dp(8),dp(8),0,0);box.addView(sound,soundPosition);
        sound.setOnClickListener(v->{muted=!muted;applyVolume();updateSound();showControls();});
        controls=new LinearLayout(context);controls.setOrientation(LinearLayout.VERTICAL);controls.setPadding(dp(8),dp(8),dp(8),dp(6));
        controls.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x00101012,0xDA101012}));
        FrameLayout.LayoutParams controlsPosition=new FrameLayout.LayoutParams(-1,dp(84),Gravity.BOTTOM);box.addView(controls,controlsPosition);
        seek=new SeekBar(context);seek.setProgressTintList(ColorStateList.valueOf(Color.WHITE));seek.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        controls.addView(seek,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);controls.addView(row,new LinearLayout.LayoutParams(-1,dp(42)));
        Button back=icon("back10",UiLanguage.t("Voltar 10 segundos")),forward=icon("forward10",UiLanguage.t("Avançar 10 segundos"));play=icon("pause",UiLanguage.t("Pausar vídeo"));
        row.addView(back,new LinearLayout.LayoutParams(dp(44),-1));row.addView(play,new LinearLayout.LayoutParams(dp(44),-1));row.addView(forward,new LinearLayout.LayoutParams(dp(44),-1));
        time=new TextView(context);time.setTextColor(Color.WHITE);time.setTextSize(12);time.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);time.setPadding(0,0,dp(8),0);row.addView(time,new LinearLayout.LayoutParams(0,-1,1));
        back.setOnClickListener(v->jump(-10000));forward.setOnClickListener(v->jump(10000));
        play.setOnClickListener(v->{if(!prepared)return;try{if(player.isPlaying()){player.pause();playWhenReady=false;}else{player.start();playWhenReady=true;}}catch(IllegalStateException ignored){}showControls();});
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){if(fromUser)time.setText(clock(progress)+" / "+clock(bar.getMax()));}
            public void onStartTrackingTouch(SeekBar bar){seeking=true;handler.removeCallbacks(hideControls);resumeAfterSeek=isPlaying();if(resumeAfterSeek)player.pause();}
            public void onStopTrackingTouch(SeekBar bar){seeking=false;if(prepared){player.seekTo(bar.getProgress());playWhenReady=resumeAfterSeek;}showControls();}
        });
    }
    private int dp(float value){return FlowDesign.dp(getContext(),value);}
    private void hideControlsIfPlaying(){if(isPlaying()&&!seeking)controls.setVisibility(INVISIBLE);}
    private Button icon(String symbol,String label){Button button=new FlowDesign.IconButton(getContext());button.setBackgroundColor(Color.TRANSPARENT);button.setMinWidth(0);button.setMinimumWidth(0);button.setStateListAnimator(null);FlowDesign.symbol(button,symbol,label);return button;}
    private static String clock(int milliseconds){int seconds=Math.max(0,milliseconds/1000);return String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    boolean hasFrame(){return firstFrame&&player!=null;}
    void setRange(long start,long end,boolean seekNow,boolean tail){rangeStart=start;rangeEnd=end;if(prepared&&seekNow)player.seekTo((int)(tail?Math.max(start,end-700):start));}
    boolean isLoaded(String id){return id.equals(key)&&!path.isEmpty();}
    boolean isPlaying(){try{return prepared&&player!=null&&player.isPlaying();}catch(IllegalStateException ignored){return false;}}
    void setPagingListener(View.OnTouchListener listener){texture.setOnTouchListener(listener);setOnTouchListener(listener);}
    void toggleControls(){if(controls.getVisibility()==VISIBLE)controls.setVisibility(INVISIBLE);else showControls();}
    private void showControls(){controls.setVisibility(VISIBLE);handler.removeCallbacks(hideControls);handler.postDelayed(hideControls,3500);}
    private void updateSound(){FlowDesign.symbol(sound,muted?"muted":"sound",muted?UiLanguage.t("Ativar som"):UiLanguage.t("Silenciar vídeo"));}
    private void applyVolume(){if(player!=null)try{player.setVolume(muted?0f:1f,muted?0f:1f);}catch(IllegalStateException ignored){}}
    private void jump(int delta){if(!prepared)return;try{player.seekTo(Math.max(0,Math.min(player.getDuration(),player.getCurrentPosition()+delta)));}catch(IllegalStateException ignored){}showControls();}

    void play(File file,String id){
        if(isLoaded(id)){if(dragFrozen){dragFrozen=false;setAlpha(firstFrame?1f:0f);if(prepared&&playingBeforeDrag)player.start();}return;}
        stop();setAlpha(0f);path=file.getAbsolutePath();key=id;muted=true;playWhenReady=true;updateSound();seek.setProgress(0);seek.setEnabled(false);time.setText("0:00 / 0:00");
        setVisibility(VISIBLE);showControls();if(texture.isAvailable())prepare();
    }
    private void prepare(){
        if(path.isEmpty()||!texture.isAvailable()||player!=null)return;
        final MediaPlayer current=new MediaPlayer();player=current;prepared=false;
        try{
            surface=new Surface(texture.getSurfaceTexture());current.setSurface(surface);current.setVolume(0f,0f);current.setLooping(true);vaultSource=VaultFiles.media(new File(path));current.setDataSource(vaultSource);
            current.setOnPreparedListener(mp->{if(player!=mp)return;prepared=true;videoWidth=Math.max(1,mp.getVideoWidth());videoHeight=Math.max(1,mp.getVideoHeight());requestLayout();applyVolume();seek.setEnabled(true);if(playWhenReady&&!dragFrozen)mp.start();handler.removeCallbacks(tick);tick.run();showControls();});
            current.setOnInfoListener((mp,what,extra)->{if(player==mp&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){firstFrame=true;if(!dragFrozen){setAlpha(1f);if(onFirstFrame!=null)onFirstFrame.run();}}return false;});
            current.setOnVideoSizeChangedListener((mp,w,h)->{if(player==mp&&w>0&&h>0){videoWidth=w;videoHeight=h;requestLayout();}});
            current.setOnSeekCompleteListener(mp->{if(player==mp&&playWhenReady&&!seeking&&!dragFrozen)mp.start();});
            current.setOnErrorListener((mp,what,extra)->{if(player==mp){stop();if(onError!=null)onError.run();}return true;});
            current.prepareAsync();
        }catch(Exception e){stop();if(onError!=null)onError.run();}
    }
    private void releasePlayer(){handler.removeCallbacks(tick);prepared=false;firstFrame=false;if(player!=null){MediaPlayer old=player;player=null;old.setOnPreparedListener(null);old.setOnErrorListener(null);old.setOnInfoListener(null);old.release();}VaultFiles.close(vaultSource);vaultSource=null;if(surface!=null){surface.release();surface=null;}}
    void freezeForGesture(){if(getVisibility()!=VISIBLE)return;dragFrozen=true;playingBeforeDrag=prepared?isPlaying():playWhenReady;if(prepared&&playingBeforeDrag)player.pause();}
    void stop(){handler.removeCallbacks(hideControls);releasePlayer();path="";key="";muted=true;seeking=false;dragFrozen=false;rangeStart=0;rangeEnd=0;setVisibility(GONE);setAlpha(1f);}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){prepare();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){releasePlayer();return true;}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture){}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);setMeasuredDimension(width,height);
        int availableWidth=Math.max(1,width),availableHeight=Math.max(1,height);
        float scale=Math.min((float)availableWidth/videoWidth,(float)availableHeight/videoHeight);
        box.measure(MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(videoWidth*scale)),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,Math.round(videoHeight*scale)),MeasureSpec.EXACTLY));
    }
    @Override protected void onLayout(boolean changed,int l,int t,int r,int b){int x=(getWidth()-box.getMeasuredWidth())/2,y=(getHeight()-box.getMeasuredHeight())/2;box.layout(x,y,x+box.getMeasuredWidth(),y+box.getMeasuredHeight());}
}
