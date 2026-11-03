package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import org.json.*;

/** Touch-first regional composition sheet. Stored coordinates are independent of resolution. */
final class BoxEditor {
    interface Saved {void accept(JSONObject layout);}
    static void show(Activity activity,JSONObject initial,double aspect,Saved saved){show(activity,initial,aspect,null,saved);}
    static void show(Activity activity,JSONObject initial,double aspect,Bitmap background,Saved saved){new Editor(activity,initial,aspect,background,saved).show();}
    private static final class Editor {
        final Activity a;final Dialog dialog;final Saved saved;final JSONArray boxes;final CanvasView canvas;
        final EditText prompt,text;final TextView selected;final Button type,remove;final LinearLayout page;boolean binding,useBoxes;int index=-1;
        final Bitmap background;final boolean privateLayout;
        Editor(Activity a,JSONObject initial,double aspect,Bitmap background,Saved saved){
            this.background=background;privateLayout=initial.optBoolean("private");
            this.a=a;this.saved=saved;dialog=new Dialog(a,android.R.style.Theme_Material_NoActionBar);JSONArray source=initial.optJSONArray("boxes");JSONArray copy;try{copy=source==null?new JSONArray():new JSONArray(source.toString());}catch(Exception e){copy=new JSONArray();}boxes=copy;useBoxes=initial.optBoolean("enabled",boxes.length()>0);
            page=column(a);FlowDesign.surface(page);page.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,12));
            LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);Button close=icon(a,"close",UiLanguage.t("Cancelar composição"));header.addView(close,new LinearLayout.LayoutParams(dp(a,44),dp(a,48)));close.setOnClickListener(v->dialog.dismiss());
            TextView title=label(a,UiLanguage.t("Composição"),20);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));Button done=icon(a,"check",UiLanguage.t("Aplicar boxes"));header.addView(done,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));page.addView(header);
            TextView hint=label(a,UiLanguage.t("Arraste para desenhar · mova pelos boxes · ajuste pelo canto"),12);hint.setTextColor(FlowDesign.MUTED);hint.setGravity(Gravity.CENTER);page.addView(hint);
            canvas=new CanvasView(a,aspect);canvas.drawing=boxes.length()==0;page.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
            LinearLayout tools=new LinearLayout(a);tools.setGravity(Gravity.CENTER_VERTICAL);selected=label(a,UiLanguage.t("Nenhum box"),14);tools.addView(selected,new LinearLayout.LayoutParams(0,dp(a,48),1));selected.setGravity(Gravity.CENTER_VERTICAL);
            if(background!=null&&privateLayout){Button reveal=icon(a,"image",UiLanguage.t("Mostrar imagem para selecionar"));tools.addView(reveal,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));reveal.setOnClickListener(v->{canvas.imageVisible=!canvas.imageVisible;canvas.invalidate();});}
            Button add=icon(a,"plus",UiLanguage.t("Desenhar novo box"));tools.addView(add,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));add.setOnClickListener(v->{canvas.drawing=true;select(-1);Toast.makeText(a,UiLanguage.t("Desenhe o novo box na imagem."),Toast.LENGTH_SHORT).show();});
            remove=icon(a,"delete",UiLanguage.t("Apagar box selecionado"));tools.addView(remove,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));remove.setOnClickListener(v->{if(index>=0){boxes.remove(index);select(boxes.length()-1);}});page.addView(tools);
            LinearLayout inspector=column(a);inspector.setPadding(dp(a,12),dp(a,8),dp(a,12),dp(a,12));inspector.setBackground(FlowDesign.shape(a,0xFF1C1D22,24,false));page.addView(inspector,new LinearLayout.LayoutParams(-1,-2));
            type=new Button(a);type.setAllCaps(false);type.setTextSize(13);type.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);type.setPadding(dp(a,12),0,dp(a,12),0);type.setTextColor(0xFFECECEF);type.setBackground(FlowDesign.shape(a,Color.TRANSPARENT,16,false));inspector.addView(type,new LinearLayout.LayoutParams(-1,dp(a,40)));
            type.setOnClickListener(v->{if(index>=0){JSONObject b=boxes.optJSONObject(index);try{b.put("type",b.optString("type").equals("text")?"obj":"text");}catch(Exception ignored){}select(index);}});
            prompt=input(a,"O que deve aparecer nesta área?");inspector.addView(prompt,new LinearLayout.LayoutParams(-1,dp(a,72)));text=input(a,"Texto exato para escrever na imagem");inspector.addView(text,new LinearLayout.LayoutParams(-1,dp(a,52)));
            TextWatcher watcher=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int af){}public void onTextChanged(CharSequence s,int st,int before,int count){if(binding||index<0)return;try{boxes.getJSONObject(index).put("desc",prompt.getText().toString()).put("text",text.getText().toString());canvas.invalidate();}catch(Exception ignored){}}public void afterTextChanged(Editable e){}};prompt.addTextChangedListener(watcher);text.addTextChangedListener(watcher);
            done.setOnClickListener(v->{try{JSONObject value=new JSONObject().put("enabled",useBoxes&&boxes.length()>0).put("boxes",boxes).put("aspect",canvas.aspect).put("private",privateLayout);BoxLayout.regions(value);saved.accept(value);dialog.dismiss();}catch(Exception e){Toast.makeText(a,e.getMessage(),Toast.LENGTH_LONG).show();}});
            dialog.setContentView(page);select(boxes.length()>0?0:-1);
        }
        void show(){
            dialog.show();Window window=dialog.getWindow();if(background!=null)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);window.setBackgroundDrawableResource(android.R.color.black);window.setLayout(-1,-1);window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN|WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);window.setStatusBarColor(Color.BLACK);window.setNavigationBarColor(Color.BLACK);
            if(android.os.Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(false);WindowInsetsController bars=window.getInsetsController();if(bars!=null){bars.show(WindowInsets.Type.statusBars());bars.setSystemBarsAppearance(0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}}
            page.setOnApplyWindowInsetsListener((v,insets)->{int left=0,top,bottom,right=0;if(android.os.Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());Insets ime=insets.getInsets(WindowInsets.Type.ime());left=bars.left;top=bars.top;right=bars.right;bottom=Math.max(bars.bottom,ime.bottom);}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}page.setPadding(left+dp(a,12),top+dp(a,8),right+dp(a,12),bottom+dp(a,12));return insets;});page.requestApplyInsets();
        }
        void select(int at){index=at;binding=true;JSONObject b=boxes.optJSONObject(at);prompt.setText(b==null?"":b.optString("desc"));text.setText(b==null?"":b.optString("text"));boolean words=b!=null&&b.optString("type").equals("text");type.setText(words?UiLanguage.t("Texto na imagem"):UiLanguage.t("Objeto / personagem"));text.setVisibility(words?View.VISIBLE:View.GONE);prompt.setEnabled(b!=null);type.setEnabled(b!=null);remove.setEnabled(b!=null);selected.setText(b==null?UiLanguage.t("Desenhe seu primeiro box"):UiLanguage.t("Box ")+(at+1)+UiLanguage.t(" de ")+boxes.length());binding=false;canvas.invalidate();}
        final class CanvasView extends View {
            boolean imageVisible=!privateLayout;
            @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(!focused&&privateLayout){imageVisible=false;invalidate();}}
            final double aspect;final Paint paint=new Paint(3);final RectF area=new RectF();boolean drawing=true,resize,moving;float downX,downY;double x,y,w,h;RectF draft;
            CanvasView(Activity a,double aspect){super(a);this.aspect=Double.isFinite(aspect)&&aspect>0?aspect:1;setContentDescription(UiLanguage.t("Canvas de composição: desenhe boxes com o dedo"));setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
            protected void onMeasure(int ws,int hs){int width=MeasureSpec.getSize(ws),height=MeasureSpec.getSize(hs);if(MeasureSpec.getMode(hs)==MeasureSpec.UNSPECIFIED)height=(int)(width/aspect)+dp(a,16);setMeasuredDimension(width,Math.max(0,height));}
            RectF box(JSONObject b){return new RectF(area.left+(float)b.optDouble("x")*area.width(),area.top+(float)b.optDouble("y")*area.height(),area.left+(float)(b.optDouble("x")+b.optDouble("w"))*area.width(),area.top+(float)(b.optDouble("y")+b.optDouble("h"))*area.height());}
            protected void onDraw(Canvas c){float aw=getWidth()-dp(a,8),ah=getHeight()-dp(a,16);if(aw<=0||ah<=0){area.setEmpty();return;}if(aw/ah>aspect)aw=(float)(ah*aspect);else ah=(float)(aw/aspect);area.set((getWidth()-aw)/2,(getHeight()-ah)/2,(getWidth()+aw)/2,(getHeight()+ah)/2);
                paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF1B1D22);c.drawRoundRect(area,dp(a,14),dp(a,14),paint);c.save();Path boundary=new Path();boundary.addRoundRect(area,dp(a,14),dp(a,14),Path.Direction.CW);c.clipPath(boundary);if(background!=null&&imageVisible)c.drawBitmap(background,null,area,paint);paint.setColor(background==null?0xFF303239:0x55303239);paint.setStrokeWidth(dp(a,1));for(int i=1;i<4;i++){c.drawLine(area.left+area.width()*i/4,area.top,area.left+area.width()*i/4,area.bottom,paint);c.drawLine(area.left,area.top+area.height()*i/4,area.right,area.top+area.height()*i/4,paint);}
                for(int i=0;i<boxes.length();i++){JSONObject b=boxes.optJSONObject(i);RectF r=box(b);paint.setColor(i==index?0x446D6BDB:0x228C93A5);paint.setStyle(Paint.Style.FILL);c.drawRoundRect(r,dp(a,6),dp(a,6),paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(a,i==index?2:1));paint.setColor(i==index?0xFFBBB8FF:0xFF9C9EA8);c.drawRoundRect(r,dp(a,6),dp(a,6),paint);paint.setStyle(Paint.Style.FILL);paint.setTextSize(dp(a,12));c.save();c.clipRect(r);String caption=String.valueOf(i+1);if(i==index)caption+=" · "+(b.optString("type").equals("text")?b.optString("text"):b.optString("desc"));caption=TextUtils.ellipsize(caption,new TextPaint(paint),Math.max(0,r.width()-dp(a,12)),TextUtils.TruncateAt.END).toString();paint.setColor(0xD91B1D22);c.drawRoundRect(r.left,r.top,r.left+paint.measureText(caption)+dp(a,12),r.top+dp(a,24),dp(a,6),dp(a,6),paint);paint.setColor(i==index?0xFFE2D9FF:0xFFECECEF);c.drawText(caption,r.left+dp(a,6),r.top+dp(a,17),paint);c.restore();if(i==index)c.drawCircle(r.right-dp(a,4),r.bottom-dp(a,4),dp(a,6),paint);}
                if(draft!=null){paint.setStyle(Paint.Style.STROKE);paint.setColor(0xFFD4D0FF);c.drawRect(draft,paint);}paint.setStyle(Paint.Style.FILL);c.restore();
            }
            float cx(float x){return Math.max(area.left,Math.min(area.right,x));}float cy(float y){return Math.max(area.top,Math.min(area.bottom,y));}
            public boolean onTouchEvent(MotionEvent e){if(area.width()==0)return true;float px=cx(e.getX()),py=cy(e.getY());
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){getParent().requestDisallowInterceptTouchEvent(true);downX=px;downY=py;moving=false;resize=false;int hit=-1;
                    if(!drawing)for(int i=boxes.length()-1;i>=0;i--)if(box(boxes.optJSONObject(i)).contains(px,py)){hit=i;break;}
                    if(hit>=0){select(hit);JSONObject b=boxes.optJSONObject(index);RectF r=box(b);resize=Math.abs(px-r.right)<dp(a,28)&&Math.abs(py-r.bottom)<dp(a,28);moving=true;drawing=false;x=b.optDouble("x");y=b.optDouble("y");w=b.optDouble("w");h=b.optDouble("h");}
                    else{if(boxes.length()>=12){Toast.makeText(a,UiLanguage.t("Limite de 12 boxes."),Toast.LENGTH_SHORT).show();return true;}drawing=true;select(-1);draft=new RectF(px,py,px,py);}return true;
                }
                if(e.getActionMasked()==MotionEvent.ACTION_MOVE){if(moving&&index>=0)try{double dx=(px-downX)/area.width(),dy=(py-downY)/area.height();JSONObject b=boxes.optJSONObject(index);if(resize)b.put("w",Math.max(.03,Math.min(1-x,w+dx))).put("h",Math.max(.03,Math.min(1-y,h+dy)));else b.put("x",Math.max(0,Math.min(1-w,x+dx))).put("y",Math.max(0,Math.min(1-h,y+dy)));}catch(Exception ignored){}else if(drawing&&draft!=null)draft.set(Math.min(px,downX),Math.min(py,downY),Math.max(px,downX),Math.max(py,downY));invalidate();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL){if(e.getActionMasked()==MotionEvent.ACTION_UP&&draft!=null&&draft.width()>dp(a,16)&&draft.height()>dp(a,16))try{boxes.put(new JSONObject().put("x",(draft.left-area.left)/area.width()).put("y",(draft.top-area.top)/area.height()).put("w",draft.width()/area.width()).put("h",draft.height()/area.height()).put("type","obj").put("desc","").put("text",""));useBoxes=true;select(boxes.length()-1);}catch(Exception ignored){}draft=null;moving=false;drawing=false;getParent().requestDisallowInterceptTouchEvent(false);invalidate();return true;}return true;
            }
        }
    }
    static int dp(Activity a,float n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    static LinearLayout column(Activity a){LinearLayout v=new LinearLayout(a);v.setOrientation(1);return v;}
    static TextView label(Activity a,String text,int size){TextView v=new TextView(a);v.setText(text);v.setTextColor(0xFFE7E7EA);v.setTextSize(size);v.setPadding(0,dp(a,4),0,dp(a,4));return v;}
    static EditText input(Activity a,String hint){EditText v=new EditText(a);v.setTextColor(0xFFF1F1F4);v.setHintTextColor(0xFF9A9BA5);v.setTextSize(15);v.setHint(hint);v.setGravity(Gravity.TOP);v.setPadding(dp(a,12),dp(a,12),dp(a,12),dp(a,8));v.setBackground(FlowDesign.shape(a,0xFF1C1D22,16,false));v.setInputType(0x20001);return v;}
    static Button icon(Activity a,String name,String description){Button b=new FlowDesign.IconButton(a);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(0,0,0,0);b.setBackgroundColor(Color.TRANSPARENT);FlowDesign.symbol(b,name,description);return b;}
}
