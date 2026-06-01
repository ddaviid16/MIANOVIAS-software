# load-schema.ps1
# Carga el esquema inicial de tienda_vestidos en MySQL.
#
# El contenido SQL está embebido en Base64 (se inyecta en tiempo de build).
# Usa CREATE TABLE IF NOT EXISTS, por lo que es seguro ejecutarlo en
# instalaciones nuevas Y en reinstalaciones: nunca borra datos existentes.

function Find-MySQLBin {
    $versions = '8.0','8.4','8.3','8.2','8.1','9.4','5.7'
    foreach ($v in $versions) {
        $p = "C:\Program Files\MySQL\MySQL Server $v\bin\mysql.exe"
        if (Test-Path $p) { return Split-Path $p }
    }
    foreach ($d in ($env:PATH -split ';')) {
        $d = $d.TrimEnd('\')
        if (Test-Path "$d\mysql.exe") { return $d }
    }
    return $null
}

$bin = Find-MySQLBin
if (-not $bin) { exit 0 }   # MySQL no encontrado; no hacer nada

# ── Decodificar el SQL embebido y escribirlo a un archivo temporal ────────────
$sqlB64  = '##SQL_B64##'
$tmpSql  = Join-Path $env:TEMP 'mianovias_schema_init.sql'

try {
    $sqlBytes = [Convert]::FromBase64String($sqlB64)
    [System.IO.File]::WriteAllBytes($tmpSql, $sqlBytes)
} catch {
    exit 1
}

# ── Ejecutar el schema contra MySQL ──────────────────────────────────────────
$mysql = "`"$bin\mysql.exe`""
cmd /c "$mysql -u root `"--password=MIA1234`" < `"$tmpSql`" 2>&1" | Out-Null

# Limpiar archivo temporal
Remove-Item $tmpSql -Force -ErrorAction SilentlyContinue

exit 0
