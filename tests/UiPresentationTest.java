package com.leal.comfyremote;

import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Looper;
import java.lang.reflect.*;
import java.util.*;

/** Uses installed Android resources, fonts and Canvas rather than translation stubs. */
public final class UiPresentationTest {
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception{
        if(Looper.myLooper()==null)Looper.prepareMainLooper();
        Class<?> thread=Class.forName("android.app.ActivityThread");
        Object instance=thread.getMethod("systemMain").invoke(null);
        // app_process has no zygote font preload; initialize the real system fallback map.
        Method preload=Typeface.class.getDeclaredMethod("loadPreinstalledSystemFontMap");preload.setAccessible(true);preload.invoke(null);
        Context system=(Context)thread.getMethod("getSystemContext").invoke(instance);
        Context app=system.createPackageContext("com.leal.bwdacc.commercial",Context.CONTEXT_IGNORE_SECURITY);
        Field context=UiLanguage.class.getDeclaredField("resources");context.setAccessible(true);
        String[] expected={"Ajustes","Settings","Настройки","Einstellungen","設定","设置","Ajustes"};
        for(int i=0;i<UiLanguage.TAGS.length;i++){
            Configuration config=new Configuration(app.getResources().getConfiguration());config.setLocale(Locale.forLanguageTag(UiLanguage.TAGS[i]));
            Context localized=app.createConfigurationContext(config);context.set(null,localized);
            for(String field:new String[]{"translations","originals"}){Field cache=UiLanguage.class.getDeclaredField(field);cache.setAccessible(true);((Map<?,?>)cache.get(null)).clear();}
            check(UiLanguage.t("Ajustes").equals(expected[i]),"compiled locale "+UiLanguage.TAGS[i]);
            check(UiLanguage.original(UiLanguage.t("Reutilizar prompt")).equals("Reutilizar prompt"),"menu action identity "+UiLanguage.TAGS[i]);
            String[][] actions={{"Remover marcação NSFW","tag-off"},{"Marcar como NSFW","tag"},{"Criar personagem com esta capa","person-add"},{"Ver versões e editar","layers"},{"Adicionar a projeto","folder-add"},{"Excluir vídeo","delete"},{"Editar vídeo","edit"},{"Remover deste projeto","folder-remove"}};
            for(String[] action:actions)check(MenuIcons.kind(UiLanguage.t(action[0])).equals(action[1]),"localized menu icon "+UiLanguage.TAGS[i]+" / "+action[0]);
            String prompt="@Mira $Film #Beach -- natural light 35mm";
            check(UiLanguage.t(prompt).equals(prompt),"user prompt must not be translated");
            check(UiLanguage.t("models/custom_name.safetensors").equals("models/custom_name.safetensors"),"model path identity");
            Paint text=new Paint();text.setTypeface(Branding.body(localized,500));check(text.hasGlyph(expected[i].substring(0,1)),"font fallback "+UiLanguage.TAGS[i]);
            check(text.getTypeface().getWeight()==500,"readable variable font weight");
        }
        context.set(null,null);
        Drawable background=Appearance.atmosphere(),surface=Appearance.surface(app,FlowDesign.SURFACE,28,0xFF323335);
        Bitmap bitmap=Bitmap.createBitmap(360,720,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);background.setBounds(0,0,360,720);surface.setBounds(20,500,340,690);
        Appearance.mode(app,false);background.draw(canvas);surface.draw(canvas);int neutral=bitmap.getPixel(180,600),neutralTop=bitmap.getPixel(280,100);
        Appearance.mode(app,true);background.draw(canvas);surface.draw(canvas);int violet=bitmap.getPixel(180,600);
        check(neutral==violet&&bitmap.getPixel(280,100)==neutralTop,"NSFW never tints surfaces or background");
        check(Appearance.icon(FlowDesign.INK)!=FlowDesign.INK,"NSFW affects icon accent only");
        check(bitmap.getPixel(280,100)!=bitmap.getPixel(40,400),"subtle depth gradient");check(Color.red(bitmap.getPixel(280,100))<40,"background remains dark");
        Appearance.mode(app,false);background.draw(canvas);surface.draw(canvas);check(bitmap.getPixel(180,600)==neutral&&bitmap.getPixel(280,100)==neutralTop,"return to neutral without recreating drawables");
        Appearance.light(true,-.8f,.2f);background.draw(canvas);surface.draw(canvas);int lit=bitmap.getPixel(240,580);
        Appearance.light(true,.8f,-.2f);background.draw(canvas);surface.draw(canvas);check(lit!=bitmap.getPixel(240,580),"silver shimmer follows tilt");
        Appearance.light(false,0,0);background.draw(canvas);surface.draw(canvas);check(bitmap.getPixel(180,600)==neutral,"shimmer opt-out restores neutral surface");
        bitmap.recycle();System.out.println("PASS: 7 compiled locales, prompt/path identity, fallback fonts, live palette and depth");
    }
}
