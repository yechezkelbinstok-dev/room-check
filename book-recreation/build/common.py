from PIL import Image, ImageDraw, ImageFilter, ImageChops
import numpy as np
import os
D = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + '/'
def load_rgba(path, white_bg=False, thr=245):
    im=Image.open(path)
    if im.mode=='RGBA' and not white_bg:
        return im
    rgb=np.array(im.convert('RGB')).astype(np.int16)
    near=(rgb.min(axis=2)>=thr).astype(np.uint8)*255
    m=Image.fromarray(near).copy()  # 255 = near white
    # flood fill from borders: mark background as 128
    W,H=m.size
    for seed in [(0,0),(W-1,0),(0,H-1),(W-1,H-1)]+[(x,0) for x in range(0,W,50)]+[(x,H-1) for x in range(0,W,50)]+[(0,y) for y in range(0,H,50)]+[(W-1,y) for y in range(0,H,50)]:
        if m.getpixel(seed)==255: ImageDraw.floodfill(m,seed,128)
    a=np.array(m); alpha=np.where(a==128,0,255).astype(np.uint8)
    al=Image.fromarray(alpha).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
    out=im.convert('RGB').convert('RGBA'); out.putalpha(al); return out
def bbox(im, t=8):
    a=np.array(im.split()[-1]); ys,xs=np.where(a>t); return (xs.min(),ys.min(),xs.max()+1,ys.max()+1)
