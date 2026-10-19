import sys
from perfetto.trace_processor import TraceProcessor
tp=TraceProcessor(trace=sys.argv[1])
p=list(tp.query("select upid, start_ts from process where name='com.superchant' order by upid desc limit 1"))[0]
upid=p.upid
t0=list(tp.query(f"select min(ts) t from slice s join thread_track tt on s.track_id=tt.id join thread using(utid) where upid={upid}"))[0].t
print("main thread top-level slices (ms from first app slice):")
for r in tp.query(f"""select s.ts, s.dur, s.name from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid)
 where t.upid={upid} and t.is_main_thread=1 and s.depth=0 and s.dur>2e6 order by s.ts limit 40"""):
    print(f"  {(r.ts-t0)/1e6:8.1f} +{r.dur/1e6:7.1f}  {r.name[:90]}")
for r in tp.query(f"""select s.ts, s.name from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid)
 where t.upid={upid} and (s.name like '%ullyDrawn%' or s.name like 'reportFullyDrawn%') order by s.ts"""):
    print(f"  FD {(r.ts-t0)/1e6:8.1f} {r.name}")
print("threads CPU (ms) in first 3 s:")
for r in tp.query(f"""select t.name, sum(ss.dur)/1e6 ms from sched_slice ss join thread t using(utid) where t.upid={upid} and ss.ts < {t0}+3e9 group by t.utid order by ms desc limit 12"""):
    print(f"  {r.ms:8.1f} {r.name}")
print("busy windows of DefaultDispatcher threads:")
for r in tp.query(f"""select t.name, min(ss.ts) a, max(ss.ts+ss.dur) b, sum(ss.dur)/1e6 ms from sched_slice ss join thread t using(utid) where t.upid={upid} and t.name like 'DefaultDisp%' group by t.utid order by ms desc limit 6"""):
    print(f"  {r.name} {(r.a-t0)/1e6:8.1f}..{(r.b-t0)/1e6:8.1f} cpu={r.ms:.1f}")
