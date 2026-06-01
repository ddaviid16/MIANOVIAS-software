# load-schema.ps1  <rutaDelSQL>
# Ejecuta el schema inicial de tienda_vestidos contra MySQL.
# El archivo SQL usa CREATE TABLE IF NOT EXISTS, por lo que es seguro en
# instalaciones nuevas Y en reinstalaciones: nunca borra datos existentes.

param(
    [Parameter(Mandatory=$true)]
    [string]$SqlPath
)

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

if (-not (Test-Path $SqlPath)) { exit 1 }

$mysql = "`"$bin\mysql.exe`""
cmd /c "$mysql -u root `"--password=MIA1234`" < `"$SqlPath`" 2>&1" | Out-Null
exit 0
