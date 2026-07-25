#!/bin/bash
# Linux equivalent of register-weekly-update.ps1: adds a crontab entry that
# runs `run.ps1 Update` weekly, instead of Windows Task Scheduler.
#
# Requires PowerShell Core (pwsh) on this box, since run.ps1/config.ps1 are
# PowerShell - install it first if missing:
#   https://learn.microsoft.com/powershell/scripting/install/install-debian (or your distro's page)
#
# PDB publishes its weekly release Wednesdays ~00:00 UTC. Default here is
# Wednesday 06:00 server-local time, a few hours after that - edit CRON_SCHEDULE
# below if your server is in a different timezone or you want more buffer.
#
# Run once (does NOT execute Update itself - only adds the crontab line):
#   ./db-tools/pipeline-blast/register-weekly-update.sh
#
# Remove later with: crontab -e   (delete the line tagged # g2s-pipeline-blast-update)
set -euo pipefail

PIPELINE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUN_SCRIPT="$PIPELINE_ROOT/run.ps1"
LOG_FILE="$PIPELINE_ROOT/update.log"
CRON_SCHEDULE="0 6 * * 3"   # minute hour * * day-of-week(3=Wednesday)
CRON_TAG="# g2s-pipeline-blast-update"

if [ ! -f "$RUN_SCRIPT" ]; then
    echo "Could not find run.ps1 next to this script: $RUN_SCRIPT" >&2
    exit 1
fi

if ! command -v pwsh >/dev/null 2>&1; then
    echo "pwsh (PowerShell Core) not found on PATH - install it before this cron job can run:" >&2
    echo "  https://learn.microsoft.com/powershell/scripting/install/installing-powershell-on-linux" >&2
    exit 1
fi

CRON_LINE="$CRON_SCHEDULE pwsh -NoProfile -File \"$RUN_SCRIPT\" Update >> \"$LOG_FILE\" 2>&1 $CRON_TAG"

existing="$(crontab -l 2>/dev/null || true)"
if echo "$existing" | grep -qF "$CRON_TAG"; then
    echo "Already registered (found a line tagged '$CRON_TAG' in crontab -l). Not adding a duplicate."
    echo "Current line:"
    echo "$existing" | grep -F "$CRON_TAG"
    exit 0
fi

{ echo "$existing"; echo "$CRON_LINE"; } | crontab -

echo "Registered weekly cron job: $CRON_SCHEDULE (Wednesday 06:00) -> run.ps1 Update"
echo "Logs: $LOG_FILE"
echo "Verify with: crontab -l"
echo "Remove with: crontab -e   (delete the line tagged '$CRON_TAG')"
