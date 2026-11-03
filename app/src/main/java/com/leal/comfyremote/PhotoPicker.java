package com.leal.comfyremote;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.provider.MediaStore;

/** Photos use the media picker, never the last folder of the document browser. */
final class PhotoPicker {
    static Intent photos(int remaining,int platformLimit){
        int count=Math.max(1,Math.min(3,Math.min(remaining,platformLimit)));
        Intent intent=new Intent(MediaStore.ACTION_PICK_IMAGES).setType("image/*");
        if(count>1)intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX,count);
        return intent;
    }
    static Intent gallery(int remaining){
        return new Intent(Intent.ACTION_PICK).setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,remaining>1).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
    static boolean launch(Activity activity,int remaining,int request){
        if(remaining<=0)return false;
        if(Build.VERSION.SDK_INT>=33)try{activity.startActivityForResult(photos(remaining,MediaStore.getPickImagesMaxLimit()),request);return true;}catch(ActivityNotFoundException ignored){}
        try{activity.startActivityForResult(gallery(remaining),request);return true;}catch(ActivityNotFoundException ignored){return false;}
    }
}
