package com.leal.comfyremote;

import java.util.List;

/** References across media types do not belong to the same editable generation set. */
final class MediaFamilies {
    static class Record {
        String id, rootId = "", parentId = "", sourceId = "", mediaKind = "image";
        int editNumber, familySchema = 2;
    }

    static boolean migrate(List<? extends Record> items) {
        boolean changed = false;
        for (Record item : items) {
            if (item.familySchema >= 2) continue;
            // Before schema 2 every animation was incorrectly stored as an image edit.
            if ("video".equals(item.mediaKind)) {
                item.sourceId = item.parentId;
                item.rootId = item.id;
                item.parentId = "";
                item.editNumber = 0;
            }
            item.familySchema = 2;
            changed = true;
        }
        return changed;
    }

    static String root(Record item, List<? extends Record> items) {
        String root = item.rootId == null || item.rootId.isEmpty() ? item.id : item.rootId;
        for (Record other : items) {
            if ((other.id.equals(root) || other.id.equals(item.parentId)) && !other.mediaKind.equals(item.mediaKind)) return item.id;
        }
        return root;
    }

    static void videoEdit(Record result,Record source,List<? extends Record> items){
        if(!"video".equals(source.mediaKind)||!"video".equals(result.mediaKind))throw new IllegalArgumentException(UiLanguage.t("Edições de vídeo exigem origem e resultado em vídeo."));
        result.rootId=root(source,items);result.parentId=source.id;result.sourceId=source.id;
        for(Record item:items)if("video".equals(item.mediaKind)&&root(item,items).equals(result.rootId))result.editNumber=Math.max(result.editNumber,item.editNumber);
        result.editNumber++;result.familySchema=2;
    }
}
