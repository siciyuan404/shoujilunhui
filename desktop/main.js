const { app, BrowserWindow, ipcMain } = require('electron');
const { spawn } = require('child_process');
const path = require('path');
const http = require('http');

const PORT = 8760;
const SERVER_JS = app.isPackaged
  ? path.join(process.resourcesPath, 'server', 'src', 'index.js')
  : path.join(__dirname, '..', 'server', 'src', 'index.js');
let serverProc = null;
let mainWindow = null;

function checkServer() {
  return new Promise((resolve) => {
    const req = http.get(`http://127.0.0.1:${PORT}/api/health`, (res) => { res.resume(); resolve(res.statusCode === 200); });
    req.on('error', () => resolve(false));
    req.setTimeout(1200, () => { req.destroy(); resolve(false); });
  });
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

const injectCss = `
  html { background: transparent !important; padding: 8px !important; }
  body {
    border-radius: 14px !important;
    box-shadow: 0 12px 48px rgba(0,0,0,.38) !important;
    min-height: calc(100vh - 16px) !important;
    overflow: hidden !important;
    transition: box-shadow .25s !important;
  }
  body.win-blur { box-shadow: 0 4px 18px rgba(0,0,0,.18) !important; }
  body.win-blur .header { filter: saturate(.6) brightness(.92); }
  .header { -webkit-app-region: drag; }
  .header .mode-switch, .header .update-info { -webkit-app-region: no-drag; }
  .header .mode-btn { -webkit-app-region: no-drag; }
  .win-ctrl {
    position: fixed; top: 10px; right: 12px; z-index: 99999;
    display: flex; gap: 2px; -webkit-app-region: no-drag;
  }
  .win-ctrl button {
    width: 30px; height: 26px; border: none; border-radius: 6px; cursor: pointer;
    background: rgba(255,255,255,.16); color: #fff; font-size: 13px; line-height: 1;
    font-family: inherit; transition: background .15s;
  }
  .win-ctrl button:hover { background: rgba(255,255,255,.35); }
  .win-ctrl button.win-close:hover { background: #e81123; }
  .win-ctrl svg { width: 12px; height: 12px; fill: currentColor; }
`;

const injectJs = `
  (function () {
    if (document.getElementById('winCtrlBar')) return;
    const bar = document.createElement('div');
    bar.id = 'winCtrlBar';
    bar.className = 'win-ctrl';
    bar.innerHTML = [
      '<button id="wcMin" title="最小化"><svg viewBox="0 0 12 12"><rect x="1" y="5.5" width="10" height="1"/></svg></button>',
      '<button id="wcMax" title="最大化/还原"><svg viewBox="0 0 12 12"><rect x="1.5" y="1.5" width="9" height="9" fill="none" stroke="currentColor" stroke-width="1.2"/></svg></button>',
      '<button id="wcClose" class="win-close" title="关闭"><svg viewBox="0 0 12 12"><path d="M1.5 1.5 L10.5 10.5 M10.5 1.5 L1.5 10.5" stroke="currentColor" stroke-width="1.2" fill="none"/></svg></button>',
    ].join('');
    document.body.appendChild(bar);
    document.getElementById('wcMin').onclick = () => window.__desktop.minimize();
    document.getElementById('wcMax').onclick = () => window.__desktop.toggleMaximize();
    document.getElementById('wcClose').onclick = () => window.__desktop.close();
    document.addEventListener('keydown', (e) => {
      if (e.key === 'F5') { e.preventDefault(); location.reload(); }
    });
  })();
`;

async function createWindow() {
  await ensureServer();

  mainWindow = new BrowserWindow({
    width: 1280,
    height: 860,
    minWidth: 760,
    minHeight: 520,
    frame: false,
    transparent: true,
    backgroundColor: '#00000000',
    show: false,
    icon: path.join(__dirname, 'assets', 'icon.png'),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
    },
  });

  // Ctrl+滚轮 缩放
  mainWindow.webContents.setVisualZoomLevelLimits(1, 3);

  mainWindow.loadURL(`http://127.0.0.1:${PORT}/`);
  mainWindow.once('ready-to-show', () => mainWindow.show());

  mainWindow.webContents.on('did-finish-load', () => {
    mainWindow.webContents.insertCSS(injectCss);
    mainWindow.webContents.executeJavaScript(injectJs);
  });

  // 焦点状态视觉区分
  const setBlur = (on) => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.executeJavaScript(
        `document.body.classList.toggle('win-blur', ${on}); void 0;`
      ).catch(() => {});
    }
  };
  mainWindow.on('blur', () => setBlur(true));
  mainWindow.on('focus', () => setBlur(false));

  // 最大化时移除圆角
  mainWindow.on('maximize', () => {
    mainWindow.webContents.insertCSS('body { border-radius: 0 !important; } html { padding: 0 !important; }').catch(() => {});
  });
  mainWindow.on('unmaximize', () => {
    mainWindow.webContents.insertCSS(injectCss).catch(() => {});
  });

  mainWindow.on('closed', () => { mainWindow = null; });

  ipcMain.handle('win:minimize', () => mainWindow && mainWindow.minimize());
  ipcMain.handle('win:toggle', () => {
    if (!mainWindow) return;
    if (mainWindow.isMaximized()) mainWindow.unmaximize(); else mainWindow.maximize();
  });
  ipcMain.handle('win:close', () => mainWindow && mainWindow.close());
}

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (serverProc) { try { serverProc.kill(); } catch (e) {} }
  app.quit();
});

app.on('before-quit', () => {
  if (serverProc) { try { serverProc.kill(); } catch (e) {} }
});
