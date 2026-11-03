package com.leal.comfyremote;

import android.app.AlertDialog;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** The same library works as a full navigation destination and a quick prompt picker. */
final class FragmentLibrary {
    final ReferenceStudio s;final LinearLayout view,list,tabs;final EditText search;final Button add;final boolean full;
    final HorizontalScrollView strip;final Button previous,next;
    final String[] kinds={"style","character","location","object","composition","control","audio"};
    final String[] names={"Estilos","Personagens","Locais","Objetos","Composições","Blocos","Áudios"};
    final String[] icons={"style","person","location","object","boxes","layers","wave"};
    int tab;
    FragmentLibrary(ReferenceStudio studio,int first,boolean embedded){
        s=studio;full=embedded;tab=Math.max(0,Math.min(kinds.length-1,first));list=s.column();
        view=full?s.column():s.page(UiLanguage.t("Fragmentos"),()->{if(s.manager!=null)s.manager.dismiss();});
        if(full){view.setPadding(s.dp(12),s.dp(8),s.dp(12),s.dp(12));}
        search=s.field(UiLanguage.t("Buscar fragmentos"),1);search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);view.addView(search,new LinearLayout.LayoutParams(-1,s.dp(48)));s.gap(view,12);
        LinearLayout navigation=s.row();previous=s.icon("left",UiLanguage.t("Categorias anteriores"));next=s.icon("right",UiLanguage.t("Mais categorias"));previous.setBackgroundColor(Color.TRANSPARENT);next.setBackgroundColor(Color.TRANSPARENT);navigation.addView(previous,new LinearLayout.LayoutParams(s.dp(36),s.dp(48)));
        strip=new HorizontalScrollView(s.activity);strip.setHorizontalScrollBarEnabled(false);strip.setHorizontalFadingEdgeEnabled(true);strip.setFadingEdgeLength(s.dp(20));tabs=s.row();strip.addView(tabs);navigation.addView(strip,new LinearLayout.LayoutParams(0,s.dp(48),1));navigation.addView(next,new LinearLayout.LayoutParams(s.dp(36),s.dp(48)));view.addView(navigation);
        previous.setOnClickListener(v->strip.smoothScrollBy(-s.dp(200),0));next.setOnClickListener(v->strip.smoothScrollBy(s.dp(200),0));strip.getViewTreeObserver().addOnScrollChangedListener(this::arrows);
        ScrollView scroll=new ScrollView(s.activity);scroll.setVerticalScrollBarEnabled(false);scroll.addView(list);view.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        add=s.button("","plus");s.gap(view,10);view.addView(add,new LinearLayout.LayoutParams(-1,s.dp(48)));
        s.renderManager=this::render;
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence a,int b,int c,int d){}public void onTextChanged(CharSequence a,int b,int c,int d){render();}public void afterTextChanged(Editable e){}});
        render();revealTab();
    }
    void render(){
        tabs.removeAllViews();for(int i=0;i<kinds.length;i++){
            final int next=i;Button button=s.button(UiLanguage.t(names[i]),icons[i]);button.setSingleLine(true);
            button.setBackground(FlowDesign.shape(s.activity,next==tab?ReferenceStudio.CONTROL:Color.TRANSPARENT,22,false));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,s.dp(44));lp.rightMargin=s.dp(6);tabs.addView(button,lp);
            button.setOnClickListener(v->{tab=next;render();revealTab();UiMotion.enter(list);});
        }
        list.removeAllViews();s.gap(list,12);String filter=search.getText().toString().trim().toLowerCase(Locale.ROOT);int count=0;
        List<ReferencePreset> items=s.store.all();Collections.sort(items,(a,b)->a.name.compareToIgnoreCase(b.name));
        for(ReferencePreset p:items)if(p.kind.equals(kinds[tab])&&(!p.hidden()||s.host.nsfw())&&p.name.toLowerCase(Locale.ROOT).contains(filter)){
            count++;LinearLayout tile=s.row();tile.setPadding(s.dp(8),s.dp(8),s.dp(4),s.dp(8));tile.setBackground(FlowDesign.shape(s.activity,0xFF1B1C1E,22,false));
            ImageView cover=s.image(p,p.coverPicture(),64);tile.addView(cover,new LinearLayout.LayoutParams(s.dp(64),s.dp(64)));
            LinearLayout label=s.column();label.setPadding(s.dp(14),0,s.dp(8),0);TextView title=s.text(p.name,16,ReferenceStudio.INK,true);title.setMaxLines(2);title.setEllipsize(TextUtils.TruncateAt.END);label.addView(title);s.gap(label,4);
            label.addView(s.text(p.audio()?AudioTools.seconds(p.audioMillis):p.control()?ControlFragments.summary(p):p.kind.equals("composition")?UiLanguage.t("Boxes editáveis"):s.host.tag(p),12,ReferenceStudio.MUTED,false));
            tile.addView(label,new LinearLayout.LayoutParams(0,-2,1));View.OnClickListener open=v->{activate();if(full||p.control())s.edit(p);else{if(s.manager!=null)s.manager.dismiss();s.use(p);}};cover.setOnClickListener(open);label.setOnClickListener(open);
            Button more=s.icon("more",UiLanguage.t("Opções de ")+p.name);tile.addView(more,new LinearLayout.LayoutParams(s.dp(44),s.dp(48)));
            more.setOnClickListener(v->{activate();PopupMenu menu=new OrganicPopupMenu(s.activity,more);
                menu.getMenu().add(UiLanguage.t("Editar")).setOnMenuItemClickListener(it->{s.edit(p);return true;});
                if(!p.control())menu.getMenu().add(UiLanguage.t(p.audio()?"Usar no Lipsync":"Usar no prompt")).setOnMenuItemClickListener(it->{if(s.manager!=null)s.manager.dismiss();s.use(p);return true;});
                menu.getMenu().add(UiLanguage.t("Duplicar")).setOnMenuItemClickListener(it->{ReferencePreset copy=p.copy();copy.id=UUID.randomUUID().toString();copy.name=p.name+" · "+UiLanguage.t("Cópia");s.edit(copy);return true;});
                menu.getMenu().add(UiLanguage.t("Apagar")).setOnMenuItemClickListener(it->{new FlowDialogs.Builder(s.activity).setTitle(UiLanguage.t("Apagar fragmento?")).setMessage(UiLanguage.t("Remove este fragmento. Gerações e arquivos do ComfyUI permanecem.")).setNegativeButton(UiLanguage.t("Cancelar"),null).setPositiveButton(UiLanguage.t("Apagar"),(d,w)->{s.store.delete(p.id);render();}).show();return true;});MenuIcons.show(s.activity,menu);
            });list.addView(tile);s.gap(list,8);
        }
        if(count==0){s.gap(list,24);list.addView(s.text(filter.isEmpty()?UiLanguage.t("Seu repertório começa aqui"):UiLanguage.t("Nenhum fragmento encontrado"),20,ReferenceStudio.INK,true));s.gap(list,8);list.addView(s.text(UiLanguage.t(tab==6?"Importe ou grave falas e sons para reutilizar no Lipsync.":tab==5?"Reúna mapas de depth, pose e traços em blocos reutilizáveis.":"Guarde personagens, estilos, lugares, objetos e composições para suas próximas criações."),14,ReferenceStudio.MUTED,false));}
        String[] labels={"Novo estilo","Novo personagem","Novo local","Novo objeto","Nova composição","Novo bloco","Novo áudio"};add.setText(UiLanguage.t(labels[tab]));
        add.setOnClickListener(v->{activate();ReferencePreset p=new ReferencePreset();p.kind=kinds[tab];s.edit(p);});
        strip.post(this::arrows);
    }
    void revealTab(){strip.post(()->{View selected=tabs.getChildAt(tab);if(selected!=null)strip.smoothScrollTo(Math.max(0,selected.getLeft()-(strip.getWidth()-selected.getWidth())/2),0);arrows();});}
    void arrows(){previous.setEnabled(strip.canScrollHorizontally(-1));next.setEnabled(strip.canScrollHorizontally(1));previous.setAlpha(previous.isEnabled()?1:.25f);next.setAlpha(next.isEnabled()?1:.25f);}
    private void activate(){s.managingFullArea=full;s.renderManager=this::render;}
}
