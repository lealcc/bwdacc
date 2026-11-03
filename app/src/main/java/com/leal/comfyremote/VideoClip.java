package com.leal.comfyremote;

import android.media.MediaMetadataRetriever;
import java.io.File;

final class VideoClip {
    final int width,height;
    final long millis;
    final boolean audio;
    final String mime;
    private VideoClip(int width,int height,long millis,boolean audio,String mime){this.width=width;this.height=height;this.millis=millis;this.audio=audio;this.mime=mime;}
    static VideoClip read(File file)throws Exception{
        return read(file,true);
    }
    static VideoClip read(File file,boolean editing)throws Exception{
        if(!file.isFile())throw new IllegalArgumentException(UiLanguage.t("Clipe original não está salvo neste aparelho."));
        MediaMetadataRetriever reader=new MediaMetadataRetriever();
        android.media.MediaDataSource vaultSource=null;
        try{
            vaultSource=VaultFiles.media(file);reader.setDataSource(vaultSource);
            int width=Integer.parseInt(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int height=Integer.parseInt(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            long millis=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            String rotation=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if("90".equals(rotation)||"270".equals(rotation)){int swap=width;width=height;height=swap;}
            if(width<=0||height<=0)throw new IllegalArgumentException(UiLanguage.t("Clipe de entrada inválido."));
            if(editing)VideoEditWorkflow.frames(millis,0);
            String mime=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            return new VideoClip(width,height,millis,"yes".equals(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)),mime==null?"video/mp4":mime);
        }finally{reader.release();VaultFiles.close(vaultSource);}
    }
}
