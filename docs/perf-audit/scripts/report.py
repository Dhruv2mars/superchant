"""report.py : before/after tables (markdown) from the interleaved A/B data in ~/perf-superchant"""
import json, os, re, glob, statistics as st
R=os.path.expanduser('~/perf-superchant')
out=[]
def fmt(v):
    if v is None: return '–'
    if abs(v)>=1000: return f'{v:,.0f}'
    if abs(v)>=100: return f'{v:.0f}'
    if abs(v)>=10: return f'{v:.1f}'
    return f'{v:.2f}'
def delta(a,b):
    if a in (None,0) or b is None: return ''
    return f'{(b-a)/a*100:+.0f}%'

# Startup
out.append('### Startup (am start -W; medians, A/B interleaved in alternating blocks)\n')
out.append('| Compilation | Data | Mode | Before TTID | After TTID | Δ | Before TTFD | After TTFD | Δ | runs (A/B) |')
out.append('|---|---|---|---|---|---|---|---|---|---|')
rows=[]
for comp in ['fresh','profile']:
    for data in ['d0','d10']:
        for mode in ['cold','warm','hot']:
            f=f'{R}/final/startup_{comp}_{data}_{mode}.txt'
            if not os.path.exists(f): continue
            s=open(f).read()
            def med(side,k):
                m=re.search(rf'^{side} {k} median=([\d.]+) n=\s*(\d+)',s,re.M)
                return (float(m.group(1)),int(m.group(2))) if m and m.group(1) else (None,0)
            (at,an),(bt,bn)=med('A','TotalTime'),med('B','TotalTime')
            (af,_),(bf,_)=med('A','FullyDrawn'),med('B','FullyDrawn')
            label={'fresh':'store install (baseline profile only)','profile':'mature (profile + JIT data)'}[comp]
            dl={'d0':'empty','d10':'10 years (10,432 sessions)'}[data]
            out.append(f'| {label} | {dl} | {mode} | {fmt(at)} | {fmt(bt)} | {delta(at,bt)} | {fmt(af) if mode!="hot" else "–"} | {fmt(bf) if mode!="hot" else "–"} | {delta(af,bf) if mode!="hot" else ""} | {an}/{bn} |')
out.append('')
sel={
 'tap':['SC.countToDraw.p50','SC.countToDraw.p90','SC.increment.p50','SC.increment.p90','cpu.app_main_ms','main.binder.ms','main.binder.n','composeRecompose.n2','semantics.n','semantics.ms','doFrame.n','gc.n','gc.ms','SC.write.n','SC.write.p50','cpu.app_ms','cpu.systemui_ms','cpu.system_server_ms'],
 'vol':['SC.countToDraw.p50','SC.countToDraw.p90','SC.countToDraw.p99','SC.onKeyEvent.p90','SC.increment.p50','SC.increment.p90','cpu.app_main_ms','main.binder.ms','main.binder.n','doFrame.n','gc.n','gc.ms','SC.write.n','SC.write.p50','cpu.app_ms','cpu.systemui_ms','cpu.system_server_ms','io.write_bytes'],
 'toggle':['cpu.app_main_ms','doFrame.n','doFrame.p50','doFrame.p90','main.long_ms','recompose.n'],
 'nav':['cpu.app_main_ms','main.long_ms','main.long_n','main.max_slice_ms','doFrame.p90','main.binder.ms','gc.n','gc.ms'],
 'history':['cpu.app_main_ms','main.long_ms','main.max_slice_ms','doFrame.n','doFrame.p50','doFrame.p90','gc.n'],
 'idle':['SC.increment.n','SC.increment.p50','SC.countToDraw.p50','cpu.app_ms','cpu.app_main_ms','main.binder.ms','cpu.systemui_ms','cpu.system_server_ms','io.write_bytes','SC.write.n'],
 'import':['cpu.app_main_ms','main.long_ms','main.long_n','main.max_slice_ms','main.binder.ms'],
}
desc={'tap':'Touch counting, ~10 taps/s for 30 s (debouncer allows ≤6.7/s), 10-year data',
 'vol':'Volume counting via uinput, 10 presses/s for 30 s','toggle':'Touch-counting toggle ×10',
 'nav':'Home → Goals/History/Settings and back ×3 (10-year data)','history':'History: 6 flings down, 6 up (10-year data)',
 'idle':'Session at chanting pace: 1 volume press/s for 55 s (60 s trace)','import':'Import the 10-year backup (1.9 MB) into a fresh install'}
for sc,keys in sel.items():
    a=f'{R}/fbase/{sc}/metrics.json'; b=f'{R}/ffinal/{sc}/metrics.json'
    if not (os.path.exists(a) and os.path.exists(b)): continue
    A=json.load(open(a)); B=json.load(open(b)); ma=A['median']; mb=B['median']
    out.append(f'### {sc}: {desc[sc]} (medians of {len(A["reps"])} interleaved runs each)\n')
    out.append('| Metric | Before | After | Δ |'); out.append('|---|---|---|---|')
    for k in keys:
        if k in ma or k in mb: out.append(f'| `{k}` | {fmt(ma.get(k))} | {fmt(mb.get(k))} | {delta(ma.get(k),mb.get(k))} |')
    out.append('')
open(f'{R}/final/tables.md','w').write('\n'.join(out))
print('\n'.join(out))
