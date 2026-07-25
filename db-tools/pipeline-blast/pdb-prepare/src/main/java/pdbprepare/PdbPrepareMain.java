package pdbprepare;

import java.io.File;

import org.apache.log4j.Logger;

/**
 * PDB Step 1+2 for yichuan_scripts pipeline-blast.
 * Parses g2s_pdb/*.pdb.gz into segmented pdb_seqres.fasta (production semantics).
 */
public class PdbPrepareMain {
    private static final Logger log = Logger.getLogger(PdbPrepareMain.class);

    public static void main(String[] args) {
        String pdbRepo = null;
        String workspace = null;
        String g2sRoot = null;
        int maxFiles = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
            case "--pdb-repo":
                pdbRepo = args[++i];
                break;
            case "--workspace":
                workspace = args[++i];
                break;
            case "--g2s-root":
                g2sRoot = args[++i];
                break;
            case "--max-files":
                maxFiles = Integer.parseInt(args[++i]);
                break;
            default:
                printUsage();
                System.exit(1);
            }
        }

        if (pdbRepo == null || workspace == null || g2sRoot == null) {
            printUsage();
            System.exit(1);
        }

        PrepareConfig.configure(workspace, pdbRepo, g2sRoot);

        File repoDir = new File(pdbRepo);
        if (!repoDir.isDirectory()) {
            System.err.println("ERROR: PDB repo is not a directory: " + pdbRepo);
            System.exit(1);
        }

        File tmpDir = new File(PrepareConfig.tmpdir);
        if (!tmpDir.exists() && !tmpDir.mkdirs()) {
            System.err.println("ERROR: Could not create tmpdir: " + PrepareConfig.tmpdir);
            System.exit(1);
        }

        String downloadFile = PrepareConfig.workspace + PrepareConfig.pdbSeqresDownloadFile;
        String fastaFile = PrepareConfig.workspace + PrepareConfig.pdbSeqresFastaFile;
        new File(downloadFile).delete();
        new File(fastaFile).delete();

        log.info("[prepare-pdb] Step 1: parse g2s_pdb -> " + downloadFile);
        PdbSequenceUtil pu = new PdbSequenceUtil();
        pu.initSequencefromFolder(pdbRepo, downloadFile, maxFiles);

        File intermediate = new File(downloadFile);
        if (!intermediate.exists() || intermediate.length() == 0) {
            System.err.println("ERROR: No PDB sequences produced. Is g2s_pdb empty? " + pdbRepo);
            System.exit(1);
        }
        if (!intermediateContentLooksSegmented(intermediate)) {
            System.err.println("ERROR: Step 1 output is not segmented FASTA (expected pdbId_chain_seg ... SEG_START SEG_END)");
            System.exit(1);
        }

        log.info("[prepare-pdb] Step 2: filter mol:protein -> " + fastaFile);
        PdbFastaFilter.preprocessPDBsequences(downloadFile, fastaFile);

        File fasta = new File(fastaFile);
        if (!fasta.exists() || fasta.length() == 0) {
            System.err.println("ERROR: pdb_seqres.fasta missing or empty after Step 2");
            System.exit(1);
        }

        log.info("[prepare-pdb] Done: " + fastaFile);
    }

    private static void printUsage() {
        System.err.println(
                "Usage: PdbPrepareMain --pdb-repo DIR --workspace DIR --g2s-root DIR [--max-files N]");
    }

    private static boolean intermediateContentLooksSegmented(File file) {
        try (java.util.Scanner scan = new java.util.Scanner(file, "UTF-8")) {
            while (scan.hasNextLine()) {
                String line = scan.nextLine();
                if (!line.startsWith(">")) {
                    continue;
                }
                String[] parts = line.substring(1).split("\\s+");
                if (parts.length >= 5 && parts[1].equals("mol:protein") && parts[0].split("_").length >= 3) {
                    return true;
                }
            }
        } catch (Exception ex) {
            System.err.println("ERROR: Could not read Step 1 output: " + ex.getMessage());
        }
        return false;
    }
}
