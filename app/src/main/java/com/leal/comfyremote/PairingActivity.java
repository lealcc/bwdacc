package com.leal.comfyremote;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Build;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.util.*;
import java.util.concurrent.*;

/** Explicit, in-app camera flow: never opens browser links or logs QR contents. */
public class PairingActivity extends Activity implements SurfaceHolder.Callback {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private SurfaceView preview;
    private Camera camera;
    private EditText code;
    private TextView status;
    private Button scan,submit;
    private LinearLayout previewHint;
    private volatile boolean decoding,closed;
    private boolean pairing,requestedCamera;
    private long lastFrame;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiLanguage.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(dp(20),dp(16),dp(20),dp(16));page.setBackground(Appearance.atmosphere());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
            page.setOnApplyWindowInsetsListener((view,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());page.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,dp(12)+bars.bottom);return insets;});
        }
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=button("");FlowDesign.symbol(back,"left",UiLanguage.t("Voltar"));header.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));back.setOnClickListener(v->finish());
        TextView title=label(UiLanguage.t("Conectar ao PC"),22);title.setTypeface(Branding.secondary(this,600));title.setPadding(dp(14),0,0,0);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setVerticalScrollBarEnabled(false);LinearLayout content=new LinearLayout(this);content.setOrientation(1);scroll.addView(content);LinearLayout.LayoutParams scrolling=new LinearLayout.LayoutParams(-1,0,1);scrolling.topMargin=dp(20);page.addView(scroll,scrolling);
        TextView intro=label(UiLanguage.t("No bwdacc Desktop, abra Dispositivos e gere um QR para conectar este celular."),14);intro.setTextColor(FlowDesign.MUTED);intro.setPadding(0,0,0,dp(20));content.addView(intro);
        FrameLayout frame=new FrameLayout(this);frame.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,24,false));frame.setClipToOutline(true);
        preview=new SurfaceView(this);preview.setVisibility(View.INVISIBLE);preview.getHolder().addCallback(this);frame.addView(preview,new FrameLayout.LayoutParams(-1,-1));content.addView(frame,new LinearLayout.LayoutParams(-1,dp(220)));
        previewHint=new LinearLayout(this);previewHint.setOrientation(1);previewHint.setGravity(Gravity.CENTER);ImageView qr=new ImageView(this);qr.setImageDrawable(FlowDesign.icon(this,"qr",64));previewHint.addView(qr,new LinearLayout.LayoutParams(dp(64),dp(64)));TextView hint=label(UiLanguage.t("Seu computador. Seu espaço."),14);hint.setPadding(0,dp(18),0,0);hint.setTextColor(FlowDesign.MUTED);hint.setGravity(Gravity.CENTER);previewHint.addView(hint);frame.addView(previewHint,new FrameLayout.LayoutParams(-1,-1));
        scan=button(UiLanguage.t("Ler QR com câmera"));FlowDesign.labelIcon(scan,"qr");LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-1,dp(52));action.topMargin=dp(12);content.addView(scan,action);scan.setOnClickListener(v->{requestedCamera=true;preview.setVisibility(View.VISIBLE);if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},41);else startCamera();});
        TextView manual=label(UiLanguage.t("Ou use o código de pareamento"),13);manual.setPadding(0,dp(24),0,dp(10));manual.setTextColor(FlowDesign.MUTED);content.addView(manual);
        code=new EditText(this);code.setSingleLine(true);code.setHint(UiLanguage.t("Cole o código do Desktop"));code.setTypeface(Branding.body(this,400));code.setTextColor(FlowDesign.INK);code.setHintTextColor(FlowDesign.MUTED);code.setTextSize(14);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);code.setBackground(FlowDesign.pill(this,FlowDesign.SURFACE,22,true));code.setPadding(dp(16),dp(12),dp(16),dp(12));content.addView(code,new LinearLayout.LayoutParams(-1,dp(54)));
        submit=button(UiLanguage.t("Conectar"));FlowDesign.labelIcon(submit,"connection");LinearLayout.LayoutParams connect=new LinearLayout.LayoutParams(-1,dp(50));connect.topMargin=dp(10);content.addView(submit,connect);submit.setOnClickListener(v->beginPair(code.getText().toString()));
        status=label(UiLanguage.t("QR de uso único. Confirme a conexão no computador."),12);status.setTextColor(FlowDesign.MUTED);status.setPadding(0,dp(20),0,dp(14));status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);content.addView(status);
        Button remote=button(UiLanguage.t("Fora de casa com Tailscale"));FlowDesign.labelIcon(remote,"connection");remote.setBackgroundColor(Color.TRANSPARENT);content.addView(remote,new LinearLayout.LayoutParams(-1,dp(48)));remote.setOnClickListener(v->ConnectionGuide.show(this));setContentView(page);
    }
    private int dp(int value){return FlowDesign.dp(this,value);}
    private TextView label(String text,int size){TextView value=new TextView(this);value.setText(UiLanguage.t(text));value.setTextSize(size);value.setTextColor(FlowDesign.INK);value.setTypeface(Branding.body(this,400));return value;}
    private Button button(String text){Button value=new FlowDesign.IconButton(this);value.setText(UiLanguage.t(text));value.setAllCaps(false);value.setTextSize(14);value.setTypeface(Branding.secondary(this,500));value.setTextColor(FlowDesign.INK);value.setBackground(FlowDesign.pill(this,FlowDesign.CONTROL,24,false));value.setPadding(dp(14),0,dp(14),0);return value;}
    private void beginPair(String value){
        if(pairing||closed)return;if(!value.trim().startsWith("bwdacc://pair?")){status.setText(UiLanguage.t("Use o QR gerado pelo bwdacc Desktop."));return;}
        pairing=true;stopCamera();scan.setEnabled(false);submit.setEnabled(false);code.setText("");code.setEnabled(false);status.setText(UiLanguage.t("Confira a solicitação e confirme no Desktop…"));
        worker.execute(()->{String error=null;try{DesktopConnection.pair(this,value,Build.MANUFACTURER+" "+Build.MODEL);}catch(Exception failure){error=failure instanceof javax.net.ssl.SSLException?"Identidade do Desktop não conferiu. Gere outro QR e confira o computador.":failure.getMessage();}
            final String message=error;runOnUiThread(()->{if(closed)return;pairing=false;if(message==null){setResult(RESULT_OK);finish();}else{status.setText(message==null?UiLanguage.t("Pareamento indisponível."):message);scan.setEnabled(true);submit.setEnabled(true);code.setEnabled(true);}});
        });
    }
    @SuppressWarnings("deprecation") private void startCamera(){
        if(closed||pairing||camera!=null||!requestedCamera||checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||!preview.getHolder().getSurface().isValid())return;
        try{
            camera=Camera.open();Camera.Parameters p=camera.getParameters();
            List<Camera.Size> sizes=p.getSupportedPreviewSizes();Camera.Size chosen=null;for(Camera.Size s:sizes)if(s.width<=1280&&(chosen==null||s.width>chosen.width))chosen=s;
            if(chosen!=null)p.setPreviewSize(chosen.width,chosen.height);p.setPreviewFormat(android.graphics.ImageFormat.NV21);
            if(p.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))p.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(p);camera.setDisplayOrientation(90);camera.setPreviewDisplay(preview.getHolder());
            camera.setPreviewCallback((bytes,source)->{
                if(closed||pairing||decoding||System.currentTimeMillis()-lastFrame<350)return;lastFrame=System.currentTimeMillis();decoding=true;
                Camera.Size size=source.getParameters().getPreviewSize();byte[] copy=Arrays.copyOf(bytes,bytes.length);
                worker.execute(()->{String found=null;try{PlanarYUVLuminanceSource luminance=new PlanarYUVLuminanceSource(copy,size.width,size.height,0,0,size.width,size.height,false);found=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(luminance))).getText();}catch(Exception ignored){}finally{decoding=false;}
                    if(found!=null){final String text=found;runOnUiThread(()->beginPair(text));}
                });
            });camera.startPreview();previewHint.setVisibility(View.GONE);status.setText(UiLanguage.t("Aponte para o QR em Dispositivos no Desktop."));
        }catch(Exception failure){stopCamera();status.setText(UiLanguage.t("Câmera indisponível. Você pode colar o código do Desktop."));}
    }
    private void stopCamera(){if(camera!=null){try{camera.setPreviewCallback(null);camera.stopPreview();}catch(Exception ignored){}camera.release();camera=null;}}
    @Override public void surfaceCreated(SurfaceHolder holder){startCamera();}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){stopCamera();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==41){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startCamera();else status.setText(UiLanguage.t("Sem acesso à câmera. Cole o código para continuar."));}}
    @Override protected void onResume(){super.onResume();if(preview!=null)startCamera();}
    @Override protected void onPause(){stopCamera();super.onPause();}
    @Override protected void onDestroy(){closed=true;stopCamera();worker.shutdownNow();super.onDestroy();}
}
