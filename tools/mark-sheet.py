#!/usr/bin/env python3
"""
A Hebrew sheet to mark a round on without the app or the site: every room's beds, the same plans
as beds.png, with an empty circle beside each name. Send the picture, the other person draws a
tick or a cross in each circle with their phone's markup tool (or on paper), and sends it back.

One sheet per round - the date and time go in the blanks at the top.

Drawing, parsing and capture all come from plan-poster.py, so the two pictures cannot disagree.

    python3 tools/mark-sheet.py             -> dist/mark-sheet.png
"""
import html
import importlib.util
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("plan_poster", HERE / "plan-poster.py")
pp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pp)

pp.OUT_PNG = pp.ROOT / "dist/mark-sheet.png"
pp.OUT_HTML = pp.OUT_HTML.with_name("roomcheck-mark-sheet.html")

RING = "#3A3D46"


def room_svg(room, people, cell_w, row_h):
    total_w, total_h = room["w"] + 2 * pp.MARGIN, room["h"] + 2 * pp.MARGIN
    ox, oy = (cell_w - total_w) / 2, (row_h - total_h) / 2
    parts = [
        f'<svg class="plan" viewBox="0 0 {cell_w} {row_h}" '
        f'width="{cell_w * pp.SCALE}" height="{row_h * pp.SCALE}">',
        f'<g transform="translate({ox} {oy})">',
        f'<rect x="{pp.MARGIN}" y="{pp.MARGIN}" width="{room["w"]}" height="{room["h"]}" '
        f'fill="{pp.FLOOR}"/>',
        f'<path d="{pp.wall_path(room)}" stroke="{pp.WALL}" stroke-width="1.6" fill="none" '
        f'stroke-linecap="square"/>',
    ]
    for bed in room["beds"]:
        bx, by = pp.MARGIN + bed["x"], pp.MARGIN + bed["y"]
        parts.append(
            f'<rect x="{bx}" y="{by}" width="{bed["w"]}" height="{bed["h"]}" rx="1.8" '
            f'fill="#fff" stroke="#C9CBD2" stroke-width="0.7"/>'
        )
        n = len(bed["slots"])
        sh = bed["h"] / n
        # a wide, shallow slot puts its circle beside the name; a tall one puts it underneath
        wide = bed["w"] > sh * 1.4
        for i, pid in enumerate(bed["slots"]):
            p = people[pid]
            sy = by + i * sh
            if i:
                parts.append(
                    f'<line x1="{bx + 1.5}" y1="{sy}" x2="{bx + bed["w"] - 1.5}" y2="{sy}" '
                    f'stroke="{pp.SEP}" stroke-width="0.5"/>'
                )
            tag = "" if n == 1 else ("עליון" if i == 0 else "תחתון")
            parts.append(
                f'<foreignObject x="{bx}" y="{sy}" width="{bed["w"]}" height="{sh}">'
                f'<div xmlns="http://www.w3.org/1999/xhtml" class="slot{" wide" if wide else ""}">'
                f'<div class="ring"></div><div class="names">'
                f'<div class="last">{html.escape(p["hebLast"])}</div>'
                f'<div class="first">{html.escape(p["hebFirst"])}</div>'
                + (f'<div class="bunk">{tag}</div>' if tag else "")
                + "</div></div></foreignObject>"
            )
    parts += ["</g>", "</svg>"]
    return "".join(parts)


def build(people, rooms):
    cell_w = max(r["w"] for r in rooms) + 2 * pp.MARGIN
    rows = [rooms[i:i + pp.COLS] for i in range(0, len(rooms), pp.COLS)]
    cells = []
    for row in rows:
        row_h = max(r["h"] for r in row) + 2 * pp.MARGIN
        for room in row:
            beds = sum(len(b["slots"]) for b in room["beds"])
            cells.append(
                f'<section class="cell"><header><h2>{html.escape(room["heb"])}</h2>'
                f'<span class="n">{beds} בחורים</span></header>'
                f'{room_svg(room, people, cell_w, row_h)}</section>'
            )
    ink, sub, sep = pp.INK, pp.SUB, pp.SEP
    return f"""<!doctype html><html lang="he"><head><meta charset="utf-8">
<title>Mark sheet</title><style>
  * {{ box-sizing: border-box; margin: 0; }}
  body {{ width: max-content; background: #fff; padding: {pp.PAD_T}px {pp.PAD_X}px {pp.PAD_B}px;
         font-family: "DejaVu Sans", sans-serif; color: {ink}; }}
  .page {{ direction: rtl; width: max-content; }}
  .top {{ display: flex; align-items: baseline; gap: 28px; }}
  h1 {{ font-size: 30px; }}
  .blank {{ font-size: 19px; }}
  .blank span {{ display: inline-block; width: 150px; border-bottom: 1.5px solid {ink}; }}
  .key {{ margin-top: 12px; font-size: 17px; color: #45474E; display: flex; gap: 26px; }}
  .key b {{ font-size: 21px; }}
  .ok {{ color: #1C7C54; }} .no {{ color: #C0392B; }}
  .grid {{ display: grid; grid-template-columns: repeat({pp.COLS}, max-content);
           gap: {pp.GAP_Y}px {pp.GAP_X}px; margin-top: 20px; }}
  .cell {{ border: 1px solid {sep}; border-radius: 10px; padding: 10px 10px 12px; }}
  header {{ display: flex; align-items: baseline; gap: 9px; padding: 0 3px; height: 30px; }}
  h2 {{ font-size: 20px; }}
  .n {{ margin-right: auto; font-size: 13px; color: {sub}; }}
  .plan {{ display: block; margin-top: 6px; direction: ltr; }}
  .slot {{ direction: rtl; height: 100%; display: flex; flex-direction: column-reverse;
           align-items: center; justify-content: center; gap: 1.6px; text-align: center;
           line-height: 1.12; font-family: "DejaVu Sans", sans-serif; padding: 0 1px;
           overflow: hidden; }}
  .slot.wide {{ flex-direction: row; gap: 3px; }}
  .ring {{ flex: none; width: 11px; height: 11px; border-radius: 50%;
           border: .6px solid {RING}; background: #fff; }}
  .last {{ font-size: 4.7px; font-weight: bold; }}
  .first {{ font-size: 3.9px; color: {sub}; margin-top: .5px; }}
  .bunk {{ font-size: 3px; color: #A4A4A8; margin-top: .6px; }}
</style></head><body><div class="page">
  <div class="top"><h1>בדיקת חדרים</h1>
    <div class="blank">תאריך: <span></span></div>
    <div class="blank">שעה: <span></span></div></div>
  <div class="key"><span><b class="ok">✓</b> בחדר</span><span><b class="no">✗</b> לא בחדר</span>
    <span>עיגול אחד לכל בחור · דף אחד לכל סיבוב</span></div>
  <div class="grid">{"".join(cells)}</div></div>
  <script>
    var g = document.querySelector(".grid").getBoundingClientRect();
    document.body.dataset.size =
      Math.ceil(g.right + {pp.PAD_X}) + "x" + Math.ceil(g.bottom + {pp.PAD_B});
  </script>
</body></html>"""


pp.build = build

if __name__ == "__main__":
    pp.main()
