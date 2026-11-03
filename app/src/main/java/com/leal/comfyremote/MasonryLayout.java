package com.leal.comfyremote;

import java.util.*;

/** Natural image ratios, shortest-column placement, full-width panoramas. No image cropping. */
final class MasonryLayout {
    static final class Tile {
        final int x, y, width, height;
        Tile(int x, int y, int width, int height) { this.x=x; this.y=y; this.width=width; this.height=height; }
    }
    static final class Plan {
        final List<Tile> tiles = new ArrayList<>();
        int height;
    }
    static Plan arrange(int width, int padding, int gap, int columns, double[] ratios) {
        Plan plan = new Plan();
        columns = Math.max(1, columns);
        int available = Math.max(columns, width - padding * 2);
        int cell = Math.max(1, (available - gap * (columns - 1)) / columns);
        int[] bottoms = new int[columns]; Arrays.fill(bottoms, padding);
        for (double original : ratios) {
            double ratio = Double.isNaN(original) || Double.isInfinite(original) || original <= 0 ? 1 : original;
            int lowest=bottoms[0],highest=bottoms[0];for(int bottom:bottoms){lowest=Math.min(lowest,bottom);highest=Math.max(highest,bottom);}
            // Span a panorama only at a nearly level row; never leave a hole to force it across columns.
            boolean wide = ratio >= 2.2 && columns > 1 && highest-lowest<=gap;
            int column = 0, top = bottoms[0];
            for (int i=1; i<columns; i++) {
                if (wide) top = Math.max(top, bottoms[i]);
                else if (bottoms[i] < top) { column=i; top=bottoms[i]; }
            }
            int tileWidth = wide ? available : column == columns-1 ? available-(cell+gap)*(columns-1) : cell;
            int tileHeight = Math.max(1, (int)Math.round(tileWidth / ratio));
            plan.tiles.add(new Tile(padding + column*(cell+gap), top, tileWidth, tileHeight));
            if (wide) Arrays.fill(bottoms, top+tileHeight+gap);
            else bottoms[column] = top+tileHeight+gap;
        }
        plan.height=padding;
        for(int bottom:bottoms)plan.height=Math.max(plan.height,bottom-gap+padding);
        return plan;
    }
}
