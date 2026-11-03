package com.leal.comfyremote;
import java.util.*;

/** Only explicitly public, completed local media may provide a stealth cover. */
final class StealthCoverPolicy {
    interface Access<T>{String id(T item);String workflow(T item);boolean safeAndReady(T item);}
    static <T> List<T> candidates(List<T> all,String id,String workflow,Access<T> access){
        List<T> safe=new ArrayList<>();for(T item:all)if(!id.equals(access.id(item))&&access.safeAndReady(item))safe.add(item);
        safe.sort((a,b)->{
            int preferred=Boolean.compare(Objects.equals(workflow,access.workflow(b)),Objects.equals(workflow,access.workflow(a)));
            if(preferred!=0)return preferred;
            int score=Long.compare(score(id,access.id(b)),score(id,access.id(a)));
            return score!=0?score:access.id(a).compareTo(access.id(b));
        });return safe;
    }
    private static long score(String id,String source){long hash=0xcbf29ce484222325L;for(char c:(id+"|"+source).toCharArray()){hash^=c;hash*=0x100000001b3L;}return hash&Long.MAX_VALUE;}
    static int[] dimensions(int width,int height,int maxEdge){
        if(width<=0||height<=0){width=1;height=1;}
        double scale=(double)maxEdge/Math.max(width,height);
        return new int[]{Math.max(1,(int)Math.round(width*scale)),Math.max(1,(int)Math.round(height*scale))};
    }
}
