"""topcpu.py TRACE : main-thread top slices by self time (depth-aware) for com.superchant"""
import sys
from perfetto.trace_processor import TraceProcessor
tp=TraceProcessor(trace=sys.argv[1])
q="""
with m as (select s.id, s.name, s.dur, s.depth from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid) join process p using(upid)
 where p.name='com.superchant' and t.is_main_thread=1),
c as (select parent_id pid, sum(dur) cd from slice where parent_id in (select id from m) group by parent_id)
select m.name, count(*) n, sum(m.dur - coalesce(c.cd,0))/1e6 self_ms, sum(m.dur)/1e6 tot from m left join c on c.pid=m.id group by m.name order by self_ms desc limit 30"""
for r in tp.query(q):
    print(f"{r.self_ms:9.1f} self {r.tot:9.1f} tot n={r.n:6d}  {r.name[:90]}")
