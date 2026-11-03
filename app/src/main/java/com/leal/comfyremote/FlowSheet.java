package com.leal.comfyremote;

import android.app.*;
import android.view.*;
import android.widget.*;

/** Shared compact surfaces for connection and media tools. */
final class FlowSheet {
    final Activity activity;final Dialog dialog;final LinearLayout page,body;
    FlowSheet(Activity activity,String title,String icon){
        this.activity=activity;dialog=new Dialog(activity);page=column();page.setBackground(FlowDesign.pill(activity,FlowDesign.SURFACE,28,true));page.setPadding(dp(20),dp(16),dp(20),dp(20));
        LinearLayout header=row();ImageView glyph=new ImageView(activity);glyph.setImageDrawable(FlowDesign.icon(activity,icon,24));header.addView(glyph,new LinearLayout.LayoutParams(dp(34),dp(44)));
        TextView heading=text(title,21,true);heading.setPadding(dp(8),0,dp(8),0);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));Button close=button("");FlowDesign.symbol(close,"close",UiLanguage.t("Fechar"));header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));close.setOnClickListener(v->dialog.dismiss());page.addView(header);gap(page,16);
        body=column();ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
        dialog.setContentView(page);dialog.setOnShowListener(d->{Window w=dialog.getWindow();w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setLayout(-1,-2);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);w.setNavigationBarColor(FlowDesign.SURFACE);
            page.post(()->{int max=(int)(activity.getResources().getDisplayMetrics().heightPixels*.86f);if(page.getHeight()>max){scroll.getLayoutParams().height=Math.max(dp(100),max-dp(96));scroll.requestLayout();}});});
    }
    void show(){dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);UiMotion.dialog(dialog);dialog.show();}
    int dp(int n){return FlowDesign.dp(activity,n);}
    LinearLayout column(){LinearLayout l=new LinearLayout(activity);l.setOrientation(1);return l;}
    LinearLayout row(){LinearLayout l=new LinearLayout(activity);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    TextView text(String s,int size,boolean heading){TextView t=new TextView(activity);t.setText(s);t.setTextSize(size);t.setTextColor(heading?FlowDesign.INK:FlowDesign.MUTED);t.setTypeface(heading?Branding.secondary(activity,600):Branding.body(activity,400));return t;}
    Button button(String label){Button b=new FlowDesign.IconButton(activity);b.setText(label);b.setAllCaps(false);b.setTextColor(FlowDesign.INK);b.setTextSize(14);b.setTypeface(Branding.secondary(activity,500));b.setBackground(FlowDesign.pill(activity,FlowDesign.CONTROL,24,false));b.setPadding(dp(16),0,dp(16),0);b.setMinimumWidth(0);b.setMinWidth(0);return b;}
    Button action(String label,String icon,Runnable action){Button b=button(label);FlowDesign.labelIcon(b,icon);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.bottomMargin=dp(8);body.addView(b,p);b.setOnClickListener(v->action.run());return b;}
    EditText field(String hint){EditText f=new EditText(activity);f.setSingleLine(true);f.setHint(hint);f.setTextColor(FlowDesign.INK);f.setHintTextColor(FlowDesign.MUTED);f.setTextSize(16);f.setTypeface(Branding.body(activity,400));f.setBackground(FlowDesign.pill(activity,FlowDesign.CONTROL,20,false));f.setPadding(dp(16),dp(12),dp(16),dp(12));body.addView(f,new LinearLayout.LayoutParams(-1,dp(54)));gap(body,12);return f;}
    void note(String message){body.addView(text(message,13,false));gap(body,16);}
    void gap(LinearLayout parent,int size){parent.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(size)));}
}
