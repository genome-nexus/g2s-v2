# db-tools

Helper scripts for G2S. Daily usage (starting/stopping the app) is now all
Docker Compose — see `../README.md` at the repo root. The three Java
services and `blastp` no longer run natively, so there are no
start/stop/env scripts here anymore.

## pipeline-blast/

Rebuilds the active alignment database (`pdb_2026`) from scratch via BLAST —
see `pipeline-blast/README.md` for that subsystem.
