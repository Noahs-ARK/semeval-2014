package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.util.HashMap;
import java.util.Map;

import util.BasicFileIO;
import util.U;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureExtractor;

public class BrownFeatures extends FeatureExtractor implements FE.TokenFE, FE.EdgeFE {
	
	//private String brownFileName;
	private Map<String, String> brownMap;
    private final int MAX_CLUSTER_LENGTH = 16; // best length is 12

	public BrownFeatures(String brownFileName) {
    	Map<String, String> clusterMap = new HashMap<String, String>();
		for (String line : BasicFileIO.openFileLines(brownFileName)) {
			String[] parts =line.trim().split("\t");
			clusterMap.put(parts[1], parts[0]);
		}
		this.brownMap = clusterMap;    
	}
	
	public BrownFeatures(Map<String, String> brownMap) {
		this.brownMap = brownMap;
	}

	@Override
	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);
	}
	
	/*@Override
	public void initializeAtStartup() {
		brownMap = new HashMap<String, String>();
		for (String line : BasicFileIO.openFileLines(brownFileName)) {
			String[] parts =line.trim().split("\t");
		    brownMap.put(parts[1], parts[0]);
		}
	}*/

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
	public void features(int tokenIdx, FeatureAdder fa) {
		/*final String token = sent.sentence[tokenIdx];
		final String postag = sent.pos[tokenIdx];
		
		if (brownMap.containsKey(token) == false) {
	        fa.add("brown:oov");
		    fa.add(U.sf("brown:oov_%s", postag));
		    fa.add(U.sf("brown:oov_%s", token));
		    fa.add(U.sf("brown:oov_%s_%s", token, postag));
		    return;
		}
		
		String brownId = getPrefix(brownMap.get(token), maxClusterLength);
	    String brown4 = getPrefix(brownId, 4);
	    String brown6 = getPrefix(brownId, 6);
	    
	    // complete brown string - removing these helps
	    fa.add(U.sf("brown0:%s", brownId));
	    fa.add(U.sf("brown4:%s", brown4));
	    fa.add(U.sf("brown6:%s", brown6));
	    
	    // brown prefix for postag
	    fa.add(U.sf("brown0p:%s_%s", brownId, postag));
	    fa.add(U.sf("brown4p:%s_%s",  brown4, postag));
	    fa.add(U.sf("brown6p:%s_%s",  brown6, postag));
	    
	    // brown prefix for token
	    fa.add(U.sf("brown0:%s_%s", brownId, token));
	    fa.add(U.sf("brown4:%s_%s",  brown4, token));
	    fa.add(U.sf("brown6:%s_%s",  brown6, token));
	    
	    // brown prefix for postag + token -- removing this helps
	    fa.add(U.sf("brown0:%s_%s_%s", brownId, token, postag));
	    fa.add(U.sf("brown4:%s_%s_%s",  brown4, token, postag));
	    fa.add(U.sf("brown6:%s_%s_%s",  brown6, token, postag));*/
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
