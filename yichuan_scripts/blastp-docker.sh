#!/bin/bash
# blastp-docker.sh
#
# Linux/Docker equivalent of blastp-docker.cmd: lets pdb-alignment-web's
# "search by protein sequence" endpoints run blastp via the ncbi/blast Docker
# image instead of requiring a native BLAST+ install on the host.
#
# CommandProcessUtil.java invokes this file exactly as it would invoke a real
# `blastp` executable (see blastp= in application-local.properties), passing
# the usual -db/-query/-out flags with absolute paths under g2s/ (workspace=/
# uploaddir= in the same properties file). This rewrites those paths to the
# /g2s mount point below and runs blastp inside the container.
set -euo pipefail

G2S_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

rewritten=()
for arg in "$@"; do
    rewritten+=("${arg//$G2S_ROOT//g2s}")
done

exec docker run --rm -v "$G2S_ROOT:/g2s" ncbi/blast:2.16.0 blastp "${rewritten[@]}"
