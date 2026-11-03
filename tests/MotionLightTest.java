package com.leal.comfyremote;
public final class MotionLightTest {
    static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        MotionLight.Tilt t=new MotionLight.Tilt();long now=1_000_000_000L;
        check(t.sample(0,0,9.81f,now,0)&&t.x==0&&t.y==0,"calibrates initial grip without jumping");
        now+=33_333_333;t.sample(3,0,9,now,0);check(t.x>0&&t.x<.3f&&t.y==0,"smooth tilt without immediate jump");
        for(int i=0;i<100;i++){now+=33_333_333;t.sample(8,2,4,now,0);}check(t.x<=1&&t.y<=1&&t.x>.95,"bounded even at extreme tilt");
        float x=t.x,y=t.y;check(!t.sample(Float.NaN,0,9,now+1,0)&&x==t.x&&y==t.y,"invalid readings cannot poison light");
        check(!t.sample(0,0,0,now+1,0)&&x==t.x,"freefall ignored");check(!t.sample(30,0,0,now+1,0),"sharp shocks ignored");
        check(!t.sample(0,0,9,now-1,0),"out of order samples ignored");
        now+=33_333_333;t.sample(8,2,4,now,1);check(t.x==0&&t.y==0,"orientation change recalibrates");
        t.reset();now+=33_333_333;t.sample(0,0,9.81f,now,1);now+=33_333_333;t.sample(3,0,9,now,1);check(t.y>0&&Math.abs(t.x)<.001,"landscape screen axes remapped");
        t.reset();now+=33_333_333;t.sample(0,0,9.81f,now,0);int changed=0;for(int i=0;i<200;i++){now+=33_333_333;if(t.sample(.001f,0,9.81f,now,0))changed++;}check(changed==0,"stationary sensor noise does not schedule repaints");
        System.out.println("PASS: accelerometer calibration, smoothing, bounds, rotation, shocks and idle noise");
    }
}
