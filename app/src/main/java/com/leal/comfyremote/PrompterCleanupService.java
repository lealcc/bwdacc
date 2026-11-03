package com.leal.comfyremote;
import android.app.job.*;
import android.content.*;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;
import java.util.*;
import org.json.*;

/** One local, periodic cleanup job. The Android scheduler may defer it while idle. */
public final class PrompterCleanupService extends JobService {
    private volatile boolean canceled;
    static final Object LOCK=new Object();
    static WeakReference<MainActivity> activity=new WeakReference<>(null);
    static void schedule(Context context){
        JobScheduler jobs=(JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        jobs.schedule(new JobInfo.Builder(2401,new ComponentName(context,PrompterCleanupService.class)).setPeriodic(60*60*1000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters params){
        canceled=false;
        if(!VaultFiles.ready()){
            new Thread(()->{
                try{VaultFiles.prepare(this);new android.os.Handler(getMainLooper()).post(()->{if(!canceled){cleanup();jobFinished(params,false);}});}
                catch(Exception e){if(!canceled)jobFinished(params,true);}
            },"vault-cleanup").start();
            return true;
        }
        cleanup();return false;
    }
    private void cleanup(){
        MainActivity current=activity.get();if(current!=null&&!current.isFinishing()){if(current.libraryReady())current.cleanupExpiredTrials();return;}
        // Main-thread serialization prevents an Activity from loading stale feed data during cleanup.
        synchronized(LOCK){
            File file=new File(getFilesDir(),"feed.json");
            try{
                byte[] data=VaultFiles.read(file,8_000_000);
                JSONArray feed=new JSONArray(new String(data,StandardCharsets.UTF_8));Set<String> expired=TrialPolicy.expiredIds(feed,System.currentTimeMillis(),new HashSet<>());
                if(expired.isEmpty())return;JSONArray kept=new JSONArray();for(int i=0;i<feed.length();i++)if(!expired.contains(feed.getJSONObject(i).optString("id")))kept.put(feed.getJSONObject(i));
                VaultFiles.write(file,kept.toString().getBytes(StandardCharsets.UTF_8));
                for(String id:expired)GenerationRecipe.delete(getFilesDir(),id);
                for(String id:expired)for(String extension:new String[]{".img",".jpg",".mp4",".mp4.part"})VaultFiles.delete(new File(new File(getFilesDir(),"images"),id+extension));
            }catch(Exception ignored){}
        }
    }
    @Override public boolean onStopJob(JobParameters params){canceled=true;return true;}
}
