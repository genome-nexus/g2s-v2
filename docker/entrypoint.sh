#!/bin/sh
set -eu
mkdir -p /app/workdir /app/tmp/upload
exec "$@"
