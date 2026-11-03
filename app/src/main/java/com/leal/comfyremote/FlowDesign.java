package com.leal.comfyremote;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared geometry and vector symbols for the feed and image editor. */
final class FlowDesign {
    static final int SURFACE=0xFF121314, CONTROL=0xFF292A2C, INK=0xFFF2F2F2, MUTED=0xFF97999C;
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context c,int color,int radius,boolean border){
        return Appearance.surface(c,color,radius,border?0xFF323335:0);
    }
    static Drawable pill(Context c,int fill,int radius,boolean border){return new InsetDrawable(new RippleDrawable(ColorStateList.valueOf(0x20FFFFFF),shape(c,fill,radius,border),null),0,dp(c,5),0,dp(c,5));}
    static void surface(LinearLayout view){Context c=view.getContext();view.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,6));view.setBackground(shape(c,SURFACE,24,true));view.setElevation(0);}
    static void prompt(EditText input){Context c=input.getContext();input.setTextSize(16);input.setTypeface(Branding.secondary(c,500));input.setTextColor(INK);input.setHintTextColor(MUTED);input.setIncludeFontPadding(false);input.setMinLines(1);input.setMaxLines(4);input.setGravity(Gravity.TOP|Gravity.START);input.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,8));input.setBackgroundColor(Color.TRANSPARENT);}
    static LinearLayout actions(Context c,Button plus,Button loras,Button agent,Button chip,Button send){
        LinearLayout row=new LinearLayout(c);row.setGravity(Gravity.CENTER_VERTICAL);
        symbol(plus,"plus",UiLanguage.t("Adicionar referência e recursos"));symbol(send,"arrow",send.getContentDescription()==null?UiLanguage.t("Gerar"):send.getContentDescription().toString());
        plus.setBackground(shape(c,Color.TRANSPARENT,24,false));
        send.setBackground(pill(c,CONTROL,20,true));send.setTextColor(INK);
        chip.setBackground(pill(c,CONTROL,20,true));
        row.addView(plus,new LinearLayout.LayoutParams(dp(c,44),dp(c,48)));
        loras.setBackground(pill(c,CONTROL,20,true));
        loras.setTextSize(11);loras.setPadding(0,0,0,0);loras.setSingleLine(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(c,60),dp(c,48));lp.rightMargin=dp(c,4);row.addView(loras,lp);
        LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(c,44),dp(c,48));ap.rightMargin=dp(c,4);row.addView(agent,ap);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(c,48),1);cp.rightMargin=dp(c,4);row.addView(chip,cp);
        row.addView(send,new LinearLayout.LayoutParams(dp(c,44),dp(c,48)));return row;
    }
    static void symbol(Button b,String kind,String description){
        b.setText("");b.setContentDescription(description);b.setPadding(0,0,0,0);b.setCompoundDrawables(null,null,null,null);
        if(b instanceof IconButton)((IconButton)b).symbol=kind;b.invalidate();
    }
    static void labelIcon(Button b,String kind){Drawable d=icon(b.getContext(),kind,20);b.setCompoundDrawables(d,null,null,null);b.setCompoundDrawablePadding(dp(b.getContext(),8));}
    static Drawable icon(Context c,String kind,int size){Drawable d=new Drawable(){final Paint p=new Paint(3);
        public void draw(Canvas canvas){int save=canvas.save();Rect b=getBounds();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);drawIcon(canvas,p,kind,INK);canvas.restoreToCount(save);}
        public int getIntrinsicWidth(){return dp(c,size);}public int getIntrinsicHeight(){return dp(c,size);}
        public void setAlpha(int a){p.setAlpha(a);}public void setColorFilter(ColorFilter f){p.setColorFilter(f);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}};
        d.setBounds(0,0,dp(c,size),dp(c,size));return d;
    }
    static final class IconButton extends Button {
        String symbol;
        IconButton(Context c){super(c);}
        protected void onDraw(Canvas c){String kind=symbol;String t=getText().toString();
            if(kind==null){if(t.equals("+"))kind="plus";else if(t.equals("→"))kind="arrow";else if(t.equals("⋮"))kind="more";else if(t.equals("⌄"))kind="down";else if(t.equals("‹"))kind="left";else if(t.equals("›"))kind="right";else if(t.equals("⟳"))kind="refresh";else if(t.equals("↺"))kind="reuse";}
            if(kind==null){super.onDraw(c);return;}
            Paint p=new Paint(3);int save=c.save();float size=dp(getContext(),22);c.translate((getWidth()-size)/2,(getHeight()-size)/2);c.scale(size/24,size/24);drawIcon(c,p,kind,isEnabled()?INK:0xFF616367);c.restoreToCount(save);
        }
    }
    static final class Chip extends Button {
        String ratio="1:1";int batch=1;boolean video;
        Chip(Context c){super(c);setAllCaps(false);setMinWidth(0);setMinimumWidth(0);setPadding(0,0,0,0);setStateListAnimator(null);}
        void update(String ratio,int batch,boolean video){this.ratio=ratio;this.batch=batch;this.video=video;setText((video?UiLanguage.t("Vídeo"):UiLanguage.t("Imagem"))+", "+ratio+UiLanguage.t(", lote ")+batch);setContentDescription(UiLanguage.t("Configurações de geração e workflow. ")+getText());invalidate();}
        protected void onDraw(Canvas c){
            Paint p=new Paint(3);p.setTextSize(dp(getContext(),12));p.setTypeface(Branding.secondary(getContext(),600));String label=video?UiLanguage.t("Vídeo"):UiLanguage.t("Imagem");
            float ratioX=Math.max(dp(getContext(),64),dp(getContext(),14)+p.measureText(label)+dp(getContext(),12));float batchX=ratioX+dp(getContext(),24),chevronX=batchX+dp(getContext(),21);float width=chevronX+dp(getContext(),23);
            int outer=c.save();float scale=Math.min(1,getWidth()/width);c.translate((getWidth()-width*scale)/2,0);c.scale(scale,1);p.setColor(INK);float base=getHeight()/2f-(p.ascent()+p.descent())/2;
            c.drawText(label,dp(getContext(),14),base,p);float unit=dp(getContext(),1);int save=c.save();c.translate(ratioX,getHeight()/2f-dp(getContext(),10));c.scale(unit*.83f,unit*.83f);drawIcon(c,p,"ratio:"+ratio,INK);c.restoreToCount(save);
            p.setStyle(Paint.Style.FILL);p.setColor(INK);p.setTextSize(dp(getContext(),13));c.drawText("x"+batch,batchX,base,p);
            save=c.save();c.translate(chevronX,getHeight()/2f-dp(getContext(),7));c.scale(unit*.6f,unit*.6f);drawIcon(c,p,"down",INK);c.restoreToCount(save);c.restoreToCount(outer);
        }
    }

    static void drawIcon(Canvas c,Paint p,String kind,int color){
        p.setColor(Appearance.icon(color));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path q=new Path();
        if(kind.startsWith("ratio:")){float r=1;try{String[] a=kind.substring(6).split(":");r=Float.parseFloat(a[0])/Float.parseFloat(a[1]);}catch(Exception ignored){}float w=r>1?18:18*r,h=r>1?18/r:18;c.drawRoundRect(12-w/2,12-h/2,12+w/2,12+h/2,1,1,p);return;}
        switch(kind){
            case "download":c.drawLine(12,3,12,15,p);q.moveTo(7,10);q.lineTo(12,15);q.lineTo(17,10);q.moveTo(3,16);q.lineTo(3,21);q.lineTo(21,21);q.lineTo(21,16);break;
            case "copy":c.drawRoundRect(8,7,21,21,2,2,p);q.moveTo(16,3);q.lineTo(5,3);q.quadTo(3,3,3,5);q.lineTo(3,16);break;
            case "tag":case "tag-off":q.moveTo(3,3);q.lineTo(11,3);q.lineTo(21,13);q.lineTo(13,21);q.lineTo(3,11);q.close();c.drawCircle(7,7,1,p);if(kind.equals("tag-off")){c.drawPath(q,p);q.reset();c.drawLine(3,21,21,3,p);}break;
            case "person-add":c.drawCircle(9,7,3.5f,p);c.drawArc(2,13,16,25,180,180,false,p);c.drawLine(19,10,19,18,p);c.drawLine(15,14,23,14,p);break;
            case "folder-add":case "folder-remove":q.moveTo(3,5);q.lineTo(9,5);q.lineTo(12,8);q.lineTo(21,8);q.lineTo(21,21);q.lineTo(3,21);q.close();c.drawLine(8,15,16,15,p);if(kind.equals("folder-add"))c.drawLine(12,11,12,19,p);break;
            case "owl":case "owl-sleep":q.moveTo(3,4);q.lineTo(7,6);q.quadTo(12,3,17,6);q.lineTo(21,4);q.lineTo(20,15);q.quadTo(18,22,12,22);q.quadTo(6,22,4,15);q.close();
                if(kind.equals("owl")){c.drawCircle(8,11,3,p);c.drawCircle(16,11,3,p);p.setStyle(Paint.Style.FILL);c.drawCircle(8,11,.8f,p);c.drawCircle(16,11,.8f,p);p.setStyle(Paint.Style.STROKE);}else{c.drawArc(5,8,11,13,10,160,false,p);c.drawArc(13,8,19,13,10,160,false,p);}
                c.drawLine(11,15,12,17,p);c.drawLine(12,17,13,15,p);break;
            case "upscale":c.drawRoundRect(3,9,15,21,2,2,p);q.moveTo(13,3);q.lineTo(21,3);q.lineTo(21,11);q.moveTo(21,3);q.lineTo(11,13);break;
            case "extend":c.drawRoundRect(2,5,14,19,2,2,p);q.moveTo(6,9);q.lineTo(10,12);q.lineTo(6,15);q.close();c.drawLine(16,12,23,12,p);q.moveTo(20,9);q.lineTo(23,12);q.lineTo(20,15);c.drawLine(17,5,17,7,p);c.drawLine(17,17,17,19,p);break;
            case "search":c.drawCircle(10,10,6,p);c.drawLine(15,15,21,21,p);break;
            case "language":c.drawCircle(12,12,9,p);c.drawOval(8,3,16,21,p);c.drawLine(3,12,21,12,p);break;
            case "memory":c.drawRoundRect(5,5,19,19,3,3,p);c.drawRoundRect(9,9,15,15,1,1,p);for(int i=8;i<=16;i+=4){c.drawLine(i,2,i,5,p);c.drawLine(i,19,i,22,p);c.drawLine(2,i,5,i,p);c.drawLine(19,i,22,i,p);}break;
            case "queue":for(int y=6;y<=18;y+=6){c.drawCircle(4,y,1,p);c.drawLine(9,y,21,y,p);}break;
            case "qr":for(int[] cell:new int[][]{{3,3},{15,3},{3,15}}){c.drawRoundRect(cell[0],cell[1],cell[0]+6,cell[1]+6,1,1,p);c.drawPoint(cell[0]+3,cell[1]+3,p);}q.moveTo(15,15);q.lineTo(18,15);q.lineTo(18,18);q.lineTo(21,18);q.lineTo(21,21);q.moveTo(15,21);q.lineTo(15,18);break;
            case "mic":c.drawRoundRect(8,2,16,15,4,4,p);c.drawArc(5,7,19,19,0,180,false,p);c.drawLine(12,19,12,22,p);c.drawLine(8,22,16,22,p);break;
            case "wave":for(int i=0;i<7;i++){float h=new float[]{3,6,9,5,8,4,2}[i];c.drawLine(3+i*3,12-h,3+i*3,12+h,p);}break;
            case "connection":c.drawCircle(5,17,3,p);c.drawCircle(19,7,3,p);q.moveTo(8,17);q.cubicTo(17,17,7,7,16,7);break;
            case "stop":c.drawRoundRect(5,5,19,19,3,3,p);break;
            case "terminal":c.drawRoundRect(2,3,22,21,3,3,p);q.moveTo(6,8);q.lineTo(10,12);q.lineTo(6,16);c.drawLine(13,16,18,16,p);break;
            case "agent":case "agent-filled":q.moveTo(10,5);q.lineTo(12,11);q.lineTo(18,13);q.lineTo(12,15);q.lineTo(10,21);q.lineTo(8,15);q.lineTo(2,13);q.lineTo(8,11);q.close();c.drawLine(18,2,18,8,p);c.drawLine(15,5,21,5,p);if(kind.equals("agent-filled"))p.setStyle(Paint.Style.FILL_AND_STROKE);break;
            case "heart":case "heart-filled":if(kind.equals("heart-filled"))p.setStyle(Paint.Style.FILL);q.moveTo(12,21);q.cubicTo(9,18,2,13,2,8);q.cubicTo(2,1,10,1,12,6);q.cubicTo(14,1,22,1,22,8);q.cubicTo(22,13,15,18,12,21);q.close();break;
            case "folder":q.moveTo(3,6);q.lineTo(10,6);q.lineTo(12,9);q.lineTo(21,9);q.lineTo(21,21);q.lineTo(3,21);q.close();break;
            case "flask":q.moveTo(9,3);q.lineTo(15,3);q.moveTo(10,3);q.lineTo(10,9);q.lineTo(3,20);q.lineTo(21,20);q.lineTo(14,9);q.lineTo(14,3);c.drawLine(7,14,17,14,p);break;
            case "book":q.moveTo(12,5);q.quadTo(7,2,2,4);q.lineTo(2,20);q.quadTo(7,18,12,21);q.quadTo(17,18,22,20);q.lineTo(22,4);q.quadTo(17,2,12,5);q.lineTo(12,21);c.drawLine(5,8,9,9,p);c.drawLine(15,9,19,8,p);break;
            case "info":c.drawCircle(12,12,9,p);c.drawPoint(12,7,p);c.drawLine(12,11,12,17,p);break;
            case "swap":c.drawLine(3,8,20,8,p);q.moveTo(16,4);q.lineTo(20,8);q.lineTo(16,12);c.drawLine(4,16,21,16,p);q.moveTo(8,12);q.lineTo(4,16);q.lineTo(8,20);break;
            case "plus":c.drawLine(5,12,19,12,p);c.drawLine(12,5,12,19,p);break;
            case "minus":c.drawLine(5,12,19,12,p);break;
            case "arrow":c.drawLine(4,12,20,12,p);q.moveTo(13,5);q.lineTo(20,12);q.lineTo(13,19);break;
            case "up":q.moveTo(7,14);q.lineTo(12,9);q.lineTo(17,14);break;
            case "down":q.moveTo(7,10);q.lineTo(12,15);q.lineTo(17,10);break;
            case "left":q.moveTo(15,5);q.lineTo(8,12);q.lineTo(15,19);break;
            case "right":q.moveTo(9,5);q.lineTo(16,12);q.lineTo(9,19);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.5f,p);break;
            case "layers":c.drawRoundRect(7,3,21,17,2,2,p);q.moveTo(17,21);q.lineTo(3,21);q.lineTo(3,7);break;
            case "boxes":c.drawRoundRect(3,3,21,21,2,2,p);c.drawRoundRect(6,6,12,15,1,1,p);c.drawRoundRect(14,11,18,18,1,1,p);break;
            case "object":q.moveTo(12,2);q.lineTo(22,7);q.lineTo(22,17);q.lineTo(12,22);q.lineTo(2,17);q.lineTo(2,7);q.close();q.moveTo(2,7);q.lineTo(12,12);q.lineTo(22,7);q.moveTo(12,12);q.lineTo(12,22);break;
            case "scan":q.moveTo(3,8);q.lineTo(3,3);q.lineTo(8,3);q.moveTo(16,3);q.lineTo(21,3);q.lineTo(21,8);q.moveTo(21,16);q.lineTo(21,21);q.lineTo(16,21);q.moveTo(8,21);q.lineTo(3,21);q.lineTo(3,16);c.drawCircle(12,12,4,p);break;
            case "undo":q.moveTo(8,3);q.lineTo(3,8);q.lineTo(8,13);q.moveTo(3,8);q.lineTo(14,8);q.cubicTo(23,8,23,21,14,21);break;
            case "grid":for(int x=3;x<18;x+=10)for(int y=3;y<18;y+=10)c.drawRoundRect(x,y,x+7,y+7,1.5f,1.5f,p);break;
            case "feed":c.drawRoundRect(4,3,20,21,3,3,p);c.drawLine(4,8,20,8,p);c.drawLine(8,17,16,17,p);break;
            case "image":c.drawRoundRect(3,3,21,21,3,3,p);c.drawCircle(8,8,1.5f,p);q.moveTo(3,18);q.lineTo(10,11);q.lineTo(14,15);q.lineTo(17,12);q.lineTo(21,16);break;
            case "video":c.drawRoundRect(3,5,16,19,3,3,p);q.moveTo(16,9);q.lineTo(22,6);q.lineTo(22,18);q.lineTo(16,15);break;
            case "lipsync":q.moveTo(2,12);q.quadTo(8,5,12,8);q.quadTo(16,5,22,12);q.quadTo(12,23,2,12);q.moveTo(2,12);q.quadTo(12,16,22,12);c.drawLine(9,2,9,4,p);c.drawLine(15,2,15,4,p);break;
            case "play":q.moveTo(7,4);q.lineTo(21,12);q.lineTo(7,20);q.close();break;
            case "pause":c.drawLine(8,5,8,19,p);c.drawLine(16,5,16,19,p);break;
            case "muted":case "sound":q.moveTo(3,9);q.lineTo(7,9);q.lineTo(12,4);q.lineTo(12,20);q.lineTo(7,15);q.lineTo(3,15);q.close();
                if(kind.equals("muted")){c.drawLine(17,9,22,15,p);c.drawLine(22,9,17,15,p);}else{c.drawArc(10,6,20,18,-65,130,false,p);c.drawArc(7,2,24,22,-60,120,false,p);}break;
            case "back10":case "forward10":int saved=c.save();if(kind.equals("forward10")){c.translate(24,0);c.scale(-1,1);}c.drawArc(3,4,21,22,-140,290,false,p);c.drawLine(3,3,3,9,p);c.drawLine(3,9,9,9,p);c.restoreToCount(saved);p.setStyle(Paint.Style.FILL);p.setTextSize(8);p.setTypeface(Typeface.create("sans-serif-medium",0));p.setTextAlign(Paint.Align.CENTER);c.drawText("10",12,16,p);break;
            case "edit":q.moveTo(4,16);q.lineTo(16,4);q.lineTo(20,8);q.lineTo(8,20);q.lineTo(3,21);q.close();q.moveTo(13,7);q.lineTo(17,11);break;
            case "cutout":c.drawRoundRect(3,3,21,21,4,4,p);q.moveTo(3,17);q.lineTo(17,3);q.moveTo(8,21);q.lineTo(21,8);c.drawCircle(8,8,2,p);break;
            case "outpaint":c.drawRect(8,8,16,16,p);q.moveTo(3,8);q.lineTo(3,3);q.lineTo(8,3);q.moveTo(16,3);q.lineTo(21,3);q.lineTo(21,8);q.moveTo(21,16);q.lineTo(21,21);q.lineTo(16,21);q.moveTo(8,21);q.lineTo(3,21);q.lineTo(3,16);break;
            case "close":c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);break;
            case "check":q.moveTo(4,12);q.lineTo(9,17);q.lineTo(20,6);break;
            case "select":c.drawRoundRect(3,3,21,21,4,4,p);q.moveTo(7,12);q.lineTo(11,16);q.lineTo(17,8);break;
            case "depth":for(int n=0;n<3;n++){float top=3+n*5;q.moveTo(3,top+4);q.lineTo(12,top);q.lineTo(21,top+4);q.lineTo(12,top+8);q.close();}break;
            case "pose":c.drawCircle(12,4,2,p);q.moveTo(5,8);q.lineTo(12,10);q.lineTo(19,7);q.moveTo(12,10);q.lineTo(12,15);q.lineTo(6,21);q.moveTo(12,15);q.lineTo(18,21);break;
            case "edges":q.moveTo(9,3);q.lineTo(3,3);q.lineTo(3,9);q.moveTo(15,3);q.lineTo(21,3);q.lineTo(21,9);q.moveTo(21,15);q.lineTo(21,21);q.lineTo(15,21);q.moveTo(9,21);q.lineTo(3,21);q.lineTo(3,15);c.drawCircle(12,12,4,p);break;
            case "person":c.drawCircle(12,7,4,p);c.drawArc(4,13,20,25,180,180,false,p);break;
            case "location":c.drawCircle(12,9,2.5f,p);q.moveTo(12,22);q.cubicTo(8,17,4,13,4,9);q.cubicTo(4,-1,20,-1,20,9);q.cubicTo(20,13,16,17,12,22);q.close();break;
            case "style":q.moveTo(12,2);q.lineTo(15,9);q.lineTo(22,12);q.lineTo(15,15);q.lineTo(12,22);q.lineTo(9,15);q.lineTo(2,12);q.lineTo(9,9);q.close();break;
            case "delete":c.drawLine(4,6,20,6,p);q.moveTo(6,6);q.lineTo(7,21);q.lineTo(17,21);q.lineTo(18,6);q.moveTo(9,6);q.lineTo(9,3);q.lineTo(15,3);q.lineTo(15,6);break;
            case "reuse":c.drawRoundRect(3,3,15,17,2,2,p);c.drawLine(6,7,12,7,p);c.drawLine(6,10,10,10,p);q.moveTo(13,21);q.lineTo(20,21);q.quadTo(23,21,23,17);q.quadTo(23,13,19,13);q.lineTo(16,13);q.moveTo(19,10);q.lineTo(16,13);q.lineTo(19,16);break;
            case "refresh":c.drawArc(4,4,20,20,30,290,false,p);q.moveTo(20,3);q.lineTo(20,9);q.lineTo(14,9);break;
            default:for(int y=5;y<=19;y+=7){c.drawLine(3,y,21,y,p);c.drawCircle(y==12?8:16,y,2,p);}break;
        }c.drawPath(q,p);
    }
}
