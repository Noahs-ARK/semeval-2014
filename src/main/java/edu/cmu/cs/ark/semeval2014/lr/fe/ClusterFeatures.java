package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.util.Map;

import util.U;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.EdgeFE;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureExtractor;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.TokenFE;

public class ClusterFeatures extends FeatureExtractor implements EdgeFE, TokenFE {

	private Map<String, String> clusterMap;
	//private final String clusterFileName;
	
	public ClusterFeatures(Map<String, String> clusterMap) {
		this.clusterMap = clusterMap;
	}
	
	/*@Override
	public void initializeAtStartup() {
		clusterMap = new HashMap<String, String>();
		for (String line : BasicFileIO.openFileLines(clusterFileName)) {
			String[] parts =line.trim().split("\t");
			clusterMap.put(parts[1], parts[0]);
		}
	}*/

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		/*final String token = sent.sentence[tokenIdx];
		final String postag = sent.pos[tokenIdx];
		final String cluster;
		if (clusterMap.containsKey(token)) {
			cluster = clusterMap.get(token);
		} else {
			cluster = "oov";
		}
		fa.add(U.sf("cluster:%s", cluster));
		fa.add(U.sf("cluster:%s_%s", cluster, token));
		fa.add(U.sf("cluster:%s_%s", cluster, postag));*/
	}

	@Override
	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {
		final String srcToken = sent.sentence[srcTokenIdx];
		final String srcPostag = sent.pos[srcTokenIdx];
		final String srcCluster;
		if (clusterMap.containsKey(srcToken)) {
			srcCluster = clusterMap.get(srcToken);
		} else {
			srcCluster = "oov";
		}
		
		final String destToken = sent.sentence[destTokenIdx];
		final String destPostag = sent.pos[destTokenIdx];
		final String destCluster;
		if (clusterMap.containsKey(destToken)) {
			destCluster = clusterMap.get(destToken);
		} else {
			destCluster = "oov";
		}
		
		//fa.add(U.sf("cluster:%s_%s", srcCluster, destCluster));
		//fa.add(U.sf("cluster:%s_%s_%s_%s", srcCluster, srcToken, destCluster, destToken));
		fa.add(U.sf("cluster:%s_%s", srcCluster, srcPostag, destCluster, destPostag));
	}

}
