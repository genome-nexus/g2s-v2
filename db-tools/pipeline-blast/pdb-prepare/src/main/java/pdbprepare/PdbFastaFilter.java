package pdbprepare;

import java.io.File;
import java.io.FileWriter;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.biojava.nbio.core.sequence.ProteinSequence;
import org.biojava.nbio.core.sequence.io.FastaReaderHelper;

/** Step 2: filter segmented pdb_seqres.txt to mol:protein FASTA for makeblastdb. */
public final class PdbFastaFilter {
    private static final Logger log = Logger.getLogger(PdbFastaFilter.class);

    private PdbFastaFilter() {
    }

    public static void preprocessPDBsequences(String infileName, String outfileName) {
        try {
            File inFile = new File(infileName);
            if (!inFile.exists() || inFile.length() == 0) {
                log.error("[Preprocessing] Input PDB sequence file is missing or empty: " + infileName);
                return;
            }
            log.info("[Preprocessing] Preprocessing PDB sequences... ");
            LinkedHashMap<String, ProteinSequence> entries = FastaReaderHelper.readFastaProteinSequence(inFile);
            StringBuffer sb = new StringBuffer();
            for (Entry<String, ProteinSequence> entry : entries.entrySet()) {
                String[] tmp = entry.getValue().getOriginalHeader().toString().split("\\s+");
                if (tmp.length > 1 && tmp[1].equals("mol:protein")) {
                    sb.append(">" + entry.getValue().getOriginalHeader() + "\n"
                            + entry.getValue().getSequenceAsString() + "\n");
                }
            }
            FileWriter fw = new FileWriter(new File(outfileName));
            fw.write(sb.toString());
            fw.close();
            log.info("[Preprocessing] PDB sequences Ready ... ");
        } catch (Exception ex) {
            log.error("[Preprocessing] Fatal Error: Could not Successfully Preprocessing PDB sequences");
            log.error(ex.getMessage());
            ex.printStackTrace();
        }
    }
}
