package pdbprepare;

import java.io.File;

/** Minimal config for PDB Step 1+2 (matches G2S application.properties defaults). */
public final class PrepareConfig {
    public static String workspace;
    public static String tmpdir;
    public static String pdbRepo;
    public static String pdbSegMinLengthMulti = "5";
    public static String pdbSegMinLengthSingle = "10";
    public static String pdbSegGapThreshold = "10";
    public static String pdbSeqresDownloadFile = "pdb_seqres.txt";
    public static String pdbSeqresFastaFile = "pdb_seqres.fasta";

    private PrepareConfig() {
    }

    public static void configure(String workspaceArg, String pdbRepoArg, String g2sRoot) {
        workspace = workspaceArg.endsWith(File.separator) ? workspaceArg : workspaceArg + File.separator;
        pdbRepo = pdbRepoArg;
        tmpdir = g2sRoot + File.separator + "tmp" + File.separator;
    }
}
