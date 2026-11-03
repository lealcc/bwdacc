package com.leal.comfyremote;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.security.*;
import javax.net.ssl.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.extensions.IExtension;

/** Local TCP-to-WSS carrier. Inner TLS remains between Android and the pinned Desktop. */
final class RemoteTunnel implements Closeable {
    static { System.setProperty("slf4j.provider","org.slf4j.nop.NOPServiceProvider"); }
    final String relay,key;
    private final ServerSocket listener;
    private final Set<Socket> connections=ConcurrentHashMap.newKeySet();
    private final Set<WebSocketClient> sockets=ConcurrentHashMap.newKeySet();
    private final Semaphore slots=new Semaphore(16);
    private final byte[] admission=new byte[32];
    private volatile boolean closed;
    RemoteTunnel(String relay,String key)throws Exception{
        this(relay,key,false);
    }
    RemoteTunnel(String relay,String key,boolean permanent)throws Exception{
        validate(relay,key,permanent);this.relay=relay;this.key=key;new SecureRandom().nextBytes(admission);
        listener=new ServerSocket();listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),16);
        Thread accept=new Thread(()->{
            while(!closed)try{
                Socket socket=listener.accept();
                if(!slots.tryAcquire()){socket.close();continue;}
                connections.add(socket);Thread worker=new Thread(()->bridge(socket),"desktop-tunnel");worker.setDaemon(true);worker.start();
            }catch(IOException e){if(!closed)close();}
        },"desktop-tunnel-listener");accept.setDaemon(true);accept.start();
    }
    static void validate(String relay,String key)throws Exception{
        validate(relay,key,false);
    }
    static void validate(String relay,String key,boolean permanent)throws Exception{
        URI uri=new URI(relay);
        // A custom hostname is accepted only by explicit v3 pairing, never discovered from a redirect.
        if(!"https".equals(uri.getScheme())||uri.getHost()==null||
            !(permanent?publicHost(uri.getHost()):uri.getHost().matches("[a-z0-9]+(?:-[a-z0-9]+)*\\.trycloudflare\\.com"))||
            uri.getPort()!=-1||uri.getRawUserInfo()!=null||uri.getRawQuery()!=null||uri.getRawFragment()!=null||
            !uri.getRawPath().isEmpty()||!key.matches("[A-Za-z0-9_-]{43}"))
            throw new IOException(UiLanguage.t("Endereço remoto inválido. Gere outro QR no Desktop."));
    }
    private static boolean publicHost(String host){
        if(host.length()>253||!host.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}"))return false;
        for(String suffix:new String[]{"local","localhost","internal","lan","home","test","invalid","example"})if(host.endsWith("."+suffix))return false;
        return true;
    }
    int port(){return listener.getLocalPort();}
    boolean active(){return !closed;}
    SSLSocketFactory sockets(SSLSocketFactory tls){
        return new SSLSocketFactory(){
            private Socket connect(String host,int port,InetAddress bind,int localPort)throws IOException{
                if(closed||!"127.0.0.1".equals(host)||port!=port())throw new IOException(UiLanguage.t("Destino do túnel inválido."));
                Socket plain=new Socket();
                try{if(bind!=null)plain.bind(new InetSocketAddress(bind,localPort));plain.connect(new InetSocketAddress("127.0.0.1",port),5000);return layer(plain,host,port,true);}
                catch(IOException e){shut(plain);throw e;}
            }
            private Socket layer(Socket plain,String host,int port,boolean close)throws IOException{
                if(closed||!"127.0.0.1".equals(host)||port!=port()||!plain.getInetAddress().isLoopbackAddress()||plain.getPort()!=port)
                    throw new IOException(UiLanguage.t("Destino do túnel inválido."));
                // Per-process local admission, sent before TLS only on loopback.
                // Other Android apps cannot use this listener as a relay credential oracle.
                plain.getOutputStream().write(admission);plain.getOutputStream().flush();
                return tls.createSocket(plain,host,port,close);
            }
            public String[] getDefaultCipherSuites(){return tls.getDefaultCipherSuites();}
            public String[] getSupportedCipherSuites(){return tls.getSupportedCipherSuites();}
            public Socket createSocket()throws IOException{return connect("127.0.0.1",port(),null,0);}
            public Socket createSocket(Socket s,String host,int p,boolean autoClose)throws IOException{return layer(s,host,p,autoClose);}
            public Socket createSocket(String host,int p)throws IOException{return connect(host,p,null,0);}
            public Socket createSocket(String host,int p,InetAddress bind,int lp)throws IOException{return connect(host,p,bind,lp);}
            public Socket createSocket(InetAddress host,int p)throws IOException{return connect(host.getHostAddress(),p,null,0);}
            public Socket createSocket(InetAddress host,int p,InetAddress bind,int lp)throws IOException{return connect(host.getHostAddress(),p,bind,lp);}
        };
    }
    private static void shut(Socket socket){
        if(socket==null)return;
        try{socket.shutdownInput();}catch(IOException ignored){}try{socket.shutdownOutput();}catch(IOException ignored){}try{socket.close();}catch(IOException ignored){}
    }
    private static void shut(WebSocketClient ws){ws.closeConnection(1001,"Connection closed");shut(ws.getSocket());}
    private void bridge(Socket local){
        WebSocketClient remote=null;
        try{
            local.setTcpNoDelay(true);local.setSoTimeout(2000);
            byte[] presented=new byte[32];new DataInputStream(local.getInputStream()).readFully(presented);
            if(!MessageDigest.isEqual(admission,presented)||closed)throw new IOException(UiLanguage.t("Local admission refused."));
            local.setSoTimeout(30_000);
            java.util.concurrent.atomic.AtomicLong activity=new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
            OutputStream received=local.getOutputStream();
            // A bounded frame decoder prevents buffering an arbitrary relay message.
            Draft_6455 draft=new Draft_6455(Collections.<IExtension>emptyList(),65536);
            remote=new WebSocketClient(new URI(relay.replaceFirst("^https","wss")+"/bwdacc/tunnel"),draft,
                Collections.singletonMap("Authorization","Bearer "+key),10000){
                @Override protected void onSetSSLParameters(SSLParameters p){p.setEndpointIdentificationAlgorithm("HTTPS");}
                @Override public void onOpen(ServerHandshake h){}
                @Override public void onMessage(String text){closeConnection(1003,"Binary required");try{local.close();}catch(IOException ignored){}}
                @Override public void onMessage(ByteBuffer bytes){
                    try{byte[] buffer=new byte[bytes.remaining()];bytes.get(buffer);if(buffer.length>0)activity.set(System.nanoTime());received.write(buffer);received.flush();}
                    catch(IOException e){closeConnection(1001,"Connection closed");}
                }
                @Override public void onClose(int code,String reason,boolean peer){try{local.close();}catch(IOException ignored){}}
                @Override public void onError(Exception error){try{local.close();}catch(IOException ignored){}}
            };
            remote.setSocketFactory(SSLSocketFactory.getDefault());remote.setConnectionLostTimeout(20);sockets.add(remote);
            if(!remote.connectBlocking(12,TimeUnit.SECONDS))throw new IOException(UiLanguage.t("Conexão remota indisponível."));
            InputStream input=local.getInputStream();byte[] bytes=new byte[16384];int n;
            while(!closed){
                try{n=input.read(bytes);}catch(SocketTimeoutException idle){
                    // A download can be active without any client-to-server application bytes.
                    if(System.nanoTime()-activity.get()>TimeUnit.SECONDS.toNanos(120))throw idle;
                    continue;
                }
                if(n==-1)break;
                activity.set(System.nanoTime());
                long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
                // Do not build an unbounded websocket send queue during large uploads.
                while(remote.hasBufferedData()){
                    if(!remote.isOpen()||closed||System.nanoTime()>end)throw new IOException(UiLanguage.t("Conexão remota interrompida."));
                    Thread.sleep(5);
                }
                if(!remote.isOpen())throw new IOException(UiLanguage.t("Conexão remota encerrada."));
                remote.send(Arrays.copyOf(bytes,n));
            }
        }catch(Exception ignored){}finally{
            if(remote!=null){sockets.remove(remote);shut(remote);}
            connections.remove(local);shut(local);slots.release();
        }
    }
    public void close(){
        closed=true;try{listener.close();}catch(IOException ignored){}
        for(Socket socket:connections)shut(socket);
        for(WebSocketClient ws:sockets)shut(ws);
    }
}
