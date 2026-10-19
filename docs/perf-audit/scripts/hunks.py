"""hunks.py FILE list | hunks.py FILE stage IDX[,IDX...] : list or stage (git apply --cached) selected hunks of one file's diff"""
import subprocess, sys, re
f=sys.argv[1]; cmd=sys.argv[2]
d=subprocess.run(["git","diff","-U1","--",f],capture_output=True,text=True).stdout
head,_,body=d.partition("\n@@")
body="@@"+body
hunks=re.split(r"(?m)^(?=@@ )",body)
hunks=[h for h in hunks if h.strip()]
if cmd=="list":
    for i,h in enumerate(hunks):
        lines=[l for l in h.splitlines()[1:] if l.startswith(('+','-'))]
        print(f"[{i}] {h.splitlines()[0][:40]} | "+" || ".join(l[:90] for l in lines[:3]))
else:
    idx=[int(x) for x in sys.argv[3].split(",")]
    patch=head+"\n"+"".join(hunks[i] if hunks[i].endswith("\n") else hunks[i]+"\n" for i in idx)
    r=subprocess.run(["git","apply","--cached","--recount","-"],input=patch,text=True,capture_output=True)
    print(r.stdout, r.stderr, "ok" if r.returncode==0 else "FAILED")
