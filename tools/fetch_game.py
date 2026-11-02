#!/usr/bin/env python3
"""Fetch the original SWF at build time, never from the Android app."""
import html
import re
import sys
import urllib.request
from pathlib import Path
from urllib.parse import urljoin

PAGE = "https://www.comdotgame.com/play/witch-girl"
DEST = Path("app/src/main/assets/game.swf")
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

def get(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": PAGE})
    with urllib.request.urlopen(req, timeout=120) as response:
        return response.read()

page = get(PAGE).decode("utf-8", "replace")
page = html.unescape(page).replace("\\/", "/").replace("\\u002F", "/")
m = re.search(r'(?:(?:https?:)?//)[^"\'\s<>]+?([a-f0-9]{32})\.swf', page, re.I)
if not m:
    raise RuntimeError("SWF filename not found in the original page.")
name = m.group(1) + ".swf"
urls = [
    f"https://comdotcdn.com/4x90xhl3k0/games/files/{name}",
    f"https://echo.comdotgame.com/4x90xhl3k0/games/files/{name}",
]
for url in urls:
    try:
        data = get(url)
        if data[:3] not in (b"FWS", b"CWS", b"ZWS") or len(data) < 100_000:
            raise ValueError(f"Invalid SWF header or length: {len(data)}")
        DEST.parent.mkdir(parents=True, exist_ok=True)
        DEST.write_bytes(data)
        print(f"Downloaded a real SWF: {len(data):,} bytes; header={data[:3]!r}")
        break
    except Exception as error:
        print("Source failed:", url, type(error).__name__, str(error), file=sys.stderr)
else:
    raise RuntimeError("All direct SWF sources failed; refusing to build an online launcher.")
