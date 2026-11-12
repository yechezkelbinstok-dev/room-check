# Handoff: high-res recreation of the Chabad seforim composite

## Goal (what the user wants)
- A high-quality, very close recreation of `uploads/original_composite.jpg` (568×356, a 2015 Chabad Shop / Hei Teves sale ad).
- **Chabad seforim only.** Real product photos wherever possible; stand-ins only when no photo can be found.
- Deliver as a **transparent PNG** (the latest delivery is `output/recreation_v7_transparent.png`).
- The user is impatient. Show results fast, and don't redo work that already matches.

## Current output
| File | What |
|---|---|
| `output/recreation_v7_transparent.png` | **Latest.** 2364×1305 RGBA, soft shadows kept as semi-transparent |
| `output/recreation_v7.png` | Same composite on white, 2368×1424 (canvas spans original x −12..580, ×4) |
| `output/recreation_v6.png` | Previous version: real-photo stand-ins for the Chumash and the maroon book instead of re-lettered covers |

## Rebuild
```bash
pip install opencv-python-headless scipy pillow numpy
apt-get install -y culmus            # Frank Ruehl Hebrew font, only needed for reletter.py
cd book-recreation/build
python3 reletter.py                  # regenerates src_chumash_brown.png + src_shidduchim.png
python3 build4.py 4.0 ../output/recreation_v8.png 1   # args: scale, output (white), tone-match on/off
# -> writes recreation_v8.png and recreation_v8_transparent.png
```
`common.py` sets `D` = the `book-recreation/` folder; all paths are relative to it.

## How build4.py works
1. Coordinates are in **original-image pixels** (568×356); `S=4` is the output scale. `put(img, x, y)` places a piece, `fit()` scales a crop to a width/height, `put_registered(key, img)` uses `build/reg.json`.
2. `build/reg.json` holds template-matched placements (scale or sx/sy, plus top-left x/y in original pixels). They were found with `register.py`: NCC search over scales against the original. Anisotropic `sx/sy` are used for D (Melukat) and E2 (Rashi). Reuse this approach to drop in any new photo.
3. Draw order is back to front: D2, D, E2, E1, H, F, E3, C, G, J, B, K, I, A.
4. **Tone matching** (`MATCH=1`): each object's colour/lighting gets a smooth per-object gain map toward the original at the same spot, weaker on exact photos (0.6) than on stand-ins (0.85). This cut the low-frequency error vs the original from 8.7 to 6.6 per pixel.
5. Edge bleed: object edge colours come from interior pixels, so there are no white rims on transparent output. Sharpening is applied to interior pixels only.

## Set-by-set status (keys used in the code)
| Key | Set (position in original) | Source now | Status |
|---|---|---|---|
| B | Likkutei Sichos Parshiyos, pocket, brown (far left) | `series/bo3.png` (Chabad Shop `Books-only3.png`) | **Exact photo** |
| K | Likkutei Sichos, regular (bottom centre) | `series/bo.png` | **Exact photo** |
| G | Tehillim Ohel Yosef Yitzchak / Tehillos Menachem 5 vol | `comp/tehillim5_0.png` | **Exact photo** |
| C | Biurim L'Pirkei Avos, green | `uploads/user_avos_green.jpg` → `build/src_avos_green.png` | **Exact** (user found it) |
| J | Haggadah im Likkutei Taamim, red 2 vol | `uploads/user_haggadah_red.jpg` → `build/src_haggadah_red.png` | **Exact** (user found it) |
| D | Sefer HaMaamarim Melukat, brown 6 vol (top centre) | `uploads/user_melukat_brown.jpg` → `build/src_melukat_brown.png` | **Exact** (user found it) |
| F | Likkutei Sichos Moadim, 3 vol (top right, black) | `comp/ls_purim3_0.png` (Purim set) | Same edition, different photo |
| A | Big black book at front + 2 spines | Sefiras HaOmer cover (darkened) + Purim spines | Stand-in. Probably **Likkutei Sichos Moadim – Daled Parshiyos (2 vol)** |
| E3 | Grey row in the middle ("red circle") | `cand3/3968737738838_0.png`: Sefer HaSichos 5747–52 (Kehot); spines re-laid in a row, page tops flattened and made silver | Right set (fairly sure), different photo/printing |
| E2 | Black set next to the white box | `comp/rashi5_0.png`: Biurim L'Pirush Rashi | Right title, newer printing |
| E1 | White slipcase with a small black label | Drawn in code | Unknown item |
| D2 | 2 dark books right of the Melukat | Darkened copy of 2 Melukat spines | Unknown item |
| H | Big maroon book (top right) | Igros Kodesh photo **re-lettered** "שידוכים ונישואין" (`reletter.py`) | Reconstruction; the subtitle lines are a guess |
| I | Brown Chumash with arch on the cover, front right | Tehillim photo **recoloured brown and re-lettered** "חמשה חומשי תורה … רש"י" | Reconstruction. Real item likely Kehot's Ohel-Yosef-Yitzchak-style Chumash |

## Fastest ways to improve (in order)
1. **Get real photos for A, H, I, E2, F** (user can supply them; that's how C, J and D were solved). Hebrew search terms:
   `לקוטי שיחות מועדים ד' פרשיות` · `שידוכים ונישואין` (old 1-volume edition) · `חומש אהל יוסף יצחק` / brown Kehot Chumash with arch · `ביאורים לפירוש רש"י` · `ספר השיחות תשמ"ז-תשנ"ב`.
   To drop one in: cut it out (`common.load_rgba(path, white_bg=True)`), register it with `register.py` (template-match a visible sub-rect against the original), add it to `reg.json`, and swap the line in `build4.py`.
2. esicha.org (Vaad L'hafotzas Sichos, the publisher) has original photos, but it rate-limits/blocks scripted downloads. Open in a browser:
   `/product/likkutei-sichos-moadim-4-parshiyos-2-vol/`, `/product/sefer-hamaamorim-melukat-set/`, `/product/hilchos-beis-habchira-leharambam-im-chidushim-ubiurim/` (might be the white box E1).
3. Known small defects:
   - H spine (`reletter.py`, section 2): faint remnants near the old "מתורגמות" (~y 730 in `src_r100`) and a slightly pale rim on the spine's right edge.
   - Spine lettering on I is tiny/approximate.
4. Chabad Shop (Shopify) catalogue JSON is open: `https://chabadshop.com/products.json?limit=250&page=N`. Full-size images, many with transparent backgrounds; the `Books-only*.png` series are the ad's own cut-outs.
5. Google Lens and Bing visual search are captcha-blocked from cloud sessions. Yandex works (`register`-style upload: `yandex.com/images/search?rpt=imageview&format=json`) but returned no exact matches.

## Lessons from this session
- The user rejected flat copy-pasted spines and non-Chabad books. Always compare zoomed crops side by side with the original before sending.
- Measure, don't guess: use the low-frequency diff per region (see `build4.py` tone-matching code) to find the worst areas.
- `load_rgba` flood-fill masks: `Image.fromarray(...)` must be `.copy()`'d or floodfill silently does nothing.
