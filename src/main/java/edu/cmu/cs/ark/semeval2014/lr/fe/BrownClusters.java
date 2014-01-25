package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import util.BasicFileIO;
import util.U;

public class BrownClusters extends FE.FeatureExtractor implements FE.TokenFE {
	public static String clusterResourceName = "resources/50mpaths2";
	
	public static HashMap<String,String> wordToPath;
	public int[] prefixes = new int[]{4,8,12,16};
//	public int[] prefixes = new int[]{2,4,6,8,10,12,14,16};
//	public int[] prefixes = new int[]{2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
	
	public BrownClusters() {
		U.p("HERE");
		BufferedReader bReader = null;
		bReader = BasicFileIO.openFileToRead(clusterResourceName);
		String[] splitline;
		String line=BasicFileIO.getLine(bReader);
		wordToPath = new HashMap<String,String>(); 
		while(line != null){
			splitline = line.split("\\t");
			wordToPath.put(splitline[1], splitline[0]);
			line = BasicFileIO.getLine(bReader);
		}			
		U.pf("loaded brown clusters with %d wordtypes\n", wordToPath.size());
	}

	@Override
	public void features(int t, FE.FeatureAdder fa) {
		String bitstring = null;
		String tok = sent.sentence()[t];
		String normaltok = tok.toLowerCase();
	    bitstring = wordToPath.get(normaltok);
	    
		if (bitstring != null){
			bitstring = StringUtils.leftPad(bitstring, 16).replace(' ', '-');
			for(int i=0; i<prefixes.length; i++) {
				int prefixlen = prefixes[i];
				fa.add("TwCluster|" + bitstring.substring(0,prefixlen));
			}
		}
	}


}
