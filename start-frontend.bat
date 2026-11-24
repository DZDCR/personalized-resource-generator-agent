@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

if exist "C:\Program Files\nodejs\npm.cmd" set "PATH=C:\Program Files\nodejs;%PATH%"
if exist "%LocalAppData%\Programs\nodejs\npm.cmd" set "PATH=%LocalAppData%\Programs\nodejs;%PATH%"

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js not found. Install Node.js 20+ and retry.
  pause
  exit /b 1
)

echo Starting frontend on http://localhost:5173 ...
cd /d "%~dp0frontend"
call npm run dev
endlocal
