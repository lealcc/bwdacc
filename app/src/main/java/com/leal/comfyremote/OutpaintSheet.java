package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;

/** Preview the extra canvas without exposing private source media through a tool sheet. */
final class OutpaintSheet {
    private static BitmapShader checkerShader;
    private static final Matrix checkerMatrix=new Matrix();
    interface Submit {void run(JSONObject edges,String prompt);}
    static void show(Activity a,Bitmap image,int width,int height,JSONObject previous,String draft,Submit submit){
        FlowSheet sheet=new FlowSheet(a,UiLanguage.t("Expandir imagem"),"outpaint");
        JSONObject edges=new JSONObject();String[] keys={"left","top","right","bottom"};
        String[] labels={UiLanguage.t("Esquerda"),UiLanguage.t("Acima"),UiLanguage.t("Direita"),UiLanguage.t("Abaixo")};
        try{for(String key:keys)edges.put(key,previous==null?Math.max(8,(key.equals("left")||key.equals("right")?width:height)/4):previous.optInt(key));}catch(Exception ignored){}
        CanvasPreview preview=new CanvasPreview(a,image,width,height,edges);sheet.body.addView(preview,new LinearLayout.LayoutParams(-1,sheet.dp(200)));
        TextView size=sheet.text("",14,true);size.setGravity(Gravity.CENTER);sheet.body.addView(size,new LinearLayout.LayoutParams(-1,sheet.dp(36)));
        LinearLayout presets=sheet.row();sheet.body.addView(presets);String[] ratios={"1:1","4:3","3:4","16:9","9:16"};
        SeekBar[] bars=new SeekBar[4];TextView[] values=new TextView[4];final boolean[] updating={false};
        TextView error=sheet.text("",12,false);EditText prompt=sheet.field(UiLanguage.t("Descreva a área nova (opcional)"));prompt.setSingleLine(false);prompt.setMaxLines(3);prompt.setText(draft);prompt.setMinHeight(sheet.dp(70));prompt.getLayoutParams().height=-2;
        // Edge controls precede the prompt, with a compact value and a full-width slider.
        LinearLayout controls=sheet.column();sheet.body.addView(controls,sheet.body.indexOfChild(prompt));
        Button send=sheet.button(UiLanguage.t("Expandir imagem"));
        Runnable refresh=()->{preview.invalidate();try{int[] dimensions=ImageToolsWorkflow.dimensions(width,height,edges);size.setText(dimensions[0]+" × "+dimensions[1]);error.setText("");send.setEnabled(true);}catch(Exception e){size.setText(width+" × "+height);error.setText(e.getMessage());send.setEnabled(false);}
            updating[0]=true;for(int i=0;i<4;i++){int pixels=edges.optInt(keys[i]),base=(i==0||i==2)?width:height;values[i].setText(labels[i]+" · "+pixels+UiLanguage.t(" px"));bars[i].setProgress(Math.min(100,(int)Math.round(pixels*100.0/base)));}updating[0]=false;};
        for(int i=0;i<4;i++){final int at=i;values[i]=sheet.text("",12,false);controls.addView(values[i]);bars[i]=new SeekBar(a);bars[i].setMax(100);bars[i].setContentDescription(labels[i]);bars[i].setProgressTintList(android.content.res.ColorStateList.valueOf(Appearance.accent()));controls.addView(bars[i],new LinearLayout.LayoutParams(-1,sheet.dp(32)));
            bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}public void onProgressChanged(SeekBar b,int n,boolean fromUser){if(updating[0]||!fromUser)return;try{int base=(at==0||at==2)?width:height;edges.put(keys[at],n==0?0:Math.max(8,(int)Math.round(base*n/800.0)*8));refresh.run();}catch(Exception ignored){}}});}
        for(String ratio:ratios){Button button=sheet.button(ratio);button.setTextSize(12);button.setPadding(0,0,0,0);presets.addView(button,new LinearLayout.LayoutParams(0,sheet.dp(40),1));button.setOnClickListener(v->{String[] parts=ratio.split(":");double r=Double.parseDouble(parts[0])/Double.parseDouble(parts[1]);int w=Math.max(width,(int)Math.ceil(height*r)),h=Math.max(height,(int)Math.ceil(width/r));if(w==width&&h==height){w=(int)Math.ceil(width*1.25);h=(int)Math.ceil(height*1.25);}try{edges.put("left",(w-width)/2).put("right",w-width-(w-width)/2).put("top",(h-height)/2).put("bottom",h-height-(h-height)/2);refresh.run();}catch(Exception ignored){}});}
        sheet.body.addView(error);sheet.gap(sheet.body,8);sheet.body.addView(send,new LinearLayout.LayoutParams(-1,sheet.dp(50)));send.setOnClickListener(v->{try{ImageToolsWorkflow.dimensions(width,height,edges);submit.run(new JSONObject(edges.toString()),prompt.getText().toString().trim());sheet.dialog.dismiss();}catch(Exception e){error.setText(e.getMessage());}});
        refresh.run();sheet.dialog.setOnDismissListener(d->{if(image!=null&&!image.isRecycled())image.recycle();});sheet.show();
    }
    static final class CanvasPreview extends View {
        final Bitmap image;final int width,height;final JSONObject edges;final Paint paint=new Paint(3);
        CanvasPreview(Activity a,Bitmap image,int width,int height,JSONObject edges){super(a);this.image=image;this.width=width;this.height=height;this.edges=edges;setContentDescription(UiLanguage.t("Prévia do canvas expandido"));}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);int left=edges.optInt("left"),top=edges.optInt("top"),w=width+left+edges.optInt("right"),h=height+top+edges.optInt("bottom");float inset=FlowDesign.dp(getContext(),8),scale=Math.min((getWidth()-2*inset)/w,(getHeight()-2*inset)/h),x=(getWidth()-w*scale)/2,y=(getHeight()-h*scale)/2;
            RectF bounds=new RectF(x,y,x+w*scale,y+h*scale);Path path=new Path();path.addRoundRect(bounds,FlowDesign.dp(getContext(),14),FlowDesign.dp(getContext(),14),Path.Direction.CW);int saved=canvas.save();canvas.clipPath(path);checker(canvas,bounds,FlowDesign.dp(getContext(),12),paint);
            paint.setColor(Appearance.accent());paint.setAlpha(35);canvas.drawRect(bounds,paint);paint.setAlpha(255);
            RectF original=new RectF(x+left*scale,y+top*scale,x+(left+width)*scale,y+(top+height)*scale);if(image!=null&&!image.isRecycled())canvas.drawBitmap(image,null,original,paint);paint.setColor(0xBBDADAE2);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(FlowDesign.dp(getContext(),1));canvas.drawRect(original,paint);paint.setStyle(Paint.Style.FILL);canvas.restoreToCount(saved);
        }
    }
    static void checker(Canvas canvas,RectF bounds,int cell,Paint paint){
        if(checkerShader==null){Bitmap tile=Bitmap.createBitmap(new int[]{0xFF25262A,0xFF34353A,0xFF34353A,0xFF25262A},2,2,Bitmap.Config.ARGB_8888);checkerShader=new BitmapShader(tile,Shader.TileMode.REPEAT,Shader.TileMode.REPEAT);}
        checkerMatrix.setScale(cell,cell);checkerMatrix.postTranslate(bounds.left,bounds.top);checkerShader.setLocalMatrix(checkerMatrix);boolean filtered=paint.isFilterBitmap();paint.setFilterBitmap(false);paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);paint.setShader(checkerShader);canvas.drawRect(bounds,paint);paint.setShader(null);paint.setFilterBitmap(filtered);
    }
}
