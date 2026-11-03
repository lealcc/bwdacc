package com.leal.comfyremote;

import android.animation.*;
import android.content.Context;
import android.content.res.TypedArray;
import android.transition.*;
import android.util.AttributeSet;
import android.view.*;
import android.view.animation.*;
import android.widget.AbsListView;
import java.util.*;

/** Animate the actual popup surface, so touch, focus and dismissal remain native. */
public final class OrganicMenuTransition extends Visibility {
    private final float pivotX,pivotY;
    private final Interpolator stretch,unfold;
    public OrganicMenuTransition(Context context,AttributeSet attrs){
        super(context,attrs);TypedArray a=context.obtainStyledAttributes(attrs,new int[]{android.R.attr.pivotX,android.R.attr.pivotY});
        pivotX=a.getFraction(0,1,1,.5f);pivotY=a.getFraction(1,1,1,0);a.recycle();
        stretch=curve(context,"menu_spring_x");unfold=curve(context,"menu_spring_y");
    }
    OrganicMenuTransition(Context context,float x,float y){pivotX=x;pivotY=y;stretch=curve(context,"menu_spring_x");unfold=curve(context,"menu_spring_y");}
    private static Interpolator curve(Context c,String name){return AnimationUtils.loadInterpolator(c,c.getResources().getIdentifier(name,"interpolator",c.getPackageName()));}
    @Override public Animator onAppear(ViewGroup root,View view,TransitionValues start,TransitionValues end){return animate(view,true);}
    @Override public Animator onDisappear(ViewGroup root,View view,TransitionValues start,TransitionValues end){return animate(view,false);}
    private Animator animate(View view,boolean opening){
        if(!UiMotion.enabled())return null;
        float direction=pivotY==0?-1:1,tilt=pivotX>.5f?-2.2f:2.2f;
        view.setPivotX(view.getWidth()*pivotX);view.setPivotY(view.getHeight()*pivotY);
        List<View> rows=new ArrayList<>();collectRows(view,rows);
        final float[] rowAlpha=new float[rows.size()],rowY=new float[rows.size()];
        for(int i=0;i<rows.size();i++){rowAlpha[i]=rows.get(i).getAlpha();rowY[i]=rows.get(i).getTranslationY();}
        final float alpha=view.getAlpha(),sx=view.getScaleX(),sy=view.getScaleY(),rotation=view.getRotation(),y=view.getTranslationY();
        ValueAnimator run=ValueAnimator.ofFloat(0,1);run.setDuration(opening?460:160);run.setInterpolator(new LinearInterpolator());
        if(opening){view.setAlpha(0);view.setScaleX(.62f);view.setScaleY(.18f);view.setRotation(tilt);view.setTranslationY(FlowDesign.dp(view.getContext(),10)*direction);for(View row:rows)row.setAlpha(0);}
        run.addUpdateListener(a->{float t=(Float)a.getAnimatedValue();
            if(opening){float x=stretch.getInterpolation(Math.min(1,t*460/400)),h=unfold.getInterpolation(t);
                view.setScaleX(.62f+.38f*x);view.setScaleY(.18f+.82f*h);view.setRotation(tilt*(1-h));
                view.setTranslationY(FlowDesign.dp(view.getContext(),10)*direction*(1-UiMotion.EASE.getInterpolation(Math.min(1,t*460/320))));view.setAlpha(Math.min(1,t*460/110));
                for(int i=0;i<rows.size();i++){int order=pivotY==0?i:rows.size()-1-i;float f=Math.max(0,Math.min(1,(t*460-35-Math.min(order,6)*24)/150));f=UiMotion.EASE.getInterpolation(f);
                    rows.get(i).setAlpha(rowAlpha[i]*f);rows.get(i).setTranslationY(rowY[i]-FlowDesign.dp(view.getContext(),9)*direction*(1-f));}
            }else{float f=t*t;view.setScaleX(sx*(1-.09f*f));view.setScaleY(sy*(1-.28f*f));view.setAlpha(alpha*(1-f));view.setTranslationY(y+FlowDesign.dp(view.getContext(),8)*direction*f);}
        });
        run.addListener(new AnimatorListenerAdapter(){@Override public void onAnimationEnd(Animator a){view.setAlpha(alpha);view.setScaleX(sx);view.setScaleY(sy);view.setRotation(rotation);view.setTranslationY(y);
            for(int i=0;i<rows.size();i++){rows.get(i).setAlpha(rowAlpha[i]);rows.get(i).setTranslationY(rowY[i]);}}});
        return run;
    }
    private static void collectRows(View view,List<View> rows){
        if(!(view instanceof ViewGroup))return;ViewGroup group=(ViewGroup)view;
        if(view instanceof AbsListView){for(int i=0;i<group.getChildCount();i++)rows.add(group.getChildAt(i));return;}
        boolean direct=group.getChildCount()>1;for(int i=0;i<group.getChildCount();i++)direct&=group.getChildAt(i).isClickable();
        if(direct){for(int i=0;i<group.getChildCount();i++)rows.add(group.getChildAt(i));return;}
        for(int i=0;i<group.getChildCount();i++)collectRows(group.getChildAt(i),rows);
    }
}
