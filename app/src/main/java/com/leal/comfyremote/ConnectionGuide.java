package com.leal.comfyremote;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;

final class ConnectionGuide {
    static FlowSheet show(Activity a){
        FlowSheet s=new FlowSheet(a,UiLanguage.t("Fora de casa"),"connection");
        s.note(UiLanguage.t("Seu PC e celular, na mesma rede privada do Tailscale."));
        step(s,"1",UiLanguage.t("Conecte os dois aparelhos"),UiLanguage.t("Use sua própria conta Tailscale no PC e no celular."));
        step(s,"2",UiLanguage.t("Escolha a rede no Desktop"),UiLanguage.t("Em Dispositivos, selecione o endereço Tailscale e ative a conexão."));
        step(s,"3",UiLanguage.t("Pareie com o novo QR"),UiLanguage.t("Leia o QR no celular e confirme a solicitação no computador."));
        s.gap(s.body,8);s.action(UiLanguage.t("Abrir Tailscale"),"connection",()->{
            try{Intent launch=a.getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");a.startActivity(launch!=null?launch:new Intent(Intent.ACTION_VIEW,Uri.parse("https://tailscale.com/download/android")));}
            catch(Exception e){Toast.makeText(a,UiLanguage.t("Abra Tailscale no celular ou instale pelo site tailscale.com."),Toast.LENGTH_LONG).show();}
        });
        s.action(UiLanguage.t("Problemas com a internet?"),"info",()->{FlowSheet help=new FlowSheet(a,UiLanguage.t("Conexão do Tailscale"),"connection");help.note(UiLanguage.t("Não precisa de nó de saída (exit node). Se a internet parar, confira nó de saída e DNS no Tailscale. Android permite apenas uma VPN ativa. Na mesma rede Wi-Fi, você também pode usar o app sem Tailscale."));help.show();});s.show();return s;
    }
    private static void step(FlowSheet s,String n,String title,String body){
        LinearLayout row=s.row();TextView number=s.text(n,17,true);number.setGravity(17);number.setBackground(FlowDesign.pill(s.activity,FlowDesign.CONTROL,16,false));row.addView(number,new LinearLayout.LayoutParams(s.dp(38),s.dp(38)));LinearLayout words=s.column();words.setPadding(s.dp(12),0,0,0);words.addView(s.text(title,15,true));s.gap(words,4);words.addView(s.text(body,13,false));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));s.body.addView(row);s.gap(s.body,20);
    }
}
