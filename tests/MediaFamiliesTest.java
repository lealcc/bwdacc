package com.leal.comfyremote;
import java.util.*;
public final class MediaFamiliesTest {
    static MediaFamilies.Record item(String id,String root,String parent,String kind,int schema){MediaFamilies.Record r=new MediaFamilies.Record();r.id=id;r.rootId=root;r.parentId=parent;r.mediaKind=kind;r.familySchema=schema;return r;}
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        MediaFamilies.Record image=item("image","","","image",0),edit=item("edit","image","image","image",0);
        edit.editNumber=1;
        MediaFamilies.Record video1=item("video1","image","edit","video",0),video2=item("video2","image","edit","video",0);video1.editNumber=2;video2.editNumber=3;
        List<MediaFamilies.Record> list=new ArrayList<>(Arrays.asList(image,edit,video1,video2));
        check(MediaFamilies.migrate(list),"legacy migration occurs");
        check(MediaFamilies.root(edit,list).equals("image")&&edit.editNumber==1,"image edit family preserved");
        check(MediaFamilies.root(video1,list).equals("video1")&&MediaFamilies.root(video2,list).equals("video2"),"each animation has its own set");
        check(video1.parentId.isEmpty()&&video1.sourceId.equals("edit")&&video1.editNumber==0,"reference retained separately, edit counter reset");
        check(!MediaFamilies.migrate(list),"migration idempotent");
        MediaFamilies.Record videoEdit=item("videoEdit","video1","video1","video",2);videoEdit.editNumber=1;list.add(videoEdit);
        MediaFamilies.migrate(list);check(MediaFamilies.root(videoEdit,list).equals("video1"),"future video edits stay with their video");
        list.remove(video1);check(MediaFamilies.root(videoEdit,list).equals("video1"),"deleted video original does not break edits");
        list.remove(image);check(MediaFamilies.root(edit,list).equals("image"),"deleted image original does not break edits");
        MediaFamilies.Record malformed=item("bad","edit","edit","video",2);list.add(malformed);check(MediaFamilies.root(malformed,list).equals("bad"),"cross-media references cannot merge sets");
        System.out.println("PASS: legacy video separation, references, future typed edits and deleted roots");
    }
}
