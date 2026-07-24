package pdbprepare;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.log4j.Logger;
import org.biojava.nbio.structure.Chain;
import org.biojava.nbio.structure.Group;
import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.StructureIO;
import org.biojava.nbio.structure.io.FileParsingParameters;
import org.biojava.nbio.structure.io.PDBFileReader;
import org.biojava.nbio.structure.io.mmcif.ChemCompGroupFactory;
import org.biojava.nbio.structure.io.mmcif.ReducedChemCompProvider;

/**
 * Read PDB files Utils
 * 
 * @author Juexin Wang
 *
 */
public class PdbSequenceUtil {
    final static Logger log = Logger.getLogger(PdbSequenceUtil.class);

    /**
     * Parsing number only from string mixed with number and non-number e.g.
     * input "12 A", get "12"
     * 
     * @param inputStr
     * @return
     */
    public int parseNumberfromMix(String inputStr) {
        Pattern pattern = Pattern.compile("([A-Za-z])+");
        Matcher matcher = pattern.matcher(inputStr);
        if (matcher.find()) {
            Scanner in = new Scanner(inputStr).useDelimiter("[^0-9]+");
            int integer = in.nextInt();
            return (integer);
        } else {
            return (Integer.parseInt(inputStr));
        }
    }

    /**
     * Whether contains non-number from string
     * 
     * @param str
     * @return
     */
    public boolean containsLetter(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isLetter(str.charAt(i)))
                return true;
        }
        return false;
    }

    /**
     * Remove ith character from string s
     * 
     * @param s
     * @param i
     * @return
     */
    public String removeCharAt(String s, int i) {
        StringBuffer buf = new StringBuffer(s.length() - 1);
        buf.append(s.substring(0, i)).append(s.substring(i + 1));
        return buf.toString();
    }

    /**
     * Generate new segment from parsing results
     * 
     * @param start
     * @param end
     * @param resiMap
     *            <int residueNumber, String residueName >
     * @return
     */
    SegmentRecord generateNewSegmentRecord(int start, int end, Map<Integer, String> resiMap) {
        SegmentRecord sr = new SegmentRecord();
        sr.setSegmentStart(start);
        sr.setSegmentEnd(end);
        String tmpSeq = "";
        for (int i = start; i <= end; i++) {
            tmpSeq = tmpSeq + resiMap.get(i);
        }
        sr.setAaSequence(tmpSeq);

        return sr;
    }

    /**
     * Obsolete: Generate new segment from parsing results
     * 
     * @param start
     * @param end
     * @param aaCount
     * @param c
     * @param insertionCodeAl
     * @return
     */
    SegmentRecord generateNewSegmentRecord(int start, int end, int aaCount, Chain c,
            ArrayList<Integer> insertionCodeAl) {
        SegmentRecord sr = new SegmentRecord();
        sr.setSegmentStart(start);
        sr.setSegmentEnd(end);
        // System.out.println("###"+aaCount+"\t"+numPrior+"\t"+start);
        if (insertionCodeAl.size() == 0) {
            sr.setAaSequence(c.getAtomSequence().substring(aaCount, aaCount + end - start + 1));
        } else {
            String preStr = c.getAtomSequence().substring(aaCount, aaCount + end - start + 1 + insertionCodeAl.size());
            for (int j = insertionCodeAl.size() - 1; j >= 0; j--) {
                // System.out.println("@@@"+preStr+"\t"+j+"\t"+insertionCodeAl.get(j));
                preStr = removeCharAt(preStr, insertionCodeAl.get(j));
            }
            // System.out.println("@@@"+preStr);
            sr.setAaSequence(preStr);
        }
        return sr;
    }

    /**
     * Obsolete, need to delete late read PDB file and generate the results to a
     * String
     * 
     * Known bug for Insertion code of PDB: 1. For 1HAG_E, there are 1H, 1G, 1F,
     * 1A,...1, the function will choose the first one: 1H, not 1 as it should
     * be
     * 
     * @param pdbFileName
     * @return
     */
    public String readPDB2Results_stepbystep(String pdbFileName) {
        String outstr = "";
        try {

            Structure s = StructureIO.getStructure(pdbFileName);
            String molClassification = "mol:protein";
            if (s.getPDBHeader().getClassification().equals("DNA-RNA HYBRID")
                    || s.getPDBHeader().getClassification().equals("DNA/RNA HYBRID")
                    || s.getPDBHeader().getClassification().equals("DNA BINDING PROTEIN/DNA")
                    || s.getPDBHeader().getClassification().equals("COMPLEX (AMINOACYL-TRNA SYNTHASE/TRNA)")
                    || s.getPDBHeader().getClassification().equals("TRANSCRIPTION/DNA")
                    || s.getPDBHeader().getClassification().equals("HYDROLASE/DNA")
                    || s.getPDBHeader().getClassification().equals("HYDROLASE/RNA")
                    || s.getPDBHeader().getClassification().equals("GENE REGULATION/RNA")
                    || s.getPDBHeader().getClassification().equals("DNA")
                    || s.getPDBHeader().getClassification().equals("RNA")) {
                molClassification = "mol:na";
                // Directly use only protein
                return "";
            }
            for (Chain c : s.getChains()) {
                try {
                    // System.out.println("*"+s.getPDBCode().toLowerCase()+"*"+c.getChainID());
                    /*
                     * System.out.println(s.getDBRefs().size()); for(DBRef
                     * dbref:s.getDBRefs()){
                     * System.out.println(dbref.getChainId()+"\t"+dbref.
                     * getSeqBegin()+"\t"+dbref.getSeqEnd()+"\t"+dbref.
                     * getDbSeqBegin()+"\t"+dbref.getDbSeqEnd()); }
                     */
                    ArrayList<SegmentRecord> srAl = new ArrayList<SegmentRecord>();
                    List<Group> groups = c.getAtomGroups();
                    int aaCount = 0;
                    if (c.getAtomSequence().length() < 1) {
                        // log.warn(s.getPDBCode()+"_"+c.getChainID()+": No
                        // Chain for AtomSequence<1");
                        continue;
                    }
                    // One possible bug for biojava, e.g. 3igv_A, the end of
                    // chain should be 269Y, not Hetatm 1K
                    // Set endCheck to amend this
                    boolean endCheck = false;

                    // PDB is garbled, e.g. 1IAO
                    boolean garbledCheck = false;

                    /*
                     * //Test for(Group group: c.getSeqResGroups()){
                     * System.out.print(group.getChemComp().getOne_letter_code()
                     * ); }
                     * 
                     * System.out.println();
                     * System.out.println(c.getSeqResGroups().size());
                     * System.out.println();
                     * 
                     * for(Group group: c.getAtomGroups()){
                     * System.out.print(group.getChemComp().getOne_letter_code()
                     * ); }
                     * 
                     * System.out.println();
                     * System.out.println("c.getAtomGroups().size():\t"+c.
                     * getAtomGroups().size());
                     * 
                     * System.out.println(c.getAtomLength());
                     * System.out.println(c.getAtomSequence());
                     * System.out.println("c.getAtomSequence().length():\t"+c.
                     * getAtomSequence().length());
                     * 
                     * //int tcount =0; //for(Group group: c.getAtomGroups()){
                     * //
                     * System.out.println(tcount+"\t"+group.getResidueNumber()+
                     * "&\t"+group); // tcount++; //}
                     * 
                     * for(int k=0;k<c.getAtomSequence().length();k++){
                     * System.out.println(k+"\t"+c.getAtomGroup(k).
                     * getResidueNumber()+"&\t"+c.getAtomGroup(k)); } //Test End
                     */

                    int start = parseNumberfromMix(groups.get(0).getResidueNumber().toString());
                    int end;
                    int seqLength = c.getAtomSequence().length();
                    // Check error for biojava bugs in 3h1r :
                    // getAtomSequence().length is 226, it should be 224
                    if (c.getAtomSequence().length() > c.getAtomLength()) {
                        end = parseNumberfromMix(groups.get(c.getAtomLength() - 1).getResidueNumber().toString());
                        seqLength = c.getAtomLength();
                    } else {
                        end = parseNumberfromMix(
                                groups.get(c.getAtomSequence().length() - 1).getResidueNumber().toString());
                    }

                    if (end < start) {
                        // log.warn(s.getPDBCode()+"_"+c.getChainID()+": One
                        // additional Hetatm for End<=Start");
                        endCheck = true;
                    }

                    if (endCheck) {
                        seqLength = seqLength - 1;
                        end = parseNumberfromMix(groups.get(seqLength - 1).getResidueNumber().toString());
                    }

                    String headstr = ">" + s.getPDBCode().toLowerCase() + "_" + c.getChainID();
                    String tmpstr = "";

                    ArrayList<Integer> insertionCodeAl = new ArrayList<Integer>();

                    for (int i = 1; i < seqLength; i++) {
                        int numPresent = parseNumberfromMix(groups.get(i).getResidueNumber().toString());
                        int numPrior = parseNumberfromMix(groups.get(i - 1).getResidueNumber().toString());
                        // System.out.println(numPrior+"
                        // "+groups.get(i-1).getResidueNumber().toString()+"
                        // "+groups.get(i-1).getChemComp().getOne_letter_code()+"\t"+numPresent+"
                        // "+groups.get(i).getResidueNumber().toString()+"
                        // "+groups.get(i).getChemComp().getOne_letter_code());
                        if ((numPresent - numPrior) > 1) {

                            SegmentRecord sr = generateNewSegmentRecord(start, numPrior, aaCount, c, insertionCodeAl);
                            if (numPrior - start >= Integer.parseInt(PrepareConfig.pdbSegMinLengthMulti)) {
                                srAl.add(sr);
                            }
                            aaCount = aaCount + numPrior - start + 1 + insertionCodeAl.size();
                            start = numPresent;
                            insertionCodeAl = new ArrayList<Integer>();
                        }
                        // In case of insertion code of residue, such as 1dpo_A,
                        // residue 221 and 221A, we only choose the first one
                        else if ((numPresent - numPrior) == 0) {
                            insertionCodeAl.add(i - aaCount);
                        }

                        // Notes: Deal with minus number, such as 4mf6, the
                        // minus residue numbers are kept
                        else if ((numPresent - numPrior) < 0) {
                            // if contains Letter, as 1nsa_A, we ignore first
                            // 7A-95A
                            if (containsLetter(groups.get(i - 1).getResidueNumber().toString())
                                    && !containsLetter(groups.get(i).getResidueNumber().toString())) {
                                // log.warn(s.getPDBCode()+"_"+c.getChainID()+":
                                // Have Fragments in decreasing order of
                                // Insertion codes, ignoring Former Insertion
                                // codes fragments");
                                aaCount = aaCount + numPrior - start + 1 + insertionCodeAl.size();
                                start = parseNumberfromMix(groups.get(i).getResidueNumber().toString());
                                insertionCodeAl = new ArrayList<Integer>();
                            }
                            // if contains Letter, as 1iao_B, 6-94, 94A, 95-188,
                            // 1T, 2T. we ignore the following 1T, return the
                            // fragment immediately
                            else if (!containsLetter(groups.get(i - 1).getResidueNumber().toString())
                                    && containsLetter(groups.get(i).getResidueNumber().toString())) {
                                log.warn(s.getPDBCode() + "_" + c.getChainID()
                                        + ": Have Fragments in decreasing order of Insertion codes, ignoring Later Insertion codes fragments");
                                end = parseNumberfromMix(groups.get(i - 1).getResidueNumber().toString());
                                insertionCodeAl = new ArrayList<Integer>();
                                break;
                            }
                            // if does not contains Letter, then that is error
                            // here
                            else {
                                log.warn(s.getPDBCode() + "_" + c.getChainID()
                                        + ": Garbled! Have Fragments in decreasing order");
                                System.out.println("Case3");
                                System.out.println("numPrior:" + numPrior + "\tnumPresent:" + numPresent);
                                System.out.println("start:" + start + "\tend:" + end + "\taaCount:" + aaCount);
                                garbledCheck = true;
                                break;
                            }
                        }
                    }

                    if (garbledCheck) {
                        garbledCheck = false;
                        continue;
                    }

                    SegmentRecord sr = generateNewSegmentRecord(start, end, aaCount, c, insertionCodeAl);
                    insertionCodeAl = new ArrayList<Integer>();
                    if (end - start >= Integer.parseInt(PrepareConfig.pdbSegMinLengthSingle)) {
                        srAl.add(sr);
                    }

                    // Check inner linker, link gaps
                    ArrayList<SegmentRecord> srAlCopy = new ArrayList<SegmentRecord>();
                    if (srAl.size() > 0) {
                        srAlCopy.add(srAl.get(0));
                    } else {
                        // log.warn(s.getPDBCode()+"_"+c.getChainID()+":
                        // Fragments length is not long enough");
                        continue;
                    }

                    for (int i = 1; i < srAl.size(); i++) {
                        if (srAl.get(i).getSegmentStart() - srAlCopy.get(srAlCopy.size() - 1).getSegmentEnd()
                                - 1 <= Integer.parseInt(PrepareConfig.pdbSegGapThreshold)) {
                            int linkNum = srAl.get(i).getSegmentStart()
                                    - srAlCopy.get(srAlCopy.size() - 1).getSegmentEnd() - 1;
                            sr = srAlCopy.get(srAlCopy.size() - 1);
                            sr.setSegmentEnd(srAl.get(i).getSegmentEnd());
                            String linkStr = "";
                            for (int j = 0; j < linkNum; j++) {
                                linkStr = linkStr + "X";
                            }
                            sr.setAaSequence(sr.getAaSequence() + linkStr + srAl.get(i).getAaSequence());
                        } else {
                            srAlCopy.add(srAl.get(i));
                        }
                    }

                    int segCount = 1;

                    for (SegmentRecord srr : srAlCopy) {
                        // System.out.println(headerStr + " " + segCount + " " +
                        // srr.getSegmentStart() + " " + srr.getSegmentEnd() +
                        // "\n" + srr.getAaSequence() + "\n");
                        tmpstr = tmpstr + headstr + "_" + segCount + " " + molClassification + " length:" + seqLength
                                + " " + srr.getSegmentStart() + " " + srr.getSegmentEnd() + "\n" + srr.getAaSequence()
                                + "\n";
                        segCount++;
                    }

                    outstr = outstr + tmpstr;
                } catch (Exception ex) {
                    log.error(s.getPDBCode() + "_" + c.getChainID() + ": Exception");
                    ex.printStackTrace();
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return outstr;
    }

    /**
     * To avoid some exceptions in file system, if happens, read the file from
     * web
     * 
     * @param pdbFileName
     * @return
     */
    public String readPDB2Results(String pdbFileName) {
        try {
            // if does not read file from local system, read the file from web
            if (pdbFileName.indexOf(".") == -1) {
                return readPDB2ResultsProcess(pdbFileName);
            } else {
                // if read file from local system, check it, if bug continues,
                // read the file from web
                try {
                    return readPDB2ResultsProcess(pdbFileName);
                } catch (IndexOutOfBoundsException ex) {
                    // just use PDB name in four characters in the web
                    String tmpstr = pdbFileName.split("\\.")[0];
                    String pdbnameWeb = tmpstr.substring(tmpstr.length() - 4, tmpstr.length());
                    log.info("Bugs of Biojava, " + pdbFileName + "Error; Read " + pdbnameWeb + " from web instead.");
                    return readPDB2ResultsProcess(pdbnameWeb);
                } catch (Exception ex) {
                    log.error("Error in " + pdbFileName + " Please Check!");
                    ex.printStackTrace();
                    return "";
                }
            }
        } catch (Exception ex) {
            log.error("Error in " + pdbFileName + " Please Check!");
            ex.printStackTrace();
            return "";
        }
    }

    /**
     * Read PDB file and generate the results to a String Use HashMap to involve
     * all the protein residues
     * 
     * Throw Exception to help decide read file from local or web
     * 
     * 
     * @param pdbFileName
     * @return
     */
    public String readPDB2ResultsProcess(String pdbFileName) throws Exception {
        String outstr = "";

        Structure s;
        if (pdbFileName.contains("/") || pdbFileName.contains("\\") || pdbFileName.contains(".")) {
            // Local file path: use PDBFileReader directly to avoid mmCIF attempts
            // and NPE in ChargeAdder (BioJava 4.2.0 bug)
            ChemCompGroupFactory.setChemCompProvider(new ReducedChemCompProvider());
            FileParsingParameters params = new FileParsingParameters();
            params.setCreateAtomBonds(false);
            PDBFileReader reader = new PDBFileReader();
            reader.setFileParsingParameters(params);
            s = reader.getStructure(new File(pdbFileName));
        } else {
            s = StructureIO.getStructure(pdbFileName);
        }
        String molClassification = "mol:protein";
        if (s.getPDBHeader().getClassification().equals("DNA-RNA HYBRID")
                || s.getPDBHeader().getClassification().equals("DNA/RNA HYBRID")
                || s.getPDBHeader().getClassification().equals("DNA BINDING PROTEIN/DNA")
                || s.getPDBHeader().getClassification().equals("COMPLEX (AMINOACYL-TRNA SYNTHASE/TRNA)")
                || s.getPDBHeader().getClassification().equals("TRANSCRIPTION/DNA")
                || s.getPDBHeader().getClassification().equals("HYDROLASE/DNA")
                || s.getPDBHeader().getClassification().equals("HYDROLASE/RNA")
                || s.getPDBHeader().getClassification().equals("GENE REGULATION/RNA")
                || s.getPDBHeader().getClassification().equals("DNA")
                || s.getPDBHeader().getClassification().equals("RNA")) {
            molClassification = "mol:na";
            // Directly use only protein
            return "";
        }
        for (Chain c : s.getChains()) {
            try {
                ArrayList<SegmentRecord> srAl = new ArrayList<SegmentRecord>();
                List<Group> groups = c.getAtomGroups();
                if (c.getAtomSequence().length() < 1) {
                    // log.warn(s.getPDBCode()+"_"+c.getChainID()+": No Chain
                    // for AtomSequence<1");
                    continue;
                }

                int seqLength = c.getAtomSequence().length();

                // Check error for biojava bugs in 3h1r :
                // getAtomSequence().length is 226, it should be 224
                if (c.getAtomSequence().length() > c.getAtomLength()) {
                    seqLength = c.getAtomLength();
                }

                String headstr = ">" + s.getPDBCode().toLowerCase() + "_" + c.getChainID();
                String tmpstr = "";

                // Generate one HashMap to store all the residue information
                Map<Integer, String> resiMap = new HashMap<Integer, String>();

                for (int i = 0; i < seqLength; i++) {
                    int resiNo = parseNumberfromMix(groups.get(i).getResidueNumber().toString());
                    if (resiMap.containsKey(resiNo)) {
                        if (containsLetter(groups.get(i).getResidueNumber().toString())) {
                            // If contains letters, then do nothing
                        } else {
                            // Overwrite if contains no letters
                            resiMap.put(resiNo, c.getAtomSequence().substring(i, i + 1));
                        }
                    } else {
                        resiMap.put(resiNo, c.getAtomSequence().substring(i, i + 1));
                    }
                }

                // sort all the residues in TreeMap
                Map<Integer, String> treeResiMap = new TreeMap<Integer, String>(resiMap);

                // Regenerate residues from scratch
                ArrayList<Integer> resiAl = new ArrayList<Integer>();
                for (int resiNo : treeResiMap.keySet()) {
                    resiAl.add(resiNo);
                }

                // Check
                int start = resiAl.get(0);
                int end = resiAl.get(resiAl.size() - 1);

                // Generate Segments
                for (int i = 1; i < resiAl.size(); i++) {
                    int numPresent = resiAl.get(i);
                    int numPrior = resiAl.get(i - 1);
                    // if Gap is found there
                    if ((numPresent - numPrior) > 1) {
                        SegmentRecord sr = generateNewSegmentRecord(start, numPrior, resiMap);
                        if (numPrior - start >= Integer.parseInt(PrepareConfig.pdbSegMinLengthMulti)) {
                            srAl.add(sr);
                        }
                        start = numPresent;
                    }
                }

                // Once finished traversing all the residues, add segments
                SegmentRecord sr = generateNewSegmentRecord(start, end, resiMap);
                if (end - start >= Integer.parseInt(PrepareConfig.pdbSegMinLengthSingle)) {
                    srAl.add(sr);
                }

                // Check inner linker, link gaps
                ArrayList<SegmentRecord> srAlCopy = new ArrayList<SegmentRecord>();
                if (srAl.size() > 0) {
                    srAlCopy.add(srAl.get(0));
                } else {
                    // log.warn(s.getPDBCode()+"_"+c.getChainID()+": Fragments
                    // length is not long enough");
                    continue;
                }

                // Add linked residues to srAlCopy
                for (int i = 1; i < srAl.size(); i++) {
                    if (srAl.get(i).getSegmentStart() - srAlCopy.get(srAlCopy.size() - 1).getSegmentEnd() - 1 <= Integer
                            .parseInt(PrepareConfig.pdbSegGapThreshold)) {
                        int linkNum = srAl.get(i).getSegmentStart() - srAlCopy.get(srAlCopy.size() - 1).getSegmentEnd()
                                - 1;
                        sr = srAlCopy.get(srAlCopy.size() - 1);
                        sr.setSegmentEnd(srAl.get(i).getSegmentEnd());
                        String linkStr = "";
                        for (int j = 0; j < linkNum; j++) {
                            linkStr = linkStr + "X";
                        }
                        sr.setAaSequence(sr.getAaSequence() + linkStr + srAl.get(i).getAaSequence());
                    } else {
                        srAlCopy.add(srAl.get(i));
                    }
                }

                int segCount = 1;

                for (SegmentRecord srr : srAlCopy) {
                    // System.out.println(headerStr + " " + segCount + " " +
                    // srr.getSegmentStart() + " " + srr.getSegmentEnd() + "\n"
                    // + srr.getAaSequence() + "\n");
                    tmpstr = tmpstr + headstr + "_" + segCount + " " + molClassification + " length:" + seqLength + " "
                            + srr.getSegmentStart() + " " + srr.getSegmentEnd() + "\n" + srr.getAaSequence() + "\n";
                    segCount++;
                }

                outstr = outstr + tmpstr;
            } catch (Exception ex) {
                log.error(s.getPDBCode() + "_" + c.getChainID() + ": Exception");
                ex.printStackTrace();
            }
        }

        return outstr;
    }

    public void initSequencefromFolder(String dirname, String outfilename) {
        initSequencefromFolder(dirname, outfilename, 0);
    }

    public void initSequencefromFolder(String dirname, String outfilename, int maxFiles) {
        try {
            log.info("[PDB] Use part of all PDB");

            File dir = new File(dirname);
            log.info("[PDB] Getting all files in " + dir.getCanonicalPath() + " including those in subdirectories");
            List<File> files = (List<File>) FileUtils.listFiles(dir, TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
            if (files.isEmpty()) {
                log.warn("[PDB] No files found under " + dir.getCanonicalPath() + "; output will be empty.");
            }
            List<String> outlist = new ArrayList<String>();
            int processed = 0;
            for (File file : files) {
                if (maxFiles > 0 && processed >= maxFiles) {
                    break;
                }
                String gzfilename = file.getName();
                String decompressedFile = PrepareConfig.tmpdir + processed + "_" + gzfilename.replace(".gz", "");
                unGunzipFile(file.getCanonicalPath(), decompressedFile);
                String seqs = readPDB2Results(decompressedFile);
                if (seqs != null && !seqs.isEmpty()) {
                    outlist.add(seqs);
                }
                new File(decompressedFile).delete();
                processed++;
            }
            log.info("[PDB] Write PDB sequences ...");
            FileUtils.writeLines(new File(outfilename), outlist, "");
            log.info("[PDB] PDB sequences generation done (" + processed + " files processed)");

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Used for Statistics unGunzip .gz file to plain file
     * 
     * @param compressedFile
     * @param decompressedFile
     */
    public void unGunzipFile(String compressedFile, String decompressedFile) {
        byte[] buffer = new byte[1024];
        try {
            FileInputStream fileIn = new FileInputStream(compressedFile);
            GZIPInputStream gZIPInputStream = new GZIPInputStream(fileIn);
            FileOutputStream fileOutputStream = new FileOutputStream(decompressedFile);
            int bytes_read;
            while ((bytes_read = gZIPInputStream.read(buffer)) > 0) {
                fileOutputStream.write(buffer, 0, bytes_read);
            }
            gZIPInputStream.close();
            fileOutputStream.close();
            // System.out.println("The file was decompressed successfully!");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

}

