package com.leal.comfyremote;

import android.app.*;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Skippable introduction and searchable tool guides share the same offline content. */
final class HelpCenter {
    static final String COMPLETE="intro_complete", PROGRESS="intro_progress";
    final Activity activity;final SharedPreferences preferences;final Runnable connect;final HelpContent content;
    Dialog dialog;LinearLayout page;String screen="",topic="",query="";int index;boolean automatic;
    HelpCenter(Activity activity,Runnable connect)throws Exception {
        this.activity=activity;this.connect=connect;preferences=activity.getSharedPreferences("interface",0);content=new HelpContent(activity);
    }
    static boolean pending(SharedPreferences preferences){return !preferences.getBoolean(COMPLETE,false);}
    static int progress(SharedPreferences preferences,int count){return Math.max(0,Math.min(count-1,preferences.getInt(PROGRESS,0)));}
    static void complete(SharedPreferences preferences){preferences.edit().putBoolean(COMPLETE,true).remove(PROGRESS).apply();}
    String title(){return content.text("tutorials");}
    String subtitle(){return content.text("subtitle");}
    void start(Bundle state,boolean settingsReopened){
        if(state!=null&&!state.getString("help_screen","").isEmpty()){
            screen=state.getString("help_screen","");index=Math.max(0,Math.min(content.intro.length()-1,state.getInt("help_index",0)));
            automatic=state.getBoolean("help_automatic",false);query=state.getString("help_query","");topic=state.getString("help_topic","");
            if(!screen.equals("intro")&&!screen.equals("topic"))screen="library";
            if(screen.equals("topic")&&content.topic(topic)==null)screen="library";
            open();return;
        }
        if(!settingsReopened&&pending(preferences))introduction(true);
    }
    void library(){automatic=false;screen="library";query="";open();}
    void introduction(boolean firstRun){automatic=firstRun;index=firstRun?progress(preferences,content.intro.length()):0;screen="intro";open();}
    void open(){
        if(activity.isFinishing()||activity.isDestroyed())return;
        if(dialog==null){
            dialog=new Dialog(activity,android.R.style.Theme_Material_NoActionBar){@Override public void onBackPressed(){back();}};
            dialog.setCanceledOnTouchOutside(false);
            page=new LinearLayout(activity);page.setOrientation(1);page.setBackground(Appearance.atmosphere());
            page.setOnApplyWindowInsetsListener((v,insets)->{
                if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());page.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
                else page.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;
            });dialog.setContentView(page);
            final Dialog opened=dialog;dialog.setOnDismissListener(d->{if(dialog==opened){screen="";dialog=null;}});
        }
        UiMotion.dialog(dialog);dialog.show();Window window=dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.setStatusBarColor(Color.BLACK);window.setNavigationBarColor(Color.BLACK);window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setLayout(-1,-1);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        page.requestApplyInsets();render();
    }
    void save(Bundle state){if(dialog==null||!dialog.isShowing())return;
        state.putString("help_screen",screen);state.putInt("help_index",index);state.putBoolean("help_automatic",automatic);state.putString("help_topic",topic);state.putString("help_query",query);
    }
    void dispose(){Dialog closing=dialog;dialog=null;screen="";if(closing!=null)closing.dismiss();}
    void back(){
        if(screen.equals("intro")){if(index>0){index--;remember();render();}else finishIntro(false);}
        else if(screen.equals("topic")){screen="library";render();}
        else dispose();
    }
    private void remember(){if(automatic)preferences.edit().putInt(PROGRESS,index).apply();}
    private void finishIntro(boolean pairing){
        if(automatic){complete(preferences);dispose();if(pairing)connect.run();}
        else if(pairing){dispose();connect.run();}
        else{screen="library";render();}
    }
    private void render(){
        page.removeAllViews();
        if(screen.equals("intro"))renderIntro();else if(screen.equals("topic"))renderTopic();else renderLibrary();
        Appearance.refresh(page);
    }
    private LinearLayout header(String title,String symbol,Runnable action){
        LinearLayout bar=row();bar.setPadding(dp(12),dp(8),dp(16),dp(4));
        if(symbol!=null){Button back=icon(symbol,content.text("back"));bar.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->action.run());}
        TextView heading=text(title,21,true);bar.addView(heading,new LinearLayout.LayoutParams(0,-2,1));page.addView(bar);return bar;
    }
    private LinearLayout body(){
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body=column();body.setPadding(dp(24),dp(20),dp(24),dp(20));scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return body;
    }
    private void renderIntro(){
        LinearLayout top=header("bwdacc",null,null);Branding.apply((TextView)top.getChildAt(0));
        Button skip=button(content.text("skip"),false);top.addView(skip,new LinearLayout.LayoutParams(-2,dp(48)));skip.setOnClickListener(v->finishIntro(false));
        JSONObject slide=content.intro.optJSONObject(index);LinearLayout body=body();body.setGravity(Gravity.CENTER_VERTICAL);
        Illustration illustration=new Illustration(activity,slide.optString("icon"));int height=Math.max(120,Math.min(210,(int)(activity.getResources().getDisplayMetrics().heightPixels/activity.getResources().getDisplayMetrics().density*.25f)));
        body.addView(illustration,new LinearLayout.LayoutParams(-1,dp(height)));gap(body,28);
        body.addView(text(slide.optString("title"),28,true));gap(body,12);body.addView(text(slide.optString("body"),16,false));gap(body,20);
        LinearLayout note=column();note.setPadding(dp(16),dp(14),dp(16),dp(14));note.setBackground(FlowDesign.shape(activity,FlowDesign.SURFACE,20,true));
        note.addView(text(slide.optString("hint"),13,false));body.addView(note);UiMotion.enter(body);
        LinearLayout footer=column();footer.setPadding(dp(24),dp(8),dp(24),dp(16));page.addView(footer);
        LinearLayout dots=row();dots.setGravity(Gravity.CENTER);dots.setContentDescription((index+1)+" / "+content.intro.length());dots.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        for(int n=0;n<content.intro.length();n++){View dot=new View(activity);dot.setBackground(FlowDesign.shape(activity,n==index?FlowDesign.INK:FlowDesign.CONTROL,4,false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(n==index?24:6),dp(6));p.setMargins(dp(3),dp(4),dp(3),dp(16));dots.addView(dot,p);}footer.addView(dots);
        boolean last=index==content.intro.length()-1;
        Button next=button(content.text(last?(automatic?"explore":"tutorials"):"next"),true);footer.addView(next,new LinearLayout.LayoutParams(-1,dp(52)));
        next.setOnClickListener(v->{if(last)finishIntro(false);else{index++;remember();render();}});
        if(last){Button pairing=button(content.text("connect"),false);footer.addView(pairing,new LinearLayout.LayoutParams(-1,dp(48)));pairing.setOnClickListener(v->finishIntro(true));}
        else if(index>0){Button previous=button(content.text("back"),false);footer.addView(previous,new LinearLayout.LayoutParams(-1,dp(48)));previous.setOnClickListener(v->back());}
    }
    private void renderLibrary(){
        header(title(),"left",this::dispose);LinearLayout body=body();body.setPadding(dp(20),dp(8),dp(20),dp(20));body.addView(text(subtitle(),14,false));gap(body,16);
        Button intro=button(content.text("replay"),false);FlowDesign.labelIcon(intro,"play");intro.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);body.addView(intro,new LinearLayout.LayoutParams(-1,dp(52)));intro.setOnClickListener(v->introduction(false));gap(body,12);
        EditText search=new EditText(activity);search.setSingleLine(true);search.setTextSize(15);search.setTypeface(Branding.body(activity,400));search.setTextColor(FlowDesign.INK);search.setHintTextColor(FlowDesign.MUTED);search.setHint(content.text("search"));search.setContentDescription(content.text("search"));search.setPadding(dp(16),dp(10),dp(16),dp(10));search.setBackground(FlowDesign.shape(activity,FlowDesign.SURFACE,18,true));search.setText(query);
        body.addView(search,new LinearLayout.LayoutParams(-1,dp(52)));gap(body,16);LinearLayout list=column();body.addView(list);renderResults(list);
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();renderResults(list);}public void afterTextChanged(android.text.Editable e){}});
        body.setFocusableInTouchMode(true);body.requestFocus();
    }
    private void renderResults(LinearLayout list){
        list.removeAllViews();for(JSONObject item:content.search(query)){
            LinearLayout card=row();card.setBackground(FlowDesign.pill(activity,FlowDesign.SURFACE,20,true));card.setPadding(dp(16),dp(20),dp(16),dp(20));
            ImageView icon=new ImageView(activity);icon.setImageDrawable(FlowDesign.icon(activity,item.optString("icon"),24));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,new LinearLayout.LayoutParams(dp(32),dp(40)));
            LinearLayout copy=column();copy.setPadding(dp(12),0,dp(8),0);copy.addView(text(item.optString("title"),16,true));gap(copy,4);copy.addView(text(item.optString("summary"),13,false));card.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
            ImageView arrow=new ImageView(activity);arrow.setImageDrawable(FlowDesign.icon(activity,"right",16));arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(arrow,new LinearLayout.LayoutParams(dp(20),dp(32)));
            card.setFocusable(true);card.setContentDescription(item.optString("title")+". "+item.optString("summary"));copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            card.setOnClickListener(v->{((android.view.inputmethod.InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(page.getWindowToken(),0);topic=item.optString("id");screen="topic";render();});
            list.addView(card,new LinearLayout.LayoutParams(-1,-2));
        }
        if(list.getChildCount()==0)list.addView(text(content.text("empty"),15,false));
    }
    private void renderTopic(){
        JSONObject item=content.topic(topic);if(item==null){screen="library";renderLibrary();return;}
        header(title(),"left",this::back);LinearLayout body=body();
        ImageView symbol=new ImageView(activity);symbol.setImageDrawable(FlowDesign.icon(activity,item.optString("icon"),30));symbol.setBackground(FlowDesign.shape(activity,FlowDesign.CONTROL,18,true));symbol.setPadding(dp(12),dp(12),dp(12),dp(12));symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);body.addView(symbol,new LinearLayout.LayoutParams(dp(56),dp(56)));gap(body,20);
        body.addView(text(item.optString("title"),25,true));gap(body,10);body.addView(text(item.optString("summary"),15,false));gap(body,24);
        JSONArray steps=item.optJSONArray("steps");for(int n=0;n<steps.length();n++){
            LinearLayout step=row();step.setGravity(Gravity.TOP);TextView number=text(String.valueOf(n+1),13,true);number.setGravity(Gravity.CENTER);number.setBackground(FlowDesign.shape(activity,FlowDesign.CONTROL,12,false));step.addView(number,new LinearLayout.LayoutParams(dp(28),dp(28)));
            TextView detail=text(steps.optString(n),15,false);detail.setTextColor(FlowDesign.INK);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.leftMargin=dp(14);step.addView(detail,p);body.addView(step);gap(body,24);
        }
        UiMotion.enter(body);
    }
    private int dp(float n){return FlowDesign.dp(activity,n);}
    private LinearLayout column(){LinearLayout v=new LinearLayout(activity);v.setOrientation(1);return v;}
    private LinearLayout row(){LinearLayout v=new LinearLayout(activity);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView text(String value,int size,boolean heading){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);v.setTextColor(heading?FlowDesign.INK:FlowDesign.MUTED);v.setTypeface(heading?Branding.secondary(activity,600):Branding.body(activity,400));v.setIncludeFontPadding(false);v.setLineSpacing(dp(3),1);return v;}
    private Button button(String value,boolean primary){Button v=new FlowDesign.IconButton(activity);v.setText(value);v.setAllCaps(false);v.setTextSize(14);v.setTypeface(Branding.secondary(activity,600));v.setTextColor(FlowDesign.INK);v.setBackground(FlowDesign.pill(activity,primary?FlowDesign.CONTROL:Color.TRANSPARENT,20,primary));v.setPadding(dp(16),0,dp(16),0);return v;}
    private Button icon(String kind,String label){Button v=button("",false);FlowDesign.symbol(v,kind,label);return v;}
    private void gap(LinearLayout parent,int size){parent.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(size)));}
    /** Decorative vector preview, independent of personal media and screen captures. */
    static final class Illustration extends View {
        final Paint paint=new Paint(3);final String symbol;
        Illustration(android.content.Context c,String symbol){super(c);this.symbol=symbol;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas canvas){
            float unit=Math.min(getWidth()/320f,getHeight()/190f);int save=canvas.save();canvas.translate((getWidth()-320*unit)/2,(getHeight()-190*unit)/2);canvas.scale(unit,unit);
            paint.setShader(new RadialGradient(160,85,145,new int[]{0x244F5668,0x004F5668},null,Shader.TileMode.CLAMP));canvas.drawRect(0,0,320,190,paint);paint.setShader(null);
            paint.setColor(0xff191b1e);canvas.save();canvas.rotate(-9,94,77);canvas.drawRoundRect(37,24,142,144,18,18,paint);canvas.restore();canvas.save();canvas.rotate(9,230,82);canvas.drawRoundRect(184,32,280,150,18,18,paint);canvas.restore();
            paint.setShader(new LinearGradient(106,18,226,152,new int[]{0xff424952,0xff1d2027},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(100,12,220,152,20,20,paint);paint.setShader(null);
            int center=canvas.save();canvas.translate(139,60);canvas.scale(1.75f,1.75f);FlowDesign.drawIcon(canvas,paint,symbol,0xffe3e6ec);canvas.restoreToCount(center);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xff272a2f);canvas.drawRoundRect(48,138,272,184,23,23,paint);paint.setColor(0xff9298a3);canvas.drawRoundRect(70,157,166,162,3,3,paint);paint.setColor(0xff454b54);canvas.drawRoundRect(181,147,228,175,14,14,paint);
            int arrow=canvas.save();canvas.translate(237,150);FlowDesign.drawIcon(canvas,paint,"arrow",0xffe3e6ec);canvas.restoreToCount(arrow);canvas.restoreToCount(save);
        }
    }
}
