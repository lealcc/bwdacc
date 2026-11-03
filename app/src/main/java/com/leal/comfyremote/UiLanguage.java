package com.leal.comfyremote;

import android.app.Activity;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** UI-only localization. Prompts, model names, paths and workflow values stay untouched. */
final class UiLanguage {
    static final String[] TAGS={"pt-BR","en","ru","de","ja","zh-Hans","es"};
    static final String[] NAMES={"Português (Brasil)","English","Русский","Deutsch","日本語","简体中文","Español"};
    private static volatile Context resources;
    private static final Map<String,String> translations=new ConcurrentHashMap<>(), originals=new ConcurrentHashMap<>();
    static String tag(Context c){
        if(Build.VERSION.SDK_INT>=33){LocaleList locales=c.getSystemService(LocaleManager.class).getApplicationLocales();if(!locales.isEmpty())return locales.get(0).toLanguageTag();}
        return c.getSharedPreferences("interface",0).getString("language","pt-BR");
    }
    static Context wrap(Context c){Configuration config=new Configuration(c.getResources().getConfiguration());config.setLocale(Locale.forLanguageTag(tag(c)));return c.createConfigurationContext(config);}
    static void init(Context c){Context app=c.getApplicationContext();resources=wrap(app==null?c:app);translations.clear();originals.clear();}
    static int index(Context c){String tag=tag(c);for(int i=0;i<TAGS.length;i++)if(TAGS[i].equalsIgnoreCase(tag)||Locale.forLanguageTag(TAGS[i]).getLanguage().equals(Locale.forLanguageTag(tag).getLanguage()))return i;return 0;}
    static String display(Context c){return NAMES[index(c)];}
    static void select(Activity a,int index){
        if(index<0||index>=TAGS.length)return;
        a.getSharedPreferences("interface",0).edit().putString("language",TAGS[index]).putBoolean("reopenSettings",true).commit();
        if(Build.VERSION.SDK_INT>=33){LocaleManager manager=a.getSystemService(LocaleManager.class);String before=manager.getApplicationLocales().toLanguageTags();manager.setApplicationLocales(LocaleList.forLanguageTags(TAGS[index]));if(!before.equals(TAGS[index]))return;}
        a.recreate();
    }
    static boolean reopenSettings(Context c){boolean reopen=c.getSharedPreferences("interface",0).getBoolean("reopenSettings",false);c.getSharedPreferences("interface",0).edit().remove("reopenSettings").apply();return reopen;}
    static String t(String source){
        Context c=resources;if(c==null||source==null||source.isEmpty())return source;
        String cached=translations.get(source);if(cached!=null)return cached;
        int id=c.getResources().getIdentifier("ui_"+Integer.toHexString(source.hashCode()),"string",c.getPackageName());
        String value=id==0?source:c.getString(id);translations.put(source,value);originals.put(value,source);return value;
    }
    static String original(String label){return originals.getOrDefault(label,label);}
}
