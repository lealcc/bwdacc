package com.leal.comfyremote;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.SystemClock;

/** A generated placeholder, never a preview of private media. One continuous 14-second loop. */
final class GenerationArtwork extends Drawable {
    static final long LOOP_MILLIS=14_000;
    final double aspect;
    String label;
    GenerationProgress.Source source;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.DITHER_FLAG);
    private final Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    private final Matrix matrix=new Matrix();
    private final float[] centers=new float[8];
    private final RadialGradient[] clouds=new RadialGradient[4];
    private ShaderMesh mesh;
    private float unit=1;
    private int alpha=255;
    boolean animate=true;

    GenerationArtwork(double aspect,String label){
        this.aspect=Double.isFinite(aspect)&&aspect>0?aspect:1;this.label=label;
        int[] colors={0xFF447CEB,0xFF8854DD,0xFFEA88B2,0xFFC3B4F5};
        for(int i=0;i<clouds.length;i++)clouds[i]=new RadialGradient(0,0,1,new int[]{colors[i],colors[i]&0x00FFFFFF},null,Shader.TileMode.CLAMP);
        if(Build.VERSION.SDK_INT>=33)try{mesh=new ShaderMesh();}catch(RuntimeException ignored){}
        text.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));text.setTextAlign(Paint.Align.CENTER);
    }
    void setUiScale(float value){unit=value>0?value:1;}
    @Override public int getIntrinsicWidth(){return 1080;}
    @Override public int getIntrinsicHeight(){return Math.max(1,(int)Math.round(1080/aspect));}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();float w=bounds.width(),h=bounds.height();if(w<=0||h<=0)return;
        float phase=animate?(float)((SystemClock.uptimeMillis()%LOOP_MILLIS)/(double)LOOP_MILLIS*Math.PI*2):0;
        centers[0]=.20f+.23f*(float)Math.cos(phase);centers[1]=.25f+.20f*(float)Math.sin(phase);
        centers[2]=.76f+.22f*(float)Math.sin(phase+.6f);centers[3]=.25f+.24f*(float)Math.cos(phase+.9f);
        centers[4]=.68f+.25f*(float)Math.cos(phase+2.7f);centers[5]=.78f+.20f*(float)Math.sin(phase+.8f);
        centers[6]=.24f+.23f*(float)Math.sin(phase+3.4f);centers[7]=.72f+.22f*(float)Math.cos(phase+2.1f);
        int saved=canvas.save();canvas.translate(bounds.left,bounds.top);canvas.clipRect(0,0,w,h);
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(alpha);
        if(mesh!=null&&canvas.isHardwareAccelerated()){
            mesh.update(w,h,centers);paint.setShader(mesh.shader);canvas.drawRect(0,0,w,h,paint);
        }else{
            paint.setShader(null);paint.setColor(0xFF282A63);paint.setAlpha(alpha);canvas.drawRect(0,0,w,h,paint);
            for(int i=0;i<clouds.length;i++){
                matrix.setScale(w*.95f,h*.92f);matrix.postTranslate(w*centers[i*2],h*centers[i*2+1]);clouds[i].setLocalMatrix(matrix);
                paint.setShader(clouds[i]);canvas.drawRect(0,0,w,h,paint);
            }
        }
        paint.setShader(null);
        if(source!=null){drawProgress(canvas,w,h);canvas.restoreToCount(saved);return;}
        // Status stays readable at the same physical size in portrait and landscape.
        float u=Math.min(unit,Math.min(w/190f,h/100f));text.setTextSize(14*u);text.setColor(Color.WHITE);text.setAlpha(alpha);
        float badgeWidth=Math.min(w-28*u,text.measureText(label)+66*u),badgeHeight=40*u;
        float x=(w-badgeWidth)/2,y=h-16*u-badgeHeight;
        rect.set(x,y,x+badgeWidth,y+badgeHeight);paint.setColor(0x9C151527);paint.setAlpha(Math.round(alpha*.61f));
        canvas.drawRoundRect(rect,20*u,20*u,paint);
        paint.setColor(Color.WHITE);
        for(int i=0;i<3;i++){paint.setAlpha(Math.round(alpha*(.38f+.48f*(.5f+.5f*(float)Math.sin(phase*2-i*.8f)))));canvas.drawCircle(x+(17+i*6)*u,y+badgeHeight/2,1.6f*u,paint);}
        canvas.drawText(label,x+badgeWidth/2+11*u,y+badgeHeight/2-(text.ascent()+text.descent())/2,text);
        canvas.restoreToCount(saved);
    }
    private void drawProgress(Canvas canvas,float w,float h){
        GenerationProgress.Display status=source.read();
        float u=Math.min(unit,Math.min(w/230f,h/130f)),width=Math.min(w-24*u,320*u),height=100*u;
        float x=(w-width)/2,y=h-height-12*u;
        paint.setColor(0xD5151527);paint.setAlpha(Math.round(alpha*.78f));rect.set(x,y,x+width,y+height);canvas.drawRoundRect(rect,20*u,20*u,paint);
        text.setColor(Color.WHITE);text.setAlpha(alpha);text.setTextSize(14*u);
        canvas.drawText(fit(status.title,width-28*u),w/2,y+26*u,text);
        text.setTextSize(11*u);text.setAlpha(Math.round(alpha*.8f));
        String[] lines=status.detail.split(" · ",2);
        canvas.drawText(fit(lines[0],width-28*u),w/2,y+47*u,text);
        if(lines.length>1)canvas.drawText(fit(lines[1],width-28*u),w/2,y+64*u,text);
        if(status.fraction>=0){
            rect.set(x+18*u,y+height-18*u,x+width-18*u,y+height-15*u);paint.setColor(0x40FFFFFF);paint.setAlpha(Math.round(alpha*.25f));canvas.drawRoundRect(rect,2*u,2*u,paint);
            rect.right=rect.left+(width-36*u)*Math.max(0,Math.min(1,status.fraction));paint.setColor(0xFFD4C9FF);paint.setAlpha(alpha);canvas.drawRoundRect(rect,2*u,2*u,paint);
        }else{
            float phase=animate?(float)((SystemClock.uptimeMillis()%1800)/1800.0*Math.PI*2):0;
            for(int i=0;i<3;i++){paint.setColor(Color.WHITE);paint.setAlpha(Math.round(alpha*(.35f+.45f*(.5f+.5f*(float)Math.sin(phase-i*.8f)))));canvas.drawCircle(w/2+(i-1)*7*u,y+height-17*u,2*u,paint);}
        }
    }
    private String fit(String value,float width){if(text.measureText(value)<=width)return value;int end=value.length();while(end>0&&text.measureText(value.substring(0,end)+"…")>width)end--;return value.substring(0,end)+"…";}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}

    /** Isolated to keep the Canvas fallback usable before Android 13. */
    @android.annotation.TargetApi(33)
    private static final class ShaderMesh {
        final RuntimeShader shader=new RuntimeShader(
            "uniform float2 size; uniform float2 p0; uniform float2 p1; uniform float2 p2; uniform float2 p3;\n"+
            "float weight(float2 uv,float2 p){float2 d=(uv-p)*float2(1.0,1.08);return exp(-5.5*dot(d,d));}\n"+
            "half4 main(float2 point){float2 uv=point/size;\n"+
            "float a=weight(uv,p0),b=weight(uv,p1),c=weight(uv,p2),d=weight(uv,p3);\n"+
            "float3 col=(a*float3(.19,.43,.94)+b*float3(.50,.25,.83)+c*float3(.94,.43,.64)+d*float3(.72,.68,.96))/(a+b+c+d);\n"+
            "float shade=1.0-.22*smoothstep(.22,.8,length(uv-.5));\n"+
            "float grain=(fract(sin(dot(point,float2(12.9898,78.233)))*43758.5453)-.5)/255.0;\n"+
            "return half4(col*shade+grain,1.0);}");
        void update(float w,float h,float[] p){shader.setFloatUniform("size",w,h);shader.setFloatUniform("p0",p[0],p[1]);shader.setFloatUniform("p1",p[2],p[3]);shader.setFloatUniform("p2",p[4],p[5]);shader.setFloatUniform("p3",p[6],p[7]);}
    }
}
