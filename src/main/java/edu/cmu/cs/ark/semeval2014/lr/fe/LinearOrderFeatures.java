package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import util.U;

public class LinearOrderFeatures extends FE.FeatureExtractor implements FE.EdgeFE {

	@Override
	/**
	 * Feature for the directed linear distance between a child and its parent.
	 * Motivation: ARG1 usually on the left, ARG2 on the right, etc.
	 */
	public void features(int srcTokenIdx, int destTokenIdx, String label, FeatureAdder fa) {
		int dist = srcTokenIdx - destTokenIdx;
		fa.add(U.sf("lin:%s_%s", dist, label));
		fa.add(U.sf("left:%s_%s", (srcTokenIdx < destTokenIdx), label));
	}
}
