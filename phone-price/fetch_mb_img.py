#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为缺失 motherboardImg 的机型检索 Wikimedia Commons 拆机/主板结构图。"""
import json, time, urllib.parse, urllib.request, sys

API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "MeowMicBot/1.0 (phone-recycling-verify; contact: local)"}

# 每个机型的候选检索词（优先拆机/主板/内部结构类）
QUERIES = {
    ("小米", "小米15"):        ["Xiaomi 15 disassembly", "Xiaomi 15 teardown", "Xiaomi 15 internals", "Xiaomi 15 motherboard"],
    ("小米", "小米15 Ultra"):  ["Xiaomi 15 Ultra teardown", "Xiaomi 15 Ultra disassembly", "Xiaomi 15 Ultra internals"],
    ("小米", "小米14"):        ["Xiaomi 14 disassembly", "Xiaomi 14 teardown", "Xiaomi 14 motherboard"],
    ("红米", "红米K80"):       ["Redmi K80 teardown", "Redmi K80 disassembly", "Redmi K80 internals"],
    ("红米", "红米K70"):       ["Redmi K70 teardown", "Redmi K70 disassembly", "Redmi K70 internals"],
}

GOOD = ("teardown", "disassembly", "internals", "motherboard", "mainboard", "拆机",
        "pcb", "board", "inside", "internal", "exploded")
BAD = ("logo", "wallpaper", "box", "packaging", "render", "concept", "advert")

def api(params):
    url = API + "?" + urllib.parse.urlencode(params)
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.load(r)
        except Exception as e:
            code = getattr(getattr(e, "read", lambda: b""), "__call__", lambda: b"")
            if "429" in str(e) or "429" in str(code):
                time.sleep(5 * (attempt + 1)); continue
            time.sleep(2)
    return None

def search(q):
    d = api({"action": "query", "list": "search", "srsearch": q,
             "srnamespace": 6, "srlimit": 12, "format": "json"})
    return [x["title"] for x in (d or {}).get("query", {}).get("search", [])]

def thumb(title):
    d = api({"action": "query", "titles": title, "prop": "imageinfo",
             "iiprop": "url|mime", "iiurlwidth": 800, "format": "json"})
    pages = (d or {}).get("query", {}).get("pages", {})
    for p in pages.values():
        ii = p.get("imageinfo", [{}])[0]
        if ii.get("mime", "").startswith("image"):
            return ii.get("thumburl") or ii.get("url")
    return None

def main():
    f = "F:/git/shoujilulunhui/phone-price/phone-specs.json"
    doc = json.load(open(f, encoding="utf-8"))
    specs = doc["specs"]
    idx = {(s["brand"], s["model"]): s for s in specs}

    found = 0
    for key, qlist in QUERIES.items():
        if key not in idx:
            print("skip (not in data):", key); continue
        spec = idx[key]
        if spec.get("motherboardImg"):
            print("already has img:", key); continue
        best = None
        for q in qlist:
            titles = search(q)
            time.sleep(1.2)
            for t in titles:
                tl = t.lower()
                if any(b in tl for b in BAD):
                    continue
                score = sum(1 for g in GOOD if g in tl)
                # prefer exact model token
                if any(m.lower() in tl for m in [key[1].lower(), "xiaomi 15", "xiaomi 14", "redmi k80", "redmi k70"]):
                    score += 2
                if score <= 0:
                    continue
                url = thumb(t)
                if url and (best is None or score > best[0]):
                    best = (score, t, url)
                    break  # take first decent per query
            if best:
                break
        if best:
            spec["motherboardImg"] = best[2]
            spec.setdefault("sources", [])
            if f"Wikimedia Commons: {best[1]}" not in spec["sources"]:
                spec["sources"].append(f"Wikimedia Commons: {best[1]}")
            print(f"  OK  {key[0]} {key[1]} -> {best[1][:50]}")
            found += 1
        else:
            print(f"  NONE {key[0]} {key[1]}")
        time.sleep(1.0)

    json.dump(doc, open(f, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"\nadded motherboard images: {found}")

if __name__ == "__main__":
    main()
