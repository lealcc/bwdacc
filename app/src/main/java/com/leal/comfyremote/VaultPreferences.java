package com.leal.comfyremote;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Typed settings/drafts/reference library in a single authenticated file, never Android XML. */
final class VaultPreferences implements SharedPreferences {
    private final File file;
    private Map<String,Object> values;
    private final Set<OnSharedPreferenceChangeListener> listeners=Collections.newSetFromMap(new WeakHashMap<>());
    private final ExecutorService writes=Executors.newSingleThreadExecutor(r->new Thread(r,"vault-settings"));
    private volatile IOException failure;
    VaultPreferences(File file)throws Exception{
        this.file=file;values=decode(new JSONObject(new String(VaultFiles.read(file,16_000_000),StandardCharsets.UTF_8)));
    }
    static void importLegacy(File file,Map<String,?> values)throws Exception{VaultFiles.write(file,encode(values));}
    void flush()throws Exception{writes.submit(()->{}).get();healthy();}
    static Map<String,Object> validate(byte[] bytes)throws Exception{return decode(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));}
    private static byte[] encode(Map<String,?> values)throws JSONException{
        JSONObject result=new JSONObject();
        for(Map.Entry<String,?> entry:values.entrySet()){
            Object v=entry.getValue();String type=v instanceof String?"s":v instanceof Integer?"i":v instanceof Long?"l":v instanceof Float?"f":v instanceof Boolean?"b":v instanceof Set?"a":null;
            if(type==null)throw new IllegalArgumentException(UiLanguage.t("Tipo de configuração inválido"));
            result.put(entry.getKey(),new JSONObject().put("t",type).put("v",v instanceof Set?new JSONArray((Set<?>)v):v));
        }return result.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static Map<String,Object> decode(JSONObject json)throws JSONException{
        Map<String,Object> out=new HashMap<>();
        for(Iterator<String> keys=json.keys();keys.hasNext();){
            String key=keys.next();JSONObject value=json.getJSONObject(key);Object v;
            switch(value.getString("t")){
                case "s":v=value.getString("v");break;case "i":v=value.getInt("v");break;case "l":v=value.getLong("v");break;
                case "f":v=(float)value.getDouble("v");break;case "b":v=value.getBoolean("v");break;
                case "a":Set<String> set=new HashSet<>();JSONArray a=value.getJSONArray("v");for(int i=0;i<a.length();i++)set.add(a.getString(i));v=set;break;
                default:throw new JSONException(UiLanguage.t("Tipo de configuração inválido"));
            }out.put(key,v);
        }return out;
    }
    private void healthy(){if(failure!=null)throw new IllegalStateException(UiLanguage.t("Não foi possível salvar o cofre. Dados preservados."),failure);}
    private static Map<String,Object> copy(Map<String,Object> input){Map<String,Object> out=new HashMap<>();for(Map.Entry<String,Object> e:input.entrySet())out.put(e.getKey(),e.getValue() instanceof Set?new HashSet<>((Set<?>)e.getValue()):e.getValue());return out;}
    public synchronized Map<String,?> getAll(){healthy();return copy(values);}
    private synchronized Object get(String key,Object fallback){healthy();return values.containsKey(key)?values.get(key):fallback;}
    public String getString(String key,String fallback){return (String)get(key,fallback);}
    @SuppressWarnings("unchecked") public Set<String> getStringSet(String key,Set<String> fallback){Set<String> v=(Set<String>)get(key,fallback);return v==null?null:new HashSet<>(v);}
    public int getInt(String key,int fallback){return (Integer)get(key,fallback);}
    public long getLong(String key,long fallback){return (Long)get(key,fallback);}
    public float getFloat(String key,float fallback){return (Float)get(key,fallback);}
    public boolean getBoolean(String key,boolean fallback){return (Boolean)get(key,fallback);}
    public synchronized boolean contains(String key){healthy();return values.containsKey(key);}
    public Editor edit(){healthy();return new Edit();}
    public synchronized void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}
    public synchronized void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
    private final class Edit implements Editor {
        final Map<String,Object> changes=new HashMap<>();boolean clear;
        private Editor put(String key,Object value){changes.put(key,value);return this;}
        public Editor putString(String k,String v){return put(k,v);}
        public Editor putStringSet(String k,Set<String> v){return put(k,v==null?null:new HashSet<>(v));}
        public Editor putInt(String k,int v){return put(k,v);}
        public Editor putLong(String k,long v){return put(k,v);}
        public Editor putFloat(String k,float v){return put(k,v);}
        public Editor putBoolean(String k,boolean v){return put(k,v);}
        public Editor remove(String k){return put(k,null);}
        public Editor clear(){clear=true;return this;}
        private Future<Boolean> submit(){
            synchronized(VaultPreferences.this){
                healthy();Map<String,Object> next=clear?new HashMap<>():copy(values);
                Set<String> changed=new HashSet<>(changes.keySet());if(clear)changed.addAll(values.keySet());
                for(Map.Entry<String,Object> e:changes.entrySet())if(e.getValue()==null)next.remove(e.getKey());else next.put(e.getKey(),e.getValue());
                final byte[] snapshot;try{snapshot=encode(next);}catch(Exception e){throw new IllegalStateException(e);}
                values=next;changes.clear();clear=false;
                Future<Boolean> future=writes.submit(()->{
                    try{if(failure!=null)return false;VaultFiles.write(file,snapshot);return true;}
                    catch(IOException e){failure=e;return false;}
                    finally{Arrays.fill(snapshot,(byte)0);}
                });
                List<OnSharedPreferenceChangeListener> targets=new ArrayList<>(listeners);
                new Handler(Looper.getMainLooper()).post(()->{for(OnSharedPreferenceChangeListener l:targets)for(String k:changed)l.onSharedPreferenceChanged(VaultPreferences.this,k);});
                return future;
            }
        }
        public boolean commit(){try{return submit().get();}catch(InterruptedException e){Thread.currentThread().interrupt();return false;}catch(ExecutionException e){return false;}}
        public void apply(){submit();}
    }
}
