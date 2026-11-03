package com.leal.comfyremote;

import android.widget.*;
import java.io.File;
import java.util.UUID;

final class AudioFragmentEditor {
    final ReferenceStudio s;final ReferencePreset original,draft;final FlowSheet sheet;final EditText name;final TextView detail;Button play,save,replace;
    LipsyncAudio selected;boolean disposed,saving;
    AudioFragmentEditor(ReferenceStudio studio,ReferencePreset preset){
        s=studio;original=preset;draft=preset.copy();draft.useText=draft.useImages=draft.useRefmod=draft.useLoras=false;
        sheet=new FlowSheet(s.activity,UiLanguage.t("Fragmento de áudio"),"wave");name=sheet.field(UiLanguage.t("Nome do áudio"));name.setText(draft.name);
        sheet.note(UiLanguage.t("Guarde falas e sons para reutilizar no Lipsync. Até 30 segundos por fragmento."));
        detail=sheet.text("",15,true);sheet.body.addView(detail);sheet.gap(sheet.body,16);
        replace=sheet.action(UiLanguage.t("Adicionar ou substituir áudio"),"plus",()->{if(!saving)s.audio.choose(false,this::accept);});
        play=sheet.action(UiLanguage.t("Ouvir áudio"),"play",()->{if(s.audio.player!=null){s.audio.stopPreview();return;}if(selected!=null){play.setText(UiLanguage.t("Parar áudio"));s.audio.preview(selected,()->play.setText(UiLanguage.t("Ouvir áudio")));}});
        if(s.host.nsfw())s.permission(sheet.body,UiLanguage.t("Privado"),UiLanguage.t("Disponível apenas no modo privado."),"owl",draft.privateCover,on->draft.privateCover=on);
        save=sheet.action(UiLanguage.t("Salvar fragmento"),"check",this::save);
        sheet.dialog.setOnDismissListener(d->{disposed=true;s.audio.stopPreview();if(!saving)LipsyncAudio.discard(selected);s.renderManager.run();});
        s.editor=sheet.dialog;sheet.show();refresh();
        if(!draft.audioFile.isEmpty())s.jobs.worker.execute(()->{try{LipsyncAudio a=LipsyncAudio.read(s.store.file(draft.audioFile),draft.audioName);s.activity.runOnUiThread(()->{if(!disposed&&selected==null){selected=a;refresh();}});}catch(Exception e){s.activity.runOnUiThread(()->{if(!disposed)detail.setText(UiLanguage.t("Áudio indisponível. Importe novamente."));});}});
    }
    void accept(LipsyncAudio audio){if(disposed||saving){LipsyncAudio.discard(audio);return;}s.audio.stopPreview();LipsyncAudio.discard(selected);selected=audio;if(name.getText().toString().trim().isEmpty())name.setText(audio.name.replaceFirst("\\.[A-Za-z0-9]{1,6}$",""));refresh();}
    void refresh(){replace.setEnabled(!saving);name.setEnabled(!saving);detail.setText(selected==null?UiLanguage.t("Nenhum áudio adicionado"):selected.name+" · "+AudioTools.seconds(selected.millis));play.setEnabled(selected!=null&&!saving);save.setEnabled(selected!=null&&!saving);play.setAlpha(play.isEnabled()?1:.4f);save.setAlpha(save.isEnabled()?1:.4f);}
    void save(){
        String title=name.getText().toString().trim();if(title.isEmpty()){name.setError(UiLanguage.t("Digite um nome."));return;}for(ReferencePreset p:s.store.all())if(p.audio()&&!p.id.equals(draft.id)&&p.name.equalsIgnoreCase(title)){name.setError(UiLanguage.t("Nome já existe."));return;}if(selected==null||saving)return;
        saving=true;refresh();s.audio.stopPreview();final LipsyncAudio audio=selected;final String file=UUID.randomUUID()+".audio";
        s.jobs.worker.execute(()->{try{LipsyncAudio.copy(audio.file,s.store.file(file));s.activity.runOnUiThread(()->{
            if(disposed){VaultFiles.delete(s.store.file(file));LipsyncAudio.discard(audio);return;}
            try{draft.name=title;draft.audioFile=file;draft.audioName=audio.name;draft.audioMillis=audio.millis;s.store.save(draft);s.store.removeUnusedAudio(original.audioFile);saving=false;sheet.dialog.dismiss();}
            catch(Exception e){VaultFiles.delete(s.store.file(file));saving=false;refresh();s.toast(e.getMessage());}
        });}catch(Exception e){VaultFiles.delete(s.store.file(file));s.activity.runOnUiThread(()->{if(!disposed){saving=false;refresh();s.toast(e.getMessage());}else LipsyncAudio.discard(audio);});}});
    }
}
