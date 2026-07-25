# Stop the java processes started by start-services.ps1 (Windows equivalent of stop-services.sh).
$ErrorActionPreference = "Stop"

$patterns = @(
    "pdb-alignment-api-0.1.0.jar",
    "pdb-0.1.0.war",
    "pdb-alignment-web-0.1.0.jar"
)

$stopped = 0
Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | ForEach-Object {
    $cmd = $_.CommandLine
    if ($cmd -and ($patterns | Where-Object { $cmd -like "*$_*" })) {
        Write-Host "Stopping PID $($_.ProcessId): $cmd"
        Stop-Process -Id $_.ProcessId -Force
        $stopped++
    }
}

if ($stopped -eq 0) {
    Write-Host "No matching java processes were running."
} else {
    Write-Host "Stopped $stopped process(es)."
}
