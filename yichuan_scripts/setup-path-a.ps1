# Path A setup: directories + Docker databases (MySQL + MongoDB)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
if (Test-Path $dockerBin) { $env:Path = "$dockerBin;$env:Path" }

$dirs = @(
    "workdir",
    "tmp",
    "tmp\upload",
    "g2s_pdb",
    "mysql_data",
    "mysql_data_old",
    "mongo_data"
)
foreach ($d in $dirs) {
    $p = Join-Path $Root $d
    if (-not (Test-Path $p)) {
        New-Item -ItemType Directory -Path $p -Force | Out-Null
        Write-Host "Created $p"
    }
}

Set-Location $Root
Write-Host "Starting MySQL (pdb_2026) and legacy MySQL (pdb)..."
docker compose up -d mysql mysql-old mongo

Write-Host "Waiting for MySQL to accept connections..."
$ready = $false
for ($i = 0; $i -lt 60; $i++) {
    docker exec pdb-mariadb mysqladmin ping -h localhost -u cbio -pcbio 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $ready = $true
        break
    }
    Start-Sleep -Seconds 2
}
if (-not $ready) {
    Write-Warning "MySQL may not be ready yet. Run yichuan_scripts/import-db.ps1 after a minute."
} else {
    Write-Host "MySQL is ready."
}

Write-Host "Done. Next: .\yichuan_scripts\import-db.ps1"
