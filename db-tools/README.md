# db-tools

Helper scripts for G2S. Daily usage (starting/stopping the app) is now all
Docker Compose — see `../README.md` at the repo root. The three Java
services and `blastp` no longer run natively, so there are no
start/stop/env scripts here anymore.

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
