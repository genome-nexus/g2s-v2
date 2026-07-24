# Registers a Windows Scheduled Task that runs `run.ps1 Update` weekly.
#
# Replaces the legacy Java pipeline's "updateweekly" mode (a resident process
# with an internal java.util.Timer) with a normal OS-level scheduled task -
# nothing has to stay running between updates.
#
# PDB publishes its weekly release Wednesdays ~00:00 UTC. Default here is
# Wednesday 06:00 local time, a few hours after that, adjust with -DayOfWeek /
# -Time if your server is in a different timezone or you want more buffer.
#
# Run once, as the user who should own the task (elevated PowerShell not
# required for a per-user task):
#   .\yichuan_scripts\pipeline-blast\register-weekly-update.ps1
#
# Remove later with:
#   Unregister-ScheduledTask -TaskName "G2S-PipelineBlast-Update" -Confirm:$false

param(
    [string]$TaskName = "G2S-PipelineBlast-Update",
    [System.DayOfWeek]$DayOfWeek = [System.DayOfWeek]::Wednesday,
    [string]$Time = "06:00"
)

$ErrorActionPreference = "Stop"
$PipelineRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$RunScript = Join-Path $PipelineRoot "run.ps1"

if (-not (Test-Path $RunScript)) {
    throw "Could not find run.ps1 next to this script: $RunScript"
}

$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$RunScript`" Update"
$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek $DayOfWeek -At $Time
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -DontStopOnIdleEnd

Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger `
    -Settings $settings -Description "Weekly incremental G2S pdb_2026 update (pipeline-blast Update)" `
    -Force | Out-Null

Write-Host "Registered scheduled task '$TaskName': every $DayOfWeek at $Time."
Write-Host "Logs: whatever run.ps1 prints to the console the task runs in (redirect in the"
Write-Host "action if you want a file - e.g. wrap in a .cmd that appends to a log)."
Write-Host ""
Write-Host "Linux equivalent (cron), if this ends up on the Linux deployment instead:"
Write-Host "  0 6 * * 3 pwsh $RunScript Update >> $PipelineRoot/update.log 2>&1"
