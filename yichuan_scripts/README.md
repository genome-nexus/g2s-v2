# yichuan_scripts

Helper scripts for running G2S locally and on a deployed server.
Daily usage: `../../START-SERVICES.md`. Deploying to a new machine: `../PRODUCTION-DEPLOY.md`.

## Daily use

| File | What it does |
|------|---------------|
| `env.ps1` | Windows only. Dot-sourced by the scripts below before building/running: detects JDK 8 (Corretto or a system install), Maven (repo-local `tools/` or system), and Docker Desktop, then puts them on `PATH`/`JAVA_HOME`/`MAVEN_HOME`. |
| `start-services.ps1` | Windows daily entry point. Sources `env.ps1`, then opens three separate PowerShell windows running the G2S API (8081), PDB API (8082), and Web UI (5443) against `pdb_2026` with the `local` Spring profile. |
| `stop-services.ps1` | Windows counterpart to `start-services.ps1` — finds and kills the three java processes by matching their command line, so you don't have to close each window by hand. |
| `start-services.sh` | Linux equivalent of `start-services.ps1`. Runs the same three services as background `java` processes, logging to `g2s/logs/*.log`. Used by `PRODUCTION-DEPLOY.md`. |
| `stop-services.sh` | Linux equivalent of `stop-services.ps1` — kills the three java processes started by `start-services.sh`. |

## blastp via Docker

No native BLAST+ install needed. `application-local.properties`'s `blastp=`
points at these shims, so the "search by protein sequence" feature runs
`blastp` inside a Docker container (`ncbi/blast` image) instead.

| File | What it does |
|------|---------------|
| `blastp-docker.cmd` | Windows shim actually in use. Rewrites the paths Java passes in and runs `blastp` inside the `ncbi/blast` Docker image. |
| `blastp-docker.sh` | Linux equivalent, used the same way via `application-local.properties`'s `blastp=` on a deployed server — see `../PRODUCTION-DEPLOY.md`. |

## One-time / historical DB setup

These predate the current single active database (`pdb_2026` on `pdb-mariadb`) and
only matter if you're dealing with the legacy 2025 `pdb` dump on `pdb-mariadb-old`
(an archive nothing in the app connects to). Safe to ignore for normal setup.

| File | What it does |
|------|---------------|
| `setup-path-a.ps1` | One-time: creates the working directories (`workdir`, `tmp`, `tmp/upload`, `g2s_pdb`, `mysql_data`, `mysql_data_old`, `mongo_data`) and starts the `mysql`, `mysql-old`, `mongo` containers. |
| `import-db.ps1` | One-time: imports the legacy 2025 dump (`mysqldump_pdb_2025_08_07.sql.gz`) into `pdb-mariadb-old`'s `pdb` database. |
| `migrate-db-split.ps1` | One-time migration: splits a single combined container's `pdb`/`pdb_new` databases apart — moves legacy `pdb` to `pdb-mariadb-old` and renames `pdb_new` to `pdb_2026` on the main `pdb-mariadb` container. |

## pipeline-blast/

Rebuilds the active alignment database (`pdb_2026`) from scratch via BLAST —
see `pipeline-blast/README.md` for that subsystem.
