const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('__desktop', {
  minimize: () => ipcRenderer.invoke('win:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('win:toggle'),
  close: () => ipcRenderer.invoke('win:close'),
  checkUpdate: () => ipcRenderer.invoke('update:check'),
  downloadUpdate: () => ipcRenderer.invoke('update:download'),
  installUpdate: (filePath) => ipcRenderer.invoke('update:install', filePath),
  onUpdateProgress: (cb) => {
    const listener = (_e, data) => { try { cb(data); } catch (_) {} };
    ipcRenderer.on('update:progress', listener);
    return () => ipcRenderer.removeListener('update:progress', listener);
  },
  getVersion: () => ipcRenderer.invoke('app:version'),
  openExternal: (url) => ipcRenderer.invoke('shell:open', url),
});
