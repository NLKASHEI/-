#!/usr/bin/env python3
"""Verify game's original external asset directory and confirm valid signatures."""
import urllib.request,urllib.error
BASES=[
 'https://comdotcdn.com/4x90xhl3k0/games/files/witch-girl-u/2.20/',
 'https://comdotcdn.com/games/files/witch-girl-u/2.20/',
 'https://echo.comdotgame.com/4x90xhl3k0/games/files/witch-girl-u/2.20/',
 'https://comdotcdn.com/4x90xhl3k0/games/files/witch-girl-u/2.20/data/',
 'https://comdotcdn.com/games/files/witch-girl-u/2.20/data/',
]
FILES=['data/data-d-001a.swf','data/data-g-001.swf','data/data001.swf','sound/Stage01_forest.mp3','voice_fera1_1.wav']
for base in BASES:
  for name in FILES:
    u=base+name
    try:
      req=urllib.request.Request(u,headers={'User-Agent':'Mozilla/5.0','Referer':'https://www.comdotgame.com/play/witch-girl'})
      with urllib.request.urlopen(req,timeout=14) as resp:
        sample=resp.read(10)
        print('OK',u,'STATUS',resp.status,'SIZE',resp.headers.get('Content-Length'),'HEAD',repr(sample),'MIME',resp.headers.get('Content-Type'),flush=True)
    except Exception as e:
      print('FAIL',u,type(e).__name__,str(e),flush=True)
