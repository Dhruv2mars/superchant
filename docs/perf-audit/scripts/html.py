"""html.py : self-contained evidence page (diverging change chart + tables) from tables.md data"""
import json, os, re, html
R=os.path.expanduser('~/perf-superchant')
key=json.load(open(f'{R}/final/key_metrics.json'))   # [{"label":..., "before":..., "after":..., "unit":..., "better":"lower"}]
tables=open(f'{R}/final/tables.md').read()
findings=open(f'{R}/final/findings.md').read()
def md_table_to_html(md):
    out=[]; lines=md.splitlines(); i=0
    while i < len(lines):
        l=lines[i]
        if l.startswith('|') and i+1<len(lines) and re.match(r'^\|[-| ]+\|$', lines[i+1]):
            hdr=[c.strip() for c in l.strip('|').split('|')]; i+=2; rows=[]
            while i<len(lines) and lines[i].startswith('|'):
                rows.append([c.strip() for c in lines[i].strip('|').split('|')]); i+=1
            def cell(c):
                c=html.escape(c); return re.sub(r'`([^`]*)`', r'<code>\1</code>', c)
            out.append('<table><thead><tr>'+''.join(f'<th>{cell(h)}</th>' for h in hdr)+'</tr></thead><tbody>'+''.join('<tr>'+''.join(f'<td>{cell(c)}</td>' for c in r)+'</tr>' for r in rows)+'</tbody></table>')
            continue
        if l.startswith('### '): out.append(f'<h3>{html.escape(l[4:])}</h3>')
        elif l.startswith('## '): out.append(f'<h2>{html.escape(l[3:])}</h2>')
        elif l.strip(): out.append(f'<p>{re.sub(r"`([^`]*)`", r"<code>\\1</code>", html.escape(l))}</p>')
        i+=1
    return '\n'.join(out)
# diverging bars: improvement % (positive = better)
rows=[]
for k in key:
    b,a=k['before'],k['after']; ch=(a-b)/b*100
    imp=-ch if k['better']=='lower' else ch
    rows.append((k['label'],imp,b,a,k['unit']))
W,lab,H=760,300,26
mx=max(abs(r[1]) for r in rows); mx=max(mx,10)
x0=lab+(W-lab)/2; sc=((W-lab)/2-50)/mx
svg=[f'<svg viewBox="0 0 {W} {len(rows)*H+40}" role="img" aria-labelledby="ct"><title id="ct">Improvement per metric, after vs before (positive = better)</title>']
svg.append(f'<line x1="{x0}" y1="10" x2="{x0}" y2="{len(rows)*H+16}" class="axis"/>')
for i,(l,imp,b,a,u) in enumerate(rows):
    y=16+i*H; w=abs(imp)*sc; x=x0 if imp>=0 else x0-w
    cls='good' if imp>=0 else 'bad'
    tip=f'{l}: {b:,.2f} → {a:,.2f} {u} ({"better" if imp>=0 else "worse"} by {abs(imp):.0f}%)'
    svg.append(f'<g class="row"><title>{html.escape(tip)}</title><rect x="0" y="{y-3}" width="{W}" height="{H}" class="hit"/>'
               f'<text x="{lab-8}" y="{y+12}" text-anchor="end" class="lbl">{html.escape(l)}</text>'
               f'<rect x="{x:.1f}" y="{y+2}" width="{max(w,2):.1f}" height="14" rx="4" class="{cls}"/>'
               f'<text x="{(x0+w+6) if imp>=0 else (x0-w-6):.1f}" y="{y+13}" text-anchor="{"start" if imp>=0 else "end"}" class="val">{"+" if imp>=0 else "−"}{abs(imp):.0f}%</text></g>')
svg.append('</svg>')
page=f'''<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>SuperChant performance audit: before vs after</title><style>
:root{{--surface:#fcfcfb;--ink:#0b0b0b;--ink2:#52514e;--grid:#e3e2de;--good:#2a78d6;--bad:#e34948}}
@media (prefers-color-scheme:dark){{:root{{--surface:#1a1a19;--ink:#f0efec;--ink2:#c3c2b7;--grid:#383835;--good:#3987e5;--bad:#e66767}}}}
body{{background:var(--surface);color:var(--ink);font:14px/1.45 system-ui,sans-serif;max-width:960px;margin:24px auto;padding:0 16px}}
h1{{font-size:22px}} h2{{font-size:18px;margin-top:32px}} h3{{font-size:15px;margin-top:22px}}
p{{color:var(--ink2)}} code{{font-size:12px}}
table{{border-collapse:collapse;width:100%;margin:8px 0 16px;font-variant-numeric:tabular-nums}}
th,td{{border-bottom:1px solid var(--grid);padding:4px 8px;text-align:left;vertical-align:top}} th{{color:var(--ink2);font-weight:600}}
svg{{width:100%;height:auto}} .axis{{stroke:var(--ink2);stroke-width:1}} .good{{fill:var(--good)}} .bad{{fill:var(--bad)}}
.lbl,.val{{fill:var(--ink);font-size:12px}} .val{{fill:var(--ink2)}} .hit{{fill:transparent}} .row:hover .hit{{fill:var(--grid);opacity:.5}}
.legend span{{display:inline-flex;align-items:center;gap:6px;margin-right:16px;color:var(--ink2)}} .sw{{width:12px;height:12px;border-radius:3px;display:inline-block}}
</style></head><body>
<h1>SuperChant performance audit: before vs after</h1>
<p>Release builds on the Pixel_9_API_35_A emulator. Before = main (b67af32) plus the same trace instrumentation; after = perf/extreme-audit. Runs alternate A/B so emulator drift hits both alike. Emulator numbers are relative: read the deltas, not the absolutes.</p>
<h2>Change per key metric (positive = better)</h2>
<div class="legend"><span><i class="sw" style="background:var(--good)"></i>Better</span><span><i class="sw" style="background:var(--bad)"></i>Worse</span></div>
{"".join(svg)}
{md_table_to_html(tables)}
{md_table_to_html(findings)}
</body></html>'''
open(f'{R}/final/report.html','w').write(page); print('wrote', f'{R}/final/report.html')
