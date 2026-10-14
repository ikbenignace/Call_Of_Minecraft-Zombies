import json, sys, os
sys.path.insert(0, '/Users/ignace.mella/repos/Call_Of_Minecraft-Zombies/tools')
from silhouette import elements, MODELS

def corners(e):
    import math
    (x0,y0,z0),(x1,y1,z1)=e['from'],e['to']
    pts=[(x,y,z) for x in (x0,x1) for y in (y0,y1) for z in (z0,z1)]
    r=e.get('rotation')
    if r and r['angle']:
        a=math.radians(r['angle']); o=r['origin']; ax=r['axis']
        out=[]
        for x,y,z in pts:
            x,y,z=x-o[0],y-o[1],z-o[2]
            if ax=='x': y,z=y*math.cos(a)-z*math.sin(a), y*math.sin(a)+z*math.cos(a)
            elif ax=='y': x,z=x*math.cos(a)+z*math.sin(a), -x*math.sin(a)+z*math.cos(a)
            else: x,y=x*math.cos(a)-y*math.sin(a), x*math.sin(a)+y*math.cos(a)
            out.append((x+o[0],y+o[1],z+o[2]))
        pts=out
    return [min(q[k] for q in pts) for k in range(3)], [max(q[k] for q in pts) for k in range(3)]

def floaters(key, tol=0.05):
    els=elements(key); boxes=[corners(e) for e in els]
    n=len(els); adj=[set() for _ in range(n)]
    for i in range(n):
        for j in range(i+1,n):
            a0,a1=boxes[i]; b0,b1=boxes[j]
            if all(a0[k]<=b1[k]+tol and b0[k]<=a1[k]+tol for k in range(3)):
                adj[i].add(j); adj[j].add(i)
    # components
    seen=[-1]*n; comps=[]
    for i in range(n):
        if seen[i]>=0: continue
        st=[i]; seen[i]=len(comps); comp=[i]
        while st:
            c=st.pop()
            for d in adj[c]:
                if seen[d]<0: seen[d]=len(comps); comp.append(d); st.append(d)
        comps.append(comp)
    comps.sort(key=len, reverse=True)
    return [[els[i].get('name','?') for i in c] for c in comps[1:]]

for k in sys.argv[1:]:
    f=floaters(k)
    if f: print(k, f[:6])
