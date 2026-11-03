"""Build the bwd owl from the same OFL Syne outlines as the app wordmark.

Requires fonttools. Optional --preview uses Pillow and aggdraw. Generated Android
vectors need no font or bitmap at launcher runtime; keep all ink in the safe zone.
"""
from pathlib import Path
import argparse
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--preview', type=Path)
    args = parser.parse_args()
    font = instantiateVariableFont(TTFont(ROOT / 'app/src/main/assets/fonts/Syne.ttf'), {'wght': 800})
    glyphs = font.getGlyphSet()
    paths = []
    # b/d ascenders form ears; their counters form eyes. The low w forms the beak.
    for letter, transform in [('b', (.034, 0, 0, -.043, 19, 61)),
                              ('d', (.034, 0, 0, -.043, 52, 61)),
                              ('w', (.015, 0, 0, -.029, 43, 72))]:
        pen = SVGPathPen(glyphs)
        glyphs[font.getBestCmap()[ord(letter)]].draw(TransformPen(pen, transform))
        paths.append(pen.getCommands())
    # Small pupils are part of the single-colour silhouette, including themed icons.
    for x in [42, 66]:
        paths.append(f'M{x-1.9} 51 C{x-1.9} 46.8 {x+1.9} 46.8 {x+1.9} 51 C{x+1.9} 55.2 {x-1.9} 55.2 {x-1.9} 51 Z')
    ink = ' '.join(paths)
    # Keep the ear tips inside Android's 66dp circular adaptive-icon safe zone.
    from fontTools.svgLib.path import parse_path
    safe = SVGPathPen(None)
    parse_path(ink, TransformPen(safe, (.84,0,0,.84,8.64,8.64)))
    ink = safe.getCommands()
    vector = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
    (RES / 'drawable/ic_bwd_foreground.xml').write_text(vector + f'    <path android:fillColor="#EDEEF5" android:pathData="{ink}" />\n</vector>\n', encoding='utf-8')
    # Android 6/7 use a self-contained vector. Android 8+ apply the launcher's mask.
    background = '<path android:fillColor="#111216" android:pathData="M24,0 H84 Q108,0 108,24 V84 Q108,108 84,108 H24 Q0,108 0,84 V24 Q0,0 24,0 Z" />'
    (RES / 'drawable/ic_bwd_legacy.xml').write_text(vector + background + f'\n<path android:fillColor="#EDEEF5" android:pathData="{ink}" />\n</vector>\n', encoding='utf-8')
    for folder, content in [('mipmap-anydpi', '<inset xmlns:android="http://schemas.android.com/apk/res/android" android:drawable="@drawable/ic_bwd_legacy" />'),
                            ('mipmap-anydpi-v26', '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_background"/><foreground android:drawable="@drawable/ic_bwd_foreground"/></adaptive-icon>'),
                            ('mipmap-anydpi-v33', '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"><background android:drawable="@color/launcher_background"/><foreground android:drawable="@drawable/ic_bwd_foreground"/><monochrome android:drawable="@drawable/ic_bwd_foreground"/></adaptive-icon>')]:
        (RES / folder).mkdir(exist_ok=True)
        (RES / folder / 'ic_launcher.xml').write_text(content + '\n', encoding='utf-8')
    (RES / 'values/launcher.xml').write_text('<resources><color name="launcher_background">#111216</color></resources>\n', encoding='utf-8')
    art = ROOT / 'design'
    art.mkdir(exist_ok=True)
    (art / 'bwd-owl.svg').write_text(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108"><rect width="108" height="108" rx="24" fill="#111216"/><path fill="#edeef5" d="{ink}"/></svg>\n', encoding='utf-8')
    if args.preview:
        from PIL import Image, ImageDraw
        import aggdraw
        from fontTools.pens.basePen import BasePen
        from fontTools.svgLib.path import parse_path
        class RasterPen(BasePen):
            def __init__(self):
                super().__init__(None)
                self.path = aggdraw.Path()
            def _moveTo(self, p): self.path.moveto(p[0]*6, p[1]*6)
            def _lineTo(self, p): self.path.lineto(p[0]*6, p[1]*6)
            def _curveToOne(self, a, b, c): self.path.curveto(*[v*6 for p in [a,b,c] for v in p])
            def _closePath(self): self.path.close()
        im = Image.new('RGB', (648,648), '#000000')
        ImageDraw.Draw(im).rounded_rectangle((0,0,647,647),radius=144,fill='#111216')
        pen=RasterPen();parse_path(ink,pen)
        ctx=aggdraw.Draw(im);ctx.path(pen.path,None,aggdraw.Brush('#edeef5'));ctx.flush()
        args.preview.parent.mkdir(exist_ok=True,parents=True);im.save(args.preview)
    print('Generated adaptive, themed and legacy bwd owl icons')


if __name__ == '__main__': main()
