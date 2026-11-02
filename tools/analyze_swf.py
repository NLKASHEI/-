#!/usr/bin/env python3
import collections,io,re,struct,urllib.request,zlib
URL='https://comdotcdn.com/4x90xhl3k0/games/files/875ed08dec9f3666cd8b7fbf36d51722.swf'
req=urllib.request.Request(URL,headers={'User-Agent':'Mozilla/5.0','Referer':'https://www.comdotgame.com/play/witch-girl'})
with urllib.request.urlopen(req,timeout=180) as r: raw=r.read()
assert raw[:3]==b'CWS'
swf=raw[:8]+zlib.decompress(raw[8:])
nrect=swf[8]>>3
pos=8+((5+4*nrect+7)//8)+4
count=collections.Counter()
results=[]
def u30(b,p):
    val=0
    for step in range(5):
       char=b[p];p+=1
       val|=(char&127)<<(step*7)
       if char<128:break
    return val,p
def strings(abc):
    p=4
    try:
        for typ in ('ints','uints','doubles'):
            n,p=u30(abc,p)
            for _ in range(n-1):
                if typ=='doubles':p+=8
                else: _,p=u30(abc,p)
        n,p=u30(abc,p)
        out=[]
        for _ in range(n-1):
            size,p=u30(abc,p)
            out.append(abc[p:p+size].decode('utf8','replace'))
            p+=size
        return out
    except Exception as e:
        print('ABC parse error',str(e), 'position',p,'len',len(abc));return []
while pos<len(swf)-2:
    head=struct.unpack_from('<H',swf,pos)[0];pos+=2
    code=head>>6
    ln=head&63
    if ln==63:ln=struct.unpack_from('<I',swf,pos)[0];pos+=4
    data=swf[pos:pos+ln];pos+=ln
    count[code]+=1
    if code==82:
        name_end=data.find(b'\x00',4)
        name=data[4:name_end].decode('utf8','replace')
        abc=data[name_end+1:]
        ss=strings(abc)
        results+=ss
        print('ABC',name,'size',len(abc),'str',len(ss))
    if code==0:break
print('TAGS',count.most_common(30),'Total ABC strings',len(results))
results=list(dict.fromkeys(results))
exts=('.swf','.jpg','.jpeg','.png','.mp3','.wav','.ogg','.xml','.json','.txt','.bin','.dat','.zip','.flv','.mp4','.atf','.fla')
matched=[s for s in results if any(t in s.lower() for t in exts) or 'http://' in s.lower() or 'https://' in s.lower() or '.php' in s.lower()]
for s in matched[:1000]:
    print('ASSET',repr(s[:260]))
print('TOTAL ASSET CANDIDATES',len(matched))
keywords=['loader','load','witch','noel','character','stage','asset','chara','enemy','girl','sound','external','scene','data','file','url','path','level']
for k in keywords:
    subset=[s for s in results if k in s.lower()]
    print('KEYWORD',k,'count',len(subset),'SAMPLES',repr(subset[:65])[:3500])
