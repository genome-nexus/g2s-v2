#!/usr/bin/env python3
"""Convert NCBI BLAST+ XML (outfmt 5) to G2S init SQL.

Mirrors Java PdbScriptsPipelineMakeSQL init mode (parseSingleAlignment + makeSQLText).

Stdlib only.
"""

from __future__ import annotations

import argparse
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Dict, Iterable, Iterator, List, Tuple


def sql_escape(value: str) -> str:
    return value.replace("\\", "\\\\").replace("'", "''")


def parse_pdb_subject(sseqid: str) -> Tuple[str, str, str, str, str]:
    """Parse Hit_def like Java PdbScriptsPipelineMakeSQL.makeTable_pdb_entry_insert."""
    parts = sseqid.split()
    if not parts:
        raise ValueError(f"empty Hit_def: {sseqid!r}")

    pdb_no = parts[0]
    strarray_s = pdb_no.split("_")
    if len(strarray_s) < 3:
        raise ValueError(
            f"PDB header must be pdbId_chain_seg (3+ underscore parts), "
            f"got {pdb_no!r} in {sseqid!r}"
        )

    pdb_id, chain, pdb_seg = strarray_s[0], strarray_s[1], strarray_s[2]
    if len(parts) <= 3:
        raise ValueError(
            f"PDB header missing SEG_START at token[3] "
            f"(expected: pdbId_chain_seg mol:protein length:N SEG_START SEG_END), "
            f"got {sseqid!r}"
        )

    seg_start = parts[3]
    return pdb_no, pdb_id, chain, pdb_seg, seg_start


def parse_seq_id(qseqid: str) -> str:
    return qseqid.split(";")[0]


def _text(parent: ET.Element, tag: str) -> str:
    node = parent.find(tag)
    if node is None or node.text is None:
        return ""
    return node.text.strip()


def _iter_hsps_from_iterations(iterations: ET.Element) -> Iterator[dict]:
    for iteration in iterations.findall("Iteration"):
        query_def = _text(iteration, "Iteration_query-def")
        hits = iteration.find("Iteration_hits")
        if hits is None:
            continue
        for hit in hits.findall("Hit"):
            hit_def = _text(hit, "Hit_def")
            hsps = hit.find("Hit_hsps")
            if hsps is None:
                continue
            for hsp in hsps.findall("Hsp"):
                yield {
                    "qseqid": query_def,
                    "sseqid": hit_def,
                    "qstart": int(_text(hsp, "Hsp_query-from")),
                    "qend": int(_text(hsp, "Hsp_query-to")),
                    "sstart": int(_text(hsp, "Hsp_hit-from")),
                    "send": int(_text(hsp, "Hsp_hit-to")),
                    "evalue": _text(hsp, "Hsp_evalue"),
                    "bitscore": _text(hsp, "Hsp_bit-score"),
                    "nident": _text(hsp, "Hsp_identity"),
                    "positive": _text(hsp, "Hsp_positive"),
                    "qseq": _text(hsp, "Hsp_qseq"),
                    "sseq": _text(hsp, "Hsp_hseq"),
                    "midline": _text(hsp, "Hsp_midline"),
                }


def iter_blast_output_blocks(xml_path: Path) -> Iterator[str]:
    """Yield each BLAST XML document in a possibly concatenated outfmt=5 file."""
    buf: list[str] = []
    with xml_path.open(encoding="utf-8") as handle:
        for line in handle:
            if line.startswith("<?xml") and buf:
                block = "".join(buf).strip()
                if block:
                    yield block
                buf = [line]
            else:
                buf.append(line)
    block = "".join(buf).strip()
    if block:
        yield block


def iter_blast_hsps(xml_path: Path) -> Iterator[dict]:
    for block in iter_blast_output_blocks(xml_path):
        root = ET.fromstring(block)
        iterations = root.find("BlastOutput_iterations")
        if iterations is None:
            continue
        yield from _iter_hsps_from_iterations(iterations)


def make_pdb_entry_insert(
    pdb_no: str, pdb_id: str, chain: str, pdb_seg: str, seg_start: str
) -> str:
    return (
        "INSERT IGNORE INTO `pdb_entry` "
        "(`PDB_NO`,`PDB_ID`,`CHAIN`,`PDB_SEG`,`SEG_START`) VALUES ("
        f"'{sql_escape(pdb_no)}', '{sql_escape(pdb_id)}', "
        f"'{sql_escape(chain)}', '{sql_escape(pdb_seg)}', "
        f"'{sql_escape(seg_start)}');"
    )


def make_alignment_insert(row: dict, pdb_no: str, pdb_id: str, chain: str, pdb_seg: str, seg_start: str, seq_id: str) -> str:
    return (
        "INSERT INTO `pdb_seq_alignment` "
        "(`PDB_NO`,`PDB_ID`,`CHAIN`,`PDB_SEG`,`SEG_START`,`SEQ_ID`,"
        "`PDB_FROM`,`PDB_TO`,`SEQ_FROM`,`SEQ_TO`,`EVALUE`,`BITSCORE`,"
        "`IDENTITY`,`IDENTP`,`SEQ_ALIGN`,`PDB_ALIGN`,`MIDLINE_ALIGN`,`UPDATE_DATE`)"
        "VALUES ("
        f"'{sql_escape(pdb_no)}','{sql_escape(pdb_id)}','{sql_escape(chain)}',"
        f"'{sql_escape(pdb_seg)}','{sql_escape(seg_start)}','{sql_escape(seq_id)}',"
        f"{row['sstart']},{row['send']},{row['qstart']},{row['qend']},"
        f"'{sql_escape(row['evalue'])}',{row['bitscore']},{row['nident']},{row['positive']},"
        f"'{sql_escape(row['qseq'])}','{sql_escape(row['sseq'])}','{sql_escape(row['midline'])}',CURDATE());"
    )


def convert_blast_xml_to_sql(hsps: Iterable[dict]) -> List[str]:
    output: List[str] = ["SET autocommit = 0;", "start transaction;"]
    seen_subjects: Dict[str, str] = {}
    row_count = 0

    for row in hsps:
        sseqid = row["sseqid"]
        pdb_no, pdb_id, chain, pdb_seg, seg_start = parse_pdb_subject(sseqid)
        seq_id = parse_seq_id(row["qseqid"])

        if sseqid not in seen_subjects:
            output.append(make_pdb_entry_insert(pdb_no, pdb_id, chain, pdb_seg, seg_start))
            seen_subjects[sseqid] = pdb_no

        output.append(make_alignment_insert(row, pdb_no, pdb_id, chain, pdb_seg, seg_start, seq_id))
        row_count += 1

    output.append("commit;")
    if row_count == 0:
        raise ValueError("no HSP rows parsed from BLAST XML")
    return output


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--xml", type=Path, required=True, help="BLAST XML (outfmt 5)")
    parser.add_argument("--sql", type=Path, required=True, help="Output SQL file")
    args = parser.parse_args()

    if not args.xml.exists():
        raise FileNotFoundError(args.xml)

    sql_lines = convert_blast_xml_to_sql(iter_blast_hsps(args.xml))
    args.sql.parent.mkdir(parents=True, exist_ok=True)
    args.sql.write_text("\n".join(sql_lines) + "\n", encoding="utf-8")
    print(f"Wrote {len(sql_lines)} SQL lines -> {args.sql}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
