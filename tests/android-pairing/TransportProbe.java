package com.leal.comfyremote;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.TextView;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import org.json.*;

/** Synthetic integration test APK only; never included in the commercial application. */
public final class TransportProbe extends Activity {
    private void check(boolean condition,String message)throws Exception{if(!condition)throw new IOException(message);}
    private byte[] bytes(InputStream source)throws Exception{try(InputStream in=source;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException("Unexpected test payload");out.write(b,0,n);}return out.toByteArray();}}
    private int status(String address)throws Exception{HttpURLConnection c=DesktopConnection.open(this,address);c.setConnectTimeout(15000);c.setReadTimeout(15000);try{return c.getResponseCode();}finally{c.disconnect();}}
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText("Synthetic pairing checks");setContentView(text);String phase=getIntent().getStringExtra("phase");new Thread(()->run(phase==null?"pair":phase)).start();}
    private void run(String phase){
        JSONObject report=new JSONObject();List<String> passed=new ArrayList<>();
        try{
            JSONObject fixture=new JSONObject(new String(bytes(getAssets().open("invite.json")),StandardCharsets.UTF_8));String address=fixture.getString("endpoint"),pin=fixture.getString("pin"),code=fixture.getString("code");
            if(phase.equals("pair")){
                for(String host:new String[]{"100.64.0.0","100.127.255.255","192.168.1.20","10.1.2.3","172.16.0.1"})DesktopConnection.validateEndpoint("https://"+host+":8190");
                for(String host:new String[]{"100.63.255.255","100.128.0.0","8.8.8.8","192.168.001.2","100.64.256.1","pc.example"}){
                    boolean blocked=false;try{DesktopConnection.validateEndpoint("https://"+host+":8190");}catch(Exception expected){blocked=true;}check(blocked,"Invalid private endpoint accepted: "+host);
                }
                passed.add("LAN and Tailscale IPv4 boundaries enforced without permitting public hosts");
                DesktopConnection.forget(this);
                Bitmap image=BitmapFactory.decodeStream(getAssets().open("invite.png"));int[] pixels=new int[image.getWidth()*image.getHeight()];image.getPixels(pixels,0,image.getWidth(),0,0,image.getWidth(),image.getHeight());
                String decoded=new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels)))).getText();image.recycle();check(decoded.equals(code),"Desktop QR and Android decoder disagree");passed.add("desktop QR decoded by bundled Android ZXing");
                boolean rejected=false;try{DesktopConnection.pair(this,code.replace(pin,String.join("",Collections.nCopies(64,"0"))),"synthetic");}catch(IOException expected){rejected=true;}check(rejected,"Wrong pin accepted");passed.add("wrong desktop identity rejected before pairing");
                DesktopConnection.Record record=DesktopConnection.pair(this,code,"Synthetic Android emulator");passed.add("one-time pairing completed");
                rejected=false;
                if(fixture.optBoolean("remote")){
                    HttpsURLConnection hostname=(HttpsURLConnection)new URL(record.transport().replace("127.0.0.1","localhost")+"/bwdacc/status").openConnection(Proxy.NO_PROXY);hostname.setSSLSocketFactory(record.transportSockets());hostname.setConnectTimeout(15000);hostname.setReadTimeout(15000);
                    try{hostname.getResponseCode();}catch(IOException expected){rejected=true;}finally{hostname.disconnect();}
                }else if(fixture.optBoolean("privateNetwork")){
                    String wrongEndpoint=fixture.getString("wrongHostnameEndpoint");
                    DesktopConnection.save(this,new DesktopConnection.Record(wrongEndpoint,pin,record.token,record.id));
                    try{status(wrongEndpoint+"/bwdacc/status");}catch(javax.net.ssl.SSLException expected){rejected=true;}
                    finally{DesktopConnection.save(this,record);}
                }else{
                    HttpsURLConnection hostname=(HttpsURLConnection)new URL(record.transport().replace("127.0.0.1","localhost")+"/bwdacc/status").openConnection(Proxy.NO_PROXY);hostname.setSSLSocketFactory(record.sockets);hostname.setConnectTimeout(15000);hostname.setReadTimeout(15000);
                    try{hostname.getResponseCode();}catch(javax.net.ssl.SSLException expected){rejected=true;}finally{hostname.disconnect();}
                }
                check(rejected,"Hostname verification disabled");passed.add(fixture.optBoolean("remote")?"remote carrier refuses changed local destination":"hostname mismatch rejected with correct pin on reachable server");
                File stored=new File(getNoBackupFilesDir(),"desktop-pairing.dat");String content=new String(bytes(new FileInputStream(stored)),StandardCharsets.ISO_8859_1);check(!content.contains(record.token)&&!content.contains(address)&&(record.relayKey.isEmpty()||!content.contains(record.relayKey)),"Plaintext credential persisted");passed.add("credential stored encrypted in no-backup directory");
                if(fixture.optBoolean("remote")){
                    check(record.permanent==fixture.optBoolean("permanent"),"Remote mode not preserved");
                    RemoteTunnel.validate("https://studio.bwdacc.app",record.relayKey,true);
                    for(String invalid:new String[]{"http://studio.bwdacc.app","https://127.0.0.1","https://pc.local","https://studio.bwdacc.app:443","https://studio.bwdacc.app/path","https://user@studio.bwdacc.app","https://studio.bwdacc.app?key=x"}){
                        boolean blocked=false;try{RemoteTunnel.validate(invalid,record.relayKey,true);}catch(Exception expected){blocked=true;}check(blocked,"Invalid fixed endpoint accepted");
                    }
                    passed.add("v3 fixed hostname validation and persisted mode");
                    boolean refused=false;try{RemoteTunnel.validate("https://untrusted.example",record.relayKey);}catch(IOException expected){refused=true;}check(refused,"Unsupported relay accepted");
                    URL local=new URL(record.transport());try(Socket stranger=new Socket("127.0.0.1",local.getPort())){stranger.setSoTimeout(5000);stranger.getOutputStream().write(new byte[32]);check(stranger.getInputStream().read()==-1,"Unauthenticated local socket accepted");}
                    passed.add("relay allowlist and per-process loopback admission enforced");
                }
                check(status(address+"/system_stats")==200,"Authenticated JSON unavailable");passed.add("authenticated HTTPS works");
                HttpURLConnection media=DesktopConnection.open(this,address+"/view?filename=synthetic");try{check(Arrays.equals(bytes(media.getInputStream()),new byte[]{0,1,2,3,(byte)128,(byte)255}),"Media bytes changed");}finally{media.disconnect();}passed.add("binary media delivered intact");
                HttpURLConnection prompt=DesktopConnection.open(this,address+"/prompt");prompt.setRequestMethod("POST");prompt.setDoOutput(true);prompt.setRequestProperty("Content-Type","application/json");try{try(OutputStream out=prompt.getOutputStream()){out.write("{\"prompt\":{\"label\":\"Android synthetic\"}}".getBytes(StandardCharsets.UTF_8));}check(new String(bytes(prompt.getInputStream()),StandardCharsets.UTF_8).contains("Android synthetic"),"Prompt forwarding failed");}finally{prompt.disconnect();}passed.add("generation request body forwarded");
                HttpURLConnection upload=DesktopConnection.open(this,address+"/upload/image");upload.setRequestMethod("POST");upload.setDoOutput(true);upload.setChunkedStreamingMode(1024);upload.setRequestProperty("Content-Type","multipart/form-data; boundary=test");try{try(OutputStream out=upload.getOutputStream()){out.write(new byte[1048576]);}check(new JSONObject(new String(bytes(upload.getInputStream()),StandardCharsets.UTF_8)).getInt("bytes")==1048576,"Upload forwarding failed");}finally{upload.disconnect();}passed.add("chunked media upload forwarded");
                check(status(address+"/view?filename=redirect")==502,"Redirect followed");rejected=false;try{DesktopConnection.open(this,address.replace("https:","http:")+"/queue");}catch(IOException expected){rejected=true;}check(rejected,"Cleartext accepted");
                rejected=false;try{DesktopConnection.open(this,"https://127.0.0.2:8190/queue");}catch(IOException expected){rejected=true;}check(rejected,"Other origin accepted");passed.add("redirect, cleartext and cross-origin rejected");
                CountDownLatch message=new CountDownLatch(1);ComfyProgressSocket socket=new ComfyProgressSocket(this,address,"android-test",new ComfyProgressSocket.Listener(){public void message(JSONObject data){if(data.optString("type").equals("status"))message.countDown();}public void connected(boolean on){}});
                try{check(message.await(20,TimeUnit.SECONDS),"Authenticated WSS progress unavailable");}finally{socket.close();}passed.add("real Android WSS progress client works");
                if(fixture.optBoolean("permanent")){
                    HttpURLConnection slow=DesktopConnection.open(this,address+"/view?filename=slow");slow.setReadTimeout(20000);
                    try{check(bytes(slow.getInputStream()).length==24*16384,"Active download closed at idle deadline");}finally{slow.disconnect();}
                    passed.add("receive-only download remains alive past scaled idle deadline");
                }
            }else if(phase.equals("reopen")){
                check(DesktopConnection.endpoint(this).equals(address),"Credential failed after process restart");check(status(address+"/queue")==200,"Persisted credential unavailable");passed.add("Keystore credential survives Android process restart");
            }else if(phase.equals("serverrestart")){
                check(fixture.optBoolean("permanent")||fixture.optBoolean("privateNetwork"),"Persistent fixture required");
                check(DesktopConnection.endpoint(this).equals(address)&&status(address+"/queue")==200,"Saved pairing lost after gateway restart");
                passed.add("same phone credential reconnects after gateway restart without another QR");
            }else if(phase.equals("revoked")){
                check(status(address+"/queue")==401,"Revoked credential accepted");passed.add("revoked Android credential rejected");
            }else if(phase.equals("corrupt")){
                File file=new File(getNoBackupFilesDir(),"desktop-pairing.dat");byte[] value=bytes(new FileInputStream(file));value[value.length-1]^=1;try(FileOutputStream out=new FileOutputStream(file)){out.write(value);}passed.add("synthetic credential corrupted for next restart");
            }else if(phase.equals("corruptcheck")){
                check(DesktopConnection.endpoint(this).isEmpty(),"Corrupted credential accepted");boolean rejected=false;try{DesktopConnection.open(this,address+"/queue");}catch(IOException expected){rejected=true;}check(rejected,"Credential failure bypassed");passed.add("corrupted credential fails closed without crash");
            }else throw new IOException("Unknown test phase");
            report.put("passed",true).put("phase",phase).put("cases",new JSONArray(passed));
        }catch(Throwable error){try{StringWriter trace=new StringWriter();error.printStackTrace(new PrintWriter(trace));report.put("passed",false).put("phase",phase).put("completed",new JSONArray(passed)).put("error",trace.toString());}catch(Exception ignored){}}
        try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"result-"+phase+".json"))){out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        final String result=report.toString();runOnUiThread(()->((TextView)((android.view.ViewGroup)findViewById(android.R.id.content)).getChildAt(0)).setText(result));
    }
}
