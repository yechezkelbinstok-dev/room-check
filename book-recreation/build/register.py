import sys, json
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from common import *
import numpy as np, cv2
from PIL import Image

orig = np.asarray(Image.open(D + 'uploads/original_composite.jpg').convert('RGB')).astype(np.float32)
og = cv2.cvtColor(orig, cv2.COLOR_RGB2GRAY)

def on_white(im):
    a = np.asarray(im.convert('RGBA')).astype(np.float32)
    al = a[..., 3:] / 255.0
    return a[..., :3] * al + 255.0 * (1 - al)

def register(name, src, sub, search, scales, white=False):
    """src: path; sub: (x0,y0,x1,y1) sub-rect in SOURCE px known to be visible;
       search: (x0,y0,x1,y1) window in ORIGINAL px; returns best scale and top-left of full source in orig px"""
    im = load_rgba(src, white_bg=white)
    best = None
    for s in scales:
        w, h = max(1, round(im.width * s)), max(1, round(im.height * s))
        r = im.convert('RGBa').resize((w, h), Image.LANCZOS).convert('RGBA')
        rgb = on_white(r)
        g = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
        sx0, sy0, sx1, sy1 = [int(round(v * s)) for v in sub]
        tmpl = g[sy0:sy1, sx0:sx1]
        x0, y0, x1, y1 = search
        win = og[y0:y1, x0:x1]
        if tmpl.shape[0] >= win.shape[0] or tmpl.shape[1] >= win.shape[1]:
            continue
        res = cv2.matchTemplate(win, tmpl, cv2.TM_CCOEFF_NORMED)
        _, mv, _, ml = cv2.minMaxLoc(res)
        if best is None or mv > best[0]:
            # full-source top-left in orig px (sub-pixel precision ignored)
            fx = x0 + ml[0] - sub[0] * s
            fy = y0 + ml[1] - sub[1] * s
            best = (mv, s, fx, fy)
    print(f'{name}: ncc={best[0]:.3f} scale={best[1]:.4f} topleft=({best[2]:.1f},{best[3]:.1f})  size_in_orig=({im.width*best[1]:.1f}x{im.height*best[1]:.1f})')
    return dict(ncc=float(best[0]), scale=float(best[1]), x=float(best[2]), y=float(best[3]))

out = {}
out['K'] = register('K regular LS', D + 'series/bo.png', (460, 340, 1100, 800), (270, 235, 430, 356), np.arange(0.150, 0.215, 0.0015))
out['B'] = register('B pocket LS', D + 'series/bo3.png', (150, 150, 600, 890), (0, 190, 150, 350), np.arange(0.150, 0.215, 0.0015))
out['G'] = register('G tehillim', D + 'comp/tehillim5_0.png', (160, 150, 600, 600), (330, 120, 460, 250), np.arange(0.130, 0.190, 0.0015), white=True)
out['F'] = register('F LS moadim 3', D + 'comp/ls_purim3_0.png', (330, 50, 690, 380), (345, 45, 445, 150), np.arange(0.150, 0.215, 0.0015))
json.dump(out, open(D + 'build/reg.json', 'w'), indent=1)
