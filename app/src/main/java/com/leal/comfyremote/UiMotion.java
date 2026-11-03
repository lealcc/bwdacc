package com.leal.comfyremote;

import android.animation.*;
import android.app.Dialog;
import android.graphics.Rect;
import android.view.*;
import android.view.animation.*;
import java.util.IdentityHashMap;
import java.util.Map;

/** Short, interruptible motion for chrome. Never captures or crossfades media snapshots. */
final class UiMotion {
    static final int ENTER=180,EXIT=130;
    static final Interpolator EASE=new DecelerateInterpolator(1.5f);
    private static final Map<View,Panel> panels=new IdentityHashMap<>();
    static boolean enabled(){return android.animation.ValueAnimator.areAnimatorsEnabled();}
    static void enter(View view){
        view.clearAnimation();if(!enabled()||!view.isAttachedToWindow())return;
        AnimationSet effect=new AnimationSet(true);effect.addAnimation(new AlphaAnimation(0,1));
        effect.addAnimation(new TranslateAnimation(0,0,FlowDesign.dp(view.getContext(),6),0));
        effect.setDuration(ENTER);effect.setInterpolator(EASE);view.startAnimation(effect);
    }
    static void dialog(Dialog dialog){
        Window window=dialog.getWindow();if(window!=null)window.setWindowAnimations(enabled()?dialog.getContext().getResources().getIdentifier("Momentos.Motion","style",dialog.getContext().getPackageName()):0);
    }
    static String menuCorner(View anchor,int menuHeight){
        Rect frame=new Rect();anchor.getWindowVisibleDisplayFrame(frame);int[] at=new int[2];anchor.getLocationOnScreen(at);
        int below=frame.bottom-at[1]-anchor.getHeight(),above=at[1]-frame.top;
        boolean opensUp=below<menuHeight&&above>below;
        boolean right=at[0]+anchor.getWidth()/2>frame.centerX();
        return (opensUp?"Bottom":"Top")+(right?"Right":"Left");
    }
    static OrganicMenuTransition menuTransition(View anchor,int height){String corner=menuCorner(anchor,height);return enabled()?new OrganicMenuTransition(anchor.getContext(),corner.endsWith("Right")?1:0,corner.startsWith("Bottom")?1:0):null;}
    static boolean showing(View view){Panel panel=panels.get(view);return panel==null?view.getVisibility()==View.VISIBLE:panel.show;}
    static void toggle(View view){panel(view,!showing(view));}
    static void hide(View view){if(view==null||view.getVisibility()==View.GONE&&!panels.containsKey(view))return;stop(view);view.setVisibility(View.GONE);}
    private static void stop(View view){Panel old=panels.remove(view);if(old!=null){old.animator.cancel();view.removeOnAttachStateChangeListener(old);}view.setAlpha(1);ViewGroup.LayoutParams p=view.getLayoutParams();if(p!=null&&p.height!=ViewGroup.LayoutParams.WRAP_CONTENT){p.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(p);}}
    static void panel(View view,boolean show){
        if(showing(view)==show)return;
        int from=view.getVisibility()==View.VISIBLE?view.getHeight():0;float alpha=view.getAlpha();stop(view);
        if(!enabled()||!view.isAttachedToWindow()||!(view.getParent() instanceof ViewGroup)){view.setVisibility(show?View.VISIBLE:View.GONE);return;}
        ViewGroup parent=(ViewGroup)view.getParent();ViewGroup.LayoutParams p=view.getLayoutParams();
        int width=parent.getWidth()-parent.getPaddingLeft()-parent.getPaddingRight();
        if(p instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)p;width-=m.leftMargin+m.rightMargin;}
        if(width<=0){view.setVisibility(show?View.VISIBLE:View.GONE);return;}
        view.setVisibility(View.VISIBLE);view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int to=show?view.getMeasuredHeight():0;Panel run=new Panel(view,show,from,to,from==0?0:alpha);panels.put(view,run);view.addOnAttachStateChangeListener(run);
        p.height=from;view.setLayoutParams(p);run.animator.start();
    }
    private static final class Panel extends AnimatorListenerAdapter implements View.OnAttachStateChangeListener {
        final View view;final boolean show;final ValueAnimator animator;
        Panel(View view,boolean show,int from,int to,float alpha){this.view=view;this.show=show;animator=ValueAnimator.ofFloat(0,1);animator.setDuration(show?ENTER:EXIT);animator.setInterpolator(EASE);
            animator.addUpdateListener(a->{float f=(Float)a.getAnimatedValue();ViewGroup.LayoutParams p=view.getLayoutParams();p.height=Math.round(from+(to-from)*f);view.setLayoutParams(p);view.setAlpha(alpha+((show?1:0)-alpha)*f);});animator.addListener(this);}
        public void onAnimationEnd(Animator a){if(panels.get(view)!=this)return;panels.remove(view);view.removeOnAttachStateChangeListener(this);view.setVisibility(show?View.VISIBLE:View.GONE);view.setAlpha(1);ViewGroup.LayoutParams p=view.getLayoutParams();p.height=ViewGroup.LayoutParams.WRAP_CONTENT;view.setLayoutParams(p);}
        public void onViewAttachedToWindow(View v){}
        public void onViewDetachedFromWindow(View v){stop(view);view.setVisibility(show?View.VISIBLE:View.GONE);}
    }
}
