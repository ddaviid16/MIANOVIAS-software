@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-bd.ps1"
if errorlevel 1 (
  echo.
  echo [FALLO] Revisa los mensajes de error arriba.
)
pause
