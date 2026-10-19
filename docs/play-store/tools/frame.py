# Play Store frame: teak canvas, Iowan caption, the raw screenshot inset with device-like corners.
import sys, json
from PIL import Image, ImageDraw, ImageFont, ImageFilter
W, H = 1080, 1920  # 9:16, Play's promotion size (the raw 1080x2424 is over the 2:1 limit)
TEAK, IVORY, AMBER = (51, 37, 27), (247, 235, 220), (224, 164, 88)
SCALE, TOP, RADIUS, MARGIN = 0.72, 436, 44, 92
font = ImageFont.truetype("/System/Library/Fonts/Supplemental/Iowan Old Style.ttc", 74, index=0)

def frame(src, dst, caption):
    shot = Image.open(src).convert("RGB")
    sw, sh = int(shot.width * SCALE), int(shot.height * SCALE)
    shot = shot.resize((sw, sh), Image.LANCZOS)
    x, y = (W - sw) // 2, TOP  # the screen bleeds off the bottom edge
    canvas = Image.new("RGB", (W, H), TEAK)
    # soft shadow under the screen
    sh_layer = Image.new("L", (W, H), 0)
    ImageDraw.Draw(sh_layer).rounded_rectangle((x, y + 18, x + sw, y + sh + 18), RADIUS, fill=120)
    sh_layer = sh_layer.filter(ImageFilter.GaussianBlur(28))
    canvas.paste(Image.new("RGB", (W, H), (28, 20, 14)), (0, 0), sh_layer)
    # rounded screen, supersampled mask
    m = Image.new("L", (sw * 4, sh * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, sw * 4 - 1, sh * 4 - 1), RADIUS * 4, fill=255)
    canvas.paste(shot, (x, y), m.resize((sw, sh), Image.LANCZOS))
    # meru bead mark: a ring and three beads, echoing the app's mala ring
    ss = 4; mark = Image.new("RGBA", (300 * ss, 60 * ss), (0, 0, 0, 0)); d = ImageDraw.Draw(mark)
    cx, cy = 22 * ss, 30 * ss
    d.ellipse((cx - 17 * ss, cy - 17 * ss, cx + 17 * ss, cy + 17 * ss), outline=AMBER, width=7 * ss)
    for i, r in enumerate([7, 7, 7]):
        bx = cx + (46 + i * 30) * ss
        d.ellipse((bx - r * ss, cy - r * ss, bx + r * ss, cy + r * ss), fill=AMBER + (255 - i * 70,))
    m2 = mark.resize((300, 60), Image.LANCZOS); canvas.paste(m2, (MARGIN - 6, 92), m2)
    # caption: left aligned, wrapped to two lines max
    lines = caption.split("\n")
    assert len(lines) <= 2 and all(font.getlength(l) <= W - 2 * MARGIN for l in lines), caption
    d = ImageDraw.Draw(canvas); lh = 88
    top = 168 + (lh // 2 if len(lines) == 1 else 0)
    for i, ln in enumerate(lines):
        d.text((MARGIN, top + i * lh), ln, font=font, fill=IVORY)
    canvas.save(dst, optimize=True)

for src, dst, cap in json.load(open(sys.argv[1])):
    frame(src, dst, cap)
