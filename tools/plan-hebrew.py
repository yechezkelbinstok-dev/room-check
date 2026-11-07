#!/usr/bin/env python3
"""
beds.png in Hebrew, and nothing else: every room's plan with each bochur's Hebrew name on his bed.
For sending to someone without the app or the site.

Drawing, parsing and capture all come from plan-poster.py, so the two pictures cannot disagree.

    python3 tools/plan-hebrew.py            -> dist/beds-he.png
"""
import html
import importlib.util
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("plan_poster", HERE / "plan-poster.py")
pp = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pp)

pp.OUT_PNG = pp.ROOT / "dist/beds-he.png"
pp.OUT_HTML = pp.OUT_HTML.with_name("roomcheck-beds-he.html")


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
        sh = bed["h"] / len(bed["slots"])
        for i, pid in enumerate(bed["slots"]):
            p = people[pid]
            sy = by + i * sh
            if i:
                parts.append(
                    f'<line x1="{bx + 1.5}" y1="{sy}" x2="{bx + bed["w"] - 1.5}" y2="{sy}" '
                    f'stroke="{pp.SEP}" stroke-width="0.5"/>'
                )
            parts.append(
                f'<foreignObject x="{bx}" y="{sy}" width="{bed["w"]}" height="{sh}">'
                f'<div xmlns="http://www.w3.org/1999/xhtml" class="slot">'
                f'<div class="last">{html.escape(p["hebLast"])}</div>'
                f'<div class="first">{html.escape(p["hebFirst"])}</div>'
                "</div></foreignObject>"
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
            cells.append(
                f'<section class="cell"><h2>{html.escape(room["heb"])}</h2>'
                f'{room_svg(room, people, cell_w, row_h)}</section>'
            )
    return f"""<!doctype html><html lang="he"><head><meta charset="utf-8">
<title>Beds</title><style>
  * {{ box-sizing: border-box; margin: 0; }}
  body {{ width: max-content; background: #fff; padding: {pp.PAD_T}px {pp.PAD_X}px {pp.PAD_B}px;
         font-family: "DejaVu Sans", sans-serif; color: {pp.INK}; }}
  /* RTL on the grid, not the body: an RTL body pushes the page to the far edge of the window */
  .grid {{ direction: rtl; display: grid; grid-template-columns: repeat({pp.COLS}, max-content);
           gap: {pp.GAP_Y}px {pp.GAP_X}px; }}
  .cell {{ border: 1px solid {pp.SEP}; border-radius: 10px; padding: 10px 10px 12px; }}
  h2 {{ font-size: 20px; padding: 0 3px; height: 30px; }}
  .plan {{ display: block; margin-top: 6px; direction: ltr; }}
  .slot {{ direction: rtl; height: 100%; display: flex; flex-direction: column;
           align-items: center; justify-content: center; text-align: center; line-height: 1.15;
           padding: 0 1px; overflow: hidden; font-family: "DejaVu Sans", sans-serif; }}
  .last {{ font-size: 5.2px; font-weight: bold; }}
  .first {{ font-size: 4.2px; color: {pp.SUB}; margin-top: .6px; }}
</style></head><body>
  <div class="grid">{"".join(cells)}</div>
  <script>
    var g = document.querySelector(".grid").getBoundingClientRect();
    document.body.dataset.size =
      Math.ceil(g.right + {pp.PAD_X}) + "x" + Math.ceil(g.bottom + {pp.PAD_B});
  </script>
</body></html>"""


pp.build = build

if __name__ == "__main__":
    pp.main()
