#!/usr/bin/env python3
"""Diff two segmented pdb_seqres.fasta snapshots for incremental Update.

Classifies every PDB_NO (e.g. "101m_A_1") header in --new relative to
--old-snapshot into added / modified (same PDB_NO, different sequence) /
removed (present in old, gone from new).

Writes:
  --delta-fasta   full FASTA records (added + modified) - what actually
                  needs a fresh blastp run against the gene chunks.
  --delete-ids    one PDB_NO per line (modified + removed) - what needs
                  DELETE FROM pdb_seq_alignment / pdb_entry before the
                  delta results are imported, so modified entries don't
                  leave stale alignment rows behind.

Prints "added=N modified=N removed=N" as the last line so run.ps1 can parse
counts for the update_record row.

Stdlib only.
"""

from __future__ import annotations

import argparse
from pathlib import Path
from typing import Dict, Iterator, Tuple


def parse_fasta(path: Path) -> Iterator[Tuple[str, str, str]]:
    """Yield (pdb_no, full_header_line_without_gt, sequence)."""
    header: str | None = None
    chunks: list[str] = []
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            line = raw.rstrip("\n")
            if not line:
                continue
            if line.startswith(">"):
                if header is not None:
                    yield header.split()[0], header, "".join(chunks)
                header = line[1:]
                chunks = []
            else:
                chunks.append(line.strip())
        if header is not None:
            yield header.split()[0], header, "".join(chunks)


def load_snapshot(path: Path) -> Dict[str, Tuple[str, str]]:
    """pdb_no -> (header, sequence). Missing file = empty (first-ever run)."""
    if not path.exists():
        return {}
    return {pdb_no: (header, seq) for pdb_no, header, seq in parse_fasta(path)}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--old-snapshot", type=Path, required=True,
                         help="Previous pdb_seqres.fasta (missing = treat everything as added)")
    parser.add_argument("--new", type=Path, required=True, help="Freshly regenerated pdb_seqres.fasta")
    parser.add_argument("--delta-fasta", type=Path, required=True)
    parser.add_argument("--delete-ids", type=Path, required=True)
    args = parser.parse_args()

    old = load_snapshot(args.old_snapshot)
    new = load_snapshot(args.new)

    old_ids = set(old)
    new_ids = set(new)

    added_ids = new_ids - old_ids
    removed_ids = old_ids - new_ids
    common_ids = old_ids & new_ids
    modified_ids = {pdb_no for pdb_no in common_ids if old[pdb_no][1] != new[pdb_no][1]}

    args.delta_fasta.parent.mkdir(parents=True, exist_ok=True)
    with args.delta_fasta.open("w", encoding="utf-8", newline="\n") as out:
        for pdb_no in sorted(added_ids | modified_ids):
            header, seq = new[pdb_no]
            out.write(f">{header}\n{seq}\n")

    args.delete_ids.parent.mkdir(parents=True, exist_ok=True)
    with args.delete_ids.open("w", encoding="utf-8", newline="\n") as out:
        for pdb_no in sorted(modified_ids | removed_ids):
            out.write(f"{pdb_no}\n")

    print(f"added={len(added_ids)} modified={len(modified_ids)} removed={len(removed_ids)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
