@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul

echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║   MIANOVIAS — Build setup-bd.exe                    ║
echo ╚══════════════════════════════════════════════════════╝
echo.

rem ── Rutas ─────────────────────────────────────────────────────────────────
set "DIR=%~dp0"
set "WIX_HOME=C:\Program Files (x86)\WiX Toolset v3.14\bin"
set "OUT=%DIR%output"

rem ── Verificaciones ────────────────────────────────────────────────────────
if not exist "%WIX_HOME%\candle.exe" (
  echo [ERROR] WiX Toolset 3.14 no encontrado en: %WIX_HOME%
  echo         Descargalo en: https://wixtoolset.org/releases/
  pause & exit /b 1
)

if not exist "%DIR%mysql-8.0.43-winx64.msi" (
  echo [ERROR] Falta: %DIR%mysql-8.0.43-winx64.msi
  echo.
  echo  Descarga MySQL Server 8.0.43 ^(solo el servidor, no el Installer completo^):
  echo  1. Ve a: https://dev.mysql.com/downloads/mysql/8.0.html
  echo  2. Selecciona: Windows ^(x86, 64-bit^), MSI Installer
  echo  3. Descarga el archivo "mysql-8.0.43-winx64.msi" ^(~50 MB^)
  echo  4. Colocalo en esta carpeta: %DIR%
  echo.
  pause & exit /b 1
)

rem ── Preparar carpeta de salida ─────────────────────────────────────────────
if exist "%OUT%" rmdir /S /Q "%OUT%"
mkdir "%OUT%"

rem ── Codificar scripts PS1 a Base64 (UTF-16LE, requerido por -EncodedCommand) ─
echo [1/4] Codificando scripts PowerShell...

for /f "usebackq delims=" %%b in (`powershell -NoProfile -Command ^
  "[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes((Get-Content -Raw '%DIR%config-mysql.ps1')))"`) do (
  set "MYSQL_B64=%%b"
)
if "!MYSQL_B64!"=="" (
  echo [ERROR] No se pudo codificar config-mysql.ps1
  pause & exit /b 1
)

for /f "usebackq delims=" %%b in (`powershell -NoProfile -Command ^
  "[Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes((Get-Content -Raw '%DIR%config-workbench.ps1')))"`) do (
  set "WB_B64=%%b"
)
if "!WB_B64!"=="" (
  echo [ERROR] No se pudo codificar config-workbench.ps1
  pause & exit /b 1
)

rem ── Compilar config-bd.msi ───────────────────────────────────────────────
echo [2/4] Compilando config-bd.msi...

"%WIX_HOME%\candle.exe" ^
  -nologo ^
  -ext "%WIX_HOME%\WixUtilExtension.dll" ^
  "-dMySQLPwdB64=!MYSQL_B64!" ^
  "-dWorkbenchB64=!WB_B64!" ^
  -out "%OUT%\config-bd.wixobj" ^
  "%DIR%config-bd.wxs" > "%OUT%\build.log" 2>&1

if errorlevel 1 (
  echo [ERROR] candle fallo en config-bd.wxs. Ver: %OUT%\build.log
  pause & exit /b 1
)

"%WIX_HOME%\light.exe" ^
  -nologo ^
  -ext "%WIX_HOME%\WixUtilExtension.dll" ^
  -out "%OUT%\config-bd.msi" ^
  "%OUT%\config-bd.wixobj" >> "%OUT%\build.log" 2>&1

if errorlevel 1 (
  echo [ERROR] light fallo en config-bd.msi. Ver: %OUT%\build.log
  pause & exit /b 1
)

rem ── Compilar Bundle (setup-bd.exe) ───────────────────────────────────────
echo [3/4] Compilando Bundle...

"%WIX_HOME%\candle.exe" ^
  -nologo ^
  -ext "%WIX_HOME%\WixBalExtension.dll" ^
  -ext "%WIX_HOME%\WixUtilExtension.dll" ^
  -out "%OUT%\Bundle.wixobj" ^
  "%DIR%Bundle.wxs" >> "%OUT%\build.log" 2>&1

if errorlevel 1 (
  echo [ERROR] candle fallo en Bundle.wxs. Ver: %OUT%\build.log
  pause & exit /b 1
)

"%WIX_HOME%\light.exe" ^
  -nologo ^
  -ext "%WIX_HOME%\WixBalExtension.dll" ^
  -ext "%WIX_HOME%\WixUtilExtension.dll" ^
  -out "%OUT%\setup-bd.exe" ^
  "%OUT%\Bundle.wixobj" >> "%OUT%\build.log" 2>&1

if errorlevel 1 (
  echo [ERROR] light fallo en setup-bd.exe. Ver: %OUT%\build.log
  pause & exit /b 1
)

rem ── Mover el EXE a la raiz del proyecto ──────────────────────────────────
echo [4/4] Listo.
copy /Y "%OUT%\setup-bd.exe" "%DIR%setup-bd.exe" >nul

echo.
echo  ✓ Generado: %DIR%setup-bd.exe
echo.
echo  Enviale este archivo al cliente. Al ejecutarlo:
echo    • Si MySQL NO esta instalado  → instala MySQL 8.0.43 + configura root
echo    • Si MySQL YA esta instalado  → omite la instalacion
echo    • En ambos casos              → registra la conexion MIANOVIAS en Workbench
echo                                   ^(solo si no existe ya^)
echo.
pause
