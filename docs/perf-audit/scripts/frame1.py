import sys
from perfetto.trace_processor import TraceProcessor
tp=TraceProcessor(trace=sys.argv[1])
upid=list(tp.query("select upid from process where name='com.superchant' order by upid desc limit 1"))[0].upid
fr=list(tp.query(f"select s.id, s.ts, s.dur from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid) where t.upid={upid} and t.is_main_thread=1 and s.name like 'Choreographer#doFrame%' and s.depth=0 and s.dur>50e6 order by s.ts limit 1"))[0]
print("first long doFrame", round(fr.dur/1e6,1))
for r in tp.query(f"""select s.name, s.depth, sum(s.dur)/1e6 ms, count(*) n from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid)
 where t.upid={upid} and t.is_main_thread=1 and s.ts>={fr.ts} and s.ts<{fr.ts+fr.dur} and s.depth between 1 and 7 group by s.name, s.depth having ms>6 order by s.depth, ms desc limit 50"""):
    print(f"  d{r.depth} {r.ms:7.1f} n={r.n:4d} {r.name[:90]}")
print("main thread states during it:")
for r in tp.query(f"""select state, sum(dur)/1e6 ms from thread_state ts join thread t using(utid) where t.upid={upid} and t.is_main_thread=1 and ts.ts>={fr.ts} and ts.ts<{fr.ts+fr.dur} group by state order by ms desc"""):
    print("  ", r.state, round(r.ms,1))
