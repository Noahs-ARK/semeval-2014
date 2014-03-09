package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import util.U;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureExtractor;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.TokenFE;

public class ClusterFeatures extends FeatureExtractor implements TokenFE {

	private Map<String, String> clusterMap;
	
	public ClusterFeatures(Map<String, String> clusterMap) {
		this.clusterMap = clusterMap;
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
		//fa.add(U.sf("cluster:%s", cluster));
		fa.add(U.sf("cluster:%s_%s", cluster, token));
		//fa.add(U.sf("cluster:%s_%s", cluster, postag));
	}

}
