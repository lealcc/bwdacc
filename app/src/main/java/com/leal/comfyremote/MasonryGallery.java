package com.leal.comfyremote;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Scrollable masonry grid. Only tiles near the viewport own views and decoded thumbnails. */
final class MasonryGallery extends ScrollView {
    static abstract class Adapter extends BaseAdapter {
        abstract double aspect(int position);
        abstract String key(int position);
        boolean video(int position){return false;}
        void playback(View view,boolean active){}
    }
    private Adapter adapter;
    private View empty;
    private boolean playbackEnabled=true;
    void setPlaybackEnabled(boolean value){playbackEnabled=value;tiles.syncVisible();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(tiles!=null)tiles.syncVisible();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(tiles!=null)tiles.syncVisible();}
    private final Tiles tiles;
    private final DataSetObserver observer = new DataSetObserver() {
        @Override public void onChanged(){ invalidateData(); }
        @Override public void onInvalidated(){ invalidateData(); }
    };
    MasonryGallery(Context context) {
        super(context);setVerticalScrollBarEnabled(false);setClipToPadding(false);
        tiles=new Tiles(context);addView(tiles,new ScrollView.LayoutParams(-1,-2));
    }
    void setAdapter(Adapter value){if(adapter!=null)adapter.unregisterDataSetObserver(observer);adapter=value;adapter.registerDataSetObserver(observer);invalidateData();}
    void setEmptyView(View value){empty=value;updateEmpty();}
    private void updateEmpty(){if(empty!=null){boolean none=adapter==null||adapter.getCount()==0;empty.setVisibility(none?VISIBLE:GONE);setVisibility(none?GONE:VISIBLE);}}
    private void invalidateData(){tiles.rememberAnchor();tiles.dirty=true;tiles.removeAllViews();tiles.visible.clear();tiles.requestLayout();updateEmpty();}
    void smoothScrollToPosition(int position){post(()->{if(position==0)smoothScrollTo(0,0);else if(position<tiles.plan.tiles.size())smoothScrollTo(0,tiles.plan.tiles.get(position).y);});}
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(tiles!=null)tiles.syncVisible();}

    private final class Tiles extends ViewGroup {
        MasonryLayout.Plan plan=new MasonryLayout.Plan();
        final Map<Integer,View> visible=new HashMap<>();
        final List<String> keys=new ArrayList<>();
        boolean dirty=true;int previousWidth;String anchor="";int anchorOffset;
        Tiles(Context context){super(context);setClipChildren(true);}
        void rememberAnchor(){
            if(dirty)return;
            for(int i=0;i<plan.tiles.size();i++){MasonryLayout.Tile tile=plan.tiles.get(i);if(tile.y+tile.height>getScrollYOfGallery()){anchor=keys.get(i);anchorOffset=getScrollYOfGallery()-tile.y;return;}}
        }
        int getScrollYOfGallery(){return MasonryGallery.this.getScrollY();}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec);
            if(dirty||previousWidth!=width){
                previousWidth=width;dirty=false;
                int count=adapter==null?0:adapter.getCount();double[] ratios=new double[count];keys.clear();
                for(int i=0;i<count;i++){ratios[i]=adapter.aspect(i);keys.add(adapter.key(i));}
                plan=MasonryLayout.arrange(width,FlowDesign.dp(getContext(),12),FlowDesign.dp(getContext(),8),width>=FlowDesign.dp(getContext(),600)?3:2,ratios);
                removeAllViewsInLayout();visible.clear();
                int index=keys.indexOf(anchor);if(index>=0){final int target=Math.max(0,plan.tiles.get(index).y+anchorOffset);post(()->MasonryGallery.this.scrollTo(0,target));}anchor="";
            }
            setMeasuredDimension(width,plan.height);
        }
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){syncVisible();}
        void syncVisible(){
            if(adapter==null||dirty||getWidth()==0)return;
            int margin=Math.max(FlowDesign.dp(getContext(),240),MasonryGallery.this.getHeight()/2);
            int top=getScrollYOfGallery()-margin,bottom=getScrollYOfGallery()+MasonryGallery.this.getHeight()+margin;
            Iterator<Map.Entry<Integer,View>> iterator=visible.entrySet().iterator();
            while(iterator.hasNext()){Map.Entry<Integer,View> entry=iterator.next();MasonryLayout.Tile tile=plan.tiles.get(entry.getKey());if(tile.y+tile.height<top||tile.y>bottom){removeViewInLayout(entry.getValue());iterator.remove();}}
            boolean canPlay=playbackEnabled&&isShown()&&hasWindowFocus();
            // Release outgoing decoders before starting incoming ones, in either scroll direction.
            for(Map.Entry<Integer,View> entry:visible.entrySet()){
                MasonryLayout.Tile tile=plan.tiles.get(entry.getKey());
                if(!canPlay||visibleHeight(tile)<=0||!adapter.video(entry.getKey()))adapter.playback(entry.getValue(),false);
            }
            for(int i=0;i<plan.tiles.size();i++){
                MasonryLayout.Tile tile=plan.tiles.get(i);if(tile.y+tile.height<top||tile.y>bottom)continue;
                View view=visible.get(i);if(view==null){view=adapter.getView(i,null,this);visible.put(i,view);addViewInLayout(view,-1,new ViewGroup.LayoutParams(tile.width,tile.height),true);}
                view.measure(MeasureSpec.makeMeasureSpec(tile.width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(tile.height,MeasureSpec.EXACTLY));
                view.layout(tile.x,tile.y,tile.x+tile.width,tile.y+tile.height);
                boolean active=canPlay&&visibleHeight(tile)>0&&adapter.video(i);
                adapter.playback(view,active);
            }
            invalidate();
        }
        private int visibleHeight(MasonryLayout.Tile tile){
            return Math.max(0,Math.min(tile.y+tile.height,getScrollYOfGallery()+MasonryGallery.this.getHeight())-Math.max(tile.y,getScrollYOfGallery()));
        }
    }
}
