package com.leal.comfyremote;

import android.content.Context;
import android.view.MenuItem;
import android.widget.PopupMenu;
import java.util.Locale;

/** Action symbols are resolved from source labels, independently of the UI language. */
final class MenuIcons {
    static String kind(String title) {
        String label=UiLanguage.original(title).toLowerCase(Locale.ROOT);
        if(label.contains("áudio"))return "wave";
        if(label.equals("duplicar"))return "layers";
        if(label.equals("remover mapa"))return "delete";
        if(label.equals("importar mapa pronto")||label.equals("substituir mapa"))return "image";
        if(label.contains("composição")||label.contains("boxes"))return "boxes";
        if(label.equals("selecionar objeto"))return "scan";
        if(label.contains("objeto"))return "object";
        if(label.contains("nsfw"))return label.startsWith("remover")?"tag-off":"tag";
        if(label.startsWith("apagar")||label.startsWith("excluir")||label.equals("remover imagem"))return "delete";
        if(label.startsWith("upscale"))return "upscale";
        if(label.equals("remover fundo"))return "cutout";
        if(label.equals("expandir imagem"))return "outpaint";
        if(label.contains("lipsync"))return "lipsync";
        if(label.startsWith("estender"))return "extend";
        if(label.startsWith("criar personagem"))return "person-add";
        if(label.startsWith("ver versões")||label.equals("abrir conjunto"))return "layers";
        if(label.equals("adicionar a projeto")||label.startsWith("novo projeto"))return "folder-add";
        if(label.equals("remover deste projeto"))return "folder-remove";
        if(label.equals("adicionar gerações"))return "plus";
        if(label.startsWith("editar")||label.equals("renomear"))return "edit";
        if(label.equals("verificar resultado"))return "refresh";
        if(label.contains("prompt"))return "reuse";
        if(label.startsWith("animar")||label.contains("vídeo"))return "video";
        if(label.contains("imagem")||label.contains("imagens")||label.contains("capa"))return "image";
        if(label.contains("biblioteca"))return "grid";
        if(label.contains("referências")||label.contains("fragmentos")||label.contains("bloco"))return "layers";
        if(label.contains("estilo"))return "style";
        if(label.contains("personage"))return "person";
        if(label.contains("loca"))return "location";
        if(label.startsWith("voltar"))return "left";
        return "more";
    }
    static void show(Context context,PopupMenu menu) {
        for(int i=0;i<menu.getMenu().size();i++){
            MenuItem item=menu.getMenu().getItem(i);
            if(item.getIcon()==null)item.setIcon(FlowDesign.icon(context,kind(item.getTitle().toString()),22));
        }
        if(android.os.Build.VERSION.SDK_INT>=29)menu.setForceShowIcon(true);
        menu.show();
    }
}
