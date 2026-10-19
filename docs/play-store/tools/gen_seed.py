import json, random, uuid, datetime as dt
from zoneinfo import ZoneInfo
IST = ZoneInfo("Asia/Kolkata"); R = 108
rnd = random.Random(108)
def uid(): return str(uuid.UUID(int=rnd.getrandbits(128), version=4))
def ms(d): return int(d.timestamp() * 1000)
morning = {"id": uid(), "name": "Morning Sadhana", "createdAt": ms(dt.datetime(2026,8,20,7,0,tzinfo=IST)), "mantras": [
    {"id": uid(), "name": "Om Namah Shivaya", "targetRounds": 4},
    {"id": uid(), "name": "Gayatri Mantra", "targetRounds": 1},
    {"id": uid(), "name": "Mahamrityunjaya Mantra", "targetRounds": 2}]}
evening = {"id": uid(), "name": "Evening Japa", "createdAt": ms(dt.datetime(2026,8,22,19,0,tzinfo=IST)), "mantras": [
    {"id": uid(), "name": "Hare Krishna Mahamantra", "targetRounds": 4},
    {"id": uid(), "name": "Om Namo Narayanaya", "targetRounds": 2}]}
history = []
def session(start, goal=None, rounds=None, partial=0):
    if goal:
        tallies, total = [], 0
        for m in goal["mantras"]:
            tallies.append({"name": m["name"], "rounds": m["targetRounds"], "counts": m["targetRounds"] * R}); total += m["targetRounds"]
        counts = total * R
    else:
        total, counts, tallies = rounds, rounds * R + partial, []
    mins = counts / R * rnd.uniform(6.2, 8.0)
    history.append({"id": uid(), "startTime": ms(start), "endTime": ms(start + dt.timedelta(minutes=mins)),
        "totalCounts": counts, "totalRounds": total, "goalId": goal["id"] if goal else None,
        "goalName": goal["name"] if goal else None, "mantras": tallies})
today = dt.date(2026, 10, 19)
start = dt.date(2026, 8, 24)
gaps = {dt.date(2026,8,29), dt.date(2026,9,3), dt.date(2026,9,4), dt.date(2026,9,12), dt.date(2026,9,20), dt.date(2026,9,25)}
d = start
while d < today:
    if d not in gaps:
        t = lambda h, m: dt.datetime(d.year, d.month, d.day, h, m, tzinfo=IST)
        r = rnd.random()
        if r < 0.55: session(t(5, rnd.randint(15, 55)), morning)
        elif r < 0.8: session(t(6, rnd.randint(0, 50)), rounds=rnd.choice([4, 4, 5, 6]), partial=rnd.choice([0, 0, 12, 27]))
        else: session(t(7, rnd.randint(0, 40)), rounds=rnd.choice([1, 2, 3]))  # light day, may miss target
        if d >= dt.date(2026, 9, 26) and history[-1]["totalRounds"] < 4:
            session(t(19, rnd.randint(0, 50)), evening)
        elif rnd.random() < 0.35:
            session(t(19, rnd.randint(0, 50)), evening)
    d += dt.timedelta(days=1)
session(dt.datetime(2026, 10, 19, 6, 5, tzinfo=IST), rounds=2, partial=0)
history.sort(key=lambda s: -s["startTime"])
backup = {"app": "SuperChant", "format": 1, "exportedAt": ms(dt.datetime(2026,10,19,9,0,tzinfo=IST)),
    "settings": {"themeMode": "light", "dailyTarget": 4, "countVibration": True, "countIntensity": 100,
        "roundVibration": True, "roundIntensity": 200, "milestonePulse": True, "beadClick": False,
        "reminderEnabled": True, "reminderMinuteOfDay": 360},
    "goals": [morning, evening], "history": history}
import sys; json.dump(backup, open(sys.argv[1] if len(sys.argv) > 1 else "store-seed.json", "w"), indent=2)
days = {}
for s in history:
    k = dt.datetime.fromtimestamp(s["startTime"]/1000, IST).date(); days[k] = days.get(k, 0) + s["totalRounds"]
cur, x = 0, today - dt.timedelta(days=1)
while days.get(x, 0) >= 4: cur += 1; x -= dt.timedelta(days=1)
print(len(history), "sessions; streak", cur, "; total rounds", sum(days.values()))
