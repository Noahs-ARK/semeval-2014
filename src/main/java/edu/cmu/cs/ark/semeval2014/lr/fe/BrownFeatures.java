package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureExtractor;
import util.BasicFileIO;
import util.U;

import java.util.HashMap;
import java.util.Map;

public class BrownFeatures extends FeatureExtractor implements FE.EdgeFE {
	private final static int MAX_CLUSTER_LENGTH = 12; // best length is 12

	private final Map<String, String> brownMap;

	public BrownFeatures(Map<String, String> brownMap) {
		this.brownMap = brownMap;
	}

	public static Map<String, String> load(String brownFileName) {
		System.err.println(U.sf("Loading Brown clusters from %s", brownFileName));
		final Map<String, String> brownMap = new HashMap<>();
		for (String line : BasicFileIO.openFileLines(brownFileName)) {
			String[] parts = line.trim().split("\t");
			brownMap.put(parts[1], parts[0]);
		}
		System.err.println("Done loading Brown clusters");
		return brownMap;
	}

	@Override
	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);
	}

	private String getPrefix(String brown, int length) {
		String prefix;
		if (brown == null) {
			return "oov";
		}
		if (brown.length() > length) {
			prefix = brown.substring(0, length);
		} else {
			prefix = brown;
		}
		return prefix;
	}

	@Override
	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {
		final String srcPostag = sent.pos[srcTokenIdx];
		final String destPostag = sent.pos[destTokenIdx];
		final String srcToken = sent.sentence[srcTokenIdx];
		final String destToken = sent.sentence[destTokenIdx];
        //final String dir = srcTokenIdx > destTokenIdx ? "dir=i>j" : "dir=i<j";

        String brownSrc = brownMap.get(srcToken);	    
        String brownDest = brownMap.get(destToken);
		
	    for (Integer i = 2; i <= MAX_CLUSTER_LENGTH; i+=2) {
	    	String srcPrefix = getPrefix(brownSrc, i);
	    	String destPrefix = getPrefix(brownDest, i);
	    	fa.add(U.sf("brown%s:bg:%s_%s_%s_%s", 
	    			i.toString(), srcPrefix, srcPostag, destPrefix, destPostag));
	    }	
	}
}
