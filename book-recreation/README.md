# Chabad seforim composite: high-res recreation

Recreation of `rebuild/uploads/original_composite.jpg` (568×356, a 2015 Chabad Shop sale ad) at ×4, Chabad seforim only.

## What's here
| Path | What |
|---|---|
| `final_transparent.png` | **The final image.** 2364×1305, transparent background; soft shadows kept semi-transparent |
| `pieces/` | Each set as its own transparent cut-out, exactly as used in the final (scaled, colour-matched) |
| `layout.json` | Position (x, y in the final image), size and stacking order of every piece. Draw in `z_order` (1 = back) to reassemble |
| `rebuild.sh` | Re-renders everything from `rebuild/` (installs Python deps, optional Hebrew font) |
| `rebuild/` | Full-resolution source photos, scripts (`build/build4.py` = the composite, `reletter.py` = the 2 re-lettered covers, `register.py` = photo alignment), `build/reg.json` placements |

## The pieces
The suffix in each file name says how close it is to the original.
| # | Piece | Status | What would fix it (Hebrew search name) |
|---|---|---|---|
| 01 | 2 dark volumes right of the brown Melukat | STANDIN (darkened Melukat spines) | Unknown title |
| 02 | Sefer HaMaamarim Melukat, brown 6 vol | **EXACT** photo | n/a |
| 03 | Black set next to the white box | STANDIN (same title, newer printing) | `ביאורים לפירוש רש"י` |
| 04 | White slipcase with small black label | DRAWN | Unknown; maybe the box of 03, or Hilchos Beis HaBechira |
| 05 | Big maroon book | RELETTERED (Igros Kodesh photo re-lettered; subtitle lines guessed) | `שידוכים ונישואין` (old 1-volume edition) |
| 06 | Likkutei Sichos Moadim, 3 vol, top right | SAME_EDITION (Purim set photo) | `לקוטי שיחות מועדים` |
| 07 | Grey row, middle | STANDIN (Sefer HaSichos 5747–52, real photo re-laid in a row) | `ספר השיחות תשמ"ז-תשנ"ב` (older printing) |
| 08 | Biurim L'Pirkei Avos, green | **EXACT** | n/a |
| 09 | Tehillim Ohel Yosef Yitzchak / Tehillos Menachem | **EXACT** | n/a |
| 10 | Haggadah im Likkutei Taamim, red 2 vol | **EXACT** | n/a |
| 11 | Likkutei Sichos Parshiyos, pocket, brown | **EXACT** | n/a |
| 12 | Likkutei Sichos, regular | **EXACT** | n/a |
| 13 | Brown Chumash, front right | RELETTERED (Tehillim photo recoloured, "חמשה חומשי תורה") | `חומש אהל יוסף יצחק` / brown Kehot Chumash with arch cover |
| 14 | Big black book at front + 2 spines | STANDIN (Sefiras HaOmer cover + Purim spines) | `לקוטי שיחות מועדים ד' פרשיות` |

## Swapping in a better photo
1. Put the photo in `rebuild/comp/` and cut it out with `common.load_rgba(path, white_bg=True)`.
2. Align it to the original with `register.py`: template-match a visible part against `original_composite.jpg` over a range of scales.
3. Add the result to `build/reg.json` and change that piece's line in `build/build4.py`.
4. Run `./rebuild.sh`. It re-renders the final, the pieces and `layout.json`.

## How the composite is built (`build4.py`)
- Coordinates are in original-image pixels. Output is ×4, and the canvas spans x −12…580 so nothing gets cut off.
- Colour/lighting matching: each piece is nudged toward the original's tones at the same spot (`MATCH=1`).
- Edge colours are bled in from each piece's interior, so there are no white rims on a transparent background.

## Known issues and tips
- Piece 05's spine has a faint leftover mark and a slightly pale right edge.
- Piece 13's spine lettering is tiny/approximate.
- esicha.org (the publisher) has original photos but blocks scripted downloads; open it in a browser: `/product/likkutei-sichos-moadim-4-parshiyos-2-vol/`, `/product/sefer-hamaamorim-melukat-set/`, `/product/hilchos-beis-habchira-leharambam-im-chidushim-ubiurim/`.
- Chabad Shop's full catalogue (big images, many already transparent) is open: `https://chabadshop.com/products.json?limit=250&page=N`.
- Google Lens and Bing visual search are captcha-blocked from cloud sessions.
- The user wants Chabad seforim only and real photos over reconstructions. They rejected flat copy-pasted spines. Always check zoomed side-by-sides against the original before delivering.
