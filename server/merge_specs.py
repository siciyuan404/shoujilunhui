# -*- coding: utf-8 -*-
"""
规格合并脚本：把 phone-specs.json（验机知识库，66 款主流旗舰）已有的
上市年份 / CPU / 屏幕信息，按"品牌映射 + 归一化型号"尽力合并进 phone.db 的 models 表。
用途：作为"主流机型优先补参，逐步填满"的种子数据。未匹配到的机型保持空规格，后续手动补录。
运行：python server/merge_specs.py
"""
import json
import os
import re
import sqlite3
import sys

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB_FILE = os.path.join(BASE, "server", "db", "phone.db")
SPECS_FILE = os.path.join(BASE, "phone-price", "phone-specs.json")

# 验机库品牌 -> data.json 中该品牌使用的 brand 名列表
BRAND_MAP = {
    "苹果": ["Apple"],
    "华为": ["华为"],
    "小米": ["小米"],
    "红米": ["红米"],
    "荣耀": ["荣耀"],
    "OPPO": ["OPPO"],
    "vivo": ["vivo"],
    "iQOO": ["vivo"],
    "三星": ["三星"],
    "realme": ["真我"],
    "一加": ["一加"],
}

# 标准前缀（用于归一化，去掉口语化品牌词后统一加前缀）
PREFIX = {
    "苹果": "iphone", "华为": "huawei", "小米": "xiaomi", "红米": "redmi",
    "荣耀": "honor", "OPPO": "oppo", "vivo": "vivo", "iQOO": "iqoo",
    "三星": "samsung", "realme": "realme", "一加": "oneplus",
}

# 中文品牌词 -> 品牌键
CN_WORD = {
    "苹果": "苹果", "华为": "华为", "小米": "小米", "红米": "红米",
    "荣耀": "荣耀", "oppo": "OPPO", "vivo": "vivo", "iqoo": "iQOO",
    "三星": "三星", "真我": "realme", "realme": "realme", "一加": "一加",
    "oneplus": "一加", "samsung": "三星", "huawei": "华为", "xiaomi": "小米",
    "redmi": "红米", "honor": "荣耀", "iphone": "苹果",
}


def norm_model(brand_key, raw):
    """归一化：去空白/符号、转小写、去掉口语化品牌词、补标准前缀"""
    m = str(raw).lower()
    m = re.sub(r"[\s_\-\uFF08\uFF09()\u3010\u3011\[\]\/]", "", m)
    words = sorted(CN_WORD.keys(), key=len, reverse=True)
    for w in words:
        if m.startswith(w):
            m = m[len(w):]
            break
    if m.startswith("1+"):
        m = m[2:]
    if m.startswith("1plus"):
        m = m[5:]
    prefix = PREFIX.get(brand_key, "")
    if prefix and not m.startswith(prefix):
        m = prefix + m
    return m


def cpu_brand_of(chip):
    c = chip or ""
    if re.search(r"高通|骁龙|snapdragon", c, re.I):
        return "高通"
    if re.search(r"联发科|天玑|dimensity|helio", c, re.I):
        return "联发科"
    if re.search(r"苹果|a\d+|仿生", c, re.I):
        return "苹果"
    if re.search(r"麒麟|海思|kirin", c, re.I):
        return "海思"
    if re.search(r"三星|exynos", c, re.I):
        return "三星"
    if re.search(r"紫光|展锐|unisoc", c, re.I):
        return "紫光展锐"
    if re.search(r"google|tensor", c, re.I):
        return "谷歌"
    return ""


def cpu_model_of(chip):
    c = (chip or "").strip()
    c = re.sub(r"^(高通\s*|联发科\s*|苹果\s*)", "", c)
    return c


def parse_features(features):
    out = {}
    f = features or ""
    sz = re.search(r"([\d.]+)\s*(?:″|英寸|\")", f)
    if sz:
        out["screen_size"] = sz.group(1) + "英寸"
    tp = re.search(r"(OLED|AMOLED|LCD|IPS)", f, re.I)
    if tp:
        out["screen_type"] = tp.group(1).upper()
    hz = re.search(r"(\d{2,3})Hz", f, re.I)
    if hz:
        out["refresh"] = hz.group(1) + "Hz"
    return out


def main():
    if not os.path.exists(DB_FILE):
        print("找不到数据库:", DB_FILE)
        return
    specs = json.load(open(SPECS_FILE, encoding="utf-8")).get("specs") or []
    conn = sqlite3.connect(DB_FILE)
    cur = conn.cursor()
    # 确保列存在（若 server 未先启动过）
    existing = {r[1] for r in cur.execute("PRAGMA table_info(models)").fetchall()}
    for col in ["release_date", "cpu_brand", "cpu_model", "screen_size", "screen_type", "refresh"]:
        if col not in existing:
            cur.execute(f"ALTER TABLE models ADD COLUMN {col} TEXT NOT NULL DEFAULT ''")
    conn.commit()

    matched, skipped, failures = 0, 0, []
    for s in specs:
        spec_norm = norm_model(s["brand"], s["model"])
        update = {
            "release_date": str(s.get("releaseYear") or ""),
            "cpu_brand": cpu_brand_of(s.get("chip")),
            "cpu_model": cpu_model_of(s.get("chip")),
        }
        update.update(parse_features(s.get("features")))
        brands = BRAND_MAP.get(s["brand"], [])
        hit = None
        if brands:
            qmarks = ",".join("?" * len(brands))
            rows = cur.execute(
                f"SELECT id, brand, model FROM models WHERE brand IN ({qmarks})",
                brands,
            ).fetchall()
            for rid, rbrand, rmodel in rows:
                if norm_model(s["brand"], rmodel) == spec_norm:
                    hit = (rid, rbrand, rmodel)
                    break
        if hit:
            rid, rbrand, rmodel = hit
            cur.execute(
                """UPDATE models SET release_date=?, cpu_brand=?, cpu_model=?,
                   screen_size=?, screen_type=?, refresh=?,
                   updated_at=datetime('now','localtime') WHERE id=?""",
                (update["release_date"], update["cpu_brand"], update["cpu_model"],
                 update.get("screen_size", ""), update.get("screen_type", ""),
                 update.get("refresh", ""), rid),
            )
            matched += 1
            print(f"[OK]   {s['brand']} {s['model']} -> {rbrand} \"{rmodel}\" "
                  f"({update['release_date']} / {update['cpu_brand']} {update['cpu_model']})")
        else:
            skipped += 1
            failures.append(f"{s['brand']} {s['model']}（归一:{spec_norm}）")
            print(f"[MISS] {s['brand']} {s['model']}（归一:{spec_norm}）")
    conn.commit()
    total = cur.execute("SELECT COUNT(*) FROM models").fetchone()[0]
    with_spec = cur.execute(
        "SELECT COUNT(*) FROM models WHERE release_date != '' OR cpu_brand != ''"
    ).fetchone()[0]
    print(f"\n合并完成：命中 {matched} 条，未命中 {skipped} 条。")
    print(f"当前库中已有规格的机型数：{with_spec} / {total}")
    if failures:
        print("\n未命中清单（后续可手动补录）：")
        print("\n".join(failures))
    conn.close()


if __name__ == "__main__":
    main()
