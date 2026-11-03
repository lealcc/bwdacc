package com.leal.comfyremote;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Viewer posters come from the actual clip, independently of cropped gallery thumbnails. */
final class VideoPreviewCache {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService decoder=Executors.newSingleThreadExecutor();
    private final Set<String> loading=new HashSet<>(),failed=new HashSet<>();
    private final LruCache<String,Bitmap> frames=new LruCache<String,Bitmap>(24*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return Math.max(1,bitmap.getByteCount()/1024);}
    };
    private boolean closed;

    Bitmap get(File file,Runnable ready){
        if(closed||!file.isFile())return null;
        String key=file.getAbsolutePath()+":"+file.lastModified()+":"+file.length();
        Bitmap cached=frames.get(key);if(cached!=null)return cached;
        if(loading.contains(key)||failed.contains(key))return null;
        loading.add(key);
        decoder.execute(()->{
            Bitmap decoded=null;MediaMetadataRetriever reader=new MediaMetadataRetriever();
            android.media.MediaDataSource vaultSource=null;
            try{
                vaultSource=VaultFiles.media(file);reader.setDataSource(vaultSource);
                decoded=android.os.Build.VERSION.SDK_INT>=27
                    ?reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,1080,1080)
                    :reader.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                if(decoded!=null&&android.os.Build.VERSION.SDK_INT<27){
                    float scale=Math.min(1f,Math.min(1080f/decoded.getWidth(),1080f/decoded.getHeight()));
                    if(scale<1){Bitmap smaller=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true);decoded.recycle();decoded=smaller;}
                }
            }catch(Exception ignored){}finally{try{reader.release();}catch(Exception ignored){}VaultFiles.close(vaultSource);}
            final Bitmap result=decoded;
            main.post(()->{
                loading.remove(key);
                if(closed){if(result!=null)result.recycle();return;}
                if(result==null){failed.add(key);return;}
                frames.put(key,result);ready.run();
            });
        });
        return null;
    }
    void close(){closed=true;decoder.shutdownNow();frames.evictAll();loading.clear();failed.clear();}
}
