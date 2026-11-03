package com.leal.comfyremote;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaDataSource;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import com.google.crypto.tink.*;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;
import com.google.crypto.tink.streamingaead.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.crypto.KeyGenerator;
import org.json.*;

/**
 * Device-bound private storage. Tink authenticates each segment before releasing bytes.
 * Files retain opaque IDs; AAD binds their relative path. No plaintext disk cache.
 * Initialization/migration runs off the UI thread before any library access.
 */
final class VaultFiles {
    static final String ALIAS="bwdacc.media-vault.v1";
    private static final byte[] MAGIC="BWDVAULT1\n".getBytes(StandardCharsets.US_ASCII);
    private static File root;
    private static StreamingAead cipher;
    private static volatile boolean ready;
    private static VaultPreferences preferences;
    static boolean ready(){return ready;}
    static void close(java.io.Closeable value){if(value!=null)try{value.close();}catch(IOException ignored){}}
    static VaultPreferences preferences(){if(!ready)throw new IllegalStateException(UiLanguage.t("Cofre indisponível"));return preferences;}

    static synchronized void prepare(Context context)throws Exception{
        if(ready)return;
        Context app=context.getApplicationContext();
        root=app.getFilesDir().getCanonicalFile();
        StreamingAeadConfig.register();
        AtomicFile keyFile=new AtomicFile(new File(app.getNoBackupFilesDir(),"media-vault.keyset"));
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        byte[] keyAad=(app.getPackageName()+":media-vault:keyset:v1").getBytes(StandardCharsets.UTF_8);
        KeysetHandle handle;
        boolean exists=keyFile.getBaseFile().exists()||new File(keyFile.getBaseFile()+".bak").exists();
        if(exists){
            if(!store.containsAlias(ALIAS))throw new IOException(UiLanguage.t("Chave do cofre indisponível. Arquivos preservados."));
            try(InputStream in=keyFile.openRead()){
                handle=TinkProtoKeysetFormat.parseEncryptedKeyset(readAll(in,65536),
                    new AndroidKeystoreKmsClient().getAead("android-keystore://"+ALIAS),keyAad);
            }
        }else{
            // Never silently generate a replacement key for an existing encrypted library.
            if(store.containsAlias(ALIAS)||containsEncrypted(root))throw new IOException(UiLanguage.t("Chave do cofre ausente. Arquivos preservados."));
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
            handle=KeysetHandle.generateNew(PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB);
            byte[] sealed=TinkProtoKeysetFormat.serializeEncryptedKeyset(handle,
                new AndroidKeystoreKmsClient().getAead("android-keystore://"+ALIAS),keyAad);
            rawAtomicWrite(keyFile,sealed);
        }
        cipher=handle.getPrimitive(StreamingAead.class);
        VaultBackup.recoverRestore(app);
        migrate(app);
        preferences=new VaultPreferences(new File(root,"preferences.vault"));
        ready=true;
    }

    private static boolean containsEncrypted(File folder)throws IOException{
        File[] files=folder.listFiles();if(files==null)return false;
        for(File f:files){if(Files.isSymbolicLink(f.toPath()))throw new IOException(UiLanguage.t("Link recusado no cofre"));
            if(f.isDirectory()){if(containsEncrypted(f))return true;}
            else if(f.getName().endsWith(".vault")||encrypted(f))return true;
        }return false;
    }
    private static byte[] aad(File file)throws IOException{
        if(VaultMaintenance.restartRequired())throw new IOException(UiLanguage.t("Reabra o app para concluir a restauração."));
        if(root==null||cipher==null)throw new IOException(UiLanguage.t("Cofre indisponível"));
        Path path=file.getCanonicalFile().toPath(),base=root.toPath();
        if(!path.startsWith(base)||path.equals(base))throw new IOException(UiLanguage.t("Caminho fora do cofre"));
        // Android legitimately aliases /data/data and /data/user/0. Allow that container alias,
        // but reject every symlink below this app's files directory (including inward links).
        for(File p=file.getAbsoluteFile();p!=null&&!p.getCanonicalFile().equals(root);p=p.getParentFile())
            if(Files.isSymbolicLink(p.toPath()))throw new IOException(UiLanguage.t("Link recusado no cofre"));
        String relative=base.relativize(path).toString().replace(File.separatorChar,'/');
        // A downloaded video is validated under .part before its final atomic rename.
        if(relative.endsWith(".mp4.part"))relative=relative.substring(0,relative.length()-5);
        return ("bwdacc.media.v1:"+relative).getBytes(StandardCharsets.UTF_8);
    }
    private static boolean encrypted(File file)throws IOException{
        try(InputStream in=new FileInputStream(file)){return Arrays.equals(MAGIC,readPrefix(in));}
    }
    private static byte[] readPrefix(InputStream in)throws IOException{
        byte[] prefix=new byte[MAGIC.length];int total=0,n;
        while(total<prefix.length&&(n=in.read(prefix,total,prefix.length-total))>0)total+=n;
        return total==prefix.length?prefix:new byte[0];
    }
    static InputStream open(File file)throws IOException{
        byte[] data=aad(file);InputStream raw=new AtomicFile(file).openRead();
        try{
            if(!Arrays.equals(MAGIC,readPrefix(raw)))throw new IOException(UiLanguage.t("Arquivo do cofre inválido"));
            return cipher.newDecryptingStream(raw,data);
        }catch(Exception e){raw.close();throw io(e);}
    }
    static byte[] read(File file,int limit)throws IOException{try(InputStream in=open(file)){return readAll(in,limit);}}
    static byte[] readAll(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[65536];int n;
        while((n=in.read(buffer))!=-1){if(n>limit-out.size())throw new IOException(UiLanguage.t("Arquivo muito grande"));out.write(buffer,0,n);}return out.toByteArray();
    }
    interface Writer{void write(OutputStream stream)throws Exception;}
    static void write(File file,byte[] bytes)throws IOException{try{write(file,out->out.write(bytes));}catch(Exception e){throw io(e);}}
    static void write(File file,Writer writer)throws Exception{
        try(Write stream=output(file)){
            try{writer.write(stream);}catch(Exception e){stream.abort();throw e;}
        }
    }
    static Write output(File file)throws IOException{return new Write(file);}
    static boolean delete(File file){
        try{VaultMaintenance.beginWrite();}catch(IOException e){return false;}
        try{aad(file);new AtomicFile(file).delete();return !file.exists()&&!new File(file+".bak").exists()&&!new File(file+".new").exists();}
        catch(IOException e){return false;}finally{VaultMaintenance.endWrite();}
    }
    static boolean renameVideo(File from,File to){
        try{VaultMaintenance.beginWrite();}catch(IOException e){return false;}
        try{if(!Arrays.equals(aad(from),aad(to)))return false;return from.renameTo(to);}
        catch(IOException e){return false;}finally{VaultMaintenance.endWrite();}
    }
    // Staging lives outside filesDir, but authenticates the eventual logical file name.
    // Only VaultBackup calls these after validating names and the private staging root.
    static void writeStaged(File staged,String relative,Writer writer)throws Exception{
        try(Write stream=new Write(staged,aad(new File(root,relative)))){
            try{writer.write(stream);}catch(Exception e){stream.abort();throw e;}
        }
    }
    static Write stagedOutput(File staged,String relative)throws IOException{return new Write(staged,aad(new File(root,relative)));}
    static InputStream openStaged(File staged,String relative)throws IOException{
        InputStream raw=new FileInputStream(staged);
        try{if(!Arrays.equals(MAGIC,readPrefix(raw)))throw new IOException(UiLanguage.t("Arquivo inválido"));return cipher.newDecryptingStream(raw,aad(new File(root,relative)));}
        catch(Exception e){raw.close();throw io(e);}
    }
    static final class Write extends OutputStream{
        private final AtomicFile target;
        private final FileOutputStream raw;
        private final OutputStream encrypted;
        private boolean failed,closed;
        Write(File file)throws IOException{this(file,aad(file));}
        Write(File file,byte[] data)throws IOException{
            VaultMaintenance.beginWrite();
            AtomicFile pending=null;FileOutputStream opened=null;
            try{
                File parent=file.getParentFile();
                if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException(UiLanguage.t("Não foi possível criar pasta do cofre"));
                pending=new AtomicFile(file);opened=pending.startWrite();target=pending;raw=opened;
                raw.write(MAGIC);
                // Closing Tink finalizes the authenticated last segment; AtomicFile owns the FD.
                encrypted=cipher.newEncryptingStream(new FilterOutputStream(raw){
                    @Override public void write(byte[] b,int off,int len)throws IOException{raw.write(b,off,len);}
                    @Override public void close()throws IOException{flush();}
                },data);
            }catch(Exception e){if(pending!=null&&opened!=null)pending.failWrite(opened);VaultMaintenance.endWrite();throw io(e);}
        }
        @Override public void write(int b)throws IOException{try{encrypted.write(b);}catch(IOException e){failed=true;throw e;}}
        @Override public void write(byte[] b,int off,int len)throws IOException{try{encrypted.write(b,off,len);}catch(IOException e){failed=true;throw e;}}
        @Override public void flush()throws IOException{try{encrypted.flush();}catch(IOException e){failed=true;throw e;}}
        void abort(){failed=true;}
        @Override public void close()throws IOException{
            if(closed)return;closed=true;
            try{
                if(failed){target.failWrite(raw);return;}
                try{encrypted.close();raw.getFD().sync();target.finishWrite(raw);}
                catch(Exception e){target.failWrite(raw);throw io(e);}
            }finally{VaultMaintenance.endWrite();}
        }
    }
    static Bitmap bitmap(String path){return bitmap(path,null);}
    static Bitmap bitmap(String path,BitmapFactory.Options options){
        try(InputStream in=open(new File(path))){return BitmapFactory.decodeStream(in,null,options);}
        catch(IOException e){return null;}
    }
    static MediaDataSource media(File file)throws IOException{
        byte[] data=aad(file);FileInputStream raw=new AtomicFile(file).openRead();
        try{
            if(!Arrays.equals(MAGIC,readPrefix(raw)))throw new IOException(UiLanguage.t("Arquivo do cofre inválido"));
            // Tink's seekable channel expects its own header at offset zero.
            SeekableByteChannel source=new OffsetChannel(raw.getChannel(),MAGIC.length);
            SeekableByteChannel clear=cipher.newSeekableDecryptingChannel(source,data);
            clear.read(ByteBuffer.allocate(1)); // Tink selects/authenticates the key on first read.
            // size() alone is not an authentication check. Verify EOF and its final segment now.
            long size=clear.size();clear.position(Math.max(0,size-1));ByteBuffer end=ByteBuffer.allocate(2);
            while(clear.read(end)>0){}clear.position(0);
            return new MediaDataSource(){
                @Override public synchronized int readAt(long position,byte[] buffer,int offset,int count)throws IOException{
                    if(position<0)throw new IOException(UiLanguage.t("Posição inválida"));
                    if(count==0)return 0;
                    clear.position(position);return clear.read(ByteBuffer.wrap(buffer,offset,count));
                }
                @Override public long getSize(){return size;}
                @Override public synchronized void close()throws IOException{clear.close();}
            };
        }catch(Exception e){raw.close();throw io(e);}
    }
    private static final class OffsetChannel implements SeekableByteChannel{
        private final SeekableByteChannel channel;private final long offset;
        OffsetChannel(SeekableByteChannel channel,long offset)throws IOException{this.channel=channel;this.offset=offset;channel.position(offset);}
        public int read(ByteBuffer dst)throws IOException{return channel.read(dst);}
        public int write(ByteBuffer src)throws IOException{throw new java.nio.channels.NonWritableChannelException();}
        public long position()throws IOException{return channel.position()-offset;}
        public SeekableByteChannel position(long p)throws IOException{if(p<0)throw new IllegalArgumentException();channel.position(p+offset);return this;}
        public long size()throws IOException{return channel.size()-offset;}
        public SeekableByteChannel truncate(long n)throws IOException{throw new java.nio.channels.NonWritableChannelException();}
        public boolean isOpen(){return channel.isOpen();}
        public void close()throws IOException{channel.close();}
    }
    private static IOException io(Exception e){return e instanceof IOException?(IOException)e:new IOException(UiLanguage.t("Falha de autenticação do cofre"),e);}
    private static void rawAtomicWrite(AtomicFile file,byte[] bytes)throws IOException{
        FileOutputStream out=file.startWrite();try{out.write(bytes);out.getFD().sync();file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw io(e);}
    }

    // Resumable migration: a sealed journal records plaintext hashes before any replacement.
    // A changed/corrupt file is never silently treated as new plaintext.
    private static void migrate(Context app)throws Exception{
        File done=new File(root,"migration.vault"),journal=new File(root,"migration-plan.vault");
        if(done.exists()){if(!"1".equals(new String(read(done,16),StandardCharsets.UTF_8)))throw new IOException(UiLanguage.t("Versão do cofre inválida"));return;}
        JSONObject plan;
        if(journal.exists())plan=new JSONObject(new String(read(journal,4_000_000),StandardCharsets.UTF_8));
        else{
            plan=new JSONObject();List<File> entries=new ArrayList<>();collectLegacy(root,entries);
            for(File file:entries){
                if(encrypted(file))throw new IOException(UiLanguage.t("Migração sem registro. Arquivos preservados."));
                plan.put(root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/'),digest(file));
            }
            write(journal,plan.toString().getBytes(StandardCharsets.UTF_8));
        }
        for(Iterator<String> names=plan.keys();names.hasNext();){
            String name=names.next();File file=new File(root,name);aad(file);
            // Recover an interrupted AtomicFile replacement before inspecting it.
            try(InputStream ignored=new AtomicFile(file).openRead()){}
            if(encrypted(file)){verify(file);continue;}
            if(!plan.getString(name).equals(digest(file)))throw new IOException(UiLanguage.t("Arquivo alterado durante migração. Preservado."));
            try(InputStream in=new FileInputStream(file)){
                write(file,out->{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);});
            }
            verify(file);
        }
        File prefs=new File(root,"preferences.vault");
        if(!prefs.exists()){
            Map<String,Object> legacy=new HashMap<>(app.getSharedPreferences("MainActivity",0).getAll());
            // The commercial applicationId differs from the Java Activity package.
            // Activity.getPreferences therefore used the fully-qualified local class name.
            legacy.putAll(app.getSharedPreferences("com.leal.comfyremote.MainActivity",0).getAll());
            VaultPreferences.importLegacy(prefs,legacy);
        }
        new VaultPreferences(prefs); // Authenticate before removing the old XML.
        if(!app.deleteSharedPreferences("MainActivity")||!app.deleteSharedPreferences("com.leal.comfyremote.MainActivity"))
            throw new IOException(UiLanguage.t("Não foi possível concluir migração das configurações"));
        write(done,"1".getBytes(StandardCharsets.UTF_8));
        new AtomicFile(journal).delete();
    }
    private static void collectLegacy(File folder,List<File> entries)throws IOException{
        File[] files=folder.listFiles();if(files==null)return;
        for(File f:files){
            if(!f.exists())continue; // A previous AtomicFile recovery may have consumed its backup.
            if(Files.isSymbolicLink(f.toPath()))throw new IOException(UiLanguage.t("Link recusado na migração"));
            if(f.isDirectory()){collectLegacy(f,entries);continue;}
            String name=f.getName();if(name.endsWith(".vault"))continue;
            if(name.endsWith(".bak")||name.endsWith(".new")){
                File base=new File(f.getPath().substring(0,f.getPath().length()-4));
                try(InputStream ignored=new AtomicFile(base).openRead()){}
                if(!entries.contains(base))entries.add(base);
            }else if(!entries.contains(f))entries.add(f);
        }
    }
    private static String digest(File file)throws Exception{
        MessageDigest sha=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)sha.update(b,0,n);}
        return android.util.Base64.encodeToString(sha.digest(),android.util.Base64.NO_WRAP);
    }
    private static void verify(File file)throws IOException{try(InputStream in=open(file)){byte[] b=new byte[65536];while(in.read(b)!=-1){}}}
}
