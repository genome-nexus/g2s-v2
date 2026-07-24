#!/bin/bash
# Stop the java processes started by start-services.sh.
set -euo pipefail
pkill -f 'pdb-alignment-api-0.1.0.jar' 2>/dev/null || true
pkill -f 'pdb-0.1.0.war' 2>/dev/null || true
pkill -f 'pdb-alignment-web-0.1.0.jar' 2>/dev/null || true
echo "Stopped (if they were running)."
