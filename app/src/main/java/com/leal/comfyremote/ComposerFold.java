package com.leal.comfyremote;
import android.animation.*;
import android.content.Context;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

/** Fold the presentation while preserving the actual input, references and draft. */
final class ComposerFold {
    final LinearLayout host,expanded;final Button compact;final EditText prompt;boolean collapsed;ValueAnimator motion;
    ComposerFold(LinearLayout host,EditText prompt,String label){
        this.host=host;this.prompt=prompt;Context c=host.getContext();expanded=new LinearLayout(c);expanded.setOrientation(LinearLayout.VERTICAL);
        while(host.getChildCount()>0){View child=host.getChildAt(0);host.removeViewAt(0);expanded.addView(child);}
        host.addView(expanded,new LinearLayout.LayoutParams(-1,-2));
        compact=new Button(c);compact.setAllCaps(false);compact.setText(label);compact.setTypeface(Branding.secondary(c,600));compact.setTextSize(13);compact.setTextColor(FlowDesign.INK);compact.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);compact.setPadding(FlowDesign.dp(c,8),0,FlowDesign.dp(c,8),0);compact.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        compact.setCompoundDrawables(FlowDesign.icon(c,"edit",18),null,FlowDesign.icon(c,"up",16),null);compact.setCompoundDrawablePadding(FlowDesign.dp(c,10));compact.setContentDescription(UiLanguage.t("Abrir compositor"));compact.setVisibility(View.GONE);host.addView(compact,new LinearLayout.LayoutParams(-1,FlowDesign.dp(c,44)));
        compact.setOnClickListener(v->{setCollapsed(false);prompt.postDelayed(()->{if(!collapsed&&prompt.isAttachedToWindow()){prompt.requestFocus();((InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(prompt,InputMethodManager.SHOW_IMPLICIT);}},UiMotion.enabled()?UiMotion.ENTER+20:0);});
        host.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}public void onViewDetachedFromWindow(View v){if(motion!=null)motion.cancel();}});
    }
    void setCollapsed(boolean value){
        if(collapsed==value)return;if(motion!=null)motion.cancel();collapsed=value;Context c=host.getContext();int from=host.getHeight();
        if(value){prompt.clearFocus();((InputMethodManager)c.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(prompt.getWindowToken(),0);}
        expanded.setVisibility(value?View.GONE:View.VISIBLE);compact.setVisibility(value?View.VISIBLE:View.GONE);
        host.setPadding(FlowDesign.dp(c,12),FlowDesign.dp(c,value?2:10),FlowDesign.dp(c,12),FlowDesign.dp(c,value?2:6));
        if(host.getWidth()<=0||from<=0)return;
        host.measure(View.MeasureSpec.makeMeasureSpec(host.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));int target=host.getMeasuredHeight();
        if(!UiMotion.enabled())return;
        ViewGroup.LayoutParams params=host.getLayoutParams();motion=ValueAnimator.ofInt(from,target);motion.setDuration(UiMotion.ENTER);motion.setInterpolator(UiMotion.EASE);
        motion.addUpdateListener(a->{params.height=(Integer)a.getAnimatedValue();host.setLayoutParams(params);});
        motion.addListener(new AnimatorListenerAdapter(){public void onAnimationEnd(Animator a){params.height=ViewGroup.LayoutParams.WRAP_CONTENT;host.setLayoutParams(params);}});motion.start();
    }
}
