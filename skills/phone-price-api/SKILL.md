---
name: phone-price-api
description: 手机回收价格数据库操作。当用户需要查询手机回收报价、按品牌/型号搜索价格、增删改价格数据、统计收录情况时使用本技能。通过 HTTP API 与 SQLite 数据库交互，支持搜索、筛选、排序、分页。
---

# 手机回收价格数据库 API

本地运行于 `http://localhost:8760`（远程通过内网穿透访问）。数据库为 SQLite 文件 `server/db/phone.db`，当前收录约 1600+ 款机型的回收报价。

## 快速调用（推荐）

使用脚本调用（自动携带 API Key）：

```bash
# 查询
node skills/phone-price-api/scripts/api.js GET "/api/models?search=iphone 16"
node skills/phone-price-api/scripts/api.js GET "/api/models?brand=华为&sort=price_desc&limit=20"
node skills/phone-price-api/scripts/api.js GET "/api/models/123"

# 写操作（POST/PUT/DELETE 自动带 Key）
node skills/phone-price-api/scripts/api.js POST /api/models '{"brand":"华为","category":"Mate系列","model":"Mate80","price":"500","note":""}'
node skills/phone-price-api/scripts/api.js PUT /api/models/123 '{"price":"550"}'
node skills/phone-price-api/scripts/api.js DELETE /api/models/123
```

## 参考图片（每个产品可挂多张）

每条机型可保存 1~N 张参考图片 URL（`images` 字段，JSON 数组，首图为主图）：

```bash
# 1) 上传本地图片 → 得到 url
node skills/phone-price-api/scripts/api.js UPLOAD "C:/pics/iphone15.jpg"
#   返回 {"ok":true,"url":"/uploads/u_xxx.jpg","name":"u_xxx.jpg"}

# 2) 把 url 写入某机型
node skills/phone-price-api/scripts/api.js PUT /api/models/123 '{"images":["/uploads/u_xxx.jpg"]}'
#   多张：'{"images":["/uploads/u_a.jpg","https://example.com/b.png"]}'
#   清空：'{"images":[]}'
```

- 上传支持 png / jpg / jpeg / webp / gif / svg / bmp / avif，单文件 ≤ 50MB。
- 也可直接写入外链 URL（无需上传）。
- Web / 桌面端在表格「参考图」列和详情页可手动编辑（上传 / 粘贴 URL / 删除 / 排序 / 设主图）。

## 设备引用（桌面端「引用」按钮 → 粘贴给 AI）

桌面端 / 网页的价格列表中，每个设备行都有「引用」按钮；点击后复制一段**设备引用文本**，形如：

```
【手机设备引用】设备ID:123｜品牌:华为｜分类:P系列｜型号:P40 Pro
```

用户把这段引用粘贴给你时，按以下流程处理：

1. **解析 ID**：用正则提取 `设备ID:(\d+)`（字段可能带空格或冒号/等号，兼容 `设备ID=123`）。
2. **按 ID 定位**：调用 `GET /api/models/<id>`（例如 `node skills/phone-price-api/scripts/api.js GET "/api/models/123"`）拉取该设备**数据库最新**数据。
3. **核对身份**：将返回的 `brand/category/model` 与引用文本中的品牌/分类/型号比对；不一致时以数据库为准，并向用户说明差异（ID 才是唯一主键，型号名称可能因改名/重复而不唯一）。
4. **补充数据**：基于该设备数据回答用户问题，或按用户要求补充字段（price 回收价 / note 备注 / 规格 release_date、cpu_model、ram、rom、back_camera 等 / variants 分版本价格 / images 参考图），写操作 `PUT /api/models/<id>` 自动带 Key。

> 引用文本里的品牌/分类/型号是给人看的辅助信息，**定位请一律以 设备ID 为准**。

环境变量 `PHONE_API_BASE` 可覆盖 API 地址（默认 `http://localhost:8760`），`PHONE_API_KEY` 可覆盖密钥（默认读 `server/config.json`）。

## API 端点总览

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | /api/health | 健康检查 | 无 |
| GET | /api/stats | 统计（总数/品牌数/分类数） | 无 |
| GET | /api/brands | 品牌列表及数量 | 无 |
| GET | /api/categories?brand= | 分类列表 | 无 |
| GET | /api/models | 型号查询（分页/搜索/排序） | 无 |
| GET | /api/models/:id | 单条详情 | 无 |
| POST | /api/models | 新增 | Key |
| PUT | /api/models/:id | 修改（部分字段，含 images） | Key |
| DELETE | /api/models/:id | 删除 | Key |
| POST | /api/models/bulk | 批量新增 | Key |
| POST | /api/import | 全量导入（replace:false 追加） | Key |
| POST | /api/upload | 上传图片（原始二进制 body） | Key |
| GET | /api/export | 导出旧版分组 JSON | 无 |

完整参数说明见 [references/api.md](references/api.md)。

## 认证说明

- 查询接口（GET）公开。
- 写操作（POST/PUT/DELETE/UPLOAD）需 Header `X-API-Key: <key>`，Key 保存在 `server/config.json` 的 `apiKey` 字段。
- `GET /api/config` 从本机访问时自动返回 Key。

## 数据字段

每条机型记录：`id`（主键）、`brand`（品牌）、`category`（分类）、`model`（型号名）、`price`（回收价，元，字符串）、`note`（备注，如"不开不靓板坏相250"）、`images`（参考图片 URL 数组，JSON 字符串，首图为主图）、`created_at`、`updated_at`。

## 典型场景

**查某机型回收价：**
```bash
node skills/phone-price-api/scripts/api.js GET "/api/models?search=mate70"
```

**列出某品牌降价排名：**
```bash
node skills/phone-price-api/scripts/api.js GET "/api/models?brand=Apple&sort=price_desc&limit=10"
```

**给某机型补参考图（上传 + 挂载）：**
```bash
node skills/phone-price-api/scripts/api.js UPLOAD "C:/pics/iphone16.jpg"
node skills/phone-price-api/scripts/api.js PUT /api/models/123 '{"images":["/uploads/u_xxx.jpg"]}'
```

**批量导入新报价（替换全库）：**
```bash
node skills/phone-price-api/scripts/api.js POST /api/import '[{"brand":"X","category":"Y","models":[{"model":"m1","price":"100","note":"","images":["/uploads/a.jpg"]}]}]'
```

