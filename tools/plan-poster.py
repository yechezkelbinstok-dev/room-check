#!/usr/bin/env python3
"""
Every room's beds on one page, drawn from Roster.kt so the sheet cannot drift from the app.

Rooms are all drawn at ONE scale - a bed is the same size on every plan - so the page reads as
eight rooms of one building rather than eight unrelated diagrams. Each room sits centred in a cell
big enough for the largest room, which is what keeps that shared scale and still lines the grid up.

    python3 tools/plan-poster.py            -> dist/beds.png
"""
import html
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parent.parent
ROSTER = ROOT / "android/app/src/main/kotlin/com/roomcheck/app/data/Roster.kt"
OUT_PNG = ROOT / "dist/beds.png"
OUT_HTML = Path(tempfile.gettempdir()) / "roomcheck-beds.html"  # only ever the browser's input

MARGIN = 5.0      # floor outside the walls, so a door box has somewhere to sit
GAP = 7.0         # half the doorway width, in room units
DOOR_D = 4.0      # how far the door box juts out past the wall
SCALE = 3.6       # px per room unit - the one scale every room is drawn at
COLS = 2
DSF = 3           # device scale factor - what makes the PNG print-sharp
SLACK = 300       # extra window past the page's own measurement, cropped off after the shot

WALL = "#24262D"
FLOOR = "#FBFBFA"
INK = "#111114"
SUB = "#8A8A8E"
SEP = "#E4E4E9"


def parse_roster(text):
    people = {}
    for pid, first, last, hf, hl in re.findall(
        r'Person\("(\w+)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"\)', text
    ):
        people[pid] = dict(first=first, last=last, hebFirst=hf, hebLast=hl)

    rooms = []
    plan = text.split("val PLAN", 1)[1]
    for chunk in plan.split("Room(")[1:]:
        head = re.match(
            r'"(\w+)",\s*"([^"]*)",\s*"([^"]*)",\s*([\d.]+)f,\s*([\d.]+)f,\s*'
            r"Door\(DoorWall\.(\w+),\s*([\d.]+)f\)",
            chunk,
        )
        if not head:
            continue
        rid, label, heb, w, h, wall, pos = head.groups()
        beds = []
        for kind, x, y, slots in re.findall(
            r'\b(upright|across)\(([\d.]+)f,\s*([\d.]+)f,\s*((?:"\w+"(?:,\s*)?)+)\)', chunk
        ):
            pids = re.findall(r'"(\w+)"', slots)
            bw, bh = (32.0, 60.0) if kind == "upright" else (60.0, 32.0)
            beds.append(dict(x=float(x), y=float(y), w=bw, h=bh, slots=pids))
        rooms.append(
            dict(id=rid, label=label, heb=heb, w=float(w), h=float(h),
                 door=dict(wall=wall.lower(), pos=float(pos)), beds=beds)
        )
    return people, rooms


def wall_path(room):
    """The walls as one SVG path: full runs on three sides, and the fourth split round the door."""
    w, h, door = room["w"], room["h"], room["door"]
    left, top = MARGIN, MARGIN
    right, bottom = MARGIN + w, MARGIN + h
    d = door["wall"]
    if d in ("top", "bottom"):
        a, b = MARGIN + door["pos"] - GAP, MARGIN + door["pos"] + GAP
    else:
        a, b = MARGIN + door["pos"] - GAP, MARGIN + door["pos"] + GAP

    seg = []
    if d != "top":
        seg.append(f"M{left} {top}H{right}")
    if d != "bottom":
        seg.append(f"M{left} {bottom}H{right}")
    if d != "left":
        seg.append(f"M{left} {top}V{bottom}")
    if d != "right":
        seg.append(f"M{right} {top}V{bottom}")

    if d == "bottom":
        out = bottom + DOOR_D
        seg += [f"M{left} {bottom}H{a}", f"M{b} {bottom}H{right}",
                f"M{a} {bottom}V{out}H{b}V{bottom}"]
    elif d == "top":
        out = top - DOOR_D
        seg += [f"M{left} {top}H{a}", f"M{b} {top}H{right}",
                f"M{a} {top}V{out}H{b}V{top}"]
    elif d == "left":
        out = left - DOOR_D
        seg += [f"M{left} {top}V{a}", f"M{left} {b}V{bottom}",
                f"M{left} {a}H{out}V{b}H{left}"]
    else:
        out = right + DOOR_D
        seg += [f"M{right} {top}V{a}", f"M{right} {b}V{bottom}",
                f"M{right} {a}H{out}V{b}H{right}"]
    return " ".join(seg)


def room_svg(room, people, cell_w, row_h):
    """One room, centred in its cell. The cell is as wide as the widest room in the building and as
    tall as the deepest room in ITS OWN ROW, so the grid still lines up but a shallow room is not
    padded out with the depth of one three rows away."""
    total_w, total_h = room["w"] + 2 * MARGIN, room["h"] + 2 * MARGIN
    ox, oy = (cell_w - total_w) / 2, (row_h - total_h) / 2
    parts = [
        f'<svg class="plan" viewBox="0 0 {cell_w} {row_h}" '
        f'width="{cell_w * SCALE}" height="{row_h * SCALE}">',
        f'<g transform="translate({ox} {oy})">',
        f'<rect x="{MARGIN}" y="{MARGIN}" width="{room["w"]}" height="{room["h"]}" fill="{FLOOR}"/>',
        f'<path d="{wall_path(room)}" stroke="{WALL}" stroke-width="1.6" fill="none" '
        f'stroke-linecap="square"/>',
    ]
    for bed in room["beds"]:
        bx, by = MARGIN + bed["x"], MARGIN + bed["y"]
        parts.append(
            f'<rect x="{bx}" y="{by}" width="{bed["w"]}" height="{bed["h"]}" rx="1.8" '
            f'fill="#fff" stroke="#C9CBD2" stroke-width="0.7"/>'
        )
        n = len(bed["slots"])
        # A bunk stacks its people top over bottom - it never splits the width - exactly as the app
        # draws it, so a name always keeps the bed's full width to sit on.
        for i, pid in enumerate(bed["slots"]):
            p = people[pid]
            sh = bed["h"] / n
            sy = by + i * sh
            if i:
                parts.append(
                    f'<line x1="{bx + 1.5}" y1="{sy}" x2="{bx + bed["w"] - 1.5}" y2="{sy}" '
                    f'stroke="{SEP}" stroke-width="0.5"/>'
                )
            tag = "" if n == 1 else ("top" if i == 0 else "bottom")
            parts.append(
                f'<foreignObject x="{bx}" y="{sy}" width="{bed["w"]}" height="{sh}">'
                f'<div xmlns="http://www.w3.org/1999/xhtml" class="slot">'
                f'<div class="last">{html.escape(p["last"])}</div>'
                f'<div class="first">{html.escape(p["first"])}</div>'
                + (f'<div class="bunk">{tag}</div>' if tag else "")
                + "</div></foreignObject>"
            )
    parts += ["</g>", "</svg>"]
    return "".join(parts)


# The page is laid out to an exact pixel height so the headless capture ends on the last room
# instead of trailing a screenful of white.
PAD_T, PAD_X, PAD_B = 30, 34, 34
TITLE_H, GRID_TOP = 30, 18
HEAD_H, PLAN_TOP = 30, 6
CELL_PAD_T, CELL_PAD_B, CELL_BORDER = 10, 12, 1
GAP_Y, GAP_X = 22, 26
CELL_CHROME = 2 * CELL_BORDER + CELL_PAD_T + HEAD_H + PLAN_TOP + CELL_PAD_B


def build(people, rooms):
    cell_w = max(r["w"] for r in rooms) + 2 * MARGIN
    rows = [rooms[i:i + COLS] for i in range(0, len(rooms), COLS)]
    cells, page_h = [], PAD_T + TITLE_H + GRID_TOP + PAD_B + (len(rows) - 1) * GAP_Y
    for row in rows:
        row_h = max(r["h"] for r in row) + 2 * MARGIN
        page_h += round(row_h * SCALE) + CELL_CHROME
        for room in row:
            beds = sum(len(b["slots"]) for b in room["beds"])
            cells.append(
                f'<section class="cell"><header><h2>{html.escape(room["label"])}</h2>'
                f'<span class="heb">{html.escape(room["heb"])}</span>'
                f'<span class="n">{beds}</span></header>'
                f'{room_svg(room, people, cell_w, row_h)}</section>'
            )
    return f"""<!doctype html><html><head><meta charset="utf-8">
<title>Beds</title><style>
  * {{ box-sizing: border-box; margin: 0; }}
  body {{ width: max-content; background: #fff; padding: {PAD_T}px {PAD_X}px {PAD_B}px;
         font-family: "DejaVu Sans", sans-serif; color: {INK}; }}
  h1 {{ font-size: 25px; line-height: {TITLE_H}px; height: {TITLE_H}px; letter-spacing: -.3px; }}
  .grid {{ display: grid; grid-template-columns: repeat({COLS}, max-content);
           gap: {GAP_Y}px {GAP_X}px; margin-top: {GRID_TOP}px; }}
  .cell {{ border: {CELL_BORDER}px solid {SEP}; border-radius: 10px;
           padding: {CELL_PAD_T}px 10px {CELL_PAD_B}px; }}
  header {{ display: flex; align-items: baseline; gap: 9px; padding: 0 3px;
            height: {HEAD_H}px; }}
  h2 {{ font-size: 17px; }}
  .heb {{ font-size: 14px; color: {SUB}; }}
  .n {{ margin-left: auto; font-size: 12.5px; color: {SUB}; }}
  .plan {{ display: block; margin-top: {PLAN_TOP}px; }}
  .slot {{ height: 100%; display: flex; flex-direction: column; align-items: center;
           justify-content: center; text-align: center; line-height: 1.12;
           font-family: "DejaVu Sans", sans-serif; padding: 0 1px; overflow: hidden; }}
  .last {{ font-size: 4.6px; font-weight: bold; }}
  .first {{ font-size: 3.6px; color: {SUB}; margin-top: .5px; }}
  .bunk {{ font-size: 2.9px; color: #B4B4B8; margin-top: .8px;
           text-transform: uppercase; letter-spacing: .3px; }}
</style></head><body>
  <h1>Room Check</h1>
  <div class="grid">{"".join(cells)}</div>
  <script>
    // The capture window has to be the page's real size, so the page measures itself rather than
    // the script guessing at padding and rounding - a guess short by a row silently crops it off.
    var g = document.querySelector(".grid").getBoundingClientRect();
    document.body.dataset.size =
      Math.ceil(g.right + {PAD_X}) + "x" + Math.ceil(g.bottom + {PAD_B});
  </script>
</body></html>"""


def trim(path):
    """Crop away the blank slack, keeping the page's own padding as the margin."""
    with Image.open(path) as im:
        im = im.convert("RGB")
        box = ImageChops.difference(im, Image.new("RGB", im.size, "white")).getbbox()
        if not box:
            sys.exit("blank page")
        right, bottom = box[2] + PAD_X * DSF, box[3] + PAD_B * DSF
        if right > im.width or bottom > im.height:
            sys.exit("content ran to the edge - raise SLACK")
        im.crop((0, 0, right, bottom)).save(path)


def main():
    people, rooms = parse_roster(ROSTER.read_text(encoding="utf-8"))
    if len(rooms) != 8 or sum(len(b["slots"]) for r in rooms for b in r["beds"]) != len(people):
        sys.exit(f"parsed {len(rooms)} rooms / {len(people)} people - roster shape changed")
    OUT_PNG.parent.mkdir(parents=True, exist_ok=True)
    OUT_HTML.write_text(build(people, rooms), encoding="utf-8")

    def chrome(*args):
        return subprocess.run(
            ["/opt/pw-browsers/chromium", "--headless", "--no-sandbox", "--hide-scrollbars",
             "--force-device-scale-factor=3", *args, OUT_HTML.as_uri()],
            check=True, capture_output=True, text=True,
        ).stdout

    size = re.search(r'data-size="(\d+)x(\d+)"', chrome("--dump-dom", "--window-size=4000,4000"))
    if not size:
        sys.exit("page did not report its size")
    # Shot with slack and then trimmed back to the ink: a window sized to the page's own numbers
    # comes up a little short and crops the bottom row, and no arithmetic here is worth trusting
    # over what actually landed in the pixels.
    page_w, page_h = (int(v) + SLACK for v in size.groups())
    chrome(f"--window-size={page_w},{page_h}", f"--screenshot={OUT_PNG}")
    trim(OUT_PNG)
    with Image.open(OUT_PNG) as im:
        print(f"{OUT_PNG}  {im.width}x{im.height}  ({OUT_PNG.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
