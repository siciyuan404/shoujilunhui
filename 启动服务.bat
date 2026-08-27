@echo off
title 手机回收 API 服务 (端口 8760)
cd /d "F:\git\shoujilunhui\server"

netstat -ano | findstr ":8760" | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo 检测到 8760 端口已有旧服务，正在自动停止...
  for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8760" ^| findstr "LISTENING"') do taskkill /f /pid %%p >nul 2>&1
  timeout /t 3 >nul
)

set "NODE=C:\Users\62744\AppData\Roaming\fnm\node-versions\v24.18.0\installation\node.exe"
if not exist "%NODE%" set "NODE=node"

echo 使用 Node: %NODE%
echo 服务启动中... 关闭本窗口即停止服务。
echo.
"%NODE%" src/index.js