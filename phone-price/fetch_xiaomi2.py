#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""定向重检索：修正误匹配 + 补齐小米/红米缺失外观图。严格排除变体误命中。"""
import json, time, urllib.parse, urllib.request

API = "https://commons.wikimedia.org/w/api.php"
UA = {"User-Agent": "MeowMicBot/1.0 (phone-recycling-verify)"}

# (brand, model, [候选查询], [必须排除的子串])
TARGETS = [
    ("小米", "小米13", ["Xiaomi 13 smartphone", "Xiaomi 13 black", "Xiaomi 13 front"],
        ["13T", "13 Pro", "13 Ultra", "13 Lite", "13C", "13R"]),
    ("小米", "小米14 Pro", ["Xiaomi 14 Pro smartphone", "Xiaomi 14 Pro black"],
        ["Ultra", "14T", "14 Civi"]),
    ("小米", "小米13 Pro", ["Xiaomi 13 Pro smartphone", "Xiaomi 13 Pro blue"],
        ["Ultra", "13T"]),
    ("红米", "红米K70 Pro", ["Redmi K70 Pro", "Redmi K70 Pro black"],
        ["至尊", "Ultra", "Pro+", "Gaming"]),
    ("红米", "红米K60 至尊版", ["Redmi K60 Ultra", "Redmi K60 Gaming", "Redmi K60 Ultra black"],
        ["Pro ", "Pro+"]),
    ("红米", "红米Note 13 Pro", ["Redmi Note 13 Pro", "Redmi Note 13 Pro black"],
        ["Pro+", "13C", "13R", "5G "]),
    ("小米", "小米14 Ultra", ["Xiaomi 14 Ultra smartphone", "Xiaomi 14 Ultra black"],
        ["13 ", "13T", "14T", "14 Pro", "14 Civi"]),
    ("红米", "红米Note 14 Pro", ["Redmi Note 14 Pro", "Redmi Note 14 Pro black"],
        ["Pro+", "14C", "14R"]),
]

def api(params):
    url = API + "?" + urllib.parse.urlencode(params)
    for _ in range(4):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.load(r)
        except Exception as e:
            if "429" in str(e):
                time.sleep(6); continue
            time.sleep(2)
    return None

def search(q):
    d = api({"action": "query", "list": "search", "srsearch": q,
             "srnamespace": 6, "srlimit": 15, "format": "json"})
    return [x["title"] for x in (d or {}).get("query", {}).get("search", [])]

def thumb(title):
    d = api({"action": "query", "titles": title, "prop": "imageinfo",
             "iiprop": "url|mime", "iiurlwidth": 600, "format": "json"})
    for p in (d or {}).get("query", {}).get("pages", {}).values():
        ii = p.get("imageinfo", [{}])[0]
        if ii.get("mime", "").startswith("image"):
            return ii.get("thumburl") or ii.get("url")
    return None

def main():
    f = "F:/git/shoujilulunhui/phone-price/phone-specs.json"
    doc = json.load(open(f, encoding="utf-8"))
    idx = {(s["brand"], s["model"]): s for s in doc["specs"]}

    # 先回滚 小米13 的误匹配（13T）
    s13 = idx.get(("小米", "小米13"))
    if s13 and s13.get("appearanceImg") and "13T" in s13["appearanceImg"]:
        s13["appearanceImg"] = None
        s13["gallery"] = []
        print("reverted 小米13 wrong match (13T)")

    for brand, model, qlist, excl in TARGETS:
        spec = idx.get((brand, model))
        if not spec:
            continue
        if spec.get("appearanceImg") and "13T" not in spec["appearanceImg"]:
            print(f"skip (已有图): {brand} {model}"); continue
        best = None
        for q in qlist:
            for t in search(q):
                tl = t.lower()
                if any(x.lower() in tl for x in excl):
                    continue
                # 必须包含核心型号 token
                core = model.replace("红米", "redmi ").replace("小米", "xiaomi ")
                # 用更稳的判定：标题含 xiaomi/redmi + 数字
                tok = ("xiaomi 13" if "小米13" in model else
                       "xiaomi 14 pro" if model == "小米14 Pro" else
                       "xiaomi 13 pro" if model == "小米13 Pro" else
                       "redmi k70 pro" if model == "红米K70 Pro" else
                       "redmi k60 ultra" if "K60 至尊" in model else
                       "redmi note 13 pro" if "Note 13 Pro" in model else
                       "xiaomi 14 ultra" if "14 Ultra" in model else
                       "redmi note 14 pro" if "Note 14 Pro" in model else "")
                if tok and tok not in tl:
                    continue
                url = thumb(t)
                if url:
                    best = (t, url); break
            if best:
                break
            time.sleep(1.0)
        if best:
            spec["appearanceImg"] = best[1]
            spec.setdefault("sources", [])
            if f"Wikimedia Commons: {best[0]}" not in spec["sources"]:
                spec["sources"].append(f"Wikimedia Commons: {best[0]}")
            print(f"  OK  {brand} {model} -> {best[0][:45]}")
        else:
            print(f"  NONE {brand} {model}")
        time.sleep(1.0)

    json.dump(doc, open(f, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print("saved")

if __name__ == "__main__":
    main()
