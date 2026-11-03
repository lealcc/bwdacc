package com.leal.comfyremote;
import java.util.*;
public final class MasonryLayoutTest {
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
    public static void main(String[] args){
        double[] ratios={9d/16,16d/9,1,4d/3,3d/4,3,1,9d/16};
        for(int width:new int[]{320,411,600,900}){
            MasonryLayout.Plan plan=MasonryLayout.arrange(width,12,8,width>=600?3:2,ratios);
            check(plan.tiles.size()==ratios.length,"one tile per set");
            for(int i=0;i<ratios.length;i++){
                MasonryLayout.Tile a=plan.tiles.get(i);check(Math.abs(a.height-a.width/ratios[i])<=.51,"natural aspect ratio");
                check(a.x>=12&&a.x+a.width<=width-12&&a.y+a.height<=plan.height,"within content bounds");
                for(int j=0;j<i;j++){MasonryLayout.Tile b=plan.tiles.get(j);check(a.x>=b.x+b.width||b.x>=a.x+a.width||a.y>=b.y+b.height||b.y>=a.y+a.height,"tiles never overlap");}
            }
            check(plan.tiles.get(2).x==plan.tiles.get(1).x || width>=600,"short column filled first");
        }
        MasonryLayout.Plan fallback=MasonryLayout.arrange(400,12,8,2,new double[]{0,Double.NaN});
        check(fallback.tiles.get(0).width==fallback.tiles.get(0).height,"unknown dimensions use square placeholder");
        check(MasonryLayout.arrange(400,12,8,2,new double[]{3,1,1}).tiles.get(0).width==376,"panorama spans aligned columns");
        check(MasonryLayout.arrange(400,12,8,2,new double[]{.5,1,3}).tiles.get(2).width<376,"panorama does not force a blank hole");
        System.out.println("PASS: mixed aspect ratios, adaptive columns, panoramas, geometry and placeholders");
    }
}
