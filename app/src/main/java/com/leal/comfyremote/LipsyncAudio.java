package com.leal.comfyremote;

import android.app.Activity;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.*;
import java.util.UUID;

/** Audio stays in the authenticated vault, including temporary picker drafts. */
final class LipsyncAudio {
    final File file;final long millis;final String name;
    LipsyncAudio(File file,long millis,String name){this.file=file;this.millis=millis;this.name=name;}
    static File file(File root,String id){if(!GenerationStorage.valid(id))throw new IllegalArgumentException(UiLanguage.t("Invalid audio ID"));return new File(new File(root,"images"),id+".audio");}
    static LipsyncAudio read(File file,String name)throws Exception{
        MediaMetadataRetriever reader=new MediaMetadataRetriever();android.media.MediaDataSource source=null;
        try{source=VaultFiles.media(file);reader.setDataSource(source);
            if(!"yes".equals(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)))throw new IOException(UiLanguage.t("Arquivo sem áudio válido."));
            long duration=Long.parseLong(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if(duration<200||duration>30000)throw new IOException(UiLanguage.t("Escolha um áudio de 0,2 a 30 segundos."));
            return new LipsyncAudio(file,duration,name);
        }finally{reader.release();VaultFiles.close(source);}
    }
    static LipsyncAudio importFile(Activity activity,Uri uri)throws Exception{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException(UiLanguage.t("Arquivo de áudio inválido."));
        File file=file(activity.getFilesDir(),"draft_lipsync_"+UUID.randomUUID());
        String name=UiLanguage.t("Áudio selecionado");
        try(android.database.Cursor cursor=activity.getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){String value=cursor.getString(0);if(value!=null&&!value.trim().isEmpty())name=value.substring(0,Math.min(120,value.length()));}
        }catch(Exception ignored){}
        try{
            try(InputStream input=activity.getContentResolver().openInputStream(uri);OutputStream output=VaultFiles.output(file)){
                if(input==null)throw new IOException(UiLanguage.t("Não foi possível abrir o áudio."));byte[] buffer=new byte[8192];int count,total=0;
                while((count=input.read(buffer))!=-1){total+=count;if(total>50_000_000)throw new IOException(UiLanguage.t("O áudio excede 50 MB."));output.write(buffer,0,count);}
            }
            return read(file,name);
        }catch(Exception error){VaultFiles.delete(file);throw error;}
    }
    static void copy(File source,File destination)throws Exception{
        try(InputStream input=VaultFiles.open(source);OutputStream output=VaultFiles.output(destination)){byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);}
    }
    static void discard(LipsyncAudio audio){if(audio!=null&&audio.file.getName().startsWith("draft_lipsync_"))VaultFiles.delete(audio.file);}
    static void cleanup(File root){File[] files=new File(root,"images").listFiles();if(files!=null)for(File file:files)if(file.getName().startsWith("draft_lipsync_")&&file.getName().endsWith(".audio")&&System.currentTimeMillis()-file.lastModified()>86400000L)VaultFiles.delete(file);}
}
