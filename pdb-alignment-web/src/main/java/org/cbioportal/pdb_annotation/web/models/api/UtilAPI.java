package org.cbioportal.pdb_annotation.web.models.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.cbioportal.pdb_annotation.util.ReadConfig;
import org.cbioportal.pdb_annotation.web.models.Ensembl;
import org.cbioportal.pdb_annotation.web.models.GenomeResidueInput;
import org.cbioportal.pdb_annotation.web.models.Residue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.web.client.RestTemplate;

@ConfigurationProperties
public class UtilAPI {
    @Value("${gn.api.url}")
    private String gnApiUrl;

    public String getGnApiUrl() {
        return gnApiUrl;
    }

    public void setGnApiUrl(String gnApiUrl) {
        this.gnApiUrl = gnApiUrl;
    }

    /**
     * Call by HumanGenome
     * 
     * @param chromosomeNum
     * @param positionNum
     * @param nucleotideType
     * @param genomeVersion
     * @return
     * @throws Exception
     */
    public List<GenomeResidueInput> callHumanGenomeAPI(String chromosomeNum, long positionNum, String nucleotideType,
            String genomeVersion) throws Exception {
        ReadConfig rc = ReadConfig.getInstance();
        // System.out.println("ReadURL:\t"+rc.getGnApiUrl());
        List<GenomeResidueInput> outlist = new ArrayList<GenomeResidueInput>();

        String url = "";
        if (genomeVersion.equals("GRCH38")) {
            // For GRCH38, choose Ensembl
            url = rc.getGnApiEnsemblUrl();
        } else if (genomeVersion.equals("GRCH37")) {
            // For GRCH37, choose GenomeNexus annotation
            url = rc.getGnApiGnUrl();
        } else {
            System.out.println("Only support GRCH37 and GRCH38 Now!");
        }

        url = url.replace("CHROMSOME", chromosomeNum);
        url = url.replace("POSITION", Long.toString(positionNum));
        url = url.replace("ORIGINAL", nucleotideType);

        // Artificial mutation for API
        String mutation = "A";
        if (nucleotideType.equals("A")) {
            mutation = "T";
        }
        url = url.replace("MUTATION", mutation);
        System.out.println("APIURL:\t" + url);

        return(callURLAPI(url,true,true));
    }
    
    
    /**
     * Calling by dbSNPID
     * 
     * @param dbSNPID
     * @return
     * @throws Exception
     */
    public List<GenomeResidueInput> calldbSNPAPI(String dbSNPID) throws Exception {
        ReadConfig rc = ReadConfig.getInstance();
        // System.out.println("ReadURL:\t"+rc.getGnApiUrl());
        
        String url = rc.getGnApiGnSNPUrl();
        url = url.replace("DBSNPID", dbSNPID);
        
        System.out.println("APIURL:\t" + url);
        return(callURLAPI(url,false,false));
    }
    
    /**
     * Call Endpoints of API by URL
     * 
     * @param url
     * @param redundancyTag: True:Allow redundancy, False: does not allow redundancy
     * @return
     * @throws Exception
     */
    public List<GenomeResidueInput> callURLAPI(String url, boolean redundancyTag, boolean multipleTag) throws Exception{
        List<GenomeResidueInput> outlist = new ArrayList<GenomeResidueInput>();
        HashMap<String, String> hm = new HashMap<String, String>();

        RestTemplate restTemplate = new RestTemplate();

        try {
            if (multipleTag) {
                Quote[] quotes = restTemplate.getForObject(url, Quote[].class);
                for (Quote quote : quotes) {
                    outlist.addAll(callURLAPIcore(url, redundancyTag, outlist, hm, quote));
                }
            } else {
                Quote quote = restTemplate.getForObject(url, Quote.class);
                outlist.addAll(callURLAPIcore(url, redundancyTag, outlist, hm, quote));
            }
            hm = null;
        } catch (Exception ex) {
            //Once get error from calling upstream API from cbioportal or ensembl, using try/catch to catch this error and return [], otherwise will get 500 error finally
            ex.printStackTrace();
        }

        return outlist;       
    }
    
    
    /**
     * Call Endpoints core of API by URL
     * 
     * @param url
     * @param redundancyTag
     * @param outlist
     * @param hm
     * @param quote
     * @return
     * @throws Exception
     */
    public List<GenomeResidueInput> callURLAPIcore(String url, boolean redundancyTag, List<GenomeResidueInput> outlist, HashMap<String,String> hm, Quote quote) throws Exception{
    	
            List<Transcript_consequences> list = quote.getTranscript_consequences();
            for (int i = 0; i < list.size(); i++) {
            	//System.out.println(list.size()+":"+i+"\t"+list.get(i).getProtein_start());
                if (list.get(i).getProtein_start() != 0) {
                    // if (list.get(i).getBiotype().equals("protein_coding") &&
                    // list.get(i).getProtein_start() != 0) {
                    GenomeResidueInput gr = new GenomeResidueInput();
                    Residue residue = new Residue();
                    residue.setResidueNum(list.get(i).getProtein_start());
                    gr.setResidue(residue);

                    Ensembl ensembl = new Ensembl();
                    ensembl.setEnsemblid(list.get(i).getProtein_id());// ENSP
                    ensembl.setEnsemblgene(list.get(i).getGene_id());// ENSG
                    ensembl.setEnsembltranscript(list.get(i).getTranscript_id());// ENST
                    gr.setEnsembl(ensembl);
                    
                    /*
                     * Check whether allow redundancy
                     */
                    if(redundancyTag){
                    	outlist.add(gr);
                    }else{                   	
                    	if(hm.containsKey(list.get(i).getProtein_id())){
                    		
                    	}else{
                    		hm.put(list.get(i).getProtein_id(), "");
                    		outlist.add(gr);
                    		System.out.println(i+"th id/location:"
                                    +list.get(i).getProtein_id()+"\t"+list.get(i).getProtein_start());
                    	}                    	
                    }                    
                    
                }
            }
        

        return outlist;
    }

}
