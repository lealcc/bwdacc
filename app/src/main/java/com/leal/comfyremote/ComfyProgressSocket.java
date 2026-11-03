package com.leal.comfyremote;

import java.net.URI;
import java.net.URLEncoder;
import org.json.JSONObject;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/** Read-only execution events. HTTP history remains the authority for completed results. */
final class ComfyProgressSocket {
    // Dex packaging does not preserve ServiceLoader descriptors from dependency JARs.
    static { System.setProperty("slf4j.provider","org.slf4j.nop.NOPServiceProvider"); }
    interface Listener {void message(JSONObject message);void connected(boolean connected);}
    final String address,clientId;
    private final WebSocketClient socket;
    volatile boolean ended;
    ComfyProgressSocket(android.content.Context context,String address,String clientId,Listener listener)throws Exception{
        this.address=address;this.clientId=clientId;
        // This TLS client needs Android's hostname-verification API (24+).
        // Keep HTTP polling/estimates on older devices rather than weakening TLS checks.
        if(android.os.Build.VERSION.SDK_INT<24&&address.startsWith("https:"))throw new java.io.IOException("TLS progress requires Android 7+");
        String url=DesktopConnection.socketEndpoint(context,address).replaceFirst("^http","ws")+"/ws?clientId="+URLEncoder.encode(clientId,"UTF-8");
        socket=new WebSocketClient(new URI(url)){
            @Override public void onOpen(ServerHandshake handshake){listener.connected(true);}
            @Override public void onMessage(String text){if(text.length()>524288)return;try{listener.message(new JSONObject(text));}catch(Exception ignored){}}
            @Override public void onClose(int code,String reason,boolean remote){ended=true;listener.connected(false);}
            @Override public void onError(Exception error){ended=true;listener.connected(false);}
        };
        DesktopConnection.configureSocket(context,socket,address);
        socket.setConnectionLostTimeout(15);socket.connect();
    }
    void close(){ended=true;socket.close();}
}
