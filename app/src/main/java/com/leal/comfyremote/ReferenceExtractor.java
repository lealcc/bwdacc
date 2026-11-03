package com.leal.comfyremote;

import android.app.*;
import android.graphics.*;
import android.os.Handler;
import android.view.*;
import android.widget.*;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Image-space coordinates are independent of letterboxing, screen density and keyboard insets. */
final class ReferenceExtractor {
    final ReferenceStudio studio;
    final Activity activity;
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final Handler handler = new Handler();
    Session active;
    ReferenceExtractor(ReferenceStudio studio) { this.studio = studio; activity = studio.activity; }
    void close() { if (active != null) active.dialog.dismiss(); worker.shutdownNow(); }
    void conceal() { if (active != null && active.source.hidden) active.conceal(); }
    void open(ReferencePreset.Picture source, boolean composition) {
        worker.execute(() -> {
            Bitmap image = VaultFiles.bitmap(studio.store.file(source.file).getAbsolutePath());
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (image == null) { studio.toast(UiLanguage.t("Não foi possível abrir a imagem.")); return; }
                if (active != null) active.dialog.dismiss();
                active = new Session(source, image, composition);
                active.show();
            });
        });
    }

    final class Session {
        final ReferencePreset.Picture source;
        final Bitmap image;
        final boolean composition;
        final Dialog dialog;
        final LinearLayout page;
        final CanvasView canvas;
        final TextView status;
        final Button save, reveal, include, exclude;
        final List<JSONObject> points = new ArrayList<>();
        Bitmap mask;
        JSONObject layout;
        boolean concealed, negative, busy;
        volatile boolean closed;
        int revision;
        Session(ReferencePreset.Picture source, Bitmap image, boolean composition) {
            this.source = source; this.image = image; this.composition = composition; concealed = source.hidden;
            dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar);
            page = studio.column(); page.setBackgroundColor(Color.BLACK);
            LinearLayout header = studio.row();
            Button back = studio.icon("close", UiLanguage.t("Fechar extrator"));
            header.addView(back, new LinearLayout.LayoutParams(studio.dp(48), studio.dp(48)));
            back.setOnClickListener(v -> dialog.dismiss());
            LinearLayout.LayoutParams titleSpace=new LinearLayout.LayoutParams(0,-2,1);titleSpace.leftMargin=studio.dp(12);
            header.addView(studio.text(composition ? UiLanguage.t("Extrair composição") : UiLanguage.t("Selecionar objeto"), 20, FlowDesign.INK, true), titleSpace);
            page.addView(header);
            canvas = new CanvasView(); page.addView(canvas, new LinearLayout.LayoutParams(-1,0,1));
            reveal = studio.button(UiLanguage.t("Mostrar imagem para selecionar"), "image");
            page.addView(reveal, new LinearLayout.LayoutParams(-1,studio.dp(48)));
            reveal.setOnClickListener(v -> { concealed = false; reveal.setVisibility(View.GONE); canvas.invalidate(); if (composition && layout == null) analyse(); });
            LinearLayout tools = studio.row();
            include = studio.button(UiLanguage.t("Incluir"), "plus"); exclude = studio.button(UiLanguage.t("Excluir área"), "minus");
            LinearLayout.LayoutParams includeSpace=new LinearLayout.LayoutParams(0,studio.dp(44),1);includeSpace.rightMargin=studio.dp(6);
            LinearLayout.LayoutParams excludeSpace=new LinearLayout.LayoutParams(0,studio.dp(44),1);excludeSpace.rightMargin=studio.dp(6);
            tools.addView(include,includeSpace);tools.addView(exclude,excludeSpace);
            Button undo = studio.icon("undo",UiLanguage.t("Desfazer ponto")); tools.addView(undo,new LinearLayout.LayoutParams(studio.dp(48),studio.dp(44)));
            include.setOnClickListener(v -> { negative=false; updateTools(); });
            exclude.setOnClickListener(v -> { negative=true; updateTools(); });
            undo.setOnClickListener(v -> { if (!points.isEmpty()) { points.remove(points.size()-1); changed(); } });
            if (!composition) page.addView(tools);
            status = studio.text(composition ? UiLanguage.t("Encontrando elementos da composição…") : UiLanguage.t("Toque no objeto. Use − para retirar áreas da seleção."),13,FlowDesign.MUTED,false);
            status.setGravity(Gravity.CENTER); status.setPadding(studio.dp(12),studio.dp(12),studio.dp(12),studio.dp(12));page.addView(status);
            LinearLayout actions=studio.row();
            Button retry=studio.icon("refresh",UiLanguage.t("Analisar novamente"));actions.addView(retry,new LinearLayout.LayoutParams(studio.dp(48),studio.dp(48)));retry.setOnClickListener(v->analyse());
            save=studio.button(composition?UiLanguage.t("Editar boxes"):UiLanguage.t("Salvar referência"),composition?"boxes":"check");enableSave(false);LinearLayout.LayoutParams saveSpace=new LinearLayout.LayoutParams(0,studio.dp(48),1);saveSpace.leftMargin=studio.dp(8);actions.addView(save,saveSpace);page.addView(actions);
            if(composition){Button manual=studio.button(UiLanguage.t("Desenhar boxes manualmente"),"edit");page.addView(manual,new LinearLayout.LayoutParams(-1,studio.dp(44)));manual.setOnClickListener(v->editBoxes(layout==null?new JSONObject():layout));}
            save.setOnClickListener(v -> { if(composition)editBoxes(layout);else chooseDestination(); });
            dialog.setContentView(page); dialog.setOnDismissListener(d -> {closed=true;revision++;if(active==this)active=null;});updateTools();
        }
        void show() {
            UiMotion.dialog(dialog);dialog.show();Window window=dialog.getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE);window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setStatusBarColor(Color.BLACK);window.setNavigationBarColor(Color.BLACK);window.setLayout(-1,-1);
            if(android.os.Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);
            page.setOnApplyWindowInsetsListener((v,insets)->{int top,bottom,left=0,right=0;if(android.os.Build.VERSION.SDK_INT>=30){Insets bars=insets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());top=bars.top;bottom=bars.bottom;left=bars.left;right=bars.right;}else{top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();}page.setPadding(left+studio.dp(12),top+studio.dp(8),right+studio.dp(12),bottom+studio.dp(12));return insets;});page.requestApplyInsets();
            reveal.setVisibility(concealed?View.VISIBLE:View.GONE);
            if(composition&&!concealed)analyse();
        }
        void conceal(){concealed=true;reveal.setVisibility(View.VISIBLE);canvas.invalidate();}
        void enableSave(boolean enabled){save.setEnabled(enabled);save.setAlpha(enabled?1:.4f);}
        void updateTools(){include.setSelected(!negative);exclude.setSelected(negative);include.setAlpha(negative?.5f:1);exclude.setAlpha(negative?1:.5f);}
        void changed(){revision++;mask=null;enableSave(false);canvas.invalidate();handler.removeCallbacks(debounced);if(!points.isEmpty())handler.postDelayed(debounced,350);}
        final Runnable debounced=()->analyse();
        void analyse(){
            if(closed||concealed||busy||!composition&&points.isEmpty())return;
            final int snapshot=revision;final JSONArray clicks=new JSONArray();for(JSONObject p:points)clicks.put(p);
            busy=true;enableSave(false);status.setText(UiLanguage.t("Preparando análise…"));
            worker.execute(()->{
                try{
                    JSONObject result=VisualAnalysis.run(studio.jobs.server,studio.host.address(),studio.store.file(source.file),composition?"composition":"segment",clicks,()->closed,
                        message->activity.runOnUiThread(()->{if(!closed&&snapshot==revision)status.setText(message);}));
                    Bitmap resultMask=null;JSONObject resultLayout=null;
                    if(composition)resultLayout=VisualAnalysis.layout(result);
                    else{
                        if(result.getInt("width")!=image.getWidth()||result.getInt("height")!=image.getHeight())throw new IllegalArgumentException(UiLanguage.t("Resultado de análise inválido."));
                        byte[] bytes=android.util.Base64.decode(result.getString("mask"),android.util.Base64.DEFAULT);
                        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
                        if(bounds.outWidth!=image.getWidth()||bounds.outHeight!=image.getHeight())throw new IllegalArgumentException(UiLanguage.t("Resultado de análise inválido."));
                        resultMask=BitmapFactory.decodeByteArray(bytes,0,bytes.length);if(resultMask==null)throw new IllegalArgumentException(UiLanguage.t("Resultado de análise inválido."));
                    }
                    final Bitmap doneMask=resultMask;final JSONObject doneLayout=resultLayout;
                    activity.runOnUiThread(()->{if(closed||snapshot!=revision)return;mask=doneMask;layout=doneLayout;canvas.invalidate();enableSave(true);status.setText(composition?UiLanguage.t("Composição pronta. Revise nomes e posições dos boxes."):UiLanguage.t("Seleção pronta. Mais toques refinam o recorte."));});
                }catch(InterruptedException ignored){}
                catch(Exception error){activity.runOnUiThread(()->{if(!closed&&snapshot==revision)status.setText(error.getMessage());});}
                finally{activity.runOnUiThread(()->{busy=false;if(!closed&&snapshot!=revision&&!points.isEmpty())analyse();});}
            });
        }
        void editBoxes(JSONObject value){
            if(concealed)return;
            try{value=new JSONObject(value.toString()).put("private",source.hidden);}catch(JSONException error){studio.toast(error.getMessage());return;}
            BoxEditor.show(activity,value,(double)image.getWidth()/image.getHeight(),image,next->{
                ReferencePreset preset=new ReferencePreset();preset.kind="composition";preset.layout=next;preset.useImages=false;preset.useRefmod=false;preset.useLoras=false;
                preset.images.add(source);preset.cover=source.file;preset.privateCover=source.hidden;
                dialog.dismiss();studio.editExtracted(preset);
            });
        }
        void chooseDestination(){
            if(mask==null||concealed)return;
            String[] kinds={"object","character","style","location"};String[] names={UiLanguage.t("Objeto"),UiLanguage.t("Personagem"),UiLanguage.t("Estilo"),UiLanguage.t("Local")};
            PopupMenu menu=new OrganicPopupMenu(activity,save);
            for(int i=0;i<kinds.length;i++){final String kind=kinds[i];menu.getMenu().add(names[i]).setOnMenuItemClickListener(item->{
                PopupMenu destinations=new OrganicPopupMenu(activity,save);
                destinations.getMenu().add(UiLanguage.t("Criar nova referência")).setOnMenuItemClickListener(choice->{ReferencePreset target=new ReferencePreset();target.kind=kind;saveCrop(target);return true;});
                for(ReferencePreset p:studio.store.all())if(p.kind.equals(kind)&&p.images.size()<3&&(!p.hidden()||studio.host.nsfw()))destinations.getMenu().add(p.name).setOnMenuItemClickListener(choice->{saveCrop(p.copy());return true;});
                MenuIcons.show(activity,destinations);return true;
            });}
            MenuIcons.show(activity,menu);
        }
        void saveCrop(ReferencePreset preset){
            final Bitmap selected=mask;enableSave(false);
            worker.execute(()->{try{
                Bitmap cutout=cutout(image,selected);String name=UUID.randomUUID()+".png";
                try(OutputStream stream=VaultFiles.output(studio.store.file(name))){if(!cutout.compress(Bitmap.CompressFormat.PNG,100,stream))throw new java.io.IOException("PNG");}finally{cutout.recycle();}
                preset.images.add(new ReferencePreset.Picture(name,source.source,source.hidden));if(preset.cover.isEmpty())preset.cover=name;preset.privateCover|=source.hidden;
                activity.runOnUiThread(()->{if(closed)return;dialog.dismiss();studio.editExtracted(preset);});
            }catch(Exception error){activity.runOnUiThread(()->{if(!closed){status.setText(error.getMessage());enableSave(true);}});}});
        }
        final class CanvasView extends View {
            final RectF area=new RectF();final Paint paint=new Paint(3);float downX,downY;
            CanvasView(){super(activity);setContentDescription(UiLanguage.t("Imagem para selecionar objetos"));}
            protected void onDraw(Canvas c){
                float scale=Math.min((getWidth()-studio.dp(12))/(float)image.getWidth(),(getHeight()-studio.dp(16))/(float)image.getHeight());if(scale<=0)return;
                float w=image.getWidth()*scale,h=image.getHeight()*scale;area.set((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
                Path clip=new Path();clip.addRoundRect(area,studio.dp(22),studio.dp(22),Path.Direction.CW);c.save();c.clipPath(clip);
                paint.setColor(FlowDesign.CONTROL);c.drawRect(area,paint);
                if(!concealed){c.drawBitmap(image,null,area,paint);
                    if(mask!=null){paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(new float[]{0,0,0,0,161,0,0,0,0,125,0,0,0,0,255,.45f,0,0,0,0}));c.drawBitmap(mask,null,area,paint);paint.setColorFilter(null);}
                    if(layout!=null){JSONArray boxes=layout.optJSONArray("boxes");paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(studio.dp(2));paint.setColor(0xFFE0D1FF);for(int i=0;boxes!=null&&i<boxes.length();i++){JSONObject b=boxes.optJSONObject(i);c.drawRoundRect(area.left+(float)b.optDouble("x")*w,area.top+(float)b.optDouble("y")*h,area.left+(float)(b.optDouble("x")+b.optDouble("w"))*w,area.top+(float)(b.optDouble("y")+b.optDouble("h"))*h,studio.dp(6),studio.dp(6),paint);}paint.setStyle(Paint.Style.FILL);}
                    for(JSONObject p:points){float x=area.left+(float)p.optDouble("x")*w,y=area.top+(float)p.optDouble("y")*h;paint.setColor(Color.WHITE);c.drawCircle(x,y,studio.dp(7),paint);paint.setColor(p.optInt("label")==1?0xFF9171E9:0xFFDF6D7C);c.drawCircle(x,y,studio.dp(5),paint);}
                }else{paint.setColor(FlowDesign.MUTED);paint.setTextSize(studio.dp(15));paint.setTextAlign(Paint.Align.CENTER);c.drawText(UiLanguage.t("Imagem protegida"),area.centerX(),area.centerY(),paint);paint.setTextAlign(Paint.Align.LEFT);}
                c.restore();
            }
            public boolean onTouchEvent(MotionEvent e){
                if(composition||concealed)return true;
                if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
                if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.hypot(e.getX()-downX,e.getY()-downY)<studio.dp(14)&&area.contains(e.getX(),e.getY())){
                    if(points.size()>=16){studio.toast(UiLanguage.t("Limite de 16 pontos. Desfaça um ponto para continuar."));return true;}
                    try{points.add(new JSONObject().put("x",(e.getX()-area.left)/area.width()).put("y",(e.getY()-area.top)/area.height()).put("label",negative?0:1));changed();performClick();}catch(JSONException ignored){}
                }return true;
            }
            public boolean performClick(){super.performClick();return true;}
        }
    }
    static Bitmap cutout(Bitmap image,Bitmap mask){
        int w=image.getWidth(),h=image.getHeight();if(mask==null||mask.getWidth()!=w||mask.getHeight()!=h)throw new IllegalArgumentException(UiLanguage.t("Invalid mask"));
        int[] pixels=new int[w*h],alpha=new int[w*h];image.getPixels(pixels,0,w,0,0,w,h);mask.getPixels(alpha,0,w,0,0,w,h);
        int left=w,top=h,right=-1,bottom=-1;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){int at=y*w+x,a=Color.red(alpha[at]);pixels[at]=(pixels[at]&0xFFFFFF)|(a<<24);if(a>127){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}}
        if(right<left)throw new IllegalArgumentException(UiLanguage.t("Seleção vazia."));
        Bitmap full=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);Bitmap crop=Bitmap.createBitmap(full,left,top,right-left+1,bottom-top+1);if(crop!=full)full.recycle();return crop;
    }
}
