@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0"

REM Prefer the default Node.js installation if it is not in PATH.
if exist "C:\Program Files\nodejs\npm.cmd" (
  set "PATH=C:\Program Files\nodejs;%PATH%"
)

echo ========================================
echo  Learning Chat - one click demo
echo ========================================
echo  Backend : http://localhost:8080  (H2 demo profile)
echo  Frontend: http://localhost:5173/im
echo ========================================
echo.

echo [1/3] Starting backend with demo profile...
start "learning-backend" cmd /k "cd /d %~dp0backend && run-demo.bat"
echo      Waiting for backend to become ready...
powershell -NoProfile -Command "Start-Sleep -Seconds 9"

echo [2/3] Starting frontend...
start "learning-frontend" cmd /k "cd /d %~dp0frontend && npm run dev"
echo      Waiting for Vite to start...
powershell -NoProfile -Command "Start-Sleep -Seconds 5"

echo [3/3] Opening browser...
start "" "http://localhost:5173/im"

echo.
echo If the page still cannot connect:
echo   1. Check the "learning-backend" window for startup errors.
echo   2. Check the "learning-frontend" window for the Vite URL.
echo   3. Do not open http://localhost:8080 as a web page; it is the API server.
echo.
pause
endlocal
