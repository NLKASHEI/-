#!/usr/bin/env python3
"""Bundle Witch Girl v2.20 main SWF and all referenced external SWF/MP3 assets."""
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path
import html
import re
import struct
import time
import urllib.request
import zlib

PAGE = "https://www.comdotgame.com/play/witch-girl"
BASES = (
    "https://comdotcdn.com/4x90xhl3k0/games/files/witch-girl-u/2.20/",
    "https://echo.comdotgame.com/4x90xhl3k0/games/files/witch-girl-u/2.20/",
)
MAIN_DEST = Path("app/src/main/assets/game.swf")
ASSET_BASE = Path("app/src/main/assets/witch-girl-u/2.20")
UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36"

def request(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": PAGE})
    with urllib.request.urlopen(req, timeout=75) as response:
        return response.read()

page = request(PAGE).decode("utf-8", "replace")
page = html.unescape(page).replace("\\/", "/").replace("\\u002F", "/")
m = re.search(r'(?:(?:https?:)?//)[^"\'\s<>]+?([a-f0-9]{32})\.swf', page, re.I)
if not m:
    raise RuntimeError("Main SWF filename not found on official host")
swf_name = m.group(1) + ".swf"
main_urls = (
    "https://comdotcdn.com/4x90xhl3k0/games/files/" + swf_name,
    "https://echo.comdotgame.com/4x90xhl3k0/games/files/" + swf_name,
)
for u in main_urls:
    try:
        main = request(u)
        if main[:3] not in (b"CWS", b"FWS", b"ZWS") or len(main) < 100_000:
            raise ValueError("Invalid Flash file")
        break
    except Exception as error:
        print("Main SWF mirror failed:", str(error), flush=True)
else:
    raise RuntimeError("Cannot fetch source SWF")

MAIN_DEST.parent.mkdir(parents=True, exist_ok=True)
MAIN_DEST.write_bytes(main)
print("Main SWF", len(main), "bytes", flush=True)
if main[:3] == b"CWS":
    content = main[:8] + zlib.decompress(main[8:])
elif main[:3] == b"FWS":
    content = main
else:
    raise RuntimeError("Unsupported LZMA-compressed SWF")
assert len(content) == struct.unpack_from("<I", main, 4)[0], "SWF uncompressed length invalid"

# These relative resource names are authored into the original movie by its creator.
# Do not blindly download all words or embedded audio library linkage names.
resources = sorted(set(m.group().decode("ascii") for m in re.finditer(
    rb"(?:data/[A-Za-z0-9_-]+\.swf|sound/[A-Za-z0-9_-]+\.mp3)", content, re.I
)))
assert len([r for r in resources if r.startswith("data/")]) >= 45, resources
assert len([r for r in resources if r.startswith("sound/")]) >= 7, resources
print("External resource count", len(resources), flush=True)

def download(relative):
    for base in BASES:
        for trial in range(2):
            try:
                buf = request(base + relative)
                ok = buf[:3] in (b"CWS", b"FWS", b"ZWS") if relative.endswith(".swf") else (
                    buf[:3] == b"ID3" or buf[:2] in (b"\xff\xfb", b"\xff\xf3", b"\xff\xf2"))
                if not ok or len(buf) < 256:
                    raise ValueError(f"invalid {relative}: size={len(buf)} sig={buf[:4]!r}")
                dest = ASSET_BASE / relative
                dest.parent.mkdir(parents=True, exist_ok=True)
                dest.write_bytes(buf)
                return relative, len(buf)
            except Exception as e:
                print("Mirror problem", relative, str(e), flush=True)
                time.sleep(.3)
    raise RuntimeError(f"Required offline asset not available: {relative}")

total = 0
with ThreadPoolExecutor(max_workers=8) as pool:
    jobs = [pool.submit(download, r) for r in resources]
    for future in as_completed(jobs):
        name, size = future.result()
        total += size
        print("Bundled", name, size, "bytes", flush=True)
print("Bundled all external resources:", len(resources), "files,", total, "bytes", flush=True)
