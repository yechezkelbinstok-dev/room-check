import sys, json
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from common import *
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage as ndi

S = float(sys.argv[1]) if len(sys.argv) > 1 else 4.0
OUT = sys.argv[2] if len(sys.argv) > 2 else D + 'build/v6.png'
MATCH = (sys.argv[3] if len(sys.argv) > 3 else '1') == '1'
ORIG = D + 'uploads/original_composite.jpg'
# canvas in ORIGINAL-image coordinates (original is 568x356); widened so no set is cut off
X0, X1, Y0, Y1 = -12.0, 580.0, 0.0, 356.0
W, H = int(round((X1 - X0) * S)), int(round((Y1 - Y0) * S))
reg = json.load(open(D + 'build/reg.json'))

ops = []                       # ('shadow', img, X, Y) | ('obj', name, kind, img, X, Y)
CUR = {'name': '?', 'kind': 'exact'}
def obj(name, kind): CUR['name'], CUR['kind'] = name, kind

def cx(x): return int(round((x - X0) * S))
def cy(y): return int(round((y - Y0) * S))

def resize_rgba(im, w, h):
    w, h = max(1, int(round(w))), max(1, int(round(h)))
    out = im.convert('RGBa').resize((w, h), Image.LANCZOS).convert('RGBA')
    if w < im.width * 0.9:
        out = out.filter(ImageFilter.UnsharpMask(radius=0.7, percent=30, threshold=2))
    return out

def paste_clip(canvas, img, X, Y):
    x0, y0 = max(0, -X), max(0, -Y)
    if x0 >= img.width or y0 >= img.height: return
    canvas.alpha_composite(img.crop((x0, y0, img.width, img.height)), (X + x0, Y + y0))

def drop_shadow(img, X, Y, blur=2.4, dx=2.2, dy=1.4, opacity=0.28):
    a = img.split()[-1]; pad = int(blur * S * 3)
    big = Image.new('L', (img.width + 2 * pad, img.height + 2 * pad), 0); big.paste(a, (pad, pad))
    big = big.filter(ImageFilter.GaussianBlur(blur * S))
    sh = Image.new('RGBA', big.size, (35, 30, 28, 0)); sh.putalpha(big.point(lambda v: int(v * opacity)))
    ops.append(('shadow', sh, X - pad + int(dx * S), Y - pad + int(dy * S)))

def contact_shadow(x0, x1, ybase, strength=0.30, h=3.5):
    w = int((x1 - x0 + 16) * S); hh = int((h * 4 + 10) * S)
    m = Image.new('L', (w, hh), 0); d = ImageDraw.Draw(m)
    d.ellipse([int(8 * S), int(hh / 2 - h * S), w - int(8 * S), int(hh / 2 + h * S)], fill=int(255 * strength))
    m = m.filter(ImageFilter.GaussianBlur(h * S * 0.9))
    sh = Image.new('RGBA', (w, hh), (30, 25, 22, 0)); sh.putalpha(m)
    ops.append(('shadow', sh, cx(x0 - 8), int(cy(ybase) - hh / 2)))

def put(img, x, y, shadow=True, **kw):
    X, Y = cx(x), cy(y)
    if shadow: drop_shadow(img, X, Y, **kw)
    ops.append(('obj', CUR['name'], CUR['kind'], img, X, Y))

def put_registered(key, src_rgba, **kw):
    r = reg[key]; sx = r.get('sx', r.get('scale')); sy = r.get('sy', r.get('scale'))
    img = resize_rgba(src_rgba, src_rgba.width * sx * S, src_rgba.height * sy * S)
    put(img, r['x'], r['y'], **kw)

def fit(img, x, y, w=None, h=None, **kw):
    if w is None: w = img.width * h / img.height
    if h is None: h = img.height * w / img.width
    out = resize_rgba(img, w * S, h * S); put(out, x, y, **kw); return out

def to_np(im): return np.asarray(im.convert('RGBA')).astype(np.float32) / 255.0
def from_np(a): return Image.fromarray((np.clip(a, 0, 1) * 255 + .5).astype(np.uint8), 'RGBA')

def to_dark_gold(im):
    a = to_np(im); rgb, al = a[..., :3], a[..., 3:]
    lum = (rgb * np.array([0.30, 0.55, 0.15])).sum(-1, keepdims=True)
    mx = rgb.max(-1, keepdims=True); mn = rgb.min(-1, keepdims=True); sat = (mx - mn) / np.maximum(mx, 1e-4)
    r, g, b = rgb[..., :1], rgb[..., 1:2], rgb[..., 2:3]
    gold = np.clip((sat - 0.30) / 0.15, 0, 1) * np.clip((lum - 0.32) / 0.10, 0, 1) * (g > b * 1.05)
    leather = np.clip((r - b - 0.06) / 0.08, 0, 1) * (1 - gold) * np.clip((0.55 - lum) / 0.10, 0, 1)
    blur = np.asarray(Image.fromarray((np.clip(lum[..., 0], 0, 1) * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(7))).astype(np.float32)[..., None] / 255.0
    dark = (0.03 + blur * 0.55) * np.array([1.0, 0.86, 0.80])
    return from_np(np.concatenate([rgb * (1 - leather) + dark * leather, al], -1))

def L(path, white=False): return load_rgba(path, white_bg=white)

# ---------------- sources ----------------
rashi5 = L(D + 'comp/rashi5_0.png')
purim3 = L(D + 'comp/ls_purim3_0.png')
sefiras = L(D + 'comp/ls_sefiras_0.png')
avos_green = L(D + 'build/src_avos_green.png')
tehillim = L(D + 'comp/tehillim5_0.png', white=True)
pocket = L(D + 'series/bo3.png')
regular = L(D + 'series/bo.png')
hag_red = L(D + 'build/src_haggadah_red.png')
mel_brown = L(D + 'build/src_melukat_brown.png')
r100 = L(D + 'build/src_r100.png')

# ---------------- back row ----------------
rD = reg['D']
obj('D2 dark volumes', 'stand')
two = to_dark_gold(mel_brown).crop((53, 8, 215, mel_brown.height))
img = resize_rgba(two, two.width * rD['sx'] * S, two.height * rD['sy'] * S)
put(img, 336.2, rD['y'] + 8 * rD['sy'] + 1.5, blur=2.0, opacity=0.22)
obj('D Melukat', 'exact')
img = resize_rgba(mel_brown, mel_brown.width * rD['sx'] * S, mel_brown.height * rD['sy'] * S)
put(img, rD['x'], rD['y'], blur=2.0, opacity=0.24)

obj('E2 Rashi', 'stand')
rE = reg['E2']
e2 = rashi5.crop((440, 53, 881, 998))
img = resize_rgba(e2, e2.width * rE['sx'] * S, e2.height * rE['sy'] * S)
put(img, rE['x'] + 440 * rE['sx'], rE['y'] + 53 * rE['sy'], blur=2.0, opacity=0.22)

obj('E1 white box', 'box')
bx0, by0, bx1, by1 = 88.0, 79.8, 117.6, 215.0
bw, bh = int((bx1 - bx0) * S), int((by1 - by0) * S)
gx = np.linspace(0, 1, bw)[None, :]; gy = np.linspace(0, 1, bh)[:, None]
base = 0.965 - 0.03 * gy - 0.05 * np.exp(-gx * 12) + 0.008 * (np.random.default_rng(3).random((bh, bw)) - 0.5)
box = from_np(np.stack([base * 0.995, base * 0.995, base * 1.0, np.ones_like(base)], -1))
d = ImageDraw.Draw(box)
d.line([(0, 0), (bw, 0)], fill=(246, 246, 248, 255), width=max(1, int(S * 0.5)))
d.line([(bw - 1, 0), (bw - 1, bh)], fill=(205, 205, 210, 255), width=max(1, int(S * 0.4)))
lx, ly = int(2.2 * S), int(9.0 * S)
d.rectangle([lx, ly, lx + int(7.5 * S), ly + int(19.5 * S)], fill=(18, 18, 20, 255))
for k in range(4):
    yy = ly + int((2.6 + k * 4.3) * S)
    d.rectangle([lx + int(1.3 * S), yy, lx + int(6.2 * S), yy + max(1, int(S * 1.0))], fill=(232, 232, 232, 255))
for k in range(2):
    d.rectangle([lx + int((1.6 + k * 2.6) * S), ly + int(23.5 * S), lx + int((2.6 + k * 2.6) * S), ly + int(25.5 * S)], fill=(165, 165, 168, 255))
put(box, bx0, by0, blur=3.0, dx=-1.0, dy=0.8, opacity=0.20)

obj('H maroon', 'stand')
fit(L(D + 'build/src_shidduchim.png').crop(bbox(r100)), 388, 45, w=106, blur=3.0, dx=3.0, opacity=0.30)

obj('F Moadim 3', 'exact')
put_registered('F', purim3, blur=2.4, opacity=0.28)

obj('E3 Sefer HaSichos', 'stand')
def sefer_hasichos_row(keep=24):
    src = Image.open(D + 'cand3/3968737738838_0.png').convert('RGBA')
    a = to_np(src); rgb, al = a[..., :3], a[..., 3:]
    lum = (rgb * np.array([0.30, 0.55, 0.15])).sum(-1)
    H_, W_ = lum.shape
    top_mask = np.zeros((H_, W_), np.float32)
    for x in range(W_):
        col = lum[:, x]; alc = al[:, x, 0]
        ys = np.where(alc > 0.5)[0]
        if len(ys) == 0: continue
        y = ys[0]; run = 0; stop = None
        while y < H_ - 1 and y < ys[0] + 400:
            run = run + 1 if col[y] < 0.30 else 0
            if run >= 8: stop = y - 8; break
            y += 1
        if stop is not None: top_mask[ys[0]:stop + 1, x] = 1.0
    top_mask = np.asarray(Image.fromarray((top_mask * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(1.5))).astype(np.float32)[..., None] / 255.0
    grey = np.repeat(lum[..., None], 3, -1)
    silver = np.clip(0.55 + grey * 0.5, 0, 1) * np.array([0.97, 0.98, 1.0])
    rgb = rgb * (1 - top_mask) + silver * top_mask
    l2 = (rgb * np.array([0.30, 0.55, 0.15])).sum(-1, keepdims=True)
    rgb = l2 + (rgb - l2) * 0.65
    rgb = rgb * 0.86 + 0.085
    out = from_np(np.concatenate([rgb, al], -1))
    y0, y1 = 455, 1260
    chunk1 = out.crop((790, y0, 1532, y1)); chunk2 = out.crop((790, y0, 1240, y1))
    row = Image.new('RGBA', (chunk1.width + chunk2.width, y1 - y0), (0, 0, 0, 0))
    row.alpha_composite(chunk2, (chunk1.width, 0)); row.alpha_composite(chunk1, (0, 0))
    R = 120
    top = row.crop((0, 0, row.width, R)).convert('RGBa').resize((row.width, keep), Image.LANCZOS).convert('RGBA')
    flat = Image.new('RGBA', (row.width, row.height - R + keep), (0, 0, 0, 0))
    flat.alpha_composite(row.crop((0, R, row.width, row.height)), (0, keep)); flat.alpha_composite(top, (0, 0))
    return flat
row = sefer_hasichos_row(keep=4)
sxE3, syE3 = 0.157, 0.135
row = row.crop((0, 0, int(160 / sxE3), row.height))
img = resize_rgba(row, row.width * sxE3 * S, row.height * syE3 * S)
put(img, 186.0, 109.2, blur=2.0, opacity=0.25)

obj('C Avos', 'exact');    put_registered('C', avos_green, blur=2.6, opacity=0.30)
obj('G Tehillim', 'exact'); put_registered('G', tehillim, blur=2.6, opacity=0.30)
obj('J Haggadah', 'exact')
contact_shadow(463, 575, 342.0, strength=0.30)
put_registered('J', hag_red, blur=2.8, dx=2.6, opacity=0.30)

# ---------------- front row ----------------
obj('B pocket LS', 'exact')
contact_shadow(-7, 200, 339.5, strength=0.26)
put_registered('B', pocket, blur=2.6, opacity=0.28)
obj('K regular LS', 'exact')
contact_shadow(214, 425, 343.0, strength=0.28)
put_registered('K', regular, blur=2.4, opacity=0.28)
obj('I Chumash', 'stand')
contact_shadow(411, 516, 338.0, strength=0.32)
fit((lambda c: c.crop(bbox(c)))(L(D + 'build/src_chumash_brown.png')), 411, 205, h=133, blur=2.6, dx=2.4, opacity=0.32)

obj('A front Moadim', 'stand')
cover = sefiras.crop((112, 14, 512, 632))
c_ = to_np(cover); c_[..., :3] = np.clip(c_[..., :3] * np.array([0.70, 0.72, 0.80]) + np.array([0.0, 0.0, 0.015]), 0, 1)
cover = from_np(c_)
spines = purim3.crop((388, 50, 590, 864))
cov = resize_rgba(cover, 77 * S, 165 * S); spi = resize_rgba(spines, 33 * S, 166 * S)
grp = Image.new('RGBA', (cov.width + spi.width, max(cov.height, spi.height)), (0, 0, 0, 0))
grp.alpha_composite(spi, (cov.width, 0)); grp.alpha_composite(cov, (0, int(0.6 * S)))
contact_shadow(144, 254, 351.5, strength=0.34)
put(grp, 144, 186, blur=3.0, dx=2.8, opacity=0.34)

# ---------------- tone/colour matching to the original ----------------
PARAMS = {'exact': (3.0, 0.60), 'stand': (6.0, 0.85), 'box': (4.0, 0.90)}
if MATCH:
    W1, H1 = int(round(X1 - X0)), int(round(Y1 - Y0))
    O = np.zeros((H1, W1, 3), np.float32); valid = np.zeros((H1, W1), np.float32)
    o = np.asarray(Image.open(ORIG).convert('RGB')).astype(np.float32)
    ox = int(round(-X0)); O[:, ox:ox + o.shape[1]] = o; valid[:, ox:ox + o.shape[1]] = 1
    objs = [i for i, op in enumerate(ops) if op[0] == 'obj']
    lay = {}
    for i in objs:
        _, name, kind, img, X, Y = ops[i]
        full = Image.new('RGBA', (W, H), (0, 0, 0, 0)); paste_clip(full, img, X, Y)
        lay[i] = np.asarray(full.convert('RGBa').resize((W1, H1), Image.BOX).convert('RGBA')).astype(np.float32)
    occl = np.ones((H1, W1), np.float32); vis = {}
    for i in reversed(objs):
        A = lay[i][..., 3] / 255.0
        vis[i] = A * occl; occl = occl * (1 - A)
    lumw = np.array([0.30, 0.55, 0.15], np.float32)
    report = []
    for i in objs:
        _, name, kind, img, X, Y = ops[i]
        sig, strength = PARAMS[kind]
        R = lay[i][..., :3]
        dl = np.abs((O * lumw).sum(-1) - (R * lumw).sum(-1))
        Wt = vis[i] * valid * np.exp(-(dl / 55.0) ** 2)
        if Wt.sum() < 5: report.append((name, 'no support')); continue
        Gw = ndi.gaussian_filter(Wt, sig)
        Ob = np.stack([ndi.gaussian_filter(O[..., c] * Wt, sig) for c in range(3)], -1) / np.maximum(Gw, 1e-4)[..., None]
        Rb = np.stack([ndi.gaussian_filter(R[..., c] * Wt, sig) for c in range(3)], -1) / np.maximum(Gw, 1e-4)[..., None]
        glob = (O * Wt[..., None]).sum((0, 1)) / np.maximum((R * Wt[..., None]).sum((0, 1)), 1)
        gain = Ob / np.maximum(Rb, 10.0)
        conf = np.clip(Gw / 0.30, 0, 1)[..., None]
        gain = gain * conf + glob * (1 - conf)
        gain = np.clip(gain, 0.65, 1.6)
        gain = 1 + strength * (gain - 1)
        # apply at full resolution
        P = 80
        gx0, gy0 = X / S + P, Y / S + P; gx1, gy1 = (X + img.width) / S + P, (Y + img.height) / S + P
        a = to_np(img)
        for c in range(3):
            gc = Image.fromarray(np.pad(gain[..., c], P, mode='edge').astype(np.float32), 'F')
            gfull = np.asarray(gc.resize(img.size, Image.BILINEAR, box=(gx0, gy0, gx1, gy1))).astype(np.float32)
            a[..., c] = np.clip(a[..., c] * gfull, 0, 1)
        ops[i] = ('obj', name, kind, from_np(a), X, Y)
        report.append((name, np.round(glob, 3).tolist()))
    for r in report: print('match', r)

def bleed(img):
    a = np.asarray(img).copy(); solid = ndi.binary_erosion(a[..., 3] >= 250, iterations=2)
    if not solid.any(): return img
    _, (iy, ix) = ndi.distance_transform_edt(~solid, return_indices=True)
    a[..., :3] = a[..., :3][iy, ix]; return Image.fromarray(a, 'RGBA')
ops = [(op[0], op[1], op[2], bleed(op[3]), op[4], op[5]) if op[0] == 'obj' else op for op in ops]
white = Image.new('RGBA', (W, H), (255, 255, 255, 255)); trans = Image.new('RGBA', (W, H), (0, 0, 0, 0))
for op in ops:
    for cv in (white, trans):
        if op[0] == 'shadow': paste_clip(cv, op[1], op[2], op[3])
        else: paste_clip(cv, op[3], op[4], op[5])
NAMES = {'D2 dark volumes': '01_two_dark_volumes_STANDIN', 'D Melukat': '02_sefer_hamaamarim_melukat_brown_EXACT',
         'E2 Rashi': '03_biurim_lpirush_rashi_STANDIN', 'E1 white box': '04_white_slipcase_DRAWN',
         'H maroon': '05_shidduchim_vnisuin_RELETTERED', 'F Moadim 3': '06_likkutei_sichos_moadim_3vol_SAME_EDITION',
         'E3 Sefer HaSichos': '07_sefer_hasichos_row_STANDIN', 'C Avos': '08_biurim_lpirkei_avos_green_EXACT',
         'G Tehillim': '09_tehillim_tehillos_menachem_EXACT', 'J Haggadah': '10_haggadah_red_2vol_EXACT',
         'B pocket LS': '11_likkutei_sichos_parshiyos_pocket_EXACT', 'K regular LS': '12_likkutei_sichos_regular_EXACT',
         'I Chumash': '13_chumash_brown_RELETTERED', 'A front Moadim': '14_likkutei_sichos_moadim_front_STANDIN'}
sharp = white.convert('RGB').filter(ImageFilter.UnsharpMask(radius=1.0, percent=20, threshold=2))
sharp.save(OUT, optimize=True)
t = np.asarray(trans).copy(); inner = ndi.binary_erosion(t[..., 3] == 255, iterations=2)
t[..., :3][inner] = np.asarray(sharp)[inner]
ys, xs = np.where(t[..., 3] > 2); p = 6
box = (max(0, xs.min() - p), max(0, ys.min() - p), min(W, xs.max() + p + 1), min(H, ys.max() + p + 1))
Image.fromarray(t, 'RGBA').crop(box).save(OUT.replace('.png', '_transparent.png'), optimize=True)
PIECES = __import__('os').environ.get('PIECES')
if PIECES:
    import os; os.makedirs(PIECES, exist_ok=True); layout = []
    for z, op in enumerate([o for o in ops if o[0] == 'obj'], 1):
        _, name, kind, img, X, Y = op
        bb = img.getbbox(); piece = img.crop(bb); fn = NAMES[name] + '.png'
        piece.save(os.path.join(PIECES, fn), optimize=True)
        layout.append({'file': fn, 'z_order': z, 'x': int(X + bb[0] - box[0]), 'y': int(Y + bb[1] - box[1]),
                       'width': piece.width, 'height': piece.height, 'status': fn.rsplit('_', 1)[-1][:-4] if not fn.endswith('EDITION.png') else 'SAME_EDITION'})
    json.dump({'canvas': {'width': int(box[2] - box[0]), 'height': int(box[3] - box[1])},
               'note': 'x/y = top-left in the final transparent PNG; draw in z_order (1 = back). Shadows are only in the final image.',
               'pieces': layout}, open(os.path.join(PIECES, '..', 'layout.json'), 'w'), indent=1)
    print('pieces saved:', len(layout))
print('saved', OUT, 'and transparent', box)
