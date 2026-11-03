package com.leal.comfyremote;

import android.graphics.Typeface;
import android.os.Build;
import android.widget.TextView;
import android.content.Context;

/** The wordmark uses the bundled OFL font; no download is needed on the phone. */
final class Branding {
    private static Typeface wordmark;
    private static final android.util.SparseArray<Typeface> bodyFonts=new android.util.SparseArray<>();
    private static final android.util.SparseArray<Typeface> secondaryFonts=new android.util.SparseArray<>();
    static Typeface secondary(Context context,int weight){
        // Syne provides a naturally wider, lower silhouette; long copy keeps Manrope.
        Typeface font=secondaryFonts.get(weight);if(font!=null)return font;
        if(Build.VERSION.SDK_INT>=26)font=new Typeface.Builder(context.getAssets(),"fonts/Syne.ttf").setFontVariationSettings("'wght' "+weight).setWeight(weight).build();
        else font=Typeface.createFromAsset(context.getAssets(),"fonts/Syne.ttf");
        secondaryFonts.put(weight,font);return font;
    }
    static Typeface body(Context context,int weight){
        Typeface font=bodyFonts.get(weight);if(font!=null)return font;
        if(Build.VERSION.SDK_INT>=26)font=new Typeface.Builder(context.getAssets(),"fonts/Manrope.ttf").setFontVariationSettings("'wght' "+weight).setWeight(weight).build();
        else font=Typeface.create("sans-serif",weight>=600?Typeface.BOLD:Typeface.NORMAL);
        bodyFonts.put(weight,font);return font;
    }
    static void apply(TextView view){
        if(wordmark==null){Typeface base=Typeface.createFromAsset(view.getContext().getAssets(),"fonts/Syne.ttf");wordmark=Build.VERSION.SDK_INT>=28?Typeface.create(base,800,false):Typeface.create(base,Typeface.BOLD);}
        view.setTypeface(wordmark);view.setLetterSpacing(-.045f);view.setTextSize(25);view.setIncludeFontPadding(false);
    }
}
