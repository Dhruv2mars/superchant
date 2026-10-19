"""cmp.py A_DIR B_DIR [keys...] : side-by-side medians"""
import json, sys
a=json.load(open(sys.argv[1]+"/metrics.json"))["median"]; b=json.load(open(sys.argv[2]+"/metrics.json"))["median"]
keys=sys.argv[3:] or sorted(set(a)|set(b))
for k in keys:
    if k in a or k in b:
        va=a.get(k); vb=b.get(k)
        d = f"{(vb-va)/va*100:+6.1f}%" if va not in (None,0) and vb is not None else ""
        print(f"{k:34s} {va if va is not None else '-':>12} {vb if vb is not None else '-':>12} {d}")
