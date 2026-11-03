package com.leal.comfyremote;

import android.content.Context;
import android.system.Os;
import android.system.OsConstants;
import android.util.AtomicFile;
import com.google.crypto.tink.*;
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient;
import com.google.crypto.tink.streamingaead.*;
import com.google.crypto.tink.util.SecretBytes;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.json.*;

/**
 * Portable, authenticated backup. Recovery material never leaves RAM except by
 * the user's explicit copy. Plaintext streams straight between two AEAD streams.
 * No ZIP parser, plaintext temp files, device key export, or Desktop credentials.
 */
final class VaultBackup {
    private static final byte[] MAGIC="BWDBACKUP1\n".getBytes(StandardCharsets.US_ASCII);
    private static final int BLOCK=65536,MAX_FILES=20000,MAX_JOURNAL=16_000_000;
    private static final long MAX_FILE=1024L*1024*1024,MAX_TOTAL=64L*1024*1024*1024;
    private static final Set<String> ROOT_FILES=new HashSet<>(Arrays.asList(
        "preferences.vault","migration.vault","feed.json","deleted-generations.json","reuse-inputs.json","workflow.json"));
    interface Progress{void update(long bytes,int files);}
    private static void canceled()throws IOException{if(Thread.currentThread().isInterrupted())throw new InterruptedIOException(UiLanguage.t("Operação cancelada."));}

    static byte[] newRecoveryKey(){byte[] key=new byte[32];new SecureRandom().nextBytes(key);return key;}
    static String recoveryCode(byte[] key)throws Exception{
        if(key.length!=32)throw new IllegalArgumentException();
        String hex=hex(key).toUpperCase(Locale.ROOT),check=hex(checksum(key)).substring(0,8).toUpperCase(Locale.ROOT);
        StringBuilder out=new StringBuilder("BWD1");for(int i=0;i<hex.length();i+=8)out.append('-').append(hex,i,i+8);return out.append('-').append(check).toString();
    }
    static byte[] parseCode(String code)throws Exception{
        if(code==null||code.length()>160)throw new IOException(UiLanguage.t("Código de recuperação inválido."));
        String value=code.replaceAll("[\\s-]","").toUpperCase(Locale.ROOT);
        if(!value.matches("BWD1[0-9A-F]{72}"))throw new IOException(UiLanguage.t("Código de recuperação inválido."));
        byte[] key=new byte[32];for(int i=0;i<32;i++)key[i]=(byte)Integer.parseInt(value.substring(4+i*2,6+i*2),16);
        if(!MessageDigest.isEqual(value.substring(68).getBytes(StandardCharsets.US_ASCII),
                hex(checksum(key)).substring(0,8).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII))){
            Arrays.fill(key,(byte)0);throw new IOException(UiLanguage.t("Código de recuperação com erro de digitação."));
        }return key;
    }
    private static byte[] checksum(byte[] key)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");hash.update(MAGIC);return hash.digest(key);
    }
    private static StreamingAead archiveCipher(byte[] key)throws Exception{
        StreamingAeadConfig.register();
        AesGcmHkdfStreamingKey imported=AesGcmHkdfStreamingKey.create(
            PredefinedStreamingAeadParameters.AES256_GCM_HKDF_1MB,
            SecretBytes.copyFrom(key,InsecureSecretKeyAccess.get()));
        return KeysetHandle.newBuilder().addEntry(KeysetHandle.importKey(imported).withFixedId(1).makePrimary())
            .build().getPrimitive(StreamingAead.class);
    }
    static File export(Context context,byte[] key,Progress progress)throws Exception{
        VaultFiles.prepare(context);VaultFiles.preferences().flush();
        File scratch=scratch(context);File out=new File(scratch,"export-"+UUID.randomUUID()+".bwdvault");
        try(AutoCloseable ignored=VaultMaintenance.exclusive()){
            File root=context.getFilesDir().getCanonicalFile();List<File> files=new ArrayList<>();collect(root,root,files);
            if(files.size()>MAX_FILES)throw new IOException(UiLanguage.t("Acervo excede o limite desta versão."));
            StreamingAead cipher=archiveCipher(key);long[] total={0};int count=0;
            try(FileOutputStream raw=new FileOutputStream(out)){
                raw.write(MAGIC);
                try(DataOutputStream encrypted=new DataOutputStream(cipher.newEncryptingStream(nonClosing(raw),MAGIC))){
                    encrypted.writeInt(1);
                    for(File file:files){
                        canceled();String name=relative(root,file);byte[] path=name.getBytes(StandardCharsets.UTF_8);
                        encrypted.writeInt(path.length);encrypted.write(path);long size=0;
                        try(InputStream source=VaultFiles.open(file)){
                            byte[] bytes=new byte[BLOCK];int n;
                            while((n=source.read(bytes))!=-1){
                                canceled();size+=n;total[0]+=n;limits(size,total[0]);encrypted.writeInt(n);encrypted.write(bytes,0,n);
                                if(progress!=null)progress.update(total[0],count);
                            }
                        }encrypted.writeInt(0);count++;
                    }
                    encrypted.writeInt(0);encrypted.writeInt(count);
                }raw.getFD().sync();
            }
        }catch(Exception error){out.delete();throw error;}
        try(InputStream in=new FileInputStream(out)){verify(in,key,null);}
        catch(Exception error){out.delete();throw error;}
        return out;
    }
    // Readback of the chosen document must reach authenticated EOF before UI reports success.
    static void verify(InputStream source,byte[] key,Progress progress)throws Exception{parse(source,key,null,progress);}

    private interface EntrySink{OutputStream open(String path)throws Exception;void finish(String path,OutputStream output,boolean success)throws Exception;}
    private static void parse(InputStream source,byte[] key,EntrySink sink,Progress progress)throws Exception{
        DataInputStream raw=new DataInputStream(source);byte[] magic=new byte[MAGIC.length];raw.readFully(magic);
        if(!Arrays.equals(magic,MAGIC))throw new IOException(UiLanguage.t("Este arquivo não é um backup bwdacc compatível."));
        Set<String> seen=new HashSet<>();long total=0;
        try(DataInputStream clear=new DataInputStream(archiveCipher(key).newDecryptingStream(raw,MAGIC))){
            if(clear.readInt()!=1)throw new IOException(UiLanguage.t("Versão do backup não suportada."));
            while(true){
                canceled();int length=clear.readInt();if(length==0)break;
                if(length<1||length>512||seen.size()>=MAX_FILES)throw new IOException(UiLanguage.t("Índice do backup inválido."));
                byte[] name=new byte[length];clear.readFully(name);String path=new String(name,StandardCharsets.UTF_8);
                if(!validPath(path)||!Arrays.equals(name,path.getBytes(StandardCharsets.UTF_8))||!seen.add(path))throw new IOException(UiLanguage.t("Caminho repetido ou inválido no backup."));
                OutputStream output=sink==null?null:sink.open(path);boolean success=false;long size=0;
                ByteArrayOutputStream metadata=(path.equals("preferences.vault")||path.equals("migration.vault"))?new ByteArrayOutputStream():null;
                try{
                    byte[] bytes=new byte[BLOCK];
                    while(true){
                        canceled();int n=clear.readInt();if(n==0)break;
                        if(n<1||n>BLOCK)throw new IOException(UiLanguage.t("Bloco do backup inválido."));
                        clear.readFully(bytes,0,n);size+=n;total+=n;limits(size,total);
                        if(metadata!=null){if(size>16_000_000)throw new IOException(UiLanguage.t("Configuração muito grande."));metadata.write(bytes,0,n);}
                        if(output!=null)output.write(bytes,0,n);
                        if(progress!=null)progress.update(total,seen.size());
                    }
                    if(path.equals("preferences.vault"))VaultPreferences.validate(metadata.toByteArray());
                    if(path.equals("migration.vault")&&!"1".equals(new String(metadata.toByteArray(),StandardCharsets.UTF_8)))throw new IOException(UiLanguage.t("Marcador do cofre inválido."));
                    success=true;
                }finally{if(sink!=null)sink.finish(path,output,success);}
            }
            if(clear.readInt()!=seen.size()||!seen.contains("preferences.vault")||!seen.contains("migration.vault")||clear.read()!=-1)
                throw new IOException(UiLanguage.t("Backup incompleto ou com dados extras."));
        }
    }
    private static void limits(long file,long total)throws IOException{
        if(file>MAX_FILE||total>MAX_TOTAL)throw new IOException(UiLanguage.t("Backup excede o limite desta versão (1 GB por arquivo, 64 GB total)."));
    }
    static boolean validPath(String path){
        if(ROOT_FILES.contains(path))return true;
        if(!path.matches("[A-Za-z0-9_./-]{1,512}")||path.contains("..")||path.startsWith("/")||path.endsWith("/"))return false;
        return path.matches("images/[A-Za-z0-9_-]+\\.audio") || path.matches("references/[a-fA-F0-9-]+\\.(png|audio)") || path.matches("(images|references|characters|recipes|workflows|project-covers)/[A-Za-z0-9_-]+\\.(img|jpg|mp4|json)")
            ||path.matches("covers/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+\\.jpg");
    }
    private static void collect(File root,File folder,List<File> files)throws Exception{collect(root,folder,files,false);}
    private static void collect(File root,File folder,List<File> files,boolean strict)throws Exception{
        if(root.toPath().relativize(folder.toPath()).getNameCount()>2)throw new IOException(UiLanguage.t("Pasta inesperada no acervo."));
        File[] entries=folder.listFiles();if(entries==null)throw new IOException(UiLanguage.t("Não foi possível ler o acervo."));
        if(!strict){
            // AtomicFile's legacy backup is the last committed value, even when
            // the base is missing. Recover it before enumerating the snapshot.
            for(File file:entries)if(file.getName().endsWith(".bak")){
                File base=new File(folder,file.getName().substring(0,file.getName().length()-4));
                if(Files.isSymbolicLink(file.toPath())||Files.isSymbolicLink(base.toPath()))throw new IOException(UiLanguage.t("Link recusado no acervo."));
                String path=relative(root,base);
                if(path.endsWith(".part"))continue;
                if(!validPath(path))throw new IOException(UiLanguage.t("Arquivo inesperado no acervo. Backup não criado."));
                try(InputStream ignored=new AtomicFile(base).openRead()){}
            }
            entries=folder.listFiles();if(entries==null)throw new IOException(UiLanguage.t("Não foi possível ler o acervo."));
        }
        Arrays.sort(entries,Comparator.comparing(File::getName));
        for(File file:entries){
            canceled();if(Files.isSymbolicLink(file.toPath()))throw new IOException(UiLanguage.t("Link recusado no acervo."));
            if(!file.exists())continue;
            if(file.isDirectory()){collect(root,file,files,strict);continue;}
            String path=relative(root,file);
            if(!strict&&(path.endsWith(".part")||path.endsWith(".new")||path.endsWith(".bak")))continue;
            if(!validPath(path))throw new IOException(UiLanguage.t("Arquivo inesperado no acervo. Backup não criado."));
            files.add(file);
            if(files.size()>MAX_FILES)throw new IOException(UiLanguage.t("Acervo excede o limite desta versão."));
        }
    }
    private static String relative(File root,File file)throws IOException{
        Path p=file.getCanonicalFile().toPath();if(!p.startsWith(root.toPath()))throw new IOException(UiLanguage.t("Caminho fora do acervo."));
        return root.toPath().relativize(p).toString().replace(File.separatorChar,'/');
    }
    static void requireEmpty(Context context)throws Exception{
        List<File> files=new ArrayList<>();File root=context.getFilesDir().getCanonicalFile();collect(root,root,files,true);
        for(File file:files)if(!Arrays.asList("migration.vault","preferences.vault").contains(relative(root,file)))
            throw new IOException(UiLanguage.t("Restauração exige uma instalação vazia. O acervo atual foi preservado."));
        Map<String,?> values=VaultFiles.preferences().getAll();
        for(Map.Entry<String,?> e:values.entrySet()){
            String k=e.getKey(),v=String.valueOf(e.getValue()).trim();
            if((k.contains("draft_")||k.equals("reference_library_v1")||k.equals("characters")||k.equals("prompt_styles")||
                k.equals("prompt_locations")||k.equals("projects_v1"))&&!v.isEmpty()&&!v.equals("[]")&&!v.equals("{}"))
                throw new IOException(UiLanguage.t("Esta instalação já contém referências, projetos ou rascunhos. Nada foi substituído."));
        }
    }
    static void restore(Context context,InputStream source,byte[] key,Progress progress)throws Exception{
        VaultFiles.prepare(context);VaultFiles.preferences().flush();
        try(AutoCloseable ignored=VaultMaintenance.exclusive()){
            requireEmpty(context);
            File home=transaction(context),stage=new File(home,"data");
            if(journal(context).getBaseFile().exists())throw new IOException(UiLanguage.t("Restauração anterior precisa ser concluída ao reabrir."));
            removeTree(home);if(!stage.mkdirs())throw new IOException(UiLanguage.t("Sem espaço para preparar restauração."));
            boolean committed=false;
            try{
                // Refuse unsupported layouts before authorizing any replacement.
                if(Os.stat(context.getFilesDir().getPath()).st_dev!=Os.stat(stage.getPath()).st_dev)
                    throw new IOException(UiLanguage.t("Este armazenamento não permite restauração atômica."));
                parse(source,key,new EntrySink(){
                    public OutputStream open(String path)throws Exception{return VaultFiles.stagedOutput(new File(stage,path),path);}
                    public void finish(String path,OutputStream output,boolean success)throws Exception{
                        VaultFiles.Write write=(VaultFiles.Write)output;if(!success)write.abort();write.close();
                    }
                },progress);
                JSONObject manifest=new JSONObject();List<File> files=new ArrayList<>();collect(stage,stage,files);
                for(File file:files){canceled();String path=relative(stage,file);manifest.put(path,digest(file));
                    try(InputStream in=VaultFiles.openStaged(file,path)){byte[] b=new byte[BLOCK];while(in.read(b)!=-1){canceled();}}}
                requireEmpty(context); // No queued mutation may replace an existing library.
                JSONObject state=new JSONObject().put("version",1).put("files",manifest);
                byte[] sealed=master().encrypt(state.toString().getBytes(StandardCharsets.UTF_8),journalAad(context));
                if(sealed.length>MAX_JOURNAL)throw new IOException(UiLanguage.t("Índice de restauração muito grande."));
                syncTreeDirectories(stage);syncDirectory(home);
                atomic(journal(context),sealed);
                // Once the record exists, even a subsequent directory-fsync error
                // must preserve staging and prevent stale writers until restart.
                committed=true;VaultMaintenance.requireRestart();
                syncDirectory(context.getNoBackupFilesDir());
                recoverRestore(context);
            }finally{if(!committed)removeTree(home);}
        }
    }
    // Runs before normal library loading. A sealed journal authorizes only a verified,
    // already-complete directory replacement. Repeating any step is safe after a crash.
    static void recoverRestore(Context context)throws Exception{
        AtomicFile record=journal(context);File home=transaction(context),stage=new File(home,"data"),previous=new File(home,"previous");
        if(!record.getBaseFile().exists()&&!new File(record.getBaseFile()+".bak").exists())return;
        JSONObject state;try(InputStream in=record.openRead()){
            byte[] sealed=VaultFiles.readAll(in,MAX_JOURNAL);
            state=new JSONObject(new String(master().decrypt(sealed,journalAad(context)),StandardCharsets.UTF_8));
        }
        if(state.getInt("version")!=1)throw new IOException(UiLanguage.t("Versão de restauração inválida."));
        JSONObject manifest=state.getJSONObject("files");validateManifest(manifest);
        File root=context.getFilesDir().getCanonicalFile();
        if(stage.exists()){
            verifyTree(stage,manifest);
            if(!previous.exists()){
                // The committed transaction was created only for an empty vault.
                List<File> old=new ArrayList<>();collect(root,root,old,true);
                for(File f:old)if(!Arrays.asList("migration.vault","preferences.vault").contains(relative(root,f)))throw new IOException(UiLanguage.t("Acervo mudou durante restauração. Arquivos preservados."));
                Files.move(root.toPath(),previous.toPath(),StandardCopyOption.ATOMIC_MOVE);syncDirectory(root.getParentFile());syncDirectory(home);
            }
            if(root.exists()){
                // Context.getFilesDir() recreates an empty directory after a crash.
                String[] children=root.list();
                if(Files.isSymbolicLink(root.toPath())||children==null||children.length!=0||!root.delete())
                    throw new IOException(UiLanguage.t("Destino ambíguo na restauração. Arquivos preservados."));
            }
            Files.move(stage.toPath(),root.toPath(),StandardCopyOption.ATOMIC_MOVE);syncDirectory(root.getParentFile());syncDirectory(home);
        }
        verifyTree(root,manifest);
        record.delete();syncDirectory(context.getNoBackupFilesDir());
        removeTree(home);
    }
    private static void validateManifest(JSONObject manifest)throws Exception{
        if(manifest.length()<2||manifest.length()>MAX_FILES||!manifest.has("migration.vault")||!manifest.has("preferences.vault"))throw new IOException(UiLanguage.t("Índice de restauração inválido."));
        for(Iterator<String> it=manifest.keys();it.hasNext();){String path=it.next();if(!validPath(path)||!manifest.getString(path).matches("[a-f0-9]{64}"))throw new IOException(UiLanguage.t("Índice de restauração inválido."));}
    }
    private static void verifyTree(File root,JSONObject manifest)throws Exception{
        if(!root.isDirectory()||Files.isSymbolicLink(root.toPath()))throw new IOException(UiLanguage.t("Pasta de restauração inválida."));
        List<File> files=new ArrayList<>();collect(root.getCanonicalFile(),root,files,true);
        if(files.size()!=manifest.length())throw new IOException(UiLanguage.t("Restauração incompleta. Arquivos preservados."));
        for(File f:files)if(!digest(f).equals(manifest.optString(relative(root.getCanonicalFile(),f))))throw new IOException(UiLanguage.t("Restauração alterada. Arquivos preservados."));
    }
    private static Aead master()throws Exception{return new AndroidKeystoreKmsClient().getAead("android-keystore://"+VaultFiles.ALIAS);}
    private static byte[] journalAad(Context context){return (context.getPackageName()+":restore:v1").getBytes(StandardCharsets.UTF_8);}
    private static AtomicFile journal(Context c){return new AtomicFile(new File(c.getNoBackupFilesDir(),"restore.pending"));}
    private static File transaction(Context c)throws IOException{
        File base=c.getNoBackupFilesDir().getCanonicalFile(),folder=new File(base,"restore-stage");
        if(Files.isSymbolicLink(folder.toPath()))throw new IOException(UiLanguage.t("Pasta de restauração inválida."));return folder;
    }
    private static File scratch(Context c)throws IOException{
        File dir=new File(c.getCacheDir(),"vault-backups");if(Files.isSymbolicLink(dir.toPath())||!dir.isDirectory()&&!dir.mkdirs())throw new IOException(UiLanguage.t("Não foi possível preparar backup."));return dir;
    }
    private static OutputStream nonClosing(OutputStream out){return new FilterOutputStream(out){
        public void write(byte[] b,int off,int n)throws IOException{out.write(b,off,n);}public void close()throws IOException{flush();}
    };}
    private static void atomic(AtomicFile file,byte[] bytes)throws Exception{
        FileOutputStream out=file.startWrite();try{out.write(bytes);out.getFD().sync();file.finishWrite(out);}catch(Exception e){file.failWrite(out);throw e;}
    }
    private static String digest(File file)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[BLOCK];int n;while((n=in.read(b))!=-1)hash.update(b,0,n);}return hex(hash.digest());
    }
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static void syncDirectory(File dir)throws Exception{
        FileDescriptor fd=Os.open(dir.getPath(),OsConstants.O_RDONLY,0);
        try{if(!OsConstants.S_ISDIR(Os.fstat(fd).st_mode))throw new IOException(UiLanguage.t("Pasta inválida."));Os.fsync(fd);}finally{Os.close(fd);}
    }
    private static void syncTreeDirectories(File dir)throws Exception{
        File[] children=dir.listFiles();if(children==null)throw new IOException(UiLanguage.t("Pasta privada inacessível."));
        for(File file:children){if(Files.isSymbolicLink(file.toPath()))throw new IOException(UiLanguage.t("Link recusado."));if(file.isDirectory())syncTreeDirectories(file);}
        syncDirectory(dir);
    }
    private static void removeTree(File folder)throws IOException{
        if(!folder.exists())return;if(Files.isSymbolicLink(folder.toPath()))throw new IOException(UiLanguage.t("Link recusado na restauração."));
        File[] entries=folder.listFiles();if(entries==null)throw new IOException(UiLanguage.t("Pasta privada inacessível."));
        for(File f:entries){if(Files.isSymbolicLink(f.toPath()))throw new IOException(UiLanguage.t("Link recusado na restauração."));if(f.isDirectory())removeTree(f);else if(!f.delete())throw new IOException(UiLanguage.t("Não foi possível limpar arquivo temporário."));}
        if(!folder.delete())throw new IOException(UiLanguage.t("Não foi possível limpar pasta temporária."));
    }
}
