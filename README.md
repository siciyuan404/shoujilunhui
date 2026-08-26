# 手机回收价格数据库系统

全端覆盖的机型回收报价管理平台：**NodeJS API + SQLite 数据库 + Web 端 + Electron 桌面端 + Android APP + AI Agent Skill**。

```
                      ┌────────────────────────────┐
                      │   NodeJS API (端口 8760)   │
                      │   server/src/index.js      │
                      │   SQLite: server/db/phone.db│
                      └──────┬───────┬───────┬────┘
             本地             │       │       │          内网穿透(sj隧道)
        ┌──────────────┐      │       │       └──────────────────┐
        │ Web 浏览器     ├──────┘       │                          │
        │ localhost:8760│              │                  ┌───────┴────────┐
        └──────────────┘              │                  │ Android APP    │
        ┌──────────────┐              │                  │ (远程查价/检索) │
        │ Electron 桌面端├─────────────┘                  └────────────────┘
        │ (自动拉起API)  │      ┌────────────────┐
        └──────────────┘      │ AI Agent Skill  │
                              │ skills/phone-   │
                              │ price-api       │
                              └────────────────┘
```

## 目录结构

| 目录 | 说明 | 技术栈 |
|------|------|--------|
| `server/` | API 服务 + SQLite 数据库 | NodeJS（零依赖，内置 node:sqlite） |
| `web/` | Web 前端（查价 + 验机 + 实时编辑） | 原生 HTML/JS |
| `desktop/` | Windows 桌面应用 | Electron |
| `android/` | 安卓 APP（远程查价） | Kotlin + Material3 + Retrofit |
| `skills/phone-price-api/` | AI Agent 技能包 | SKILL.md + CLI 脚本 |
| `phone-price/` | 旧版静态文件（兼容保留） | - |

## 快速开始

### 1. 启动 API 服务（核心）

```bash
cd server
node src/index.js
```

- 首次启动自动从 `phone-price/data.json` 迁移 1600+ 条数据到 SQLite
- 自动生成 `server/config.json`（含 API Key，写操作需要）
- 监听 `0.0.0.0:8760`，供内网穿透反代到公网

### 2. Web 端（本地查看）

浏览器打开 `http://localhost:8760`
- 查价、搜索、品牌筛选、验机资料
- **本机打开自动获得编辑权限**：点击单元格直接改价，失焦即实时保存
- **每个型号可挂参考图**：表格「参考图」列点击即可上传本地图片 / 粘贴图片 URL / 删除 / 排序 / 设主图
- 「+ 添加型号」新增、× 删除（带确认）
- 远程（经内网穿透）打开为只读模式

### 3. 桌面端

```bash
cd desktop
npm install        # 已安装可跳过
npm start
```

- 自动拉起/连接 API 服务
- 无边框圆角阴影窗口、标题栏拖动、Ctrl+滚轮缩放
- **与 Web 端共用页面，同样支持参考图手动编辑**
- 打包：`npm run build` 或用 GitHub Actions

### 4. Android APP（远程）

1. GitHub Actions 自动编译（推送 `android/**` 触发，或手动 workflow_dispatch）
2. 下载 Artifacts 中的 `回收查价.apk` 安装
3. 打开 APP → 设置 → 填写内网穿透地址（如 `https://xxx.example.com`）
4. 查询无需 Key；编辑/添加/删除需填 API Key（见 `server/config.json`）

### 5. AI Agent Skill

技能包位于 `skills/phone-price-api/`，SKILL.md 已写明全部用法：

```bash
# 查询
node skills/phone-price-api/scripts/api.js GET "/api/models?search=mate70"
# 改价
node skills/phone-price-api/scripts/api.js PUT /api/models/123 '{"price":"300"}'
# 上传参考图（返回 url）→ 再写入机型
node skills/phone-price-api/scripts/api.js UPLOAD "C:/pics/iphone15.jpg"
node skills/phone-price-api/scripts/api.js PUT /api/models/123 '{"images":["/uploads/u_xxx.jpg"]}'
```

安装到 TRAE：将 `skills/phone-price-api/` 目录复制到 TRAE 的用户技能目录即可。

## 参考图片

每个型号的 `images` 字段保存参考图 URL 数组（JSON 字符串，首图为主图）：

- **上传**：`POST /api/upload`（原始二进制，Content-Type: image/*）→ 存到 `server/uploads/`，经 `/uploads/...` 访问
- **挂载**：`PUT /api/models/:id` 传 `{"images":[...]}`（可数组 / JSON 字符串 / 单 URL，`[]` 清空）
- **手动编辑**：Web / 桌面端表格「参考图」列或详情页 → 上传本地图 / 粘贴 URL / 删除 / 左右排序
- **AI Skill 编辑**：`node skills/phone-price-api/scripts/api.js UPLOAD <图>` + `PUT` 写 `images`
- 上传图片不进入 git（`server/uploads/` 已加入 .gitignore）

## API 一览

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | /api/health | 健康检查 | 无 |
| GET | /api/stats | 总数/品牌数/分类数 | 无 |
| GET | /api/brands | 品牌列表 | 无 |
| GET | /api/categories?brand= | 分类列表 | 无 |
| GET | /api/models | 查询（search/brand/category/sort/page/limit/min_price/max_price） | 无 |
| GET | /api/models/:id | 单条 | 无 |
| POST | /api/models | 新增 | X-API-Key |
| PUT | /api/models/:id | 修改（部分字段） | X-API-Key |
| DELETE | /api/models/:id | 删除 | X-API-Key |
| POST | /api/models/bulk | 批量新增 | X-API-Key |
| POST | /api/import | 全量导入 | X-API-Key |
| POST | /api/upload | 上传参考图片（二进制 body） | X-API-Key |
| GET | /api/export | 导出 JSON | 无 |
| GET | /api/config | 本机返回 API Key | 本机 |

完整参数见 `skills/phone-price-api/references/api.md`。

## 安全模型

- **查询**（GET）公开 —— 远程 APP/网页可检索
- **写操作**（POST/PUT/DELETE）需 Header `X-API-Key`（`server/config.json`）
- `/api/config` 仅本机（127.0.0.1）访问返回 Key
- `config.json` 中 `readOnly: true` 可锁死服务器为只读
- 数据库文件 `server/db/phone.db` 单文件备份即可

## 数据流

```
Excel 报价单 → data.json（一次性迁移）→ SQLite phone.db
     ↑                                    ↕
     └── Web/桌面/APP/Skill 全部通过 REST API 增删改查 ──┘
```

所有端共享同一数据库，任何一端修改，其他端刷新即见。

## GitHub Actions

| 工作流 | 触发 | 产物 |
|--------|------|------|
| `android.yml` | `android/**` 变更 | 回收查价.apk |
| `desktop.yml` | `desktop/web/server` 变更 | Windows 安装包 .exe |

## 常用维护

```bash
# 备份数据库
copy server\db\phone.db backup\phone_20260826.db

# 导出旧版 JSON
curl http://localhost:8760/api/export > backup.json

# 重置 API Key（编辑 server/config.json 后重启）
```
