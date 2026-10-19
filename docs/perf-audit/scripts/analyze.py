"""analyze.py DIR : per-trace metrics for a scenario directory, then medians across reps."""
import sys, glob, re, json, statistics as st
from perfetto.trace_processor import TraceProcessor, TraceProcessorConfig

def pct(xs, p):
    if not xs: return None
    xs = sorted(xs); k = (len(xs)-1)*p/100; f = int(k); c = min(f+1, len(xs)-1)
    return xs[f] + (xs[c]-xs[f])*(k-f)

def q(tp, sql):
    return [r for r in tp.query(sql)]

def analyze(path):
    tp = TraceProcessor(trace=path, config=TraceProcessorConfig(bin_path=None))
    m = {}
    upid = q(tp, "select upid from process where name='com.superchant' order by upid desc limit 1")
    if not upid: return m
    upid = upid[0].upid
    mt = q(tp, f"select utid from thread where upid={upid} and is_main_thread=1")
    main = mt[0].utid if mt else None
    def slices(name, thread_only=True):
        if thread_only:
            rows = q(tp, f"select s.dur from slice s join thread_track t on s.track_id=t.id join thread using(utid) where thread.upid={upid} and s.name='{name}'")
        else:
            rows = q(tp, f"select s.dur from slice s join process_track t on s.track_id=t.id where t.upid={upid} and s.name='{name}'")
        return [r.dur/1e6 for r in rows]
    for name in ["SC.increment","SC.onKeyEvent","SC.setState","SC.updateNotification","SC.feedback","SC.notify","SC.ringDraw"]:
        d = slices(name)
        if d:
            m[name+".n"] = len(d); m[name+".p50"] = pct(d,50); m[name+".p90"] = pct(d,90); m[name+".sum"] = sum(d)
    for name in ["SC.countToDraw","SC.write"]:
        d = slices(name, False)
        if d:
            m[name+".n"] = len(d); m[name+".p50"] = pct(d,50); m[name+".p90"] = pct(d,90); m[name+".p99"] = pct(d,99); m[name+".sum"]=sum(d)
    # recomposition and frames on main thread
    for name, key in [("Recomposer:recompose","recompose"),("Choreographer#doFrame%","doFrame"),("Compose:recompose","composeRecompose")]:
        rows = q(tp, f"select s.dur from slice s join thread_track t on s.track_id=t.id where t.utid={main} and s.name like '{name}' and s.depth=0") if main else []
        d=[r.dur/1e6 for r in rows]
        if d: m[key+".n"]=len(d); m[key+".p50"]=pct(d,50); m[key+".p90"]=pct(d,90); m[key+".p99"]=pct(d,99); m[key+".max"]=max(d); m[key+".sum"]=sum(d)
    # Compose accessibility semantics work (on whenever any accessibility service is enabled)
    if main is not None:
        r = q(tp, f"select count(*) n, sum(s.dur) d from slice s join thread_track t on s.track_id=t.id where t.utid={main} and s.name like 'Compose:semantics:%' and s.depth<=2")
        m["semantics.n"]=r[0].n; m["semantics.ms"]=(r[0].d or 0)/1e6
        r = q(tp, f"select count(*) n, sum(s.dur) d from slice s join thread_track t on s.track_id=t.id where t.utid={main} and s.name='Compose:recompose'")
        m["composeRecompose.n2"]=r[0].n
    # GC
    rows = q(tp, f"select s.name, s.dur from slice s join thread_track t on s.track_id=t.id join thread using(utid) where thread.upid={upid} and (s.name like '%GC%' and s.name not like '%GcRoot%') and s.depth=0")
    m["gc.n"] = len(rows); m["gc.ms"] = sum(r.dur for r in rows)/1e6
    # CPU time by process (sched)
    def cpu(where):
        r = q(tp, f"select sum(ss.dur) d from sched_slice ss join thread using(utid) join process using(upid) where {where}")
        return (r[0].d or 0)/1e6
    m["cpu.app_ms"] = cpu(f"process.upid={upid}")
    if main is not None:
        r = q(tp, f"select sum(dur) d from sched_slice where utid={main}"); m["cpu.app_main_ms"]=(r[0].d or 0)/1e6
    m["cpu.systemui_ms"] = cpu("process.name='com.android.systemui'")
    m["cpu.system_server_ms"] = cpu("process.name='system_server'")
    m["cpu.surfaceflinger_ms"] = cpu("process.name like '%surfaceflinger%'")
    # frame timeline
    try:
        rows = q(tp, f"select a.dur, a.jank_type from actual_frame_timeline_slice a where a.upid={upid}")
        d=[r.dur/1e6 for r in rows]
        if d:
            m["frames.n"]=len(d); m["frames.jank"]=sum(1 for r in rows if r.jank_type not in ('None','Buffer Stuffing')); m["frames.p50"]=pct(d,50); m["frames.p90"]=pct(d,90); m["frames.p99"]=pct(d,99)
    except Exception as e:
        pass
    # long main-thread tasks (any top-level slice over a frame)
    if main is not None:
        r = q(tp, f"select max(s.dur) mx, sum(case when s.dur>16e6 then s.dur else 0 end) lng, sum(case when s.dur>16e6 then 1 else 0 end) n from slice s join thread_track t on s.track_id=t.id where t.utid={main} and s.depth=0")
        m["main.max_slice_ms"]=(r[0].mx or 0)/1e6; m["main.long_ms"]=(r[0].lng or 0)/1e6; m["main.long_n"]=r[0].n or 0
    # binder on main thread
    if main is not None:
        r = q(tp, f"select count(*) n, sum(s.dur) d from slice s join thread_track t on s.track_id=t.id where t.utid={main} and s.name='binder transaction'")
        m["main.binder.n"]=r[0].n; m["main.binder.ms"]=(r[0].d or 0)/1e6
    tp.close()
    return m

def gfx(path):
    s=open(path).read(); g={}
    for k,pat in [("gfx.frames",r"Total frames rendered: (\d+)"),("gfx.janky_pct",r"Janky frames: \d+ \(([\d.]+)%\)"),("gfx.p50",r"50th percentile: (\d+)ms"),("gfx.p90",r"90th percentile: (\d+)ms"),("gfx.p95",r"95th percentile: (\d+)ms"),("gfx.p99",r"99th percentile: (\d+)ms"),("gfx.slow_ui",r"Number Slow UI thread: (\d+)"),("gfx.deadline_missed",r"Number Frame deadline missed: (\d+)")]:
        mm=re.search(pat,s)
        if mm: g[k]=float(mm.group(1))
    return g

def proc(path):
    s=open(path).read(); g={}
    for k in ["app_cpu_ticks","systemui_ticks","system_server_ticks"]:
        mm=re.search(k+r"=(-?\d+)",s); g["proc."+k]=int(mm.group(1)) if mm else None
    b=re.search(r"io_before=\[(.*?)\]",s).group(1); a=re.search(r"io_after=\[(.*?)\]",s).group(1)
    pb=dict(x.split("=") for x in b.replace(":","").split()); pa=dict(x.split("=") for x in a.replace(":","").split())
    for k in pa: g["io."+k]=int(pa[k])-int(pb.get(k,0))
    return g

d=sys.argv[1]
reps=[]
for t in sorted(glob.glob(d+"/trace_*.pftrace")):
    i=re.search(r"trace_(\d+)",t).group(1)
    m=analyze(t); m.update(gfx(f"{d}/gfx_{i}.txt")); m.update(proc(f"{d}/proc_{i}.txt"))
    reps.append(m)
keys=sorted(set(k for r in reps for k in r))
summary={}
for k in keys:
    vals=[r[k] for r in reps if r.get(k) is not None]
    if vals: summary[k]=st.median(vals)
json.dump({"reps":reps,"median":summary},open(d+"/metrics.json","w"),indent=1)
for k in keys:
    if k in summary: print(f"{k:32s} {summary[k]:10.2f}   reps={[round(r.get(k),2) if r.get(k) is not None else None for r in reps]}")
