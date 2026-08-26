# API 完整参考

Base URL：`http://localhost:8760`（本地）；远程请替换为内网穿透地址。

## GET /api/health
健康检查。返回 `{ok, db, models, time}`。

## GET /api/stats
```json
{ "total": 1642, "brands": 8, "categories": 38, "lastUpdated": "2026-08-26 12:58:18" }
```

## GET /api/config
本机访问返回 `{ok, readOnly, apiKey, local:true}`；远程访问 `apiKey` 为 null。
- `readOnly: true` 时服务器拒绝一切写操作。

## GET /api/brands
```json
{ "items": [{ "brand": "Apple", "count": 40 }, ...], "total": 8 }
```

## GET /api/categories
查询参数：`brand`（可选，过滤品牌）。
```json
{ "items": [{ "brand": "Apple", "category": "iPhone", "count": 29 }, ...], "total": 3 }
```

## GET /api/models
查询参数：

| 参数 | 说明 | 默认 |
|------|------|------|
| brand | 精确匹配品牌 | - |
| category | 精确匹配分类 | - |
| search | 模糊匹配型号/备注/品牌/分类 | - |
| min_price / max_price | 价格区间（数值比较） | - |
| sort | `price_asc` `price_desc` `name` `name_desc` `updated` `brand` `id` | brand |
| page | 页码 | 1 |
| limit | 每页条数；`0` 或缺省 = 全部（上限 5000） | 0 |

返回：
```json
{
  "total": 1642, "page": 1, "limit": 50,
  "items": [
    { "id": 1, "brand": "Apple", "category": "iPhone", "model": "苹果4-5系",
      "price": "20", "note": "", "images": "[\"/uploads/u_xxx.jpg\"]",
      "created_at": "...", "updated_at": "..." }
  ]
}
```
`images` 为 JSON 字符串数组（参考图片 URL），首图为主图；无图为 `"[]"`。

## GET /api/models/:id
返回单条记录；不存在时 404。

## POST /api/upload（需 Key）
上传本地图片，得到可写入 `images` 的 URL。
- 请求：`Content-Type: image/*`，body 为图片原始二进制（png/jpg/jpeg/webp/gif/svg/bmp/avif，≤50MB）。
- CLI 用法：`node api.js UPLOAD "C:/pics/a.jpg"`
- 返回：`{ "ok": true, "url": "/uploads/u_xxx.jpg", "name": "u_xxx.jpg" }`
- 上传的图片通过 `http://localhost:8760/uploads/u_xxx.jpg` 访问。

## POST /api/models（需 Key）
Body：`{"brand":"华为","category":"Mate系列","model":"Mate80","price":"500","note":"可选","images":["/uploads/a.jpg"]}`
- brand/category/model 必填；price/note/optional images（数组）可选。
- 成功返回 201 + 完整记录（含新 id）。

## PUT /api/models/:id（需 Key）
Body 传需修改的字段即可（部分更新）：`{"price":"550"}` 或 `{"model":"新名字"}` 或 `{"images":["/uploads/a.jpg","https://x.com/b.png"]}`。
- `images` 接受数组 / JSON 字符串 / 单个 URL，空数组 `[]` 清空参考图。
- 成功返回 200 + 更新后记录；updated_at 自动刷新。

## DELETE /api/models/:id（需 Key）
成功返回 `{"ok":true,"id":123}`。

## POST /api/models/bulk（需 Key）
Body：`{"items":[{brand,category,model,price,note}, ...]}` 或直接传数组。
事务批量插入，返回 `{ok:true, inserted:N}`。

## POST /api/import（需 Key）
Body：`[{brand, category, models:[{model,price,note}...]}...]`（旧 data.json 结构）。
- `replace` 缺省 `true` = 清空后导入；`{"replace":false, "data":[...]}` = 追加。

## GET /api/export
导出旧版 data.json 分组结构（用于备份）。

## 认证
写操作携带 Header：`X-API-Key: <server/config.json 中的 apiKey>`。
（也可用查询参数 `?key=...`，但 Header 更安全。）

## 错误格式
```json
{ "error": "未授权：请在 Header 中携带 X-API-Key" }
```
