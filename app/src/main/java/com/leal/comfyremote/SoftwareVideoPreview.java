package com.leal.comfyremote;

import android.media.*;
import android.os.*;
import android.view.Surface;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Locale;

/** Video-only fallback when simultaneous hardware players run out of decoder instances. */
final class SoftwareVideoPreview implements Runnable {
    private final File file;
    private final Surface surface;
    private final Runnable firstFrame,failure;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Thread thread;
    private volatile boolean canceled;
    private boolean reported;
    SoftwareVideoPreview(File file,Surface surface,Runnable firstFrame,Runnable failure){
        this.file=file;this.surface=surface;this.firstFrame=firstFrame;this.failure=failure;
        thread=new Thread(this,"gallery-software-video");thread.start();
    }
    void close(){canceled=true;thread.interrupt();}
    @Override public void run(){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        android.media.MediaDataSource vaultSource=null;
        try{
            vaultSource=VaultFiles.media(file);extractor.setDataSource(vaultSource);MediaFormat format=null;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat candidate=extractor.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("video/")){format=candidate;extractor.selectTrack(i);break;}
            }
            if(format==null)throw new IllegalArgumentException(UiLanguage.t("No video track"));
            String mime=format.getString(MediaFormat.KEY_MIME);
            for(MediaCodecInfo info:new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()){
                if(canceled)return;
                String name=info.getName().toLowerCase(Locale.ROOT);
                boolean software=Build.VERSION.SDK_INT>=29?info.isSoftwareOnly():name.startsWith("omx.google.")||name.startsWith("c2.android.");
                if(info.isEncoder()||!software)continue;
                boolean supported=false;for(String type:info.getSupportedTypes())if(type.equalsIgnoreCase(mime))supported=true;
                if(!supported)continue;
                try{
                    codec=MediaCodec.createByCodecName(info.getName());codec.configure(format,surface,null,0);
                    codec.setOnFrameRenderedListener((c,time,nanos)->{if(!canceled&&!reported){reported=true;firstFrame.run();}},main);
                    codec.start();break;
                }catch(Exception unavailable){if(codec!=null)try{codec.release();}catch(Exception ignored){}codec=null;}
            }
            if(codec==null)throw new IllegalStateException(UiLanguage.t("No software decoder available"));
            decode(extractor,codec);
        }catch(Exception error){if(!canceled)main.post(()->{if(!canceled)failure.run();});}
        finally{
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}try{codec.release();}catch(Exception ignored){}}
            extractor.release();VaultFiles.close(vaultSource);
        }
    }
    private void decode(MediaExtractor extractor,MediaCodec codec)throws Exception{
        MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();boolean inputEnded=false;long clock=Long.MIN_VALUE;
        while(!canceled){
            if(!inputEnded){
                int input=codec.dequeueInputBuffer(10000);
                if(input>=0){ByteBuffer buffer=codec.getInputBuffer(input);buffer.clear();int size=extractor.readSampleData(buffer,0);
                    if(size<0){codec.queueInputBuffer(input,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true;}
                    else{codec.queueInputBuffer(input,0,size,extractor.getSampleTime(),0);extractor.advance();}
                }
            }
            int output=codec.dequeueOutputBuffer(info,10000);
            if(output<0)continue;
            if(info.size>0){
                if(clock==Long.MIN_VALUE)clock=System.nanoTime()-info.presentationTimeUs*1000;
                long target=clock+info.presentationTimeUs*1000,wait;
                while(!canceled&&(wait=target-System.nanoTime())>0)Thread.sleep(Math.min(20,Math.max(1,wait/1000000)));
                codec.releaseOutputBuffer(output,!canceled&&System.nanoTime()-target<150000000L);
            }else codec.releaseOutputBuffer(output,false);
            if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0){extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);codec.flush();inputEnded=false;clock=Long.MIN_VALUE;}
        }
    }
}
