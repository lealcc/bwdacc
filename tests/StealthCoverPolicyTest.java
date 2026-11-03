package com.leal.comfyremote;
import java.util.*;
public final class StealthCoverPolicyTest {
    static class Item {String id,workflow;boolean safe;Item(String id,String workflow,boolean safe){this.id=id;this.workflow=workflow;this.safe=safe;}}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        Item privateItem=new Item("private","image",false),same=new Item("same","image",true),other=new Item("other","video",true);
        StealthCoverPolicy.Access<Item> access=new StealthCoverPolicy.Access<Item>(){public String id(Item i){return i.id;}public String workflow(Item i){return i.workflow;}public boolean safeAndReady(Item i){return i.safe;}};
        List<Item> all=Arrays.asList(other,privateItem,same);
        check(StealthCoverPolicy.candidates(all,"target","image",access).get(0)==same,"prefer safe media from same workflow");
        same.safe=false;
        List<Item> remaining=StealthCoverPolicy.candidates(all,"target","image",access);
        check(remaining.size()==1&&remaining.get(0)==other,"private sources never reused, other workflows precede stock");
        check(StealthCoverPolicy.candidates(all,"other","video",access).isEmpty(),"self excluded; stock only when no safe source");
        for(int[] original:new int[][]{{900,1600},{1600,900},{1024,1024},{4000,1000}}){
            int[] fit=StealthCoverPolicy.dimensions(original[0],original[1],1280);
            check(Math.abs((double)fit[0]/fit[1]-(double)original[0]/original[1])<.002,"cover keeps original aspect");
            check(Math.max(fit[0],fit[1])==1280,"cover size bounded");
        }
        System.out.println("PASS: safe-only source priority and aspect-preserving covers");
    }
}
