package com.leal.comfyremote;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared, live palette. Only chrome changes; media and privacy rules stay independent. */
final class Appearance {
    private static boolean privateMode;
    private static int revision;
    private static boolean shimmer;
    private static float lightX,lightY;
    private static final java.util.Map<Drawable,Boolean> lights=new java.util.WeakHashMap<>();
    static void light(boolean enabled,float x,float y){
        x=Math.max(-1,Math.min(1,x));y=Math.max(-1,Math.min(1,y));
        if(shimmer==enabled&&Math.abs(x-lightX)<.002f&&Math.abs(y-lightY)<.002f)return;
        shimmer=enabled;lightX=x;lightY=y;
        for(Drawable d:new java.util.ArrayList<>(lights.keySet()))if(d!=null&&d.getCallback()!=null)d.invalidateSelf();
    }
    private static <T extends Drawable>T watch(T d){lights.put(d,Boolean.TRUE);return d;}
    static boolean isPrivate(){return privateMode;}
    static int accent(){return 0xFFE8EAED;}
    static int icon(int color){return privateMode&&(color==FlowDesign.INK||color==0xFFFAFAFA)?0xFFE3D6FF:color;}
    static void mode(Context context,boolean enabled){
        if(privateMode!=enabled){privateMode=enabled;revision++;}
        int style=context.getResources().getIdentifier("Theme.Momentos","style",context.getPackageName());
        if(style!=0)context.getTheme().applyStyle(style,true);
    }
    static GradientDrawable surface(Context context,int fill,float radius,int stroke){
        return watch(new GradientDrawable(){
            final Paint shine=new Paint(3);final Matrix shift=new Matrix();final RectF rect=new RectF();final Path clip=new Path();
            Shader band;int width,height;
            int seen=-1;
            {setColor(fill);setCornerRadius(FlowDesign.dp(context,radius));}
            @Override public void draw(Canvas canvas){
                if(seen!=revision){seen=revision;int base=fill;
                    if(Color.alpha(base)>180&&Color.red(base)<105&&Color.green(base)<105&&Color.blue(base)<105){
                        setOrientation(Orientation.TL_BR);setColors(new int[]{blend(base,Color.WHITE,.035f),base});
                    }else setColor(base);
                    if(stroke!=0)setStroke(FlowDesign.dp(context,1),stroke);
                }
                super.draw(canvas);if(!shimmer||stroke==0||Color.alpha(fill)<180)return;
                Rect bounds=getBounds();if(bounds.width()<=0||bounds.height()<=0)return;
                if(band==null||width!=bounds.width()||height!=bounds.height()){
                    width=bounds.width();height=bounds.height();band=new LinearGradient(-width*.6f,0,width*.6f,height,new int[]{0x00FFFFFF,0x1BDAE2E9,0x00FFFFFF},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
                    rect.set(0,0,width,height);clip.reset();clip.addRoundRect(rect,FlowDesign.dp(context,radius),FlowDesign.dp(context,radius),Path.Direction.CW);
                }
                int saved=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.clipPath(clip);
                shift.setTranslate(width*(.5f+lightX*.65f),height*lightY*.3f);band.setLocalMatrix(shift);shine.setShader(band);canvas.drawRect(rect,shine);canvas.restoreToCount(saved);
            }
        });
    }
    static int blend(int a,int b,float f){return Color.argb(Color.alpha(a),Math.round(Color.red(a)*(1-f)+Color.red(b)*f),Math.round(Color.green(a)*(1-f)+Color.green(b)*f),Math.round(Color.blue(a)*(1-f)+Color.blue(b)*f));}
    static Drawable atmosphere(){return watch(new Drawable(){
        final Paint paint=new Paint(3);final Matrix matrix=new Matrix();int w,h;Shader base,glow,band;
        public void draw(Canvas canvas){Rect bounds=getBounds();if(bounds.isEmpty())return;
            if(base==null||w!=bounds.width()||h!=bounds.height()){
                w=bounds.width();h=bounds.height();float size=Math.max(w,h);
                base=new LinearGradient(0,0,0,h,0xFF090A0B,0xFF040506,Shader.TileMode.CLAMP);
                glow=new RadialGradient(0,0,size*.65f,new int[]{0x13BFC9D1,0x00000000},null,Shader.TileMode.CLAMP);
                band=new LinearGradient(-w*.6f,0,w*.6f,h*.35f,new int[]{0x00FFFFFF,0x0EDAE1E8,0x00FFFFFF},new float[]{0,.5f,1},Shader.TileMode.CLAMP);
            }
            int saved=canvas.save();canvas.translate(bounds.left,bounds.top);paint.setShader(base);canvas.drawRect(0,0,w,h,paint);
            matrix.setTranslate(w*(.8f+(shimmer?lightX*.22f:0)),h*(.16f+(shimmer?lightY*.12f:0)));glow.setLocalMatrix(matrix);paint.setShader(glow);canvas.drawRect(0,0,w,h,paint);
            if(shimmer){matrix.setTranslate(w*(.5f+lightX*.8f),h*(.12f+lightY*.24f));band.setLocalMatrix(matrix);paint.setShader(band);canvas.drawRect(0,0,w,h,paint);}canvas.restoreToCount(saved);
        }
        public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.OPAQUE;}
    });}
    static void refresh(View view){
        ColorStateList states=new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{accent(),0xFF72717B});
        if(view instanceof Switch){((Switch)view).setThumbTintList(states);((Switch)view).setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{0xFF666B74,0xFF34353B}));}
        else if(view instanceof CompoundButton)((CompoundButton)view).setButtonTintList(states);
        if(view instanceof ProgressBar){((ProgressBar)view).setProgressTintList(ColorStateList.valueOf(accent()));((ProgressBar)view).setIndeterminateTintList(ColorStateList.valueOf(accent()));}
        if(view instanceof EditText)((EditText)view).setHighlightColor(0x665E6A7C);
        view.invalidate();
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)refresh(group.getChildAt(i));}
    }
}
