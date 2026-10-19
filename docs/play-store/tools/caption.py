# usage: caption.py in.mp4 out.mp4 spec.json   spec: [[start,end,"text"],...] (seconds, video time)
# Adds a caption band above the 1080x2424 recording so nothing on screen is covered.
import json, subprocess, sys, os, tempfile
from PIL import Image, ImageDraw, ImageFont
src, dst, spec = sys.argv[1], sys.argv[2], json.load(open(sys.argv[3]))
W, BAND, BG = 1080, 300, (43, 33, 24)
font = ImageFont.truetype("/System/Library/Fonts/Avenir Next.ttc", 50, index=2)
tmp = tempfile.mkdtemp()
inputs, filters = [], [f"[0:v]scale=1080:2424,pad=1080:{2424+BAND}:0:{BAND}:color=0x2B2118[b]"]
last = "b"
for i, (s, e, text) in enumerate(spec):
    lines, cur = [], ""
    for w in text.split():
        t = (cur + " " + w).strip()
        if font.getlength(t) > W - 120: lines.append(cur); cur = w
        else: cur = t
    lines.append(cur)
    img = Image.new("RGB", (W, BAND), BG); d = ImageDraw.Draw(img); lh = 68
    y0 = BAND / 2 - lh * len(lines) / 2
    for j, ln in enumerate(lines):
        d.text((W / 2, y0 + lh * j + lh / 2), ln, font=font, fill=(255, 238, 214), anchor="mm")
    p = os.path.join(tmp, f"c{i}.png"); img.save(p); inputs += ["-i", p]
    filters.append(f"[{last}][{i+1}:v]overlay=0:0:enable='between(t,{s},{e})'[v{i}]"); last = f"v{i}"
subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", src, *inputs, "-filter_complex", ";".join(filters),
    "-map", f"[{last}]", "-c:v", "libx264", "-preset", "slow", "-crf", "24", "-pix_fmt", "yuv420p",
    "-r", "30", "-movflags", "+faststart", "-an", dst], check=True)
