@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ================== CODIFICACION Y JDK ==================
rem Si tu ruta es distinta, AJUSTA esta línea a tu JDK real
set "JAVA_HOME=C:\Program Files\Java\jdk-24"
if not exist "%JAVA_HOME%\bin\javac.exe" (
  echo [ERROR] JAVA_HOME no apunta a un JDK valido: %JAVA_HOME%
  goto :fail
)
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ================== CONFIG BASICA ==================
set "APP_NAME=MIANOVIAS"
set "MAIN_CLASS=Vista.menuPrincipal"
set "VERSION=1.0.0"
set "UPGRADE_UUID=B0C1F89B-7E53-4B1E-9B41-AD5F51C2A9A"

set "SRC_DIR=%CD%"
set "OUT_DIR=build"
set "CLS_DIR=%OUT_DIR%\classes"
set "APP_DIR=%OUT_DIR%\app"
set "LIB_DIR=%APP_DIR%\lib"
set "INST_DIR=%OUT_DIR%\installer"
set "LOG=%OUT_DIR%\last_build.log"

rem ================== PREP ENTORNO ==================
if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
> "%LOG%" echo [START] %DATE% %TIME%
>>"%LOG%" echo [INFO] Proyecto: %CD%
>>"%LOG%" where java
>>"%LOG%" where javac
>>"%LOG%" where jpackage
java -version 2>>"%LOG%"
javac -version 2>>"%LOG%"

set "HAS_JPACKAGE=1"
where jpackage >nul 2>&1 || set "HAS_JPACKAGE=0"

rem ================== WIX (requerido para MSI) ==================
set "HAS_WIX=0"

rem RUTA TIPICA de WiX 3.14. AJUSTA si lo instalaste en otro lado.
set "WIX_HOME=C:\Program Files (x86)\WiX Toolset v3.14\bin"

if exist "%WIX_HOME%\candle.exe" (
  set "PATH=%WIX_HOME%;%PATH%"
)

where candle >nul 2>&1
if not errorlevel 1 (
  where light >nul 2>&1
  if not errorlevel 1 (
    set "HAS_WIX=1"
  )
)

>>"%LOG%" echo [INFO] HAS_WIX=%HAS_WIX%


rem ================== LIMPIEZA ==================
echo.
echo === Preparando carpetas de build ===
if exist "%CLS_DIR%" rmdir /S /Q "%CLS_DIR%"
if exist "%APP_DIR%" rmdir /S /Q "%APP_DIR%"
if exist "%INST_DIR%" rmdir /S /Q "%INST_DIR%"
mkdir "%CLS_DIR%" "%APP_DIR%" "%LIB_DIR%" "%INST_DIR%" >nul

rem ================== LISTA DE FUENTES (ROBUSTO CON ESPACIOS) ==================
echo.
echo === Buscando .java del proyecto ===
set "SRC_LIST=%OUT_DIR%\sources.txt"
if exist "%SRC_LIST%" del /F /Q "%SRC_LIST%"

rem Recolecta TODOS los .java, excluyendo build, out, .git y node_modules
for /R "%SRC_DIR%" %%f in (*.java) do (
  echo %%f | findstr /I /C:"\build\" /C:"\out\" /C:"\.git\" /C:"\node_modules\" >nul
  if errorlevel 1 (
    set "p=%%~ff"
    rem Cambia backslashes por forward slashes para que el argfile no rompa las rutas
    set "p=!p:\=/!"
    >> "%SRC_LIST%" echo "!p!"
  )
)

for %%# in ("%SRC_LIST%") do if %%~z# LSS 5 (
  echo [ERROR] No encontre archivos .java. Corre este .bat en la raiz del proyecto.
  >>"%LOG%" echo [ERROR] sources.txt vacio
  goto :fail
)


rem ================== COMPILACION ==================
echo.
echo === Compilando ===
>>"%LOG%" echo javac -encoding UTF-8 -cp "lib\*;%CLS_DIR%" -d "%CLS_DIR%" @"%SRC_LIST%"
javac -encoding UTF-8 -cp "lib\*;%CLS_DIR%" -d "%CLS_DIR%" @"%SRC_LIST%" >>"%LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] Fallo la compilacion. Revisa "%LOG%".
  goto :fail
)

rem ================== ICONOS Y RESOURCES ==================
echo.
echo === Empaquetando recursos (icons) ===
if exist "icons" (
  xcopy /E /I /Y "icons" "%CLS_DIR%\icons" >nul
) else (
  >>"%LOG%" echo [WARN] No se encontro carpeta "icons".
)
rem === Incluir SQL en el classpath ===
if exist "sql" xcopy /E /I /Y "sql" "%CLS_DIR%\sql" >nul


rem ================== LIBRERIAS (ANTES DEL JAR) ==================
echo.
echo === Copiando dependencias (lib) ===
if not exist "lib" (
  echo [WARN] No hay carpeta "lib" con dependencias .jar.>>"%LOG%"
) else (
  xcopy /Y "lib\*.jar" "%LIB_DIR%\" >nul
)

rem Validar que exista el driver de MySQL
dir /b "%LIB_DIR%\mysql-connector*.jar" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Falta el MySQL Connector/J en lib\. Coloca mysql-connector-j-8.x.x.jar.>>"%LOG%"
  echo [ERROR] Falta el MySQL Connector/J en lib\. Coloca mysql-connector-j-8.x.x.jar.
  goto :fail
)

rem ================== EMPAQUETAR JAR (CON CLASS-PATH) ==================
echo.
echo === Creando app.jar con MANIFEST correcto ===

setlocal ENABLEDELAYEDEXPANSION
set "CP_LIST=."
for %%J in ("%LIB_DIR%\*.jar") do (
  set "CP_LIST=!CP_LIST! lib/%%~nxJ"
)

> "%OUT_DIR%\manifest.mf" (
  echo Manifest-Version: 1.0
  echo Main-Class: %MAIN_CLASS%
  echo Implementation-Version: %VERSION%
  echo Class-Path: !CP_LIST!
)

jar --create --file "%APP_DIR%\app.jar" --manifest "%OUT_DIR%\manifest.mf" -C "%CLS_DIR%" . >>"%LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] No se pudo crear el JAR. Revisa "%LOG%".
  goto :fail
)
endlocal

rem ================== LAUNCHER LOCAL ==================
> "%APP_DIR%\run.bat" (
  echo @echo off
  echo cd /d %%~dp0
  echo java -cp "app.jar;lib\*" %MAIN_CLASS%
)


rem ================== CONFIG DB ==================
echo.
echo === Generando config\db.properties (si no existe) ===
mkdir "%APP_DIR%\config" >nul 2>&1
if not exist "%APP_DIR%\config\db.properties" (
  > "%APP_DIR%\config\db.properties" (
    echo url=jdbc:mysql://localhost:3306/tienda_vestidos
    echo user=root
    echo password=MIA1234
  )
)

rem ================== PREP INPUT PARA JPACKAGE ==================
set "JP_IN=%OUT_DIR%\jpkg"
if exist "%JP_IN%" rmdir /S /Q "%JP_IN%"
mkdir "%JP_IN%" >nul

rem Copiamos el app.jar y TODAS las dependencias al nivel raiz
copy /Y "%APP_DIR%\app.jar" "%JP_IN%\" >nul
for %%J in ("%LIB_DIR%\*.jar") do copy /Y "%%~fJ" "%JP_IN%\" >nul

rem Incluye config para que viaje en el instalador
if exist "%APP_DIR%\config" xcopy /E /I /Y "%APP_DIR%\config" "%JP_IN%\config" >nul

rem === Empaquetar config por defecto dentro del JAR (fallback) ===
if exist "config" (
  xcopy /E /I /Y "config" "%CLS_DIR%\config" >nul
) else (
  rem Si no hay carpeta config en el proyecto, crea una por defecto
  mkdir "%CLS_DIR%\config" >nul 2>&1
  > "%CLS_DIR%\config\db.properties" (
    echo url=jdbc:mysql://localhost:3306/tienda_vestidos
    echo user=root
    echo password=MIA1234
  )
)


rem ================== DETECCION WiX PARA MSI ==================
set "HAS_WIX=0"
where candle >nul 2>&1 && where light >nul 2>&1 && set "HAS_WIX=1"
>>"%LOG%" echo [INFO] HAS_WIX=%HAS_WIX%

rem ================== INSTALADOR (MSI si hay WiX, si no EXE) ==================
if "%HAS_JPACKAGE%"=="1" (
  setlocal EnableDelayedExpansion

  set "PKG_TYPE="
  if "%HAS_WIX%"=="1" ( set "PKG_TYPE=msi" ) else (
    set "PKG_TYPE=exe"
    >>"%LOG%" echo [WARN] No se detecto WiX; generare EXE.
  )
  >>"%LOG%" echo [INFO] PKG_TYPE=!PKG_TYPE!

  set "ICON_SWITCH="
  if exist "icons\app.ico" set "ICON_SWITCH=--icon ""icons\app.ico"""

  rem IMPORTANTE: usar %JP_IN% (no %APP_DIR%) para incluir todas las libs al nivel raiz.
  >>"%LOG%" echo jpackage --name "%APP_NAME%" --app-version "%VERSION%" --type !PKG_TYPE! --dest "%INST_DIR%" --input "%JP_IN%" --main-jar app.jar --main-class "%MAIN_CLASS%" --add-modules java.desktop,java.sql,java.naming,jdk.crypto.ec,java.xml,java.logging --vendor "Local Deploy" --win-shortcut --win-menu --win-dir-chooser --win-per-user-install --win-upgrade-uuid %UPGRADE_UUID% --java-options "-Dapp.home=$APPDIR\app" !ICON_SWITCH!

  rem UNA SOLA LINEA, SIN CARETS, SIN VARIABLES VACIAS QUE ROMPAN CONTINUACIONES
  jpackage --name "%APP_NAME%" --app-version "%VERSION%" --type !PKG_TYPE! --dest "%INST_DIR%" --input "%JP_IN%" --main-jar app.jar --main-class "%MAIN_CLASS%" --add-modules java.desktop,java.sql,java.naming,jdk.crypto.ec,java.xml,java.logging --vendor "Local Deploy" --win-shortcut --win-menu --win-dir-chooser --win-per-user-install --win-upgrade-uuid %UPGRADE_UUID% --java-options "-Dapp.home=$APPDIR\app" !ICON_SWITCH! >>"%LOG%" 2>&1

  if errorlevel 1 (
    echo [ERROR] jpackage fallo. Revisa "%LOG%".
    goto :fail
  )
  endlocal

  echo.
  echo  Listo: instalador en "%INST_DIR%"
) else (
  echo.
  echo ⚠ jpackage no esta disponible. Ejecuta "%APP_DIR%\run.bat"
)
