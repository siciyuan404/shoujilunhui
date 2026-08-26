const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('__desktop', {
  minimize: () => ipcRenderer.invoke('win:minimize'),
  toggleMaximize: () => ipcRenderer.invoke('win:toggle'),
  close: () => ipcRenderer.invoke('win:close'),
});
