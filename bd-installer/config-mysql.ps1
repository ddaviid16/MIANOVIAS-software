# Configura la contrasena de root en una instalacion nueva de MySQL.
# En instalaciones existentes (root ya tiene contrasena) no hace nada.

function Find-MySQLBin {
    $versions = '8.0','8.4','8.3','8.2','8.1','9.4','5.7'
    foreach ($v in $versions) {
        $p = "C:\Program Files\MySQL\MySQL Server $v\bin\mysql.exe"
        if (Test-Path $p) { return Split-Path $p }
    }
    foreach ($d in ($env:PATH -split ';')) {
        if (Test-Path "$d\mysql.exe") { return $d.TrimEnd('\') }
    }
    return $null
}

# ── Asegurar que el servicio MySQL este corriendo ─────────────────────────────
# Intentamos con los nombres de servicio mas comunes.
$serviceNames = @('MySQL', 'MySQL80', 'MySQL84', 'MySQL57')
foreach ($svc in $serviceNames) {
    $s = Get-Service -Name $svc -ErrorAction SilentlyContinue
    if ($s) {
        # Configurar inicio automatico
        try { Set-Service -Name $svc -StartupType Automatic -ErrorAction SilentlyContinue } catch {}
        # Iniciar si no esta corriendo
        if ($s.Status -ne 'Running') {
            try { Start-Service -Name $svc -ErrorAction SilentlyContinue } catch {}
        }
        break
    }
}

# Esperar a que MySQL este listo y aceptando conexiones (hasta 45 seg)
$bin = Find-MySQLBin
if (-not $bin) { exit 0 }
$mysql = "`"$bin\mysql.exe`""

$ready = $false
for ($i = 0; $i -lt 15; $i++) {
    $out = cmd /c "$mysql -u root --connect-expired-password -e `"SELECT 1`" 2>&1"
    # Listo si la salida incluye "1" o si el error es de acceso (ya acepta conexiones)
    if ($LASTEXITCODE -eq 0 -or ($out -match 'Access denied|ERROR 1045|temporary password')) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 3
}
if (-not $ready) { exit 0 }   # No respondio en 45s; salir sin romper la instalacion

# Caso 1: sin contrasena (initialize-insecure)
$out = cmd /c "$mysql -u root --connect-expired-password -e `"SELECT 1`" 2>&1"
if ($LASTEXITCODE -eq 0) {
    cmd /c "$mysql -u root -e `"ALTER USER 'root'@'localhost' IDENTIFIED BY 'MIA1234'; FLUSH PRIVILEGES;`" 2>&1" | Out-Null
    exit 0
}

# Caso 2: contrasena temporal (instalacion estandar MySQL 8 MSI)
$dataDirs = @(
    'C:\ProgramData\MySQL\MySQL Server 8.0\Data',
    'C:\ProgramData\MySQL\MySQL Server 8.4\Data',
    'C:\ProgramData\MySQL\MySQL Server 9.4\Data'
)
foreach ($dataDir in $dataDirs) {
    if (-not (Test-Path $dataDir)) { continue }
    $errFile = Get-ChildItem $dataDir -Filter '*.err' -ErrorAction SilentlyContinue |
               Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $errFile) { continue }
    $line = Get-Content $errFile.FullName -Encoding UTF8 -ErrorAction SilentlyContinue |
            Select-String 'A temporary password' | Select-Object -Last 1
    if (-not $line) { continue }
    $tmpPwd = ($line.Line -replace '.*root@localhost: ','').Trim()
    if (-not $tmpPwd) { continue }
    cmd /c "$mysql -u root `"--password=$tmpPwd`" --connect-expired-password -e `"ALTER USER 'root'@'localhost' IDENTIFIED BY 'MIA1234'; FLUSH PRIVILEGES;`" 2>&1" | Out-Null
    if ($LASTEXITCODE -eq 0) { exit 0 }
}

# Caso 3: root ya tiene contrasena → no modificar
exit 0
