package com.leal.comfyremote;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.media.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import org.json.*;

/** Synthetic fixtures only. Separate package/Keystore UID; never runs against the user's app. */
public final class VaultProbe extends Activity {
    private static final String CANARY="SYNTHETIC_PRIVATE_PROMPT_7ebf42";
    private int checks;
    private File file(String path){return new File(getFilesDir(),path);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    interface Action{void run()throws Exception;}
    private void reject(Action action,String message)throws Exception{boolean failed=false;try{action.run();}catch(Exception expected){failed=true;}check(failed,message);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);String phase=getIntent().getStringExtra("phase");
        new Thread(()->{
            JSONObject result=new JSONObject();
            try{
                result.put("phase",phase);
                switch(phase){
                    case "seed":seed();break;
                    case "seed_interrupt":seedInterrupt();break;
                    case "migrate_interrupt":VaultFiles.prepare(this);break;
                    case "resume_interrupt":resumeInterrupt();break;
                    case "roundtrip":roundtrip();break;
                    case "reopen":reopen();break;
                    case "corrupt_keyset":corruptKeyset();break;
                    case "reject_keyset":rejectKeyset();break;
                    case "lose_key":loseKey();break;
                    case "reject_missing_key":rejectMissingKey();break;
                    default:throw new IllegalArgumentException(phase);
                }
                result.put("passed",true).put("checks",checks);
            }catch(Throwable e){
                try{StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.put("passed",false).put("error",trace.toString());}catch(Exception ignored){}
            }
            try(FileOutputStream out=new FileOutputStream(new File(getCacheDir(),"result-"+phase+".json"))){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        },"vault-probe").start();
    }
    private void raw(File path,byte[] data)throws Exception{path.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(path)){out.write(data);}}
    private byte[] bytes(File path)throws Exception{return Files.readAllBytes(path.toPath());}
    private byte[] pattern(){byte[] bytes=new byte[3*1024*1024+173];new Random(5249).nextBytes(bytes);return bytes;}
    private void seed()throws Exception{
        raw(file("feed.json"),("[{\"prompt\":\""+CANARY+"\"}]").getBytes(StandardCharsets.UTF_8));
        raw(file("references/ref.jpg"),png());
        raw(file("images/image.img"),png());raw(file("images/image.jpg"),png());
        raw(file("recipes/image.json"),("{\"prompt\":\""+CANARY+"\"}").getBytes(StandardCharsets.UTF_8));
        raw(file("workflows/example.json"),("{}").getBytes(StandardCharsets.UTF_8));
        raw(file("images/large.img"),pattern());
        try(InputStream in=getAssets().open("sample.mp4")){raw(file("images/video.mp4"),VaultFiles.readAll(in,1000000));}
        check(getSharedPreferences("com.leal.comfyremote.MainActivity",0).edit().putString("draft",CANARY).putInt("steps",23).putLong("seed",1234567890123L)
            .putFloat("cfg",3.5f).putBoolean("toggle",true).putStringSet("set",new HashSet<>(Arrays.asList("one","two"))).commit(),"legacy preference seed");
    }
    private byte[] png()throws Exception{
        Bitmap b=Bitmap.createBitmap(80,48,Bitmap.Config.ARGB_8888);b.eraseColor(Color.MAGENTA);
        ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();
    }
    private void roundtrip()throws Exception{
        VaultFiles.prepare(this);
        check(VaultFiles.ready(),"ready");
        check(new String(VaultFiles.read(file("feed.json"),10000),StandardCharsets.UTF_8).contains(CANARY),"metadata migration");
        check(!new String(bytes(file("feed.json")),StandardCharsets.ISO_8859_1).contains(CANARY),"no clear prompt on disk");
        check(!new File(getApplicationInfo().dataDir,"shared_prefs/com.leal.comfyremote.MainActivity.xml").exists(),"old preference XML removed");
        check(CANARY.equals(VaultFiles.preferences().getString("draft","")),"encrypted prompt prefs");
        check(VaultFiles.preferences().getInt("steps",0)==23&&VaultFiles.preferences().getLong("seed",0)==1234567890123L,"numeric types");
        check(VaultFiles.preferences().getFloat("cfg",0)==3.5f&&VaultFiles.preferences().getBoolean("toggle",false),"float and boolean");
        Set<String> set=VaultFiles.preferences().getStringSet("set",null);set.clear();check(VaultFiles.preferences().getStringSet("set",null).size()==2,"defensive set copy");
        Bitmap bitmap=VaultFiles.bitmap(file("images/image.img").getPath());check(bitmap!=null&&bitmap.getWidth()==80&&bitmap.getHeight()==48,"encrypted bitmap");
        check(BitmapFactory.decodeFile(file("images/image.img").getPath())==null,"raw ciphertext cannot decode");
        randomAccess();
        media();
        File atomic=file("atomic.json");VaultFiles.write(atomic,CANARY.getBytes(StandardCharsets.UTF_8));
        reject(()->VaultFiles.write(atomic,out->{out.write("partial".getBytes(StandardCharsets.UTF_8));throw new IOException("synthetic interruption");}),"writer interruption");
        check(CANARY.equals(new String(VaultFiles.read(atomic,1000),StandardCharsets.UTF_8)),"atomic previous value retained");
        File swapped=file("swapped.img");raw(swapped,bytes(file("images/image.img")));
        reject(()->VaultFiles.read(swapped,100000),"AAD prevents swapped files");swapped.delete();
        File large=file("images/large.img");byte[] original=bytes(large),bad=original.clone();bad[1024*1024+100]^=1;raw(large,bad);
        reject(()->VaultFiles.read(large,4000000),"tampered segment rejected");
        try(MediaDataSource data=VaultFiles.media(large)){reject(()->data.readAt(1024*1024+150,new byte[100],0,100),"random access rejects tampered segment");}
        raw(large,Arrays.copyOf(original,original.length-11));
        reject(()->VaultFiles.media(large),"truncated final segment rejected before playback");raw(large,original);
        File plain=file("plain.json");raw(plain,CANARY.getBytes(StandardCharsets.UTF_8));
        reject(()->VaultFiles.read(plain,1000),"no plaintext fallback");plain.delete();
        File outside=new File(getCacheDir(),"outside");reject(()->VaultFiles.write(outside,new byte[]{1}),"outside path refused");
        File link=file("link");Files.createSymbolicLink(link.toPath(),getCacheDir().toPath());
        reject(()->VaultFiles.write(new File(link,"secret"),new byte[]{1}),"symlink refused");Files.delete(link.toPath());
        VaultFiles.preferences().edit().putString("ordered","first").apply();
        check(VaultFiles.preferences().edit().putString("ordered","second").commit(),"ordered async and sync writes");
        check("second".equals(new VaultPreferences(file("preferences.vault")).getString("ordered","")),"latest preferences on disk");
        File part=file("images/renamed.mp4.part");try(InputStream in=getAssets().open("sample.mp4")){VaultFiles.write(part,VaultFiles.readAll(in,1000000));}
        File finalFile=file("images/renamed.mp4");check(part.renameTo(finalFile),"atomic video rename");
        try(MediaDataSource data=VaultFiles.media(finalFile)){check(data.getSize()>1000,"video staging retains AAD after rename");}
        GenerationStorageTest.main(new String[]{getFilesDir().getPath()});checks++;
        GenerationRecipe.save(getFilesDir(),"saved",new JSONObject().put("prompt",CANARY));
        check(GenerationRecipe.load(getFilesDir(),"saved").getString("prompt").equals(CANARY),"recipe vault roundtrip");
        byte[] damaged=bytes(file("recipes/saved.json"));damaged[damaged.length-1]^=1;raw(file("recipes/saved.json"),damaged);
        reject(()->GenerationRecipe.load(getFilesDir(),"saved"),"corrupt recipe rejected");GenerationRecipe.delete(getFilesDir(),"saved");
    }
    private void randomAccess()throws Exception{
        byte[] expected=pattern();
        try(MediaDataSource source=VaultFiles.media(file("images/large.img"))){
            check(source.getSize()==expected.length,"plaintext size");
            for(int at:new int[]{0,1,1024*1024-64,1024*1024+13,2*1024*1024-127,expected.length-77,32}){
                byte[] b=new byte[Math.min(257,expected.length-at)];int n=source.readAt(at,b,0,b.length);
                check(n==b.length&&Arrays.equals(b,Arrays.copyOfRange(expected,at,at+n)),"seek at "+at);
            }
            check(source.readAt(expected.length,new byte[1],0,1)==-1,"authenticated EOF");
        }
        for(int size:new int[]{0,1,1048576-56,1048576,1048576+1}){
            File empty=file("boundary");byte[] data=new byte[size];VaultFiles.write(empty,data);
            try(MediaDataSource source=VaultFiles.media(empty)){
                check(source.getSize()==size,"segment boundary size "+size);
                check(source.readAt(size,new byte[1],0,1)==-1,"segment boundary EOF "+size);
            }empty.delete();
        }
    }
    private void media()throws Exception{
        try(MediaDataSource source=VaultFiles.media(file("images/video.mp4"))){
            MediaMetadataRetriever reader=new MediaMetadataRetriever();
            try{reader.setDataSource(source);check("320".equals(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)),"video metadata");check(reader.getFrameAtTime(1000000)!=null,"video frame at 1s");}
            finally{reader.release();}
        }
        try(MediaDataSource source=VaultFiles.media(file("images/video.mp4"))){
            MediaExtractor extractor=new MediaExtractor();
            try{extractor.setDataSource(source);check(extractor.getTrackCount()==1,"extractor tracks");extractor.selectTrack(0);extractor.seekTo(1000000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);check(extractor.readSampleData(java.nio.ByteBuffer.allocate(100000),0)>0,"extractor seek sample");}
            finally{extractor.release();}
        }
        try(MediaDataSource source=VaultFiles.media(file("images/video.mp4"))){
            MediaPlayer player=new MediaPlayer();
            try{player.setDataSource(source);player.setVolume(0,0);player.prepare();check(player.getDuration()>=1900,"player prepare");player.start();Thread.sleep(200);check(player.isPlaying(),"player autoplay");player.seekTo(1500);Thread.sleep(250);check(player.getCurrentPosition()>=1300,"player seek");}
            finally{player.release();}
        }
    }
    private void reopen()throws Exception{VaultFiles.prepare(this);check("second".equals(VaultFiles.preferences().getString("ordered","")),"prefs after process restart");check(Arrays.equals(pattern(),VaultFiles.read(file("images/large.img"),4000000)),"media after process restart");media();}
    private File keyset(){return new File(getNoBackupFilesDir(),"media-vault.keyset");}
    private void corruptKeyset()throws Exception{byte[] original=bytes(keyset());raw(new File(getCacheDir(),"keyset-save"),original);original[original.length/2]^=1;raw(keyset(),original);}
    private void rejectKeyset()throws Exception{
        byte[] before=bytes(file("images/large.img")),keyBefore=bytes(keyset());
        reject(()->VaultFiles.prepare(this),"corrupt keyset blocked");check(!VaultFiles.ready(),"not ready");
        check(Arrays.equals(before,bytes(file("images/large.img")))&&Arrays.equals(keyBefore,bytes(keyset())),"no data or key replacement");
        raw(keyset(),bytes(new File(getCacheDir(),"keyset-save")));
    }
    private void loseKey()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);store.deleteEntry(VaultFiles.ALIAS);}
    private void rejectMissingKey()throws Exception{byte[] before=bytes(file("images/large.img"));reject(()->VaultFiles.prepare(this),"missing key blocked");check(Arrays.equals(before,bytes(file("images/large.img"))),"lost key preserves ciphertext");}
    private void seedInterrupt()throws Exception{
        seed();byte[] block=new byte[262144];new Random(72).nextBytes(block);
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");
        try(OutputStream out=new FileOutputStream(file("images/interruption.img"))){for(int i=0;i<512;i++){out.write(block);digest.update(block);}}
        raw(new File(getCacheDir(),"migration-expected.sha256"),digest.digest());
    }
    private void resumeInterrupt()throws Exception{
        check(file("migration-plan.vault").exists(),"sealed journal survived interruption");
        check(file("images/interruption.img.new").exists(),"interrupted encrypted staging file present");
        VaultFiles.prepare(this);
        java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long size=0;
        try(InputStream in=VaultFiles.open(file("images/interruption.img"))){byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1){digest.update(b,0,n);size+=n;}}
        check(size==128L*1024*1024&&Arrays.equals(digest.digest(),bytes(new File(getCacheDir(),"migration-expected.sha256"))),"all 128MB survived interrupted migration");
        check(!file("images/interruption.img.new").exists()&&!file("migration-plan.vault").exists(),"staging and journal retired");
        check(CANARY.equals(VaultFiles.preferences().getString("draft","")),"draft retained through interruption");
    }
}
