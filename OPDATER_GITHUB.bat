@echo off
title Jellyfin_app Auto Push
color 0A
echo.
echo ============================================
echo   Jellyfin_app - Auto opdatering til GitHub
echo ============================================
echo.

:: Gå til repo mappen (ret stien hvis din er anderledes)
cd /d "%~dp0"

echo Tjekker om det er et git repo...
if not exist ".git" (
  echo FEJL: Dette er ikke et git repo!
  echo Laeg denne .bat fil i C:\Users\sonny\Documents\GitHub\Jellyfin_app
  pause
  exit /b
)

echo.
echo Tilfoejer alle filer...
git add .

echo.
echo Commiter...
git commit -m "Update from bat - %date% %time%"

echo.
echo Pusher til GitHub...
git push origin main

if %errorlevel% neq 0 (
  echo.
  echo Kunne ikke pushe til main, proever master...
  git push origin master
)

echo.
echo ============================================
echo   FAERDIG! Tjek github.com/sonny-lab1978/Jellyfin_app/actions
echo   Din APK bliver bygget om 2-3 min
echo ============================================
echo.
pause
