# One-time split: move legacy `pdb` dump to pdb-mariadb-old; keep current build as `pdb_2026` on pdb-mariadb.
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)

$dockerBin = "C:\Program Files\Docker\Docker\resources\bin"
if (Test-Path $dockerBin) { $env:Path = "$dockerBin;$env:Path" }

function Wait-MySql($Container, $User, $Pass, $MaxSeconds = 180) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        for ($i = 0; $i -lt ($MaxSeconds / 2); $i++) {
            docker exec -e "MYSQL_PWD=$Pass" $Container mysql -N -u $User -e "SELECT 1;" 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { return $true }
            Start-Sleep -Seconds 2
        }
        return $false
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Get-DbExists($Container, $DbName) {
    $out = docker exec -e "MYSQL_PWD=cbio" $Container mysql -N -u cbio -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='$DbName';" 2>$null
    return ($out -match [regex]::Escape($DbName))
}

function Get-AlignmentCount($Container, $DbName) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $out = docker exec -e "MYSQL_PWD=cbio" $Container mysql -N -u cbio $DbName -e "SELECT COUNT(*) FROM pdb_seq_alignment;" 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $out) { return -1 }
        return [int64]$out.Trim()
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Test-HasAlignmentTable($Container, $DbName) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $out = docker exec -e "MYSQL_PWD=cbio" $Container mysql -N -u cbio -e `
            "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DbName' AND table_name='pdb_seq_alignment';" 2>$null
        return ($LASTEXITCODE -eq 0 -and $out -and ([int]$out.Trim() -gt 0))
    } finally {
        $ErrorActionPreference = $prev
    }
}

Set-Location $Root
$oldDir = Join-Path $Root "mysql_data_old"
if (-not (Test-Path $oldDir)) {
    New-Item -ItemType Directory -Path $oldDir -Force | Out-Null
    Write-Host "Created $oldDir"
}

Write-Host "Starting MySQL containers..."
docker compose up -d mysql mysql-old
if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

if (-not (Wait-MySql "pdb-mariadb" "cbio" "cbio")) { throw "pdb-mariadb not ready" }
if (-not (Wait-MySql "pdb-mariadb-old" "cbio" "cbio")) { throw "pdb-mariadb-old not ready" }

$hasPdbMain = Get-DbExists "pdb-mariadb" "pdb"
$hasPdbNew = Get-DbExists "pdb-mariadb" "pdb_new"
$hasPdb2026 = Get-DbExists "pdb-mariadb" "pdb_2026"
$hasPdbOld = Get-DbExists "pdb-mariadb-old" "pdb"

# --- Legacy pdb -> pdb-mariadb-old ---
if ($hasPdbMain) {
    $mainCount = if (Test-HasAlignmentTable "pdb-mariadb" "pdb") { Get-AlignmentCount "pdb-mariadb" "pdb" } else { -1 }
    $oldHasTable = Test-HasAlignmentTable "pdb-mariadb-old" "pdb"
    $oldCount = if ($oldHasTable) { Get-AlignmentCount "pdb-mariadb-old" "pdb" } else { -1 }

    if ($oldCount -ge 0 -and $mainCount -ge 0 -and $oldCount -eq $mainCount) {
        Write-Host "[skip] pdb already on pdb-mariadb-old ($oldCount alignments)"
    } else {
        if ($oldHasTable) {
            Write-Host "[migrate] Dropping partial pdb on pdb-mariadb-old..."
            docker exec -e "MYSQL_PWD=root" pdb-mariadb-old mysql -u root -e "DROP DATABASE IF EXISTS ``pdb``; CREATE DATABASE ``pdb`` CHARACTER SET utf8 COLLATE utf8_general_ci; GRANT ALL PRIVILEGES ON ``pdb``.* TO 'cbio'@'%'; FLUSH PRIVILEGES;"
        }
        Write-Host "[migrate] Copying pdb ($mainCount alignments) to pdb-mariadb-old (may take 30-60 min)..."
        $log = Join-Path $Root "migrate-pdb-to-old.log"
        # Stream inside Docker network — avoid PowerShell pipe buffer limits on huge dumps.
        $copyCmd = @"
mysqldump -u cbio -pcbio --single-transaction pdb | mysql -h pdb-mariadb-old -u cbio -pcbio pdb
"@
        docker exec pdb-mariadb bash -c $copyCmd 2>&1 | Tee-Object -FilePath $log
        if ($LASTEXITCODE -ne 0) { throw "pdb dump/import failed. See $log" }
        $oldCount = Get-AlignmentCount "pdb-mariadb-old" "pdb"
        if ($oldCount -ne $mainCount) {
            throw "Count mismatch after copy: main=$mainCount old=$oldCount"
        }
        Write-Host "[migrate] pdb copy OK ($oldCount alignments)"
    }

    Write-Host "[cleanup] Dropping pdb from pdb-mariadb..."
    docker exec -e "MYSQL_PWD=root" pdb-mariadb mysql -u root -e "DROP DATABASE IF EXISTS ``pdb``;"
} elseif (-not $hasPdbOld) {
    Write-Warning "No legacy pdb on pdb-mariadb and none on pdb-mariadb-old. Run import-db.ps1 if you need the 2025 dump."
}

# --- pdb_new -> pdb_2026 on pdb-mariadb ---
if ($hasPdbNew -and -not $hasPdb2026) {
    Write-Host "[rename] pdb_new -> pdb_2026 (RENAME TABLE, fast)..."
    $tables = docker exec -e "MYSQL_PWD=cbio" pdb-mariadb mysql -N -u cbio -e `
        "SELECT table_name FROM information_schema.tables WHERE table_schema='pdb_new' ORDER BY table_name;"
    if (-not $tables) { throw "pdb_new has no tables" }

    docker exec -e "MYSQL_PWD=root" pdb-mariadb mysql -u root -e `
        "CREATE DATABASE IF NOT EXISTS ``pdb_2026`` CHARACTER SET utf8 COLLATE utf8_general_ci; GRANT ALL PRIVILEGES ON ``pdb_2026``.* TO 'cbio'@'%'; FLUSH PRIVILEGES;"

    $renames = ($tables -split "`n" | Where-Object { $_.Trim() } | ForEach-Object {
        $t = $_.Trim()
        "``pdb_new``.``$t`` TO ``pdb_2026``.``$t``"
    }) -join ", "
    docker exec -e "MYSQL_PWD=root" pdb-mariadb mysql -u root -e "SET foreign_key_checks=0; RENAME TABLE $renames; SET foreign_key_checks=1;"
    if ($LASTEXITCODE -ne 0) { throw "RENAME TABLE failed" }
    docker exec -e "MYSQL_PWD=root" pdb-mariadb mysql -u root -e "DROP DATABASE IF EXISTS ``pdb_new``;"
    $hasPdb2026 = $true
    Write-Host "[rename] pdb_2026 ready"
} elseif ($hasPdb2026) {
    Write-Host "[skip] pdb_2026 already exists on pdb-mariadb"
} elseif ($hasPdbNew) {
    throw "Both pdb_new and pdb_2026 exist; resolve manually"
}

Write-Host ""
Write-Host "Final state:"
docker exec -e "MYSQL_PWD=cbio" pdb-mariadb mysql -u cbio -e "SHOW DATABASES LIKE 'pdb%';"
docker exec -e "MYSQL_PWD=cbio" pdb-mariadb-old mysql -u cbio -e "SHOW DATABASES LIKE 'pdb%';" 2>$null
if ($hasPdb2026 -or (Get-DbExists "pdb-mariadb" "pdb_2026")) {
    docker exec -e "MYSQL_PWD=cbio" pdb-mariadb mysql -u cbio pdb_2026 -e "SELECT COUNT(*) AS pdb_2026_alignments FROM pdb_seq_alignment;"
}
if (Get-DbExists "pdb-mariadb-old" "pdb") {
    docker exec -e "MYSQL_PWD=cbio" pdb-mariadb-old mysql -u cbio pdb -e "SELECT COUNT(*) AS pdb_legacy_alignments FROM pdb_seq_alignment;"
}
Write-Host "Done. Java services (local profile) use pdb_2026 on :3306; legacy pdb is on pdb-mariadb-old :3307."
