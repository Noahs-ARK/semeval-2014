package edu.cmu.cs.ark.semeval2014.lr.fe;

import util.U;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;

public class LinearOrderFeatures extends FE.FeatureExtractor implements FE.TokenFE, FE.EdgeFE {

	@Override
	/**
	 * Feature for the directed linear distance between a child and its parent.
	 * Motivation: ARG1 usually on the left, ARG2 on the right, etc.
	 */
	public void features(int word1, int word2, String label, FeatureAdder fa) {

		int dist=word1-word2;
		fa.add(U.sf("lin:%s_%s", dist, label));
		fa.add(U.sf("left:%s_%s", (word1<word2), label));
	}

	@Override
	public void features(int word1, FeatureAdder fa) {
		
	}

}
