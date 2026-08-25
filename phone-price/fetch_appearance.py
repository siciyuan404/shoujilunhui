#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为 phone-specs.json 中每个机型解析 Wikimedia Commons 外观图 URL，写入 appearanceImg / gallery。"""
import json, urllib.parse, urllib.request, urllib.error, time, sys

SPEC_FILE = "F:/git/shoujilulunhui/phone-price/phone-specs.json"
API = "https://commons.wikimedia.org/w/api.php"
UA = "MeowMicRecycleBot/1.0 (contact: user@example.com) python-urllib"

BRAND_EN = {
    '苹果': 'iPhone', '华为': 'Huawei', '小米': 'Xiaomi', '红米': 'Redmi',
    'vivo': 'Vivo', 'iQOO': 'iQOO', '荣耀': 'Honor', '三星': 'Samsung',
    'realme': 'Realme', '一加': 'OnePlus', 'OPPO': 'Oppo'
}
CN_PREFIXES = ['苹果', '华为', '小米', '红米', 'vivo', 'iQOO', '荣耀', '三星', 'realme', '一加', 'OPPO']


def make_query(brand, model):
    en = BRAND_EN.get(brand, '')
    m = model
    for cn in CN_PREFIXES:
        if m.startswith(cn):
            m = m[len(cn):].strip()
            break
    if brand == '苹果':
        return model
    if en and en.lower() not in m.lower():
        return (en + ' ' + m).strip()
    return m


def api_get(params):
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    backoff = 3
    for attempt in range(7):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                print("  ! 429 rate-limited, sleep %ds" % backoff, file=sys.stderr)
                time.sleep(backoff); backoff = min(backoff * 2, 40); continue
            else:
                print("  ! HTTP %s" % e.code, file=sys.stderr); time.sleep(2)
                if attempt < 2:
                    continue
                return None
        except Exception as e:
            print("  ! %s" % e, file=sys.stderr); time.sleep(backoff); backoff = min(backoff * 2, 40)
    return None


def score_image(name, query_lower, brand_en_lower, model_core):
    n = name.lower()
    score = 0
    if query_lower in n:
        score += 5
    elif brand_en_lower and brand_en_lower in n and model_core and model_core.lower() in n:
        score += 4
    elif model_core and len(model_core) >= 3 and model_core.lower() in n:
        score += 1  # 弱匹配，仅型号片段，可能是误匹配
    if 'vector' in n or n.endswith('.svg') or 'logo' in n:
        score -= 10
    if 'icon' in n:
        score -= 5
    return score


def resolve(brand, model):
    query = make_query(brand, model)
    query_lower = query.lower()
    brand_en_lower = BRAND_EN.get(brand, '').lower()
    model_core = model
    for cn in CN_PREFIXES:
        if model_core.startswith(cn):
            model_core = model_core[len(cn):].strip()
            break
    data = api_get({
        'action': 'query', 'generator': 'search', 'gsrsearch': query,
        'gsrnamespace': '6', 'gsrlimit': '10', 'prop': 'imageinfo',
        'iiprop': 'url|mime', 'iiurlwidth': '500', 'format': 'json'
    })
    if not data:
        return None, []
    pages = data.get('query', {}).get('pages', {})
    cands = []
    for p in pages.values():
        ii = p.get('imageinfo')
        if not ii:
            continue
        info = ii[0]
        mime = info.get('mime', '')
        if not mime.startswith('image') or mime == 'image/svg+xml':
            continue
        title = p.get('title', '')
        name = title.split(':', 1)[-1].rsplit('.', 1)[0] if ':' in title else title
        sc = score_image(name, query_lower, brand_en_lower, model_core)
        cands.append((sc, info.get('thumburl') or info.get('url'), info.get('url')))
    cands.sort(key=lambda x: x[0], reverse=True)
    good = [c for c in cands if c[0] >= 4]  # 仅接受强匹配，避免误匹配
    if not good:
        return None, []
    main = good[0][1]
    gallery = []
    seen = set()
    for sc, thumb, full in good[1:]:
        if thumb and thumb not in seen:
            seen.add(thumb)
            gallery.append(thumb)
        if len(gallery) >= 3:
            break
    return main, gallery


def main():
    with open(SPEC_FILE, encoding='utf-8') as f:
        doc = json.load(f)
    specs = doc.get('specs', [])
    updated = 0
    reprocessed = 0
    for i, s in enumerate(specs):
        brand, model = s.get('brand', ''), s.get('model', '')
        if s.get('appearanceImg'):
            print("[%d/%d] skip (已设置): %s %s" % (i + 1, len(specs), brand, model))
            continue
        print("[%d/%d] resolving: %s %s ..." % (i + 1, len(specs), brand, model))
        reprocessed += 1
        main_url, gallery = resolve(brand, model)
        if main_url:
            s['appearanceImg'] = main_url
            if gallery:
                s['gallery'] = gallery
            print("    -> OK  %s" % main_url)
            updated += 1
        else:
            print("    -> none found")
        time.sleep(1)
    with open(SPEC_FILE, 'w', encoding='utf-8') as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
    print("\n完成：本次更新 %d 个（重处理 %d 个缺失/错误项，共 %d）" % (updated, reprocessed, len(specs)))


if __name__ == '__main__':
    main()
