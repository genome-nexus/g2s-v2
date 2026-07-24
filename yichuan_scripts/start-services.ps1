# Start G2S API (8081), PDB API (8082), Web UI (5443) in separate windows
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $Root "yichuan_scripts\env.ps1")
Set-Location $Root

$ps = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
$profileArg = "--spring.profiles.active=local"
$jdbcArg = "--spring.datasource.url=jdbc:mysql://localhost:3306/pdb_2026?useSSL=false"
$jars = @(
    @{ Name = "G2S API (8081)"; Cmd = "java -Xmx4096m -jar pdb-alignment-api\target\pdb-alignment-api-0.1.0.jar $profileArg $jdbcArg" },
    @{ Name = "PDB API (8082)"; Cmd = "java -Xmx2048m '-Dorg.springframework.boot.logging.LoggingSystem=org.springframework.boot.logging.java.JavaLoggingSystem' -jar pdb\target\pdb-0.1.0.war --server.port=8082 $profileArg --spring.datasource.url=jdbc:mysql://localhost:3306/pdb_2026" },
    @{ Name = "Web UI (5443)";    Cmd = "java -Xmx4096m -jar pdb-alignment-web\target\pdb-alignment-web-0.1.0.jar $profileArg $jdbcArg" }
)

foreach ($s in $jars) {
    $jarPath = (($s.Cmd -split '-jar ', 2)[1] -split ' ', 2)[0]
    if (-not (Test-Path (Join-Path $Root $jarPath))) {
        throw "Build artifact missing: $jarPath. Run: mvn clean package -DskipTests"
    }
    Start-Process $ps -ArgumentList "-NoExit", "-Command", "cd '$Root'; . .\yichuan_scripts\env.ps1; Write-Host '$($s.Name)'; $($s.Cmd)"
    Start-Sleep -Seconds 4
}

Write-Host @"

Services starting in new windows:
  http://localhost:8081/swagger-ui.html
  http://localhost:8082/swagger-ui.html
  https://localhost:5443  (accept self-signed certificate)

"@
