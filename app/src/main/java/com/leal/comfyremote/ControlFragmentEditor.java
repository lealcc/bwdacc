package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.text.InputFilter;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Compose a reusable block of typed maps without treating a map as a normal image reference. */
final class ControlFragmentEditor {
    final ReferenceStudio s;final ReferencePreset draft;final LinearLayout page,rows;final EditText name;final TextView status;final Button save,add;
    Dialog dialog;volatile boolean closed;boolean busy;
    ControlFragmentEditor(ReferenceStudio studio,ReferencePreset original){
        s=studio;draft=original.copy();draft.useText=draft.useImages=draft.useRefmod=draft.useLoras=false;
        page=s.page(UiLanguage.t("Bloco de controle"),()->dialog.dismiss());
        name=s.field(UiLanguage.t("Nome do bloco"),1);name.setText(draft.name);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});page.addView(name,new LinearLayout.LayoutParams(-1,s.dp(52)));s.gap(page,12);
        page.addView(s.text(UiLanguage.t("Combine mapas num bloco. O suporte de cada modelo será validado depois; estes mapas ainda não entram nas gerações."),13,ReferenceStudio.MUTED,false));s.gap(page,12);
        ScrollView scroll=new ScrollView(s.activity);rows=s.column();scroll.addView(rows);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=s.text("",13,ReferenceStudio.MUTED,false);s.gap(page,10);page.addView(status);
        add=s.button(UiLanguage.t("Adicionar mapa"),"plus");s.gap(page,8);page.addView(add,new LinearLayout.LayoutParams(-1,s.dp(46)));add.setOnClickListener(v->chooseType());
        save=s.button(UiLanguage.t("Salvar bloco"),"check");s.gap(page,8);page.addView(save,new LinearLayout.LayoutParams(-1,s.dp(48)));
        save.setOnClickListener(v->{try{
            draft.name=name.getText().toString().trim();if(draft.name.isEmpty()){name.setError(UiLanguage.t("Digite um nome."));return;}
            for(ReferencePreset other:s.store.all())if(!other.id.equals(draft.id)&&other.kind.equals("control")&&other.name.equalsIgnoreCase(draft.name)){name.setError(UiLanguage.t("Nome já existe."));return;}
            if(draft.controls.length()==0){s.toast(UiLanguage.t("Adicione pelo menos um mapa."));return;}
            ControlFragments.validate(draft);s.store.save(draft);dialog.dismiss();
        }catch(Exception error){s.toast(error.getMessage());}});
    }
    void show(){dialog=s.sheet(page,.92f);s.editor=dialog;dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.setOnDismissListener(d->{closed=true;s.renderManager.run();});render();}
    void render(){
        rows.removeAllViews();for(String type:ControlFragments.TYPES){JSONObject map=draft.controls.optJSONObject(type);if(map==null)continue;
            LinearLayout tile=s.column();tile.setPadding(s.dp(12),s.dp(12),s.dp(12),s.dp(12));tile.setBackground(FlowDesign.shape(s.activity,0xFF1B1C1E,20,false));
            LinearLayout title=s.row();title.addView(s.text(ControlFragments.label(type),16,ReferenceStudio.INK,true),new LinearLayout.LayoutParams(0,s.dp(44),1));Button more=s.icon("more",UiLanguage.t("Opções de ")+ControlFragments.label(type));title.addView(more,new LinearLayout.LayoutParams(s.dp(44),s.dp(44)));tile.addView(title);
            ReferencePreset.Picture image=ControlFragments.picture(draft,map.optString("file"));ImageView preview=s.image(draft,image,160);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);tile.addView(preview,new LinearLayout.LayoutParams(-1,s.dp(180)));
            if(draft.hidden())preview.getViewTreeObserver().addOnWindowFocusChangeListener(focused->{if(!focused)preview.setImageBitmap(s.host.preview(draft,image,false));});
            if(draft.hidden())preview.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){if(s.host.nsfw()){preview.setImageBitmap(s.host.preview(draft,image,true));v.getParent().requestDisallowInterceptTouchEvent(true);}return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){preview.setImageBitmap(s.host.preview(draft,image,false));v.getParent().requestDisallowInterceptTouchEvent(false);return true;}return true;});
            more.setEnabled(!busy);more.setOnClickListener(v->{PopupMenu menu=new OrganicPopupMenu(s.activity,more);
                menu.getMenu().add(UiLanguage.t("Substituir mapa")).setOnMenuItemClickListener(it->{chooseSource(type,more);return true;});
                menu.getMenu().add(UiLanguage.t("Usar como capa")).setOnMenuItemClickListener(it->{draft.cover=map.optString("file");render();return true;});
                menu.getMenu().add(UiLanguage.t("Remover mapa")).setOnMenuItemClickListener(it->{ControlFragments.remove(draft,type);render();return true;});MenuIcons.show(s.activity,menu);
            });
            s.gap(tile,8);tile.addView(s.text(map.optString("processor").isEmpty()?UiLanguage.t("Mapa importado"):map.optString("processor"),12,ReferenceStudio.MUTED,false));rows.addView(tile);s.gap(rows,10);
        }
        if(draft.controls.length()==0){s.gap(rows,32);rows.addView(s.text(UiLanguage.t("Um bloco, várias camadas"),20,ReferenceStudio.INK,true));s.gap(rows,8);rows.addView(s.text(UiLanguage.t("Comece com depth, pose ou lineart. Importe um mapa pronto ou extraia de uma imagem."),14,ReferenceStudio.MUTED,false));}
        save.setEnabled(!busy&&draft.controls.length()>0);add.setEnabled(!busy&&draft.controls.length()<ControlFragments.TYPES.length);
    }
    void chooseType(){PopupMenu menu=new OrganicPopupMenu(s.activity,add);for(String type:ControlFragments.TYPES)if(!draft.controls.has(type))menu.getMenu().add(ControlFragments.label(type)).setIcon(FlowDesign.icon(s.activity,ControlFragments.icon(type),22)).setOnMenuItemClickListener(it->{chooseSource(type,add);return true;});MenuIcons.show(s.activity,menu);}
    void chooseSource(String type,View anchor){
        PopupMenu menu=new OrganicPopupMenu(s.activity,anchor);
        menu.getMenu().add(UiLanguage.t("Importar mapa pronto")).setOnMenuItemClickListener(it->{pick(type,false,anchor);return true;});
        menu.getMenu().add(UiLanguage.t("Extrair de imagem")).setOnMenuItemClickListener(it->{pick(type,true,anchor);return true;});MenuIcons.show(s.activity,menu);
    }
    void pick(String type,boolean extract,View anchor){
        PopupMenu menu=new OrganicPopupMenu(s.activity,anchor);for(boolean library:new boolean[]{false,true})menu.getMenu().add(UiLanguage.t(library?"Biblioteca":"Imagens")).setOnMenuItemClickListener(it->{s.host.choose(library,1,images->{if(closed||images.isEmpty())return;ReferencePreset.Picture image=images.get(0);if(extract)extract(type,image);else try{ControlFragments.put(draft,type,image,null,"");status.setText("");render();}catch(Exception error){s.toast(error.getMessage());}});return true;});MenuIcons.show(s.activity,menu);
    }
    void extract(String type,ReferencePreset.Picture source){
        if(busy||closed)return;if(source.hidden&&!s.host.nsfw()){s.toast(UiLanguage.t("Ative o modo privado para usar esta referência."));return;}
        busy=true;render();String address=s.host.address();
        s.jobs.worker.execute(()->{
            try{
                ControlFragments.Result result=ControlFragments.extract(s.jobs.server,address,s.store.file(source.file),type,()->closed,message->s.activity.runOnUiThread(()->{if(!closed)status.setText(message);}));
                BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(result.image,0,result.image.length,bounds);
                if(bounds.outWidth<1||bounds.outHeight<1||(long)bounds.outWidth*bounds.outHeight>16_777_216L)throw new IllegalArgumentException(UiLanguage.t("Imagem inválida."));
                Bitmap bitmap=BitmapFactory.decodeByteArray(result.image,0,result.image.length);if(bitmap==null)throw new IllegalArgumentException(UiLanguage.t("Imagem inválida."));
                String filename=UUID.randomUUID()+".png";try(OutputStream out=VaultFiles.output(s.store.file(filename))){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException(UiLanguage.t("PNG encoding failed"));}finally{bitmap.recycle();}
                ReferencePreset.Picture map=new ReferencePreset.Picture(filename,source.source,source.hidden);
                s.activity.runOnUiThread(()->{if(closed)return;try{ControlFragments.put(draft,type,map,source,result.processor);status.setText(UiLanguage.t("Mapa pronto. Salve o bloco para guardar."));}catch(Exception error){status.setText(error.getMessage());}finally{busy=false;render();}});
            }catch(InterruptedException ignored){}
            catch(Exception error){s.activity.runOnUiThread(()->{if(!closed){busy=false;status.setText(error.getMessage());render();}});}
        });
    }
}
