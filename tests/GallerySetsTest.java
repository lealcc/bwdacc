package com.leal.comfyremote;
import java.util.*;
public final class GallerySetsTest {
 static class Item {String id,root;long time;boolean hidden,ready;Item(String id,String root,long time,boolean hidden,boolean ready){this.id=id;this.root=root;this.time=time;this.hidden=hidden;this.ready=ready;}}
 static class Access implements GallerySets.Access<Item>{Map<String,String> saved=new HashMap<>();boolean hide=true;
  public String id(Item i){return i.id;}public String root(Item i){return i.root;}public long time(Item i){return i.time;}public boolean ready(Item i){return i.ready;}public boolean visible(Item i){return !hide||!i.hidden;}public String preferred(String root){return saved.getOrDefault(root,"");}}
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  Item original=new Item("a","a",1,false,true),edit=new Item("a2","a",2,true,true),other=new Item("b","b",3,false,true),pending=new Item("a3","a",4,false,false);
  List<Item> source=new ArrayList<>(Arrays.asList(original,edit,other,pending));Access access=new Access();access.saved.put("a","a2");
  List<Item> covers=GallerySets.covers(source,access);check(covers.size()==2&&covers.get(0)==original,"hidden preferred cover replaced by visible original");
  access.hide=false;check(GallerySets.covers(source,access).get(0)==edit,"selected edit restored with visibility enabled");
  access.saved.put("a","a3");check(GallerySets.covers(source,access).get(0)==original,"pending not promoted as cover");
  access.saved.put("a","deleted");source.remove(original);check(GallerySets.covers(source,access).get(0)==edit,"deleted original and cover fall back within same family");
  source.remove(pending);access.hide=true;check(GallerySets.covers(source,access).size()==1,"entire private family hidden");
  check(GallerySets.direction(2,-100,48)==1&&GallerySets.direction(2,100,48)==-1,"vertical versions");
  check(GallerySets.direction(-100,2,48)==2&&GallerySets.direction(100,2,48)==-2,"horizontal sets");
  check(GallerySets.direction(10,10,48)==0,"tap does not navigate");System.out.println("PASS: set grouping, preferred cover, privacy, fallback, swipe axes");
 }
}
