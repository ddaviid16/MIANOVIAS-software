# build-bd.ps1 — Genera setup-bd.exe
# Requiere: WiX Toolset 3.14 + mysql-8.0.43-winx64.msi en la misma carpeta.

$ErrorActionPreference = 'Stop'
$DIR = Split-Path $MyInvocation.MyCommand.Path -Resolve
$WIX = "C:\Program Files (x86)\WiX Toolset v3.14\bin"
$OUT = Join-Path $DIR "output"

Write-Host ""
Write-Host "=== MIANOVIAS - Build setup-bd.exe ===" -ForegroundColor Cyan
Write-Host ""

# -- Verificaciones ----------------------------------------------------------
if (-not (Test-Path "$WIX\candle.exe")) {
    Write-Host "[ERROR] WiX Toolset 3.14 no encontrado en: $WIX" -ForegroundColor Red
    Write-Host "        Descargalo en: https://wixtoolset.org/releases/" -ForegroundColor Yellow
    exit 1
}

$mysqlMsi = Join-Path $DIR "mysql-8.0.43-winx64.msi"
if (-not (Test-Path $mysqlMsi)) {
    Write-Host "[ERROR] Falta el instalador de MySQL:" -ForegroundColor Red
    Write-Host "        $mysqlMsi" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  Pasos para descargarlo:" -ForegroundColor Yellow
    Write-Host "    1. Ve a: https://dev.mysql.com/downloads/mysql/8.0.html"
    Write-Host "    2. Selecciona: Windows (x86, 64-bit), MSI Installer"
    Write-Host "    3. Descarga 'mysql-8.0.43-winx64.msi' (~50 MB)"
    Write-Host "    4. Coloca el archivo en: $DIR"
    exit 1
}

# -- Preparar carpeta de salida ----------------------------------------------
if (Test-Path $OUT) { Remove-Item $OUT -Recurse -Force }
New-Item -ItemType Directory -Path $OUT | Out-Null

# -- Codificar scripts PS1 a Base64 (UTF-16LE, requerido por -EncodedCommand) -
Write-Host "[1/4] Codificando scripts PowerShell..."

$mysqlB64 = [Convert]::ToBase64String(
    [Text.Encoding]::Unicode.GetBytes(
        (Get-Content -Raw (Join-Path $DIR "config-mysql.ps1"))
    )
)
$wbB64 = [Convert]::ToBase64String(
    [Text.Encoding]::Unicode.GetBytes(
        (Get-Content -Raw (Join-Path $DIR "config-workbench.ps1"))
    )
)

# -- Compilar config-bd.msi --------------------------------------------------
Write-Host "[2/4] Compilando config-bd.msi..."

$logFile = Join-Path $OUT "build.log"

& "$WIX\candle.exe" -nologo `
    -ext "$WIX\WixUtilExtension.dll" `
    "-dMySQLPwdB64=$mysqlB64" `
    "-dWorkbenchB64=$wbB64" `
    -out "$OUT\config-bd.wixobj" `
    "$DIR\config-bd.wxs" 2>&1 | Tee-Object -FilePath $logFile

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] candle fallo en config-bd.wxs. Ver: $logFile" -ForegroundColor Red
    exit 1
}

& "$WIX\light.exe" -nologo `
    -ext "$WIX\WixUtilExtension.dll" `
    -out "$OUT\config-bd.msi" `
    "$OUT\config-bd.wixobj" 2>&1 | Tee-Object -Append -FilePath $logFile

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] light fallo en config-bd.msi. Ver: $logFile" -ForegroundColor Red
    exit 1
}

# -- Compilar Bundle (setup-bd.exe) ------------------------------------------
Write-Host "[3/4] Compilando Bundle..."

& "$WIX\candle.exe" -nologo `
    -ext "$WIX\WixBalExtension.dll" `
    -ext "$WIX\WixUtilExtension.dll" `
    -out "$OUT\Bundle.wixobj" `
    "$DIR\Bundle.wxs" 2>&1 | Tee-Object -Append -FilePath $logFile

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] candle fallo en Bundle.wxs. Ver: $logFile" -ForegroundColor Red
    exit 1
}

& "$WIX\light.exe" -nologo `
    -ext "$WIX\WixBalExtension.dll" `
    -ext "$WIX\WixUtilExtension.dll" `
    -out "$OUT\setup-bd.exe" `
    "$OUT\Bundle.wixobj" 2>&1 | Tee-Object -Append -FilePath $logFile

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] light fallo en setup-bd.exe. Ver: $logFile" -ForegroundColor Red
    exit 1
}

# -- Copiar resultado --------------------------------------------------------
Copy-Item "$OUT\setup-bd.exe" (Join-Path $DIR "setup-bd.exe") -Force

Write-Host ""
Write-Host "[4/4] Listo!" -ForegroundColor Green
Write-Host "      Generado: $(Join-Path $DIR 'setup-bd.exe')" -ForegroundColor Green
Write-Host ""
Write-Host "  Al ejecutarlo en la maquina del cliente:"
Write-Host "    - MySQL NO instalado  -> instala MySQL 8.0.43 + configura root"
Write-Host "    - MySQL YA instalado  -> omite la instalacion de MySQL"
Write-Host "    - En ambos casos      -> registra la conexion MIANOVIAS en Workbench"
Write-Host "                            (solo si no existe ya)"
Write-Host ""
