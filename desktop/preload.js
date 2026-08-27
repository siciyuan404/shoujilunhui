const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('__desktop', {
  minimize: () => ipcRenderer.invoke('win:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('win:toggle'),
  close: () => ipcRenderer.invoke('win:close'),
  checkUpdate: () => ipcRenderer.invoke('update:check'),
  getVersion: () => ipcRenderer.invoke('app:version'),
  openExternal: (url) => ipcRenderer.invoke('shell:open', url),
});
