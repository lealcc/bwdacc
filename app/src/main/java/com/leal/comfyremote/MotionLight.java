package com.leal.comfyremote;
import android.app.Activity;
import android.animation.ValueAnimator;
import android.hardware.*;
import android.os.*;

/** Foreground-only accelerometer lighting. No timer, permissions or network requests. */
final class MotionLight implements SensorEventListener {
    final Activity activity;final SensorManager manager;final Sensor sensor;final Tilt filter=new Tilt();boolean listening;
    MotionLight(Activity a){activity=a;manager=(SensorManager)a.getSystemService(Activity.SENSOR_SERVICE);sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);}
    void start(boolean enabled){
        stop();PowerManager power=(PowerManager)activity.getSystemService(Activity.POWER_SERVICE);
        if(!enabled||sensor==null||(Build.VERSION.SDK_INT>=26&&!ValueAnimator.areAnimatorsEnabled())||(power!=null&&power.isPowerSaveMode()))return;
        filter.reset();listening=manager.registerListener(this,sensor,33_333);Appearance.light(listening,0,0);
    }
    void stop(){if(listening)manager.unregisterListener(this);listening=false;Appearance.light(false,0,0);}
    public void onAccuracyChanged(Sensor sensor,int accuracy){}
    public void onSensorChanged(SensorEvent event){
        if(!listening)return;if(Build.VERSION.SDK_INT>=26&&!ValueAnimator.areAnimatorsEnabled()){stop();return;}
        if(filter.sample(event.values[0],event.values[1],event.values[2],event.timestamp,activity.getWindowManager().getDefaultDisplay().getRotation()))Appearance.light(true,filter.x,filter.y);
    }
    /** Remap the sensor to display coordinates and calibrate to the current grip. */
    static final class Tilt {
        float x,y;double baseRoll,basePitch;long last;int rotation=-1;
        void reset(){x=y=0;last=0;rotation=-1;}
        boolean sample(float gx,float gy,float gz,long time,int screenRotation){
            double magnitude=Math.sqrt(gx*gx+gy*gy+gz*gz);if(!Double.isFinite(magnitude)||magnitude<2||magnitude>20)return false;
            float sx=gx,sy=gy;if(screenRotation==1){sx=-gy;sy=gx;}else if(screenRotation==2){sx=-gx;sy=-gy;}else if(screenRotation==3){sx=gy;sy=-gx;}
            double roll=Math.atan2(sx,Math.hypot(sy,gz)),pitch=Math.atan2(sy,gz);
            if(last==0||screenRotation!=rotation){baseRoll=roll;basePitch=pitch;last=time;rotation=screenRotation;x=y=0;return true;}
            if(time<=last)return false;double dt=Math.min(.1,(time-last)/1e9);last=time;
            float targetX=clamp((float)(Math.atan2(Math.sin(roll-baseRoll),Math.cos(roll-baseRoll))/.55));
            float targetY=clamp((float)(Math.atan2(Math.sin(pitch-basePitch),Math.cos(pitch-basePitch))/.55));
            float alpha=(float)(1-Math.exp(-dt/.13)),nextX=x+(targetX-x)*alpha,nextY=y+(targetY-y)*alpha;
            if(Math.abs(nextX-x)<.002f&&Math.abs(nextY-y)<.002f)return false;x=nextX;y=nextY;return true;
        }
        static float clamp(float value){return Math.max(-1,Math.min(1,value));}
    }
}
