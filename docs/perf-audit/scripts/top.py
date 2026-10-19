import sys
from perfetto.trace_processor import TraceProcessor
tp = TraceProcessor(trace=sys.argv[1])
thread = sys.argv[2] if len(sys.argv) > 2 else 'main'
cond = "t.is_main_thread=1" if thread=='main' else f"t.name like '{thread}'"
q = f"""
select s.name, count(*) n, sum(s.dur)/1e6 total_ms, avg(s.dur)/1e6 avg_ms, max(s.depth) maxd
from slice s join thread_track tt on s.track_id=tt.id join thread t using(utid) join process p using(upid)
where p.name='com.superchant' and {cond}
group by s.name order by total_ms desc limit {sys.argv[3] if len(sys.argv)>3 else 40}
"""
for r in tp.query(q):
    print(f"{r.total_ms:10.1f} ms  n={r.n:6d} avg={r.avg_ms:7.3f}  {r.name[:110]}")
