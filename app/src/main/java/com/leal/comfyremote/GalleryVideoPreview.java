package com.leal.comfyremote;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.view.*;
import java.io.File;

/** A silent, viewport-owned decoder; a poster remains underneath until the first frame. */
final class GalleryVideoPreview extends TextureView implements TextureView.SurfaceTextureListener {
    private final File file;private MediaPlayer player;private Surface surface;private boolean active,failed,hasFrame;
    private SoftwareVideoPreview software;
    private android.media.MediaDataSource vaultSource;
    private int session;
    private final Runnable checkFrame=()->{if(active&&!hasFrame&&player!=null)startSoftware();};
    GalleryVideoPreview(Context context,File file){super(context);this.file=file;setSurfaceTextureListener(this);setAlpha(0f);setContentDescription(UiLanguage.t("Prévia de vídeo sem som"));}
    void active(boolean value){if(!value)failed=false;active=value;if(value)start();else release();}
    private void start(){
        if(!active||failed||player!=null||software!=null||!isAvailable()||!file.isFile())return;
        hasFrame=false;
        MediaPlayer current=new MediaPlayer();player=current;
        try{surface=new Surface(getSurfaceTexture());current.setSurface(surface);vaultSource=VaultFiles.media(file);current.setDataSource(vaultSource);current.setVolume(0,0);current.setLooping(true);
            current.setOnPreparedListener(mp->{if(mp==player&&active)mp.start();});
            current.setOnInfoListener((mp,what,extra)->{if(mp==player&&active&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){hasFrame=true;removeCallbacks(checkFrame);setAlpha(1f);}return false;});
            current.setOnErrorListener((mp,what,extra)->{if(mp==player)post(()->{if(mp==player)startSoftware();});return true;});current.prepareAsync();
            postDelayed(checkFrame,2500);
        }catch(Exception e){startSoftware();}
    }
    private void releasePlayer(){if(player!=null){MediaPlayer old=player;player=null;old.setOnPreparedListener(null);old.setOnErrorListener(null);old.setOnInfoListener(null);old.release();}VaultFiles.close(vaultSource);vaultSource=null;}
    private void startSoftware(){
        removeCallbacks(checkFrame);releasePlayer();if(!active||software!=null||surface==null||!surface.isValid())return;
        final int token=++session;
        software=new SoftwareVideoPreview(file,surface,()->{if(token==session&&active){hasFrame=true;setAlpha(1f);}},()->{if(token==session){failed=true;release();}});
    }
    private void release(){session++;removeCallbacks(checkFrame);setAlpha(0f);hasFrame=false;releasePlayer();if(software!=null){software.close();software=null;}if(surface!=null){surface.release();surface=null;}}
    @Override protected void onDetachedFromWindow(){active=false;release();super.onDetachedFromWindow();}
    public void onSurfaceTextureAvailable(SurfaceTexture texture,int width,int height){start();}
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture){release();return true;}
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int width,int height){}
    public void onSurfaceTextureUpdated(SurfaceTexture texture){}
}
