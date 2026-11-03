package com.leal.comfyremote;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** User-initiated SAF backup/restore. Recovery codes are never written to state or logs. */
public final class VaultBackupActivity extends Activity {
    private static final int CREATE=71,OPEN=72;
    private LinearLayout page,body;
    private TextView status;
    private byte[] recovery;
    private Thread task;
    private boolean busy,closed;
    private long lastProgress;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);UiLanguage.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        page=new LinearLayout(this);page.setOrientation(1);page.setBackgroundColor(Color.BLACK);
        page.setPadding(dp(22),dp(22),dp(22),dp(18));
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);
            page.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.ime());
                page.setPadding(dp(22)+bars.left,dp(16)+bars.top,dp(22)+bars.right,dp(18)+bars.bottom);return insets;});}
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        Button back=new FlowDesign.IconButton(this);FlowDesign.symbol(back,"left",UiLanguage.t("Voltar"));
        header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));back.setOnClickListener(v->leave());
        TextView title=text(UiLanguage.t("Backup do cofre"),22);title.setTypeface(Branding.secondary(this,600));
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1));page.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(0,dp(24),0,dp(12));scroll.addView(body);page.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(page);showHome();
    }
    private TextView text(String value,int size){TextView view=new TextView(this);view.setText(UiLanguage.t(value));view.setTextColor(FlowDesign.INK);view.setTextSize(size);view.setTypeface(Branding.secondary(this,400));return view;}
    private void paragraph(String value){TextView view=text(value,14);view.setTextColor(FlowDesign.MUTED);view.setLineSpacing(dp(4),1);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(20);body.addView(view,p);}
    private Button button(String title,String icon,Runnable action){
        Button b=new Button(this);b.setText(UiLanguage.t(title));b.setAllCaps(false);b.setTextColor(FlowDesign.INK);b.setTextSize(15);b.setTypeface(Branding.secondary(this,500));
        b.setBackground(FlowDesign.pill(this,FlowDesign.CONTROL,22,true));b.setPadding(dp(18),0,dp(18),0);b.setGravity(Gravity.CENTER);
        if(icon!=null)FlowDesign.labelIcon(b,icon);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(52));p.bottomMargin=dp(12);body.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    private void wipe(){if(recovery!=null){Arrays.fill(recovery,(byte)0);recovery=null;}}
    private void showHome(){
        body.removeAllViews();wipe();
        paragraph("Leve seu acervo para outro aparelho com um arquivo criptografado e um código de recuperação.");
        button(UiLanguage.t("Criar backup"),"download",this::showExport);
        button(UiLanguage.t("Restaurar backup"),"folder",()->{
            if(!VaultFiles.ready()){message("Abra o cofre antes de restaurar.");return;}
            try{VaultBackup.requireEmpty(this);}catch(Exception e){message(e.getMessage());return;}
            Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");startActivityForResult(intent,OPEN);
        });
        paragraph("A restauração exige uma instalação vazia. O acervo atual nunca é substituído por esse fluxo. Conexões com computadores precisam ser pareadas novamente.");
        status=text("",13);body.addView(status);
    }
    private void showExport(){
        try{
            wipe();recovery=VaultBackup.newRecoveryKey();String code=VaultBackup.recoveryCode(recovery);
            body.removeAllViews();paragraph("Guarde este código separado do arquivo. Quem tiver os dois poderá abrir o backup. Sem o código, não há recuperação.");
            TextView value=text(code,16);value.setTypeface(android.graphics.Typeface.MONOSPACE);value.setSaveEnabled(false);value.setPadding(dp(16),dp(18),dp(16),dp(18));value.setBackground(FlowDesign.shape(this,FlowDesign.SURFACE,22,false));body.addView(value);
            button(UiLanguage.t("Copiar código"),"copy",()->{
                ClipData clip=ClipData.newPlainText("Código de recuperação bwdacc",code);
                if(Build.VERSION.SDK_INT>=33){PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);}
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(clip);
                Toast.makeText(this,UiLanguage.t("Código copiado. Guarde em local separado."),Toast.LENGTH_LONG).show();
            });
            CheckBox saved=new CheckBox(this);saved.setText(UiLanguage.t("Guardei meu código em local seguro"));saved.setTextColor(FlowDesign.INK);saved.setSaveEnabled(false);body.addView(saved);
            Button choose=button(UiLanguage.t("Escolher onde salvar"),"download",()->{
                Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream")
                    .putExtra(Intent.EXTRA_TITLE,"bwdacc-"+new SimpleDateFormat("yyyy-MM-dd-HHmm",Locale.ROOT).format(new Date())+".bwdvault");
                startActivityForResult(intent,CREATE);
            });choose.setEnabled(false);saved.setOnCheckedChangeListener((b,on)->choose.setEnabled(on));
            paragraph("O backup inclui mídia, prompts, referências e configurações. Downloads ainda em andamento ficam fora. Mantenha o app aberto até a verificação terminar.");
        }catch(Exception e){showHome();message("Não foi possível preparar o código.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();
        if(request==CREATE){if(recovery==null){showHome();message("Operação interrompida. Crie um novo código e backup.");return;}startExport(uri);}
        else if(request==OPEN)showRestore(uri);
    }
    private void showRestore(Uri uri){
        body.removeAllViews();wipe();
        paragraph("Cole o código correspondente ao arquivo escolhido. Imagens, vídeos, referências e configurações serão restaurados nesta instalação vazia.");
        EditText input=new EditText(this);input.setHint(UiLanguage.t("BWD1-…"));input.setTextColor(FlowDesign.INK);input.setHintTextColor(FlowDesign.MUTED);input.setTextSize(15);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING|EditorInfo.IME_FLAG_NO_EXTRACT_UI);input.setSaveEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        input.setBackground(FlowDesign.shape(this,FlowDesign.CONTROL,20,false));input.setPadding(dp(16),dp(16),dp(16),dp(16));body.addView(input,new LinearLayout.LayoutParams(-1,-2));
        button(UiLanguage.t("Restaurar nesta instalação"),"folder",()->{
            try{byte[] key=VaultBackup.parseCode(input.getText().toString());input.setText("");startRestore(uri,key);}
            catch(Exception e){input.setError(e.getMessage());}
        });
        paragraph("As configurações desta instalação vazia serão substituídas. Ao concluir, feche e reabra o app. O código não será salvo no aparelho.");
    }
    private void working(String label){
        busy=true;body.removeAllViews();body.addView(text(label,19));
        ProgressBar bar=new ProgressBar(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(80));p.topMargin=dp(24);body.addView(bar,p);
        status=text(UiLanguage.t("Preparando…"),14);status.setGravity(Gravity.CENTER);body.addView(status);
        paragraph("Mantenha o app aberto. Voltar solicita cancelamento antes da confirmação final.");
    }
    private void progress(long bytes,int count){
        long now=SystemClock.elapsedRealtime();if(now-lastProgress<200)return;lastProgress=now;
        runOnUiThread(()->{if(!closed&&busy)status.setText(String.format(Locale.getDefault(),UiLanguage.t("%.1f MB · %d arquivos"),bytes/1048576.0,count));});
    }
    private void startExport(Uri uri){
        byte[] key=recovery.clone();working("Criando backup criptografado");
        task=new Thread(()->{File file=null;
            try{
                file=VaultBackup.export(this,key,this::progress);
                try(InputStream in=new FileInputStream(file);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new IOException();byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException();out.write(buffer,0,n);}
                }
                runOnUiThread(()->{if(!closed)status.setText(UiLanguage.t("Verificando arquivo salvo…"));});
                try(InputStream check=getContentResolver().openInputStream(uri)){if(check==null)throw new IOException();VaultBackup.verify(check,key,this::progress);}
                runOnUiThread(()->{if(closed)return;busy=false;wipe();body.removeAllViews();paragraph("Backup salvo e verificado. Guarde o arquivo e o código em locais separados.");button(UiLanguage.t("Concluir"),null,this::finish);});
            }catch(Exception e){runOnUiThread(()->{if(closed)return;busy=false;showHome();message("Backup não concluído ou não verificado. Não use o arquivo parcial; crie uma nova cópia.");});}
            finally{Arrays.fill(key,(byte)0);if(file!=null)file.delete();}
        },"vault-export");task.start();
    }
    private void startRestore(Uri uri,byte[] key){
        working("Restaurando seu cofre");
        task=new Thread(()->{
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in==null)throw new IOException();VaultBackup.restore(this,in,key,this::progress);
                runOnUiThread(()->{if(closed)return;busy=false;body.removeAllViews();paragraph("Restauração concluída. Feche e abra o bwdacc novamente para carregar seu acervo.");
                    button(UiLanguage.t("Fechar app"),null,this::closeForRestart);});
            }catch(Exception e){runOnUiThread(()->{
                if(closed)return;busy=false;
                if(VaultMaintenance.restartRequired()){body.removeAllViews();paragraph("A confirmação foi interrompida. Reabra o app para concluir com segurança.");button(UiLanguage.t("Fechar app"),null,this::closeForRestart);}
                else{showHome();message("Não foi possível restaurar. Confira arquivo e código. Acervo atual preservado.");}
            });}finally{Arrays.fill(key,(byte)0);}
        },"vault-restore");task.start();
    }
    private void message(String value){if(status!=null)status.setText(value);}
    private void closeForRestart(){finishAffinity();android.os.Process.killProcess(android.os.Process.myPid());}
    private void leave(){if(VaultMaintenance.restartRequired()){closeForRestart();return;}if(busy){if(task!=null)task.interrupt();status.setText(UiLanguage.t("Cancelando com segurança…"));}else finish();}
    @Override public void onBackPressed(){leave();}
    @Override protected void onDestroy(){closed=true;wipe();if(task!=null&&busy)task.interrupt();super.onDestroy();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
