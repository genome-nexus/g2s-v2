#!/bin/bash
# Start G2S API (8081), PDB API (8082), Web UI (5443) as background processes.
# Linux equivalent of start-services.ps1. Run from g2s/ (the folder with pom.xml).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
mkdir -p logs

PROFILE_ARG="--spring.profiles.active=local"
JDBC_ARG="--spring.datasource.url=jdbc:mysql://localhost:3306/pdb_2026?useSSL=false"

declare -a NAMES=("G2S API (8081)" "PDB API (8082)" "Web UI (5443)")
declare -a JARS=(
    "pdb-alignment-api/target/pdb-alignment-api-0.1.0.jar"
    "pdb/target/pdb-0.1.0.war"
    "pdb-alignment-web/target/pdb-alignment-web-0.1.0.jar"
)
declare -a EXTRA_ARGS=(
    "-Xmx4096m $PROFILE_ARG $JDBC_ARG"
    "-Xmx2048m -Dorg.springframework.boot.logging.LoggingSystem=org.springframework.boot.logging.java.JavaLoggingSystem $PROFILE_ARG --server.port=8082 --spring.datasource.url=jdbc:mysql://localhost:3306/pdb_2026"
    "-Xmx4096m $PROFILE_ARG $JDBC_ARG"
)

for i in "${!JARS[@]}"; do
    jar="${JARS[$i]}"
    if [ ! -f "$jar" ]; then
        echo "Build artifact missing: $jar. Run: mvn clean package -DskipTests" >&2
        exit 1
    fi
    log="logs/$(basename "$jar" | sed 's/\.[^.]*$//').log"
    echo "Starting ${NAMES[$i]} -> $log"
    # shellcheck disable=SC2086
    nohup java ${EXTRA_ARGS[$i]} -jar "$jar" > "$log" 2>&1 &
    echo "  pid $!"
    sleep 4
done

cat <<EOF

Services starting in the background (see g2s/logs/*.log):
  http://localhost:8081/swagger-ui.html
  http://localhost:8082/swagger-ui.html
  https://localhost:5443  (accept self-signed certificate)

EOF
