package com.leal.comfyremote;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;

/** App chrome for platform alerts; retains native choice, cancellation and button semantics. */
final class FlowDialogs {
    static final class Builder extends AlertDialog.Builder {
        Builder(Context context){super(context);}
        @Override public AlertDialog create(){
            AlertDialog dialog=super.create();
            Window window=dialog.getWindow();
            if(window!=null){
                window.setBackgroundDrawable(FlowDesign.shape(getContext(),FlowDesign.SURFACE,28,true));
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_SECURE);
                window.setDimAmount(.62f);
                UiMotion.dialog(dialog);
                View decor=window.getDecorView();
                // An attach listener does not replace callers' validation/on-show handlers.
                decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
                    public void onViewAttachedToWindow(View view){view.post(()->style(dialog));}
                    public void onViewDetachedFromWindow(View view){}
                });
            }
            return dialog;
        }
    }
    static void style(AlertDialog dialog){
        Window window=dialog.getWindow();if(window==null||!dialog.isShowing())return;
        Context context=dialog.getContext();View decor=window.getDecorView();
        int available=context.getResources().getDisplayMetrics().widthPixels-FlowDesign.dp(context,40);
        window.setLayout(Math.min(available,FlowDesign.dp(context,440)),WindowManager.LayoutParams.WRAP_CONTENT);
        decor.setClipToOutline(true);
        typography(decor);
        int titleId=context.getResources().getIdentifier("alertTitle","id","android");
        TextView title=decor.findViewById(titleId);
        if(title!=null){title.setTypeface(Branding.secondary(context,650));title.setTextSize(20);title.setTextColor(FlowDesign.INK);title.setSingleLine(false);}
        TextView message=decor.findViewById(android.R.id.message);
        if(message!=null){message.setTextColor(0xFFB8BABF);message.setLineSpacing(FlowDesign.dp(context,3),1);message.setTextSize(14);}
        int panelId=context.getResources().getIdentifier("buttonPanel","id","android");
        View panel=decor.findViewById(panelId);
        if(panel instanceof ViewGroup){ViewGroup group=(ViewGroup)panel;group.setClipChildren(false);group.setClipToPadding(false);group.setPadding(FlowDesign.dp(context,18),FlowDesign.dp(context,10),FlowDesign.dp(context,18),FlowDesign.dp(context,14));ViewGroup.LayoutParams lp=group.getLayoutParams();lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;group.setLayoutParams(lp);}
        View custom=decor.findViewById(android.R.id.custom);
        if(custom!=null)custom.setPadding(FlowDesign.dp(context,24),FlowDesign.dp(context,4),FlowDesign.dp(context,24),FlowDesign.dp(context,4));
        for(int which:new int[]{AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL,AlertDialog.BUTTON_POSITIVE}){
            Button button=dialog.getButton(which);if(button==null||button.getVisibility()!=View.VISIBLE)continue;
            boolean primary=which==AlertDialog.BUTTON_POSITIVE;
            button.setAllCaps(false);button.setTypeface(Branding.secondary(context,600));button.setTextSize(14);
            button.setTextColor(primary?0xFF111214:FlowDesign.INK);
            button.setBackgroundTintList(null);
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(primary?0x22000000:0x22FFFFFF),FlowDesign.shape(context,primary?Appearance.accent():FlowDesign.CONTROL,18,false),null));
            button.setMinimumHeight(FlowDesign.dp(context,48));button.setMinHeight(FlowDesign.dp(context,48));
            button.setPadding(FlowDesign.dp(context,18),FlowDesign.dp(context,10),FlowDesign.dp(context,18),FlowDesign.dp(context,10));
            ViewGroup.LayoutParams lp=button.getLayoutParams();lp.height=ViewGroup.LayoutParams.WRAP_CONTENT;if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams margin=(ViewGroup.MarginLayoutParams)lp;margin.setMarginStart(FlowDesign.dp(context,6));margin.topMargin=0;margin.bottomMargin=0;}button.setLayoutParams(lp);
        }
        ListView choices=dialog.getListView();
        if(choices!=null){choices.setDivider(null);choices.setDividerHeight(0);choices.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){typography(child);}public void onChildViewRemoved(View parent,View child){}});typography(choices);}
        Appearance.refresh(decor);
    }
    private static void typography(View view){
        if(view instanceof TextView){TextView label=(TextView)view;label.setTypeface(Branding.body(view.getContext(),500));label.setTextColor(FlowDesign.INK);if(!(view instanceof EditText))label.setTextSize(14);}
        if(view instanceof CheckedTextView)((CheckedTextView)view).setCheckMarkTintList(ColorStateList.valueOf(Appearance.accent()));
        if(view instanceof CompoundButton)((CompoundButton)view).setButtonTintList(ColorStateList.valueOf(Appearance.accent()));
        if(view instanceof EditText){EditText input=(EditText)view;Context context=view.getContext();input.setTextSize(16);input.setHintTextColor(FlowDesign.MUTED);input.setBackgroundTintList(null);input.setBackground(FlowDesign.shape(context,FlowDesign.CONTROL,18,false));input.setPadding(FlowDesign.dp(context,14),FlowDesign.dp(context,12),FlowDesign.dp(context,14),FlowDesign.dp(context,12));input.setMinimumHeight(FlowDesign.dp(context,52));}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)typography(group.getChildAt(i));}
    }
}
