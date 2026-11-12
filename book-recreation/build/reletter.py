import sys, math
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from common import *
import numpy as np, cv2
from scipy import ndimage as ndi
from PIL import Image, ImageDraw, ImageFont, ImageFilter

FR_BOLD = '/usr/share/fonts/truetype/culmus/FrankRuehlCLM-Bold.ttf'
FR_MED = '/usr/share/fonts/truetype/culmus/FrankRuehlCLM-Medium.ttf'
rng = np.random.default_rng(11)

def text_ink(text, font=FR_BOLD, size=240):
    f = ImageFont.truetype(font, size)
    bb = f.getbbox(text, direction='rtl', language='he')
    im = Image.new('L', (bb[2] - bb[0] + 60, bb[3] - bb[1] + 60), 0)
    ImageDraw.Draw(im).text((30 - bb[0], 30 - bb[1]), text, font=f, fill=255, direction='rtl', language='he')
    return im.crop(im.getbbox())

def fit_ink(ink, max_w, max_h):
    s = min(max_w / ink.width, max_h / ink.height)
    return ink.resize((max(1, round(ink.width * s)), max(1, round(ink.height * s))), Image.LANCZOS)

def gold_rgba(mask, light, dark, angle=0.0):
    """foil look: vertical light->dark gradient, fine grain, thin debossed shadow"""
    if angle:
        mask = mask.rotate(angle, resample=Image.BICUBIC, expand=True)
    m = np.asarray(mask).astype(np.float32) / 255.0
    h, w = m.shape
    t = np.linspace(0, 1, h)[:, None, None]
    col = np.array(light)[None, None] * (1 - t) + np.array(dark)[None, None] * t
    col = col + rng.normal(0, 6, (h, w, 1))
    sh = ndi.shift(ndi.gaussian_filter(m, 0.8), (1.2, 1.2), order=1)
    out = np.zeros((h, w, 4), np.float32)
    # shadow first (dark), then foil
    out[..., :3] = 20; out[..., 3] = np.clip(sh * 0.55, 0, 1) * 255
    a_f = m
    out[..., :3] = col * a_f[..., None] + out[..., :3] * (1 - a_f[..., None])
    out[..., 3] = np.clip(a_f * 255 + out[..., 3] * (1 - a_f), 0, 255)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), 'RGBA')

def dot_ink(d=40):
    im = Image.new('L', (d + 8, d + 8), 0); ImageDraw.Draw(im).ellipse([4, 4, 4 + d, 4 + d], fill=255); return im

def place_center(base, tile, cx_, cy_):
    base.alpha_composite(tile, (int(round(cx_ - tile.width / 2)), int(round(cy_ - tile.height / 2))))

def bleed_rgba(img, thr=250):
    a = np.asarray(img).copy(); solid = a[..., 3] >= thr
    _, (iy, ix) = ndi.distance_transform_edt(~solid, return_indices=True)
    a[..., :3] = a[..., :3][iy, ix]
    return Image.fromarray(a, 'RGBA')

def erase(img_rgba, mask, radius=5, grain=None):
    a = np.asarray(img_rgba).copy()
    rgb = np.ascontiguousarray(a[..., :3])
    m8 = (mask > 0).astype(np.uint8) * 255
    out = cv2.inpaint(rgb, m8, radius, cv2.INPAINT_TELEA).astype(np.float32)
    if grain:
        noise = ndi.gaussian_filter(rng.normal(0, grain, out.shape[:2]), 0.7)[..., None]
        mf = ndi.gaussian_filter((mask > 0).astype(np.float32), 1.0)[..., None]
        out = out + noise * mf
    a[..., :3] = np.clip(out, 0, 255).astype(np.uint8)
    return Image.fromarray(a, 'RGBA')

def gold_mask_bw(img):
    a = np.asarray(img).astype(np.float32) / 255; rgb = a[..., :3]
    mx = rgb.max(-1); mn = rgb.min(-1); sat = (mx - mn) / np.maximum(mx, 1e-4)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    return (sat > 0.25) & (mx > 0.33) & (r >= g) & (g > b) & (a[..., 3] > 0.5)

def box_mask(shape, boxes):
    m = np.zeros(shape, bool)
    for x0, y0, x1, y1 in boxes: m[y0:y1, x0:x1] = True
    return m

def sample_gold(img, mask):
    a = np.asarray(img).astype(np.float32)[..., :3][mask]
    lum = a @ np.array([0.3, 0.55, 0.15])
    hi = a[lum >= np.percentile(lum, 75)].mean(0); lo = a[lum <= np.percentile(lum, 35)].mean(0)
    return tuple(hi), tuple(lo)

# =====================================================================
# 1) Chumash (Kehot, Ohel-Yosef-Yitzchak arch design) from the Tehillim photo
# =====================================================================
teh = bleed_rgba(load_rgba(D + 'comp/tehillim5_0.png', white_bg=True))
gm = gold_mask_bw(teh)
H_, W_ = gm.shape
light, dark = sample_gold(teh, gm & box_mask(gm.shape, [(314, 398, 420, 458)]))

# --- cover: erase old words (keep 'עם פירוש' and 'ספר')
cover_boxes = [(310, 396, 424, 460), (270, 462, 466, 521), (290, 551, 446, 588), (340, 657, 396, 685)]
m = gm & box_mask(gm.shape, cover_boxes)
m = ndi.binary_dilation(m, iterations=3) & box_mask(gm.shape, cover_boxes)

# --- spines: components per spine, ordered top->bottom
lab, n = ndi.label(ndi.binary_dilation(gm, iterations=2))
comps = []
for i, sl in enumerate(ndi.find_objects(lab)):
    ys, xs = sl
    if xs.start < 598 or ys.start < 160 or ys.stop > 630: continue
    if (lab[sl] == i + 1).sum() < 25: continue
    comps.append([xs.start, ys.start, xs.stop, ys.stop])
centers = [619, 658, 692, 725, 760]
spines = [[] for _ in centers]
for c in comps:
    k = int(np.argmin([abs((c[0] + c[2]) / 2 - cc) for cc in centers])); spines[k].append(c)
for s in spines: s.sort(key=lambda c: c[1])
print('components per spine:', [len(s) for s in spines])

# tilt of spine text from the top flourish of spine 1
fl = spines[0][0]; ys, xs = np.where(gm[fl[1]:fl[3], fl[0]:fl[2]])
cov = np.cov(np.stack([xs, ys])); ev, evec = np.linalg.eigh(cov); vx, vy = evec[:, -1]
if vx < 0: vx, vy = -vx, -vy
tilt = -math.degrees(math.atan2(vy, vx)); print('spine text tilt (deg):', round(tilt, 1))

spine_jobs = []
vol_names = ['בראשית', 'שמות', 'ויקרא', 'במדבר', 'דברים']
for k, s in enumerate(spines):
    if len(s) < 12: print('spine', k, 'unexpected count', len(s)); continue
    title, a_, b_, c_, dot, small, d_, e_, fl2, sefer, vol = s[1], s[2], s[3], s[4], s[5], s[6], s[7], s[8], s[9], s[10], s[11]
    for c in (title, a_, b_, c_, d_, e_, vol):
        m |= ndi.binary_dilation(gm & box_mask(gm.shape, [tuple(c)]), iterations=2) & box_mask(gm.shape, [(c[0] - 2, c[1] - 2, c[2] + 2, c[3] + 2)])
    def merged(*cs): return [min(c[0] for c in cs), min(c[1] for c in cs), max(c[2] for c in cs), max(c[3] for c in cs)]
    spine_jobs += [('חמשה', title, 0.95), ('חומשי', a_, 0.95), ('תורה', merged(b_, c_), 0.75), ('רש"י', merged(d_, e_), 0.62), (vol_names[k], vol, 0.95)]

teh2 = erase(teh, m, radius=4, grain=3.0)

# --- new gold lettering
canvas = teh2.copy()
ink = fit_ink(text_ink('חמשה'), 118, 46);           place_center(canvas, gold_rgba(ink, light, dark), 367, 430)
ink = fit_ink(text_ink('חומשי תורה'), 196, 44);    place_center(canvas, gold_rgba(ink, light, dark), 368, 492)
ink = fit_ink(text_ink('רש"י'), 110, 30);          place_center(canvas, gold_rgba(ink, light, dark), 368, 571)
ink = fit_ink(text_ink('בראשית', FR_BOLD), 62, 21); place_center(canvas, gold_rgba(ink, light, dark), 368, 671)
for word, c, hfrac in spine_jobs:
    bw, bh = c[2] - c[0], c[3] - c[1]
    ink = fit_ink(text_ink(word), bw * 0.92, bh * hfrac)
    place_center(canvas, gold_rgba(ink, light, dark, angle=tilt), (c[0] + c[2]) / 2, (c[1] + c[3]) / 2)

# --- black leather -> dark brown (keep gold, page tops and highlights)
a = np.asarray(canvas).astype(np.float32) / 255
rgb, al = a[..., :3], a[..., 3:]
mx = rgb.max(-1, keepdims=True); mn = rgb.min(-1, keepdims=True); sat = (mx - mn) / np.maximum(mx, 1e-4)
lum = (rgb * np.array([0.3, 0.55, 0.15])).sum(-1, keepdims=True)
w = np.clip((0.22 - sat) / 0.10, 0, 1) * np.clip((0.55 - lum) / 0.15, 0, 1)
brown = np.clip(lum * np.array([1.70, 1.02, 0.70]) + np.array([0.02, 0.0, 0.0]), 0, 1)
rgb = rgb * (1 - w) + brown * w
Image.fromarray((np.concatenate([rgb, al], -1) * 255 + .5).astype(np.uint8), 'RGBA').save(D + 'build/src_chumash_brown.png')
print('saved chumash')

# =====================================================================
# 2) Shidduchim V'nisuin (old 1-volume maroon edition) from the Igros Kodesh photo
# =====================================================================
ik = bleed_rgba(Image.open(D + 'build/src_r100.png').convert('RGBA'))
a = np.asarray(ik).astype(np.float32) / 255; r, g, b = a[..., 0], a[..., 1], a[..., 2]
gk = (r > 0.55) & (g > 0.64 * r) & (b < g) & (a[..., 3] > 0.5)
lightK, darkK = sample_gold(ik, gk & box_mask(gk.shape, [(560, 700, 1110, 790)]))
erase_boxes = [(540, 690, 1120, 1045),            # cover text block (keep rule line at ~1046)
               (1300, 374, 1442, 552), (1300, 604, 1442, 648), (1300, 696, 1442, 745),   # spine title, dot, 'מתורגמות'
               (1300, 955, 1442, 1115)]            # spine volume info
mK = ndi.binary_dilation(gk & box_mask(gk.shape, erase_boxes), iterations=3) & box_mask(gk.shape, erase_boxes)
spine_boxes = erase_boxes[1:]
mxK = np.maximum(np.maximum(r, g), b); satK = (mxK - np.minimum(np.minimum(r, g), b)) / np.maximum(mxK, 1e-4)
gk_loose = (r > 0.38) & (g > 0.56 * r) & (b < g) & (satK > 0.30) & (a[..., 3] > 0.5)      # darker letter edges, not the pale rim
gk_loose[:, 1437:] = False
mK |= ndi.binary_dilation(gk_loose & box_mask(gk.shape, spine_boxes), iterations=4) & box_mask(gk.shape, spine_boxes)
ik2 = erase(ik, mK, radius=5, grain=0)
def regrain(img, mask, src_box_shift):
    a = np.asarray(img).astype(np.float32)
    o = np.asarray(ik).astype(np.float32)
    dy, dx = src_box_shift
    src = np.roll(np.roll(o, dy, 0), dx, 1)[..., :3]
    hp = src - np.stack([ndi.gaussian_filter(src[..., c], 6) for c in range(3)], -1)    # leather detail only
    mf = ndi.gaussian_filter(mask.astype(np.float32), 2.0)[..., None]
    a[..., :3] = np.clip(a[..., :3] + hp * mf * 0.9, 0, 255)
    return Image.fromarray(a.astype(np.uint8), 'RGBA')
mCover = mK & box_mask(mK.shape, [(540, 690, 1120, 1045)])
mSpine = mK & ~mCover
ik2 = regrain(ik2, ndi.binary_dilation(mCover, iterations=6), (-390, 0))    # texture from plain leather 390px below
mS_d = ndi.binary_dilation(mSpine, iterations=6)
for (y0, y1), dy in [((362, 462), -886), ((462, 562), -283), ((596, 660), -150),
                     ((686, 757), -569), ((945, 1035), -309), ((1035, 1128), 290)]:   # each from a clean spine area
    part = mS_d.copy(); part[:y0] = False; part[y1:] = False
    ik2 = regrain(ik2, part, (dy, 0))
ink = fit_ink(text_ink('שידוכים ונישואין'), 440, 60);  place_center(ik2, gold_rgba(ink, lightK, darkK), 832, 832)
ink = fit_ink(dot_ink(), 13, 13);                       place_center(ik2, gold_rgba(ink, lightK, darkK), 832, 911)
ink = fit_ink(text_ink('לקט שיחות ומכתבים'), 235, 30); place_center(ik2, gold_rgba(ink, lightK, darkK), 832, 963)
ink = fit_ink(text_ink('מכ"ק אדמו"ר מליובאוויטש', FR_MED), 290, 28); place_center(ik2, gold_rgba(ink, lightK, darkK), 832, 1019)
# spine
ink = fit_ink(text_ink('שידוכים'), 104, 44);  place_center(ik2, gold_rgba(ink, lightK, darkK), 1368, 422)
ink = fit_ink(text_ink('ונישואין'), 104, 44); place_center(ik2, gold_rgba(ink, lightK, darkK), 1368, 490)
ink = fit_ink(dot_ink(), 18, 18); place_center(ik2, gold_rgba(ink, lightK, darkK), 1370, 626)
ik2.save(D + 'build/src_shidduchim.png')
print('saved shidduchim')
