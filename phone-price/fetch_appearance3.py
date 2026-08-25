#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第三轮：用英文维基百科文章配图补齐仍缺失外观图的机型。"""
import json, urllib.parse, urllib.request, urllib.error, time

SPEC_FILE = "F:/git/shoujilulunhui/phone-price/phone-specs.json"
API = "https://en.wikipedia.org/w/api.php"
UA = "MeowMicRecycleBot/1.0 (contact: user@example.com) python-urllib"

MISSING = {
    ('华为','Pura 70 Pro+'): ['Huawei Pura 70 Pro+', 'Pura 70 Pro+'],
    ('iQOO','iQOO 13'): ['iQOO 13', 'IQOO 13'],
    ('iQOO','iQOO 12'): ['iQOO 12', 'IQOO 12'],
    ('荣耀','荣耀 Magic6'): ['Honor Magic6', 'Honor Magic 6'],
    ('realme','realme GT6'): ['Realme GT 6', 'Realme GT6'],
    ('realme','realme GT7'): ['Realme GT 7', 'Realme GT7'],
    ('OPPO','Find X9 Pro'): ['Oppo Find X9 Pro', 'Find X9 Pro'],
    ('OPPO','Reno16'): ['Oppo Reno 16', 'Oppo Reno16'],
}

def api_get(params):
    url = API + "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={'User-Agent': UA})
    backoff = 3
    for _ in range(7):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(backoff); backoff = min(backoff*2, 40); continue
            time.sleep(2); return None
        except Exception:
            time.sleep(backoff); backoff = min(backoff*2, 40)
    return None

def wiki_img(query):
    # 1) 直接标题
    d = api_get({'action':'query','titles':query,'redirects':'1','prop':'pageimages',
                 'piprop':'thumbnail','pithumbsize':'500','format':'json'})
    url = _extract(d)
    if url: return url
    # 2) 搜索
    d = api_get({'action':'query','generator':'search','gsrsearch':query,'gsrlimit':'4',
                 'prop':'pageimages','piprop':'thumbnail','pithumbsize':'500','format':'json'})
    return _extract(d)

def _extract(d):
    if not d: return None
    for p in d.get('query',{}).get('pages',{}).values():
        if isinstance(p, dict) and p.get('thumbnail',{}).get('source'):
            return p['thumbnail']['source']
    return None

def main():
    doc = json.load(open(SPEC_FILE, encoding='utf-8'))
    specs = doc['specs']; updated = 0
    for s in specs:
        key = (s['brand'], s['model'])
        if key not in MISSING: continue
        if s.get('appearanceImg'):
            print('skip (已设置):', *key); continue
        print('wiki retry:', *key)
        for q in MISSING[key]:
            url = wiki_img(q)
            if url:
                s['appearanceImg'] = url
                print('   -> OK via "%s": %s' % (q, url)); updated += 1; break
        else:
            print('   -> still none')
        time.sleep(1)
    json.dump(doc, open(SPEC_FILE,'w',encoding='utf-8'), ensure_ascii=False, indent=2)
    print('\n第三轮更新 %d 个' % updated)

if __name__ == '__main__':
    main()
