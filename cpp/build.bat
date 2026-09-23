@echo off
REM Build script for C++ light version (LLVM-MinGW / clang++)
setlocal
set BIN=C:\Users\62744\AppData\Local\Microsoft\WinGet\Packages\MartinStorsjo.LLVM-MinGW.UCRT_Microsoft.Winget.Source_8wekyb3d8bbwe\llvm-mingw-20260616-ucrt-x86_64\bin
set CC=%BIN%\clang++.exe
set RC=%BIN%\windres.exe
set SRC=%~dp0main.cpp
set OUT=%~dp0phone-recycle.exe

REM Compile manifest resource (enables comctl32 v6 + per-monitor DPI)
%RC% %~dp0app.rc -o %~dp0app_res.o
if %errorlevel% neq 0 ( echo [fail] windres & exit /b 1 )

echo [build] clang++ -O2 ...
%CC% -O2 -std=c++17 -municode -static -s "%SRC%" %~dp0app_res.o -o "%OUT%" -lwinhttp -lcomctl32 -lshell32 -lgdi32 -luser32 -lgdiplus -lole32 -lshlwapi -luuid -lkernel32 2>&1
if %errorlevel%==0 (
    echo [ok] built: %OUT%
    for %%F in ("%OUT%") do echo [size] %%~zF bytes
) else (
    echo [fail] build error
)
endlocal
