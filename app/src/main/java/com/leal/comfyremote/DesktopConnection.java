package com.leal.comfyremote;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.*;
import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;

/** All commercial HTTP and WebSocket traffic uses an explicitly paired desktop identity. */
final class DesktopConnection {
    private static final String KEY="bwdacc.desktop.credential.v1";
    private static final byte[] AAD=KEY.getBytes(StandardCharsets.UTF_8);
    private static volatile Record current;
    private static boolean initialized;
    static final class Record {
        final String endpoint,pin,token,id,relay,relayKey;
        final boolean permanent;
        final SSLSocketFactory sockets;
        private RemoteTunnel tunnel;
        Record(String endpoint,String pin,String token,String id)throws Exception {
            this(endpoint,pin,token,id,"","");
        }
        Record(String endpoint,String pin,String token,String id,String relay,String relayKey)throws Exception {
            this(endpoint,pin,token,id,relay,relayKey,false);
        }
        Record(String endpoint,String pin,String token,String id,String relay,String relayKey,boolean permanent)throws Exception {
            if(!relay.isEmpty()){RemoteTunnel.validate(relay,relayKey,permanent);if(!endpoint.equals(remoteOrigin(pin)))throw new IOException(UiLanguage.t("Destino remoto inválido."));}
            else{validateEndpoint(endpoint);if(!relayKey.isEmpty()||permanent)throw new IOException(UiLanguage.t("Credencial remota inválida."));}
            if(!pin.matches("[0-9a-f]{64}")||!token.matches("[A-Za-z0-9_-]{43}")||!id.matches("[0-9a-f]{32}"))throw new IOException(UiLanguage.t("Credencial inválida. Pareie novamente."));
            this.endpoint=endpoint;this.pin=pin;this.token=token;this.id=id;this.relay=relay;this.relayKey=relayKey;this.permanent=permanent;this.sockets=pinned(pin);
        }
        JSONObject json()throws Exception{return new JSONObject().put("endpoint",endpoint).put("pin",pin).put("token",token).put("id",id).put("relay",relay).put("relayKey",relayKey).put("permanent",permanent);}
        synchronized String transport()throws Exception{
            if(relay.isEmpty())return endpoint;
            if(tunnel==null||!tunnel.active())tunnel=new RemoteTunnel(relay,relayKey,permanent);
            return "https://127.0.0.1:"+tunnel.port();
        }
        synchronized void close(){if(tunnel!=null)tunnel.close();tunnel=null;}
        synchronized SSLSocketFactory transportSockets()throws Exception{transport();return tunnel==null?sockets:tunnel.sockets(sockets);}
    }
    static synchronized void initialize(Context context) {
        if(initialized)return;initialized=true;
        try{AtomicFile file=file(context);if(!file.getBaseFile().exists())return;
            if(file.getBaseFile().length()>16384)throw new IOException(UiLanguage.t("Credencial inválida."));
            byte[] value=file.readFully();if(value.length<30||value[0]!=1)throw new IOException(UiLanguage.t("Credencial inválida."));
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Arrays.copyOfRange(value,1,13)));cipher.updateAAD(AAD);
            byte[] clear=cipher.doFinal(value,13,value.length-13);
            try{JSONObject json=new JSONObject(new String(clear,StandardCharsets.UTF_8));current=new Record(json.getString("endpoint"),json.getString("pin"),json.getString("token"),json.getString("id"),json.optString("relay",""),json.optString("relayKey",""),json.optBoolean("permanent",false));}
            finally{Arrays.fill(clear,(byte)0);}
        }catch(Exception unavailable){current=null;}
    }
    private static SecretKey key(boolean create)throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(KEY)){
            if(!create)throw new GeneralSecurityException(UiLanguage.t("Chave indisponível."));
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();
        }
        return (SecretKey)store.getKey(KEY,null);
    }
    private static AtomicFile file(Context context){return new AtomicFile(new File(context.getNoBackupFilesDir(),"desktop-pairing.dat"));}
    static synchronized void save(Context context,Record record)throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));cipher.updateAAD(AAD);
        byte[] clear=record.json().toString().getBytes(StandardCharsets.UTF_8),sealed;
        try{sealed=cipher.doFinal(clear);}finally{Arrays.fill(clear,(byte)0);}
        AtomicFile file=file(context);FileOutputStream output=null;
        try{output=file.startWrite();output.write(1);output.write(cipher.getIV());output.write(sealed);file.finishWrite(output);}
        catch(Exception e){if(output!=null)file.failWrite(output);throw e;}
        Record old=current;current=record;initialized=true;if(old!=null&&old!=record)old.close();
    }
    static synchronized void forget(Context context){file(context).delete();Record old=current;current=null;initialized=true;if(old!=null)old.close();}
    static String endpoint(Context context){initialize(context);return current==null?"":current.endpoint;}
    static String description(Context context){initialize(context);return current==null?"Nenhum Desktop pareado":current.relay.isEmpty()?(isTailscaleEndpoint(current.endpoint)?"Conexão privada · Tailscale":"Conexão local · mesma rede"):"Conexão antiga · pareie por LAN ou Tailscale";}
    static boolean isTailscaleEndpoint(String endpoint){try{String[] b=new URL(endpoint).getHost().split("\\.");return b.length==4&&Integer.parseInt(b[0])==100&&Integer.parseInt(b[1])>=64&&Integer.parseInt(b[1])<=127;}catch(Exception ignored){return false;}}
    private static String remoteOrigin(String pin){return "https://"+pin.substring(0,32)+"."+pin.substring(32)+".desktop.bwdacc.invalid";}
    private static Record require(Context context,String address)throws Exception {
        initialize(context);Record record=current;
        URL url=new URL(address);
        if(record==null)throw new IOException(UiLanguage.t("Pareie com bwdacc Desktop em Ajustes > Conexão."));
        URL origin=new URL(record.endpoint);
        if(!url.getProtocol().equals("https")||url.getUserInfo()!=null||url.getRef()!=null||!url.getHost().equals(origin.getHost())||url.getPort()!=origin.getPort())
            throw new IOException(UiLanguage.t("Este endereço não pertence ao Desktop pareado. Faça um novo pareamento."));
        return record;
    }
    static HttpURLConnection open(Context context,String address)throws Exception {
        Record record=require(context,address);HttpsURLConnection connection=(HttpsURLConnection)new URL(mapped(record,address)).openConnection(Proxy.NO_PROXY);
        connection.setSSLSocketFactory(record.transportSockets());connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Authorization","Bearer "+record.token);connection.setUseCaches(false);return connection;
    }
    static void configureSocket(Context context,WebSocketClient socket,String address)throws Exception {
        Record record=require(context,address);socket.setSocketFactory(record.transportSockets());socket.addHeader("Authorization","Bearer "+record.token);
    }
    private static String mapped(Record record,String address)throws Exception{
        URL original=new URL(address);return record.transport()+original.getFile();
    }
    static String socketEndpoint(Context context,String address)throws Exception{return require(context,address).transport();}
    static void validateEndpoint(String endpoint)throws Exception {
        URL url=new URL(endpoint);
        if(!"https".equals(url.getProtocol())||url.getPort()<1024||url.getPort()>65535||!url.getPath().isEmpty()||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null)
            throw new IOException(UiLanguage.t("Endereço de pareamento inválido."));
        String host=url.getHost();if(!host.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}"))throw new IOException(UiLanguage.t("Pareamento requer IPv4 local ou do Tailscale."));
        String[] pieces=host.split("\\.");int[] b=new int[4];for(int i=0;i<4;i++){b[i]=Integer.parseInt(pieces[i]);if(b[i]>255||!pieces[i].equals(String.valueOf(b[i])))throw new IOException(UiLanguage.t("IPv4 inválido."));}
        if(!(b[0]==10||b[0]==127||b[0]==192&&b[1]==168||b[0]==172&&b[1]>=16&&b[1]<=31||b[0]==100&&b[1]>=64&&b[1]<=127))throw new IOException(UiLanguage.t("Use o Desktop pela rede local ou pelo Tailscale."));
    }
    static SSLSocketFactory pinned(String pin)throws Exception {
        if(!pin.matches("[0-9a-f]{64}"))throw new IOException(UiLanguage.t("Identidade do Desktop inválida."));
        final byte[] expected=new byte[32];for(int i=0;i<32;i++)expected[i]=(byte)Integer.parseInt(pin.substring(i*2,i*2+2),16);
        X509TrustManager trust=new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return new X509Certificate[0];}
            public void checkClientTrusted(X509Certificate[] chain,String auth)throws CertificateException{throw new CertificateException(UiLanguage.t("Client certificates are not accepted here"));}
            public void checkServerTrusted(X509Certificate[] chain,String auth)throws CertificateException {
                try{
                    if(chain==null||chain.length!=1)throw new CertificateException(UiLanguage.t("Unexpected Desktop chain"));
                    X509Certificate leaf=chain[0];leaf.checkValidity();leaf.verify(leaf.getPublicKey());
                    if(leaf.getBasicConstraints()!=-1||leaf.getExtendedKeyUsage()==null||!leaf.getExtendedKeyUsage().contains("1.3.6.1.5.5.7.3.1"))throw new CertificateException(UiLanguage.t("Invalid Desktop certificate"));
                    if(!MessageDigest.isEqual(expected,MessageDigest.getInstance("SHA-256").digest(leaf.getPublicKey().getEncoded())))throw new CertificateException(UiLanguage.t("Desktop identity changed; pair again"));
                }catch(CertificateException e){throw e;}catch(Exception e){throw new CertificateException(UiLanguage.t("Desktop identity could not be verified"),e);}
            }
        };
        SSLContext tls=SSLContext.getInstance("TLS");tls.init(null,new TrustManager[]{trust},null);return tls.getSocketFactory();
    }
    static Record pair(Context context,String code,String deviceName)throws Exception {
        if(code.length()>2048)throw new IOException(UiLanguage.t("Código inválido."));
        Uri uri=Uri.parse(code.trim());
        if(!"bwdacc".equals(uri.getScheme())||!"pair".equals(uri.getAuthority())||uri.getFragment()!=null||!(uri.getPath()==null||uri.getPath().isEmpty()))throw new IOException(UiLanguage.t("Use o QR do bwdacc Desktop."));
        boolean permanent="3".equals(uri.getQueryParameter("v")),remote=permanent||"2".equals(uri.getQueryParameter("v"));
        Set<String> fields=new HashSet<>(Arrays.asList("v","endpoint","pin","ticket","expires"));
        if(remote){fields.add("relay");fields.add("relayKey");}
        if(!uri.getQueryParameterNames().equals(fields))throw new IOException(UiLanguage.t("Código inválido."));
        for(String field:fields)if(uri.getQueryParameters(field).size()!=1)throw new IOException(UiLanguage.t("Código inválido."));
        String endpoint=uri.getQueryParameter("endpoint"),pin=uri.getQueryParameter("pin"),ticket=uri.getQueryParameter("ticket");
        validateEndpoint(endpoint);
        if((!remote&&!"1".equals(uri.getQueryParameter("v")))||!ticket.matches("[A-Za-z0-9_-]{43}"))throw new IOException(UiLanguage.t("Versão de pareamento inválida."));
        long expires=Long.parseLong(uri.getQueryParameter("expires"));if(expires<System.currentTimeMillis()/1000-30)throw new IOException(UiLanguage.t("QR expirado. Gere outro no Desktop."));
        String relay=remote?uri.getQueryParameter("relay"):"",relayKey=remote?uri.getQueryParameter("relayKey"):"";
        SSLSocketFactory tls=pinned(pin);
        RemoteTunnel temporary=null;
        if(remote){RemoteTunnel.validate(relay,relayKey,permanent);if(!new URL(endpoint).getHost().equals("127.0.0.1"))throw new IOException(UiLanguage.t("Destino remoto inválido."));temporary=new RemoteTunnel(relay,relayKey,permanent);}
        String destination=temporary==null?endpoint:"https://127.0.0.1:"+temporary.port();
        HttpsURLConnection connection;
        try{connection=(HttpsURLConnection)new URL(destination+"/bwdacc/pair").openConnection(Proxy.NO_PROXY);}
        catch(Exception e){if(temporary!=null)temporary.close();throw e;}
        connection.setSSLSocketFactory(temporary==null?tls:temporary.sockets(tls));connection.setInstanceFollowRedirects(false);connection.setUseCaches(false);connection.setRequestMethod("POST");connection.setConnectTimeout(10000);connection.setReadTimeout(70000);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
        try{
            byte[] body=new JSONObject().put("ticket",ticket).put("name",deviceName).toString().getBytes(StandardCharsets.UTF_8);connection.setFixedLengthStreamingMode(body.length);
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            int status=connection.getResponseCode();
            if(status!=200)throw new IOException(status==403?UiLanguage.t("QR expirado, usado ou recusado no Desktop."):status==429?UiLanguage.t("Muitas tentativas. Aguarde um minuto."):UiLanguage.t("Pareamento não concluído (HTTP ")+status+").");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();try(InputStream in=connection.getInputStream()){byte[] chunk=new byte[1024];int n;while((n=in.read(chunk))!=-1){if(buffer.size()+n>8192)throw new IOException(UiLanguage.t("Resposta inválida."));buffer.write(chunk,0,n);}}
            JSONObject json=new JSONObject(new String(buffer.toByteArray(),StandardCharsets.UTF_8));
            if(!pin.equals(json.getString("desktopPin")))throw new IOException(UiLanguage.t("Identidade divergente."));
            Record record=new Record(remote?remoteOrigin(pin):endpoint,pin,json.getString("token"),json.getString("deviceId"),relay,relayKey,permanent);save(context,record);return record;
        }finally{connection.disconnect();if(temporary!=null)temporary.close();}
    }
}
