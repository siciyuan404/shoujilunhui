const { app, BrowserWindow, ipcMain, shell, screen } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');
const https = require('https');
const fs = require('fs');

const PORT = 8760;
const GITHUB_REPO = 'siciyuan404/shoujilunhui';
const GITHUB_API = `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`;
const SERVER_JS = app.isPackaged
  ? path.join(process.resourcesPath, 'server', 'src', 'index.js')
  : path.join(__dirname, '..', 'server', 'src', 'index.js');
let serverProc = null;
let mainWindow = null;

// ===== 窗口状态持久化（手写，参考 electron-window-state / VS Code 思路，零依赖） =====
const MIN_W = 760, MIN_H = 520;
const DEFAULT_STATE = { width: 1280, height: 860, x: undefined, y: undefined, isMaximized: false };

function stateFile() {
  return path.join(app.getPath('userData'), 'window-state.json');
}

// 读取上次窗口状态（含基础校验：尺寸下限、位置不跑出屏幕）
function loadWindowState() {
  const s = Object.assign({}, DEFAULT_STATE);
  try {
    if (fs.existsSync(stateFile())) {
      const j = JSON.parse(fs.readFileSync(stateFile(), 'utf-8'));
      s.width = Math.max(MIN_W, Number(j.width) || 1280);
      s.height = Math.max(MIN_H, Number(j.height) || 860);
      s.isMaximized = !!j.isMaximized;
      if (typeof j.x === 'number' && typeof j.y === 'number') { s.x = j.x; s.y = j.y; }
    }
  } catch (e) {}
  // 位置校验：窗口必须至少 40px 可见，否则回退到主屏居中
  if (s.x !== undefined && s.y !== undefined) {
    const displays = screen.getAllDisplays();
    const visible = displays.some((d) => {
      const wa = d.workArea;
      return s.x + 40 < wa.x + wa.width && s.x + s.width - 40 > wa.x &&
             s.y + 40 < wa.y + wa.height && s.y + s.height - 40 > wa.y;
    });
    if (!visible) { s.x = undefined; s.y = undefined; }
  }
  return s;
}

// 保存窗口状态：最大化时记录"还原后"的边界
function saveWindowState(win) {
  try {
    if (!win || win.isDestroyed()) return;
    const isMax = win.isMaximized();
    const b = win.getNormalBounds();
    const s = { x: b.x, y: b.y, width: Math.max(MIN_W, b.width), height: Math.max(MIN_H, b.height), isMaximized: isMax };
    fs.mkdirSync(path.dirname(stateFile()), { recursive: true });
    fs.writeFileSync(stateFile(), JSON.stringify(s), 'utf-8');
  } catch (e) {}
}

function checkServer() {
  return new Promise((resolve) => {
    const req = http.get(`http://127.0.0.1:${PORT}/api/health`, (res) => { res.resume(); resolve(res.statusCode === 200); });
    req.on('error', () => resolve(false));
    req.setTimeout(1200, () => { req.destroy(); resolve(false); });
  });
}

// ===== 基于 GitHub tag 的版本更新检查 =====
function parseVersion(v) {
  return String(v || '').replace(/^v/i, '').split(/[.\-]/).map((x) => parseInt(x, 10) || 0);
}
function isNewer(latestTag, cur) {
  const a = parseVersion(latestTag), b = parseVersion(cur);
  const len = Math.max(a.length, b.length);
  for (let i = 0; i < len; i++) {
    const x = a[i] || 0, y = b[i] || 0;
    if (x !== y) return x > y;
  }
  return false;
}

function fetchLatestRelease() {
  return new Promise((resolve) => {
    const req = https.get(GITHUB_API, {
      headers: { 'User-Agent': 'phone-recycle-desktop', 'Accept': 'application/vnd.github+json' },
    }, (res) => {
      let d = '';
      res.on('data', (c) => (d += c));
      res.on('end', () => {
        try {
          const j = JSON.parse(d);
          if (j && j.tag_name) {
            resolve({
              tag: j.tag_name,
              url: j.html_url,
              publishedAt: j.published_at,
              body: j.body || '',
              assets: (j.assets || []).map((a) => ({ name: a.name, url: a.browser_download_url, size: a.size })),
            });
          } else { resolve(null); }
        } catch (e) { resolve(null); }
      });
    });
    req.on('error', () => resolve(null));
    req.setTimeout(8000, () => { req.destroy(); resolve(null); });
  });
}

async function checkUpdate() {
  const rel = await fetchLatestRelease();
  if (!rel) return null;
  const currentVersion = app.getVersion();
  return {
    latestTag: rel.tag,
    currentVersion,
    hasUpdate: isNewer(rel.tag, currentVersion),
    url: rel.url,
    publishedAt: rel.publishedAt,
    body: rel.body,
    assets: rel.assets,
  };
}

// 静默检查：不自动弹原生对话框（更新改为前端「检查更新」按钮点击触发，对齐 MeowMic pc 端）
function maybePromptUpdate() {
  checkUpdate().then((info) => {
    if (info && info.hasUpdate) {
      console.log(`[desktop] 发现新版本 v${info.latestTag}（当前 v${info.currentVersion}），前端已高亮更新按钮，点击触发下载`);
    }
  }).catch(() => {});
}

// ===== 应用内下载安装包并拉起安装程序（免跳浏览器，一键覆盖更新） =====
// GitHub release 下载会 301/302 跳转到 objects.githubusercontent.com，需手动跟随重定向
function downloadFile(url, dest, onProgress, redirects) {
  redirects = redirects === undefined ? 5 : redirects;
  return new Promise((resolve, reject) => {
    const req = https.get(url, { headers: { 'User-Agent': 'phone-recycle-desktop' } }, (res) => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        if (redirects <= 0) return reject(new Error('下载重定向过多'));
        return resolve(downloadFile(res.headers.location, dest, onProgress, redirects - 1));
      }
      if (res.statusCode !== 200) {
        res.resume();
        return reject(new Error('下载失败（HTTP ' + res.statusCode + '）'));
      }
      const file = fs.createWriteStream(dest);
      const total = Number(res.headers['content-length']) || 0;
      let received = 0;
      const fail = (e) => {
        file.destroy();
        try { fs.unlinkSync(dest); } catch (_) {}
        reject(e);
      };
      res.on('data', (c) => {
        received += c.length;
        if (onProgress) onProgress(received, total);
      });
      res.pipe(file);
      file.on('finish', () => file.close(() => resolve(dest)));
      file.on('error', fail);
      res.on('error', fail);
    });
    req.on('error', (e) => reject(e));
    req.setTimeout(30000, () => req.destroy(new Error('下载连接超时')));
  });
}

// 下载最新版安装包到临时目录（带进度推送），返回本地路径
async function downloadLatestSetup() {
  const info = await checkUpdate();
  if (!info) throw new Error('无法获取更新信息');
  const exe = (info.assets || []).find((a) => /\.exe$/i.test(a.name));
  if (!exe) throw new Error('最新版本未找到安装包');
  const dest = path.join(app.getPath('temp'), 'shoujilunhui-setup-' + info.latestTag + '.exe');
  // 已下载且体积吻合则直接复用（断点续装场景）
  if (fs.existsSync(dest)) {
    const st = fs.statSync(dest);
    if (st.size > 0 && (!exe.size || st.size === exe.size)) return { filePath: dest, info, cached: true };
  }
  const send = (received, total) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:progress', {
        received, total,
        percent: total ? Math.round((received / total) * 100) : 0,
      });
    }
  };
  await downloadFile(exe.url, dest, send);
  // 下载完整性校验：与 release 声明的体积比对
  const st = fs.statSync(dest);
  if (exe.size && Math.abs(st.size - exe.size) > 1024) {
    try { fs.unlinkSync(dest); } catch (_) {}
    throw new Error('下载校验失败（体积不符），请重试');
  }
  return { filePath: dest, info };
}

async function ensureServer() {
  if (await checkServer()) { console.log('[desktop] API 服务已在运行'); return; }
  console.log('[desktop] 拉起 API 服务...');
  serverProc = spawn(process.execPath, [SERVER_JS], {
    cwd: path.dirname(SERVER_JS),
    stdio: 'ignore',
    windowsHide: true,
    env: Object.assign({}, process.env, { ELECTRON_RUN_AS_NODE: '1' }),
  });
  serverProc.on('exit', (code) => { serverProc = null; });
  for (let i = 0; i < 60; i++) {
    await new Promise((r) => setTimeout(r, 250));
    if (await checkServer()) { console.log('[desktop] API 服务就绪'); return; }
  }
  throw new Error('API 服务启动超时');
}

// 注入样式：系统标题栏（titleBarOverlay）方案。
// - 不再需要 html transparent / body 圆角阴影（窗口边框与圆角由系统绘制）
// - header 为拖拽区，内部所有可交互元素标记 no-drag
// - 保留失焦时头部压暗反馈（窗口级阴影由系统自动处理）
const injectCss = `
  body { min-height: 100vh !important; }
  .header { -webkit-app-region: drag; -webkit-user-select: none; }
  .header button, .header a, .header input, .header select, .header .mode-switch,
  .header .update-info, .header .app-update-bar, .header .update-check-btn, .header .mode-btn {
    -webkit-app-region: no-drag;
  }
  body.win-blur .header { filter: saturate(.6) brightness(.92); }
`;

const injectJs = `
  (function () {
    if (window.__desktopInjected) return;
    window.__desktopInjected = true;
    document.addEventListener('keydown', (e) => {
      if (e.key === 'F5') { e.preventDefault(); location.reload(); }
    });
  })();
`;

async function createWindow() {
  await ensureServer();

  const winState = loadWindowState();

  mainWindow = new BrowserWindow({
    x: winState.x,
    y: winState.y,
    width: winState.width,
    height: winState.height,
    minWidth: MIN_W,
    minHeight: MIN_H,
    // 隐藏系统标题栏内容，但保留系统原生边框/阴影/圆角与 Snap 行为
    titleBarStyle: 'hidden',
    frame: true,
    // Windows 11：右上角放回系统原生窗口按钮（最小化/最大化/关闭），颜色匹配页面蓝色渐变头部
    titleBarOverlay: { color: '#0d5fd9', symbolColor: '#ffffff', height: 40 },
    backgroundColor: '#f0f2f5',
    show: false,
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  if (winState.isMaximized) mainWindow.maximize();

  // Ctrl+滚轮 缩放
  mainWindow.webContents.setVisualZoomLevelLimits(1, 3);

  mainWindow.loadURL(`http://127.0.0.1:${PORT}/`);
  mainWindow.once('ready-to-show', () => mainWindow.show());

  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow.webContents.insertCSS(injectCss);
    mainWindow.webContents.executeJavaScript(injectJs);
  });

  // 焦点状态视觉区分（仅头部压暗；窗口阴影由系统随焦点自动变化）
  const setBlur = (on) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.executeJavaScript(
        `document.body.classList.toggle('win-blur', ${on}); void 0;`
      ).catch(() => {});
    }
  };
  mainWindow.on('blur', () => setBlur(true));
  mainWindow.on('focus', () => setBlur(false));

  // 窗口状态自动记忆（防抖写盘 + 关闭时兜底保存）
  let stateTimer = null;
  const scheduleSave = () => {
    if (stateTimer) clearTimeout(stateTimer);
    stateTimer = setTimeout(() => { stateTimer = null; saveWindowState(mainWindow); }, 400);
  };
  mainWindow.on('resize', scheduleSave);
  mainWindow.on('move', scheduleSave);
  mainWindow.on('close', () => {
    if (stateTimer) clearTimeout(stateTimer);
    saveWindowState(mainWindow);
  });

  mainWindow.on('closed', () => { mainWindow = null; });

  ipcMain.handle('win:minimize', () => mainWindow && mainWindow.minimize());
  ipcMain.handle('win:toggle', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize(); else mainWindow.maximize();
  });
  ipcMain.handle('win:close', () => mainWindow && mainWindow.close());
  ipcMain.handle('update:check', () => checkUpdate());
  // 一键更新：下载安装包 → 拉起安装程序（下一步下一步，覆盖安装）
  ipcMain.handle('update:download', async () => {
    try {
      const r = await downloadLatestSetup();
      return { ok: true, filePath: r.filePath, tag: r.info.latestTag, cached: !!r.cached };
    } catch (e) {
      return { ok: false, error: e.message };
    }
  });
  ipcMain.handle('update:install', (_e, filePath) => {
    if (!filePath || typeof filePath !== 'string' || !fs.existsSync(filePath)) {
      return { ok: false, error: '安装包不存在或已删除' };
    }
    try {
      const child = spawn(filePath, [], { detached: true, stdio: 'ignore' });
      child.unref();
      // 给安装器一点启动时间后退出应用，由 NSIS 安装向导接管（完成后自动运行新版本）
      setTimeout(() => { try { app.quit(); } catch (_) {} }, 1500);
      return { ok: true };
    } catch (e) {
      return { ok: false, error: e.message };
    }
  });
  ipcMain.handle('app:version', () => app.getVersion());
  ipcMain.handle('shell:open', (_e, url) => shell.openExternal(url));

  // 启动 4 秒后检查更新（避免打断首屏）
  setTimeout(maybePromptUpdate, 4000);
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (serverProc) { try { serverProc.kill(); } catch (e) {} }
  app.quit();
});

app.on('before-quit', () => {
  if (serverProc) { try { serverProc.kill(); } catch (e) {} }
});
