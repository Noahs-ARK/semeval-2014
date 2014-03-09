package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.util.HashMap;
import java.util.Map;

import util.BasicFileIO;
import util.U;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureExtractor;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.TokenFE;

public class ClusterFeatures extends FeatureExtractor implements TokenFE {

	private Map<String, String> clusterMap;
	private final String clusterFileName;
	
	public ClusterFeatures(String clusterFileName) {
		this.clusterFileName = clusterFileName;
	}
	
	@Override
	public void initializeAtStartup() {
		clusterMap = new HashMap<String, String>();
		for (String line : BasicFileIO.openFileLines(clusterFileName)) {
			String[] parts =line.trim().split(" +");
			clusterMap.put(parts[1], parts[0]);
		}
	}

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		final String token = sent.sentence[tokenIdx];
		final String postag = sent.pos[tokenIdx];
		final String cluster;
		if (clusterMap.containsKey(token)) {
			cluster = clusterMap.get(token);
		} else {
			cluster = "oov";
		}
		fa.add(U.sf("cluster:%s", cluster));
		fa.add(U.sf("cluster:%s_%s", cluster, token));
		fa.add(U.sf("cluster:%s_%s", cluster, postag));
	}

}
