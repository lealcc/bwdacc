package com.leal.comfyremote;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.media.*;
import com.google.crypto.tink.*;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

/** Separate synthetic source/target UIDs. Keys/codes in this harness are disposable fixtures. */
public final class BackupProbe extends Activity {
    private static final String CANARY="SYNTHETIC_BACKUP_SECRET_98aaf1";
    private int checks;
    interface Action{void run()throws Exception;}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private void reject(Action action,String why)throws Exception{boolean rejected=false;try{action.run();}catch(Exception e){rejected=true;}check(rejected,why);}
    private File f(String name){return new File(getFilesDir(),name);}
    private File c(String name){return new File(getCacheDir(),name);}
    private byte[] read(File f)throws Exception{return Files.readAllBytes(f.toPath());}
    private void raw(File file,byte[] bytes)throws Exception{file.getParentFile().mkdirs();try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}}
    private byte[] text(String value){return value.getBytes(StandardCharsets.UTF_8);}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);String phase=getIntent().getStringExtra("phase");
        new Thread(()->{
            JSONObject result=new JSONObject();
            try{
                result.put("phase",phase);switch(phase){
                    case "export":export(result);break;
                    case "negative":negative();break;
                    case "restore":restore();break;
                    case "reopen":reopen();break;
                    case "occupied":occupied();break;
                    case "fixture_before":fixture(0);break;
                    case "fixture_between":fixture(1);break;
                    case "fixture_after":fixture(2);break;
                    case "fixture_corrupt":fixture(3);break;
                    case "recover":recover();break;
                    case "reject_corrupt":rejectCorrupt();break;
                    default:throw new IllegalArgumentException(phase);
                }result.put("passed",true).put("checks",checks);
            }catch(Throwable e){try{StringWriter trace=new StringWriter();e.printStackTrace(new PrintWriter(trace));result.put("passed",false).put("error",trace.toString());}catch(Exception ignored){}}
            try{raw(c("result-"+phase+".json"),text(result.toString()));}catch(Exception ignored){}
        },"backup-probe").start();
    }
    private byte[] key()throws Exception{return VaultBackup.parseCode(new String(read(c("code.txt")),StandardCharsets.UTF_8));}
    private InputStream backup()throws Exception{return new FileInputStream(c("input.bwdvault"));}
    private byte[] png(){Bitmap b=Bitmap.createBitmap(80,48,Bitmap.Config.ARGB_8888);b.eraseColor(Color.CYAN);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();}
    private void export(JSONObject result)throws Exception{
        VaultFiles.prepare(this);
        VaultFiles.write(f("images/image.img"),png());VaultFiles.write(f("images/image.jpg"),png());
        VaultFiles.write(f("references/abcd.jpg"),png());VaultFiles.write(f("project-covers/abcd.jpg"),png());
        VaultFiles.write(f("covers/example/abcd.jpg"),png());
        VaultFiles.write(f("feed.json"),text("[{\"id\":\"image\",\"state\":\"done\",\"prompt\":\""+CANARY+"\"}]"));
        VaultFiles.write(f("recipes/image.json"),text("{\"prompt\":\""+CANARY+"\"}"));
        VaultFiles.write(f("workflow.json"),text("{}"));
        try(InputStream in=getAssets().open("sample.mp4")){VaultFiles.write(f("images/video.mp4"),VaultFiles.readAll(in,1_000_000));}
        VaultFiles.write(f("images/large.img"),out->{byte[] block=new byte[65536];new Random(782).nextBytes(block);for(int i=0;i<64;i++)out.write(block);});
        VaultFiles.write(f("images/excluded.mp4.part"),text("unfinished"));
        VaultFiles.write(f("images/interrupted.img"),png());
        Files.move(f("images/interrupted.img").toPath(),f("images/interrupted.img.bak").toPath());
        VaultFiles.preferences().edit().putString("draft_example",CANARY).putString("reference_library_v1","[{\"name\":\"synthetic reference\"}]").apply();
        raw(new File(getNoBackupFilesDir(),"desktop-secret"),text("SYNTHETIC_CREDENTIAL_NEVER_EXPORTED"));
        byte[] key=VaultBackup.newRecoveryKey();String code=VaultBackup.recoveryCode(key);
        check(Arrays.equals(key,VaultBackup.parseCode(code.toLowerCase(Locale.ROOT).replace("-"," \n"))),"code normalization");
        reject(()->VaultBackup.parseCode(code.substring(0,code.length()-1)+(code.endsWith("0")?"1":"0")),"checksum rejects typo");
        File archive=VaultBackup.export(this,key,null);
        check(f("images/interrupted.img").exists()&&!f("images/interrupted.img.bak").exists(),"interrupted AtomicFile recovered before snapshot");
        check(!new String(read(archive),StandardCharsets.ISO_8859_1).contains(CANARY),"backup conceals prompt");
        try(InputStream in=new FileInputStream(archive)){VaultBackup.verify(in,key,null);}checks++;
        Files.copy(archive.toPath(),c("output.bwdvault").toPath(),StandardCopyOption.REPLACE_EXISTING);
        raw(c("code.txt"),text(code));raw(c("source-image.sha256"),text(hash(read(f("images/image.img")))));
        result.put("bytes",archive.length());archive.delete();Arrays.fill(key,(byte)0);
    }
    private void negative()throws Exception{
        VaultFiles.prepare(this);byte[] key=key(),original=read(c("input.bwdvault")),before=read(f("preferences.vault"));
        try(InputStream in=backup()){reject(()->VaultBackup.restore(this,in,VaultBackup.newRecoveryKey(),null),"wrong recovery code");}
        byte[] bad=original.clone();bad[bad.length-5]^=1;
        reject(()->VaultBackup.restore(this,new ByteArrayInputStream(bad),key,null),"tampered final segment");
        reject(()->VaultBackup.restore(this,new ByteArrayInputStream(Arrays.copyOf(original,original.length-17)),key,null),"truncated backup");
        byte[] trailing=Arrays.copyOf(original,original.length+1);trailing[trailing.length-1]=42;
        reject(()->VaultBackup.verify(new ByteArrayInputStream(trailing),key,null),"trailing bytes");
        try(InputStream source=backup()){
            InputStream interrupted=new FilterInputStream(source){long n;public int read(byte[] b,int o,int l)throws IOException{if(n>100000)throw new IOException("synthetic read failure");int count=super.read(b,o,l);n+=Math.max(0,count);return count;}};
            reject(()->VaultBackup.restore(this,interrupted,key,null),"failed provider read");
        }
        for(String path:new String[]{"../escape","/absolute","images/../escape","images\\escape","preferences.vault/child","no_backup/desktop-secret"})
            reject(()->VaultBackup.restore(this,new ByteArrayInputStream(craft(key,path,false,false)),key,null),"reject path "+path);
        reject(()->VaultBackup.restore(this,new ByteArrayInputStream(craft(key,"images/a.img",true,false)),key,null),"duplicate paths");
        reject(()->VaultBackup.restore(this,new ByteArrayInputStream(craft(key,"images/a.img",false,true)),key,null),"oversized chunk");
        check(Arrays.equals(before,read(f("preferences.vault"))),"failed restores preserve current settings");
        check(!new File(getNoBackupFilesDir(),"restore.pending").exists(),"failed restore has no committed journal");
        check(!new File(getNoBackupFilesDir(),"restore-stage").exists(),"failed restore removes encrypted staging");
        check(!VaultMaintenance.restartRequired(),"failed restore allows retry");
        Arrays.fill(key,(byte)0);
    }
    private byte[] craft(byte[] key,String path,boolean duplicate,boolean oversized)throws Exception{
        java.lang.reflect.Method method=VaultBackup.class.getDeclaredMethod("archiveCipher",byte[].class);method.setAccessible(true);
        StreamingAead cipher=(StreamingAead)method.invoke(null,(Object)key);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] magic=text("BWDBACKUP1\n");bytes.write(magic);
        try(DataOutputStream out=new DataOutputStream(cipher.newEncryptingStream(bytes,magic))){
            out.writeInt(1);for(int i=0;i<(duplicate?2:1);i++){byte[] name=text(path);out.writeInt(name.length);out.write(name);out.writeInt(oversized?Integer.MAX_VALUE:0);}
            out.writeInt(0);out.writeInt(duplicate?2:1);
        }return bytes.toByteArray();
    }
    private void restore()throws Exception{
        VaultFiles.prepare(this);byte[] before=read(new File(getNoBackupFilesDir(),"media-vault.keyset"));
        byte[] key=key();try(InputStream in=backup()){VaultBackup.restore(this,in,key,null);}finally{Arrays.fill(key,(byte)0);}
        check(VaultMaintenance.restartRequired(),"successful restore requires restart");
        check(Arrays.equals(before,read(new File(getNoBackupFilesDir(),"media-vault.keyset"))),"destination keyset unchanged");
        reject(()->VaultFiles.write(f("feed.json"),text("stale write")),"old process cannot overwrite restored library");
        check(!VaultFiles.delete(f("feed.json")),"old process cannot delete restored library");
    }
    private void reopen()throws Exception{
        VaultFiles.prepare(this);
        check(CANARY.equals(VaultFiles.preferences().getString("draft_example","")),"prompt recovered in fresh process");
        check(new String(VaultFiles.read(f("feed.json"),10000),StandardCharsets.UTF_8).contains(CANARY),"feed metadata restored");
        Bitmap bitmap=VaultFiles.bitmap(f("images/image.img").getPath());check(bitmap!=null&&bitmap.getWidth()==80,"restored image decodes");
        check(!hash(read(f("images/image.img"))).equals(new String(read(c("source-image.sha256")),StandardCharsets.UTF_8)),"destination reencrypts with different device key");
        check(!f("images/excluded.mp4.part").exists(),"incomplete download excluded");
        check(VaultFiles.bitmap(f("images/interrupted.img").getPath())!=null,"committed AtomicFile backup included");
        check(!new File(getNoBackupFilesDir(),"desktop-secret").exists(),"device credential not copied");
        try(MediaDataSource data=VaultFiles.media(f("images/video.mp4"))){MediaMetadataRetriever reader=new MediaMetadataRetriever();try{reader.setDataSource(data);check(reader.getFrameAtTime(1000000)!=null,"restored video seek");}finally{reader.release();}}
        try(InputStream in=VaultFiles.open(f("images/large.img"))){long size=0;byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)size+=n;check(size==4*1024*1024,"large file complete");}
        check(!new File(getNoBackupFilesDir(),"restore.pending").exists()&&!new File(getNoBackupFilesDir(),"restore-stage").exists(),"transaction cleaned");
    }
    private void occupied()throws Exception{
        VaultFiles.prepare(this);byte[] before=read(f("feed.json"));byte[] key=key();
        try(InputStream in=backup()){reject(()->VaultBackup.restore(this,in,key,null),"existing library not replaced");}
        check(Arrays.equals(before,read(f("feed.json"))),"existing library unchanged");
    }
    // Build exact durable crash states with only synthetic data; no production fault hooks.
    private void fixture(int step)throws Exception{
        VaultFiles.prepare(this);File home=new File(getNoBackupFilesDir(),"restore-stage"),stage=new File(home,"data");stage.mkdirs();
        for(String name:new String[]{"preferences.vault","migration.vault"}){
            byte[] bytes=VaultFiles.read(f(name),16_000_000);VaultFiles.writeStaged(new File(stage,name),name,out->out.write(bytes));
        }
        VaultFiles.writeStaged(new File(stage,"images/crash.img"),"images/crash.img",out->out.write(png()));
        JSONObject files=new JSONObject();for(String path:new String[]{"preferences.vault","migration.vault","images/crash.img"})files.put(path,hash(read(new File(stage,path))));
        JSONObject plan=new JSONObject().put("version",1).put("files",files);
        Aead master=new AndroidKeystoreKmsClient().getAead("android-keystore://"+VaultFiles.ALIAS);
        raw(new File(getNoBackupFilesDir(),"restore.pending"),master.encrypt(text(plan.toString()),text(getPackageName()+":restore:v1")));
        File root=getFilesDir();
        if(step==1||step==2)Files.move(root.toPath(),new File(home,"previous").toPath(),StandardCopyOption.ATOMIC_MOVE);
        if(step==2)Files.move(stage.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);
        if(step==3){File image=new File(stage,"images/crash.img");byte[] changed=read(image);changed[changed.length-1]^=1;raw(image,changed);}
        checks++;
    }
    private void recover()throws Exception{VaultFiles.prepare(this);check(VaultFiles.bitmap(f("images/crash.img").getPath())!=null,"recovered image");check(!new File(getNoBackupFilesDir(),"restore.pending").exists(),"journal retired");}
    private void rejectCorrupt()throws Exception{byte[] before=read(f("preferences.vault"));reject(()->VaultFiles.prepare(this),"changed committed stage fails closed");check(Arrays.equals(before,read(f("preferences.vault"))),"existing root preserved");check(new File(getNoBackupFilesDir(),"restore.pending").exists(),"journal retained for diagnosis");}
    private String hash(byte[] bytes)throws Exception{StringBuilder s=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
}
