@echo off
REM Learning Multi-Agent System - Windows launcher with menu
REM Requires: Node 20+ (frontend), JDK 21 + Maven 3.9+ (backend), Docker (infra)

cd /d "%~dp0"

:MENU
cls
echo ========================================
echo  Learning Multi-Agent System Launcher
echo ========================================
echo  [1] Start existing system
echo  [2] Create new project (Vite + Vue3)
echo  [3] Exit
echo ========================================
set "CHOICE="
set /p CHOICE="Select [1-3]: "
if "%CHOICE%"=="1" goto START
if "%CHOICE%"=="2" goto CREATE
if "%CHOICE%"=="3" exit /b 0
goto MENU

:START
echo.
where docker >nul 2>nul
if %errorlevel%==0 (
  echo [1/4] Starting infrastructure via docker compose ...
  docker compose -f docker\docker-compose.yml up -d postgres redis rabbitmq neo4j qdrant
  echo Waiting for databases ...
  timeout /t 10 /nobreak >nul
) else (
  echo [1/4] docker not found - skip infrastructure
)

where mvn >nul 2>nul
if %errorlevel%==0 (
  echo [2/4] Starting backend on port 8080 ...
  start "learning-backend" cmd /k "cd /d %~dp0backend && set OPENAI_API_KEY=sk-xxxx && mvn spring-boot:run"
) else (
  echo [2/4] mvn not found - skip backend
)

where node >nul 2>nul
if %errorlevel%==0 (
  echo [3/4] Starting frontend on port 5173 ...
  start "learning-frontend" cmd /k "cd /d %~dp0frontend && npm run dev"
) else (
  echo [3/4] node not found - skip frontend
)

echo [4/4] Done.
echo.
echo ========================================
echo  Frontend: http://localhost:5173
echo  Backend:  http://localhost:8080
echo  Set a real OPENAI_API_KEY before using backend AI features.
echo ========================================
pause
goto MENU

:CREATE
echo.
set "PDIR="
set /p PDIR="New project folder (absolute path, e.g. D:\dev\my-app): "
if "%PDIR%"=="" (
  echo No path entered, cancelled.
  pause
  goto MENU
)
if exist "%PDIR%" (
  echo ERROR: "%PDIR%" already exists. Choose another folder.
  pause
  goto MENU
)
where node >nul 2>nul
if not %errorlevel%==0 (
  echo ERROR: node not found. Install Node.js 20+ first.
  pause
  goto MENU
)
REM Split absolute path into parent dir + leaf (create-vite sanitizes
REM colons/backslashes, so pass only the leaf name with cwd at parent)
for %%F in ("%PDIR%") do set "PPARENT=%%~dpF"
for %%F in ("%PDIR%") do set "PLEAF=%%~nxF"
if not exist "%PPARENT%" mkdir "%PPARENT%"
echo.
echo Scaffolding Vite + Vue3 project at: %PDIR%
REM stdin from nul keeps create-vite non-interactive (template vue preselected)
pushd "%PPARENT%"
call npm create vite@latest "%PLEAF%" -- --template vue < nul
if errorlevel 1 (
  echo ERROR: scaffolding failed.
  popd
  pause
  goto MENU
)
cd /d "%PDIR%"
echo Installing dependencies ...
call npm install
if errorlevel 1 (
  echo ERROR: npm install failed.
  popd
  pause
  goto MENU
)
echo.
echo Optional stack packages (element-plus pinia vue-router axios) ...
set "ADDEXT="
set /p ADDEXT="Install them too? [y/n]: "
if /i "%ADDEXT%"=="y" call npm install element-plus pinia vue-router axios
echo.
echo Project ready at: %PDIR%
set "RUNNOW="
set /p RUNNOW="Start dev server now? [y/n]: "
if /i "%RUNNOW%"=="y" start "new-project-dev" /d "%PDIR%" cmd /k npm run dev
popd
echo.
pause
goto MENU
