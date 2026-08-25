#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第二轮：为仍缺失外观图的机型用多种候选查询再试一次。"""
import json, urllib.parse, urllib.request, urllib.error, time, sys

SPEC_FILE = "F:/git/shoujilulunhui/phone-price/phone-specs.json"
API = "https://commons.wikimedia.org/w/api.php"
UA = "MeowMicRecycleBot/1.0 (contact: user@example.com) python-urllib"

BRAND_EN = {'苹果':'iPhone','华为':'Huawei','小米':'Xiaomi','红米':'Redmi','vivo':'Vivo',
            'iQOO':'iQOO','荣耀':'Honor','三星':'Samsung','realme':'Realme','一加':'OnePlus','OPPO':'Oppo'}
CN = ['苹果','华为','小米','红米','vivo','iQOO','荣耀','三星','realme','一加','OPPO']

CANDIDATES = {
    ('华为','Pura 70 Pro+'): ['Huawei Pura 70 Pro Plus','Huawei Pura 70 Pro+','Pura 70 Pro Plus smartphone'],
    ('iQOO','iQOO 13'): ['iQOO 13 smartphone','iQOO 13 phone','IQOO 13'],
    ('iQOO','iQOO 12'): ['iQOO 12 smartphone','iQOO 12 phone','IQOO 12'],
    ('荣耀','荣耀 Magic6'): ['Honor Magic6','Honor Magic 6','Honor Magic6 Pro'],
    ('三星','Galaxy S24+'): ['Samsung Galaxy S24 Plus','Samsung Galaxy S24+','Galaxy S24 Plus'],
    ('realme','realme GT6'): ['Realme GT 6','Realme GT6 smartphone','Realme GT 6 phone'],
    ('realme','realme GT7'): ['Realme GT 7','Realme GT7','Realme GT 7 smartphone'],
    ('OPPO','Find X9 Pro'): ['Oppo Find X9 Pro','Find X9 Pro','OPPO Find X9 Pro'],
    ('OPPO','Reno16'): ['Oppo Reno 16','Oppo Reno16','Reno 16 smartphone'],
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

def score(name, ql, bel, mc):
    n = name.lower(); s = 0
    if ql in n: s += 5
    elif bel and bel in n and mc and mc.lower() in n: s += 4
    elif mc and len(mc) >= 3 and mc.lower() in n: s += 1
    if 'vector' in n or n.endswith('.svg') or 'logo' in n: s -= 10
    if 'icon' in n: s -= 5
    return s

def try_query(brand, model, query):
    ql = query.lower(); bel = BRAND_EN.get(brand,'').lower()
    mc = model
    for cn in CN:
        if mc.startswith(cn): mc = mc[len(cn):].strip(); break
    data = api_get({'action':'query','generator':'search','gsrsearch':query,'gsrnamespace':'6',
                    'gsrlimit':'10','prop':'imageinfo','iiprop':'url|mime','iiurlwidth':'500','format':'json'})
    if not data: return None, []
    pages = data.get('query',{}).get('pages',{})
    cands = []
    for p in pages.values():
        ii = p.get('imageinfo')
        if not ii: continue
        info = ii[0]; mime = info.get('mime','')
        if not mime.startswith('image') or mime=='image/svg+xml': continue
        t = p.get('title',''); nm = t.split(':',1)[-1].rsplit('.',1)[0] if ':' in t else t
        cands.append((score(nm,ql,bel,mc), info.get('thumburl') or info.get('url')))
    cands.sort(key=lambda x:x[0], reverse=True)
    good = [c for c in cands if c[0] >= 4]
    if not good: return None, []
    return good[0][1], [c[1] for c in good[1:] if c[1]][:3]

def main():
    d = json.load(open(SPEC_FILE, encoding='utf-8'))
    specs = d['specs']; updated = 0
    for s in specs:
        key = (s['brand'], s['model'])
        if key not in CANDIDATES: continue
        if s.get('appearanceImg'):
            print('skip (已设置):', *key); continue
        print('retry:', *key)
        for q in CANDIDATES[key]:
            url, gal = try_query(s['brand'], s['model'], q)
            if url:
                s['appearanceImg'] = url
                if gal: s['gallery'] = gal
                print('   -> OK via "%s": %s' % (q, url)); updated += 1; break
        else:
            print('   -> still none')
        time.sleep(1)
    json.dump(d, open(SPEC_FILE,'w',encoding='utf-8'), ensure_ascii=False, indent=2)
    print('\n第二轮更新 %d 个' % updated)

if __name__ == '__main__':
    main()
