# G2S Pipeline — NCBI BLAST+

Self-contained rebuild path under `db-tools/pipeline-blast/` (target DB: **`pdb_2026`** on **`pdb-mariadb`** :3306).

Does **not** modify `pdb-alignment-pipeline`.

- **pdb-prepare** (Java fork) → **prepare_inputs.py** → **makeblastdb** → **blastp** → **SQL** → **pdb_2026**

## PDB structures (`g2s_pdb/`)

Setup requires local PDB `.pdb.gz` under `g2s/g2s_pdb/`.

`run.ps1 Setup` runs `pdb-prepare/PdbPrepareMain` (BioJava segmentation fork) before gene FASTA prep.

Headers: `>101m_A_1 mol:protein length:154 0 154`

## BLAST+ execution

`makeblastdb` and `blastp` both run inside the `ncbi/blast` Docker image
(`config.ps1`'s `$PipelineBlastDockerImage`) — no native BLAST+ install needed.

## Layout

| Path | Purpose |
|------|---------|
| **`run.ps1`** | Setup / Chunk / All / Status / Update |
| `config.ps1` | Paths and BLAST params |
| **`pdb-prepare/`** | Java Step 1+2 fork (`PdbPrepareMain`) |
| `resources/pdb_2026.sql` | Schema for `pdb_2026` only |
| `prepare_inputs.py` | Reference proteome → gene FASTA + SQL |
| `blast_to_sql.py` | BLAST XML → alignment SQL |
| `diff_pdb_seqres.py` | Diffs two `pdb_seqres.fasta` snapshots for `Update` |
| `register-weekly-update.ps1` | One-time: registers a weekly Windows Scheduled Task for `Update` |

## Commands

```powershell
cd g2s
. .\db-tools\env.ps1
.\db-tools\pipeline-blast\run.ps1 Setup
.\db-tools\pipeline-blast\run.ps1 Chunk -ChunkIndex 0
.\db-tools\pipeline-blast\run.ps1 All
.\db-tools\pipeline-blast\run.ps1 Status
```

Small test: `$MaxPdbFiles = 10`, `$MaxPdbSeqresLines = 100`, `$MaxGeneChunks = 1` in `config.ps1`.

## Incremental weekly update (`Update`)

Equivalent of the legacy Java pipeline's `update`/`updateweekly` commands, but
against `pdb_2026`. Requires `Setup` to have run at least once (reuses its
gene chunks - the reference proteome doesn't change on PDB's weekly cadence,
only PDB structures do).

```powershell
.\db-tools\pipeline-blast\run.ps1 Update
```

What it does, each run:

1. Refreshes `g2s_pdb/` from the official RCSB rsync mirror (skip with
   `-SkipRsync` if you sync it some other way, or if `rsync` isn't on PATH -
   it just warns and continues with whatever's already there).
2. Regenerates the full segmented `pdb_seqres.fasta` from that mirror (same
   `pdb-prepare` step `Setup` uses).
3. Diffs it against a saved snapshot from the last successful `Update`/`Setup`
   (`diff_pdb_seqres.py`) to classify every PDB chain as added / modified
   (same ID, changed sequence) / removed / unchanged.
4. Deletes stale `pdb_seq_alignment` / `pdb_entry` rows for modified and
   removed chains (unchanged ones are left alone).
5. `makeblastdb`s a tiny delta DB from just the added + modified sequences,
   then blasts the **existing** gene chunks against that delta DB only - not
   the full ~900k-sequence PDB DB - and imports any hits.
6. Logs the run in `update_record` (`SEG_NUM`/`PDB_NUM`/`ALIGNMENT_NUM`) and
   saves the new snapshot for next time's diff.

No changes since last run → logs `added=0 modified=0 removed=0` and exits
without touching the DB.

Schedule it (Windows Task Scheduler, Wednesday mornings by default - a few
hours after PDB's weekly release):

```powershell
.\db-tools\pipeline-blast\register-weekly-update.ps1
```

On the Linux deployment, use cron instead - see the note printed by that
script, or `PRODUCTION-DEPLOY.md`.

## Schema (`pdb_2026` only)

- `seq_entry.SEQUENCE` (TEXT NOT NULL)
- Wider UniProt/Ensembl VARCHAR
