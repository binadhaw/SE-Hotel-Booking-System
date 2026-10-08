@echo off
REM ---------------------------------------------------------------------------
REM  Shares the locally running hotel app on the internet through a free
REM  Cloudflare quick tunnel (no Cloudflare account needed).
REM
REM  Usage:  start-tunnel.bat          (app on port 8080)
REM          start-tunnel.bat 8081     (app on another port)
REM
REM  The public https://....trycloudflare.com address is printed below and
REM  changes every time the tunnel starts. Close this window to stop sharing.
REM ---------------------------------------------------------------------------
setlocal
title Aura Grand Luxe - Cloudflare tunnel

set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

REM Find cloudflared (PATH first, then the default install folders)
set "CF="
for %%I in (cloudflared.exe) do set "CF=%%~$PATH:I"
if not defined CF if exist "%ProgramFiles(x86)%\cloudflared\cloudflared.exe" set "CF=%ProgramFiles(x86)%\cloudflared\cloudflared.exe"
if not defined CF if exist "%ProgramFiles%\cloudflared\cloudflared.exe" set "CF=%ProgramFiles%\cloudflared\cloudflared.exe"
if not defined CF (
    echo cloudflared is not installed.
    echo Install it with:  winget install --id Cloudflare.cloudflared
    pause
    exit /b 1
)

REM Make sure the app is running before opening the tunnel
powershell -NoProfile -Command "if (-not (Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue)) { exit 1 }"
if errorlevel 1 (
    echo Nothing is running on port %PORT%.
    echo Start the app first ^(mvnw spring-boot:run^), then run this file again.
    pause
    exit /b 1
)

echo.
echo  Sharing http://localhost:%PORT% through Cloudflare...
echo  Look for the line with  https://....trycloudflare.com  below - that is your public link.
echo  Reminder: change the demo account passwords before sharing the link.
echo  Close this window (or press Ctrl+C) to stop sharing.
echo.
"%CF%" tunnel --no-autoupdate --url http://localhost:%PORT%

echo.
echo Tunnel stopped.
pause
