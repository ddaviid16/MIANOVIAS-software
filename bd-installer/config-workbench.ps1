# Registra la conexion MIANOVIAS en MySQL Workbench.
# Si Workbench no esta instalado, o la conexion ya existe, no hace nada.

$connName = 'MIANOVIAS'

# Verificar que Workbench este instalado
$wbInstalled = Get-ChildItem 'HKLM:\SOFTWARE\MySQL AB' -ErrorAction SilentlyContinue |
               Where-Object { $_.PSChildName -match 'Workbench' }
if (-not $wbInstalled) { exit 0 }

# Usar el API de Windows para obtener el AppData correcto (funciona incluso elevado)
$appData = [Environment]::GetFolderPath([Environment+SpecialFolder]::ApplicationData)
$wbDir   = Join-Path $appData 'MySQL\Workbench'
$xmlPath = Join-Path $wbDir 'connections.xml'

# Verificar si la conexion ya existe
if ((Test-Path $xmlPath) -and ((Get-Content $xmlPath -Raw -Encoding UTF8) -match [regex]::Escape($connName))) {
    exit 0
}

$guid = [System.Guid]::NewGuid().ToString('B').ToUpper()

$connEntry = @"
    <value type="object" struct-name="db.mgmt.Connection" id="$guid" struct-checksum="0x96ba47d8">
      <link type="object" struct-name="db.mgmt.Driver" key="driver">com.mysql.rdbms.mysql.driver.native</link>
      <value type="string" key="hostIdentifier">Mysql@127.0.0.1:3306</value>
      <value type="int" key="isDefault">0</value>
      <value type="dict" key="modules"/>
      <value type="dict" key="parameterValues">
        <value type="string" key="SQL_MODE"></value>
        <value type="string" key="hostName">127.0.0.1</value>
        <value type="int" key="port">3306</value>
        <value type="string" key="schema">tienda_vestidos</value>
        <value type="string" key="serverVersion">8.0.43</value>
        <value type="string" key="sslCA"></value>
        <value type="string" key="sslCert"></value>
        <value type="string" key="sslCipher"></value>
        <value type="string" key="sslKey"></value>
        <value type="int" key="useSSL">1</value>
        <value type="string" key="userName">root</value>
      </value>
      <value type="string" key="name">MIANOVIAS</value>
    </value>
"@

if (Test-Path $xmlPath) {
    # Insertar antes del cierre del listado
    $content = Get-Content $xmlPath -Raw -Encoding UTF8
    $closeTag = '  </value>'
    $idx = $content.LastIndexOf($closeTag)
    if ($idx -ge 0) {
        $newContent = $content.Substring(0, $idx) + $connEntry + "`n" + $content.Substring($idx)
        [System.IO.File]::WriteAllText($xmlPath, $newContent, [System.Text.Encoding]::UTF8)
    }
} else {
    if (-not (Test-Path $wbDir)) { New-Item -ItemType Directory -Force $wbDir | Out-Null }
    $newXml = @"
<?xml version="1.0"?>
<data grt_format="2.0">
  <value _ptr_="0x0000000000000001" type="list" content-type="object" content-struct-name="db.mgmt.Connection">
$connEntry
  </value>
</data>
"@
    [System.IO.File]::WriteAllText($xmlPath, $newXml, [System.Text.Encoding]::UTF8)
}

exit 0
