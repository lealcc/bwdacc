package com.leal.comfyremote;
import java.util.*;

/** One visible representative per generation family. Hidden members never affect its cover. */
final class GallerySets {
    interface Access<T> {
        String id(T item); String root(T item); boolean visible(T item); boolean ready(T item);
        long time(T item); String preferred(String root);
    }
    static <T> List<T> covers(List<T> source,Access<T> access){
        List<T> sorted=new ArrayList<>(source);Collections.sort(sorted,(a,b)->Long.compare(access.time(b),access.time(a)));
        Map<String,List<T>> sets=new LinkedHashMap<>();
        for(T item:sorted)if(access.visible(item)){String root=access.root(item);if(!sets.containsKey(root))sets.put(root,new ArrayList<>());sets.get(root).add(item);}
        List<T> result=new ArrayList<>();
        for(Map.Entry<String,List<T>> set:sets.entrySet()){
            List<T> items=set.getValue();T cover=null;String preferred=access.preferred(set.getKey());
            for(T item:items)if(access.id(item).equals(preferred)&&access.ready(item)){cover=item;break;}
            if(cover==null)for(T item:items)if(access.id(item).equals(set.getKey())&&access.ready(item)){cover=item;break;}
            if(cover==null)for(T item:items)if(access.ready(item)){cover=item;break;}
            result.add(cover==null?items.get(0):cover);
        }return result;
    }
    static int direction(float dx,float dy,float threshold){
        if(Math.max(Math.abs(dx),Math.abs(dy))<threshold)return 0;
        // +/-1: vertical versions; +/-2: horizontal sets.
        return Math.abs(dy)>=Math.abs(dx)?(dy<0?1:-1):(dx<0?2:-2);
    }
}
