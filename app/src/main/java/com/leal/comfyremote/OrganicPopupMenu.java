package com.leal.comfyremote;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.PopupMenu;

/** Native menu semantics, with a spring entrance originating at the trigger's corner. */
final class OrganicPopupMenu extends PopupMenu {
    private final ContextThemeWrapper themed;
    private final View anchor;
    OrganicPopupMenu(Context context,View anchor){
        this(new ContextThemeWrapper(context,context.getResources().getIdentifier("Theme.Momentos","style",context.getPackageName())),anchor);
    }
    private OrganicPopupMenu(ContextThemeWrapper context,View anchor){super(context,anchor);this.themed=context;this.anchor=anchor;}
    @Override public void show(){
        if(!anchor.isAttachedToWindow())return;
        int rows=0;for(int i=0;i<getMenu().size();i++)if(getMenu().getItem(i).isVisible())rows++;
        float fontScale=themed.getResources().getConfiguration().fontScale;
        int height=FlowDesign.dp(themed,16+rows*48*Math.max(1,fontScale));
        String corner=UiMotion.menuCorner(anchor,height);
        int style=themed.getResources().getIdentifier("Momentos.MenuTheme."+(UiMotion.enabled()?corner:"Still"),"style",themed.getPackageName());
        themed.getTheme().applyStyle(style,true);super.show();
    }
}
