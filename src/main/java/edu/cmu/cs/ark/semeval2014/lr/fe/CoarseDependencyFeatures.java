package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import util.U;

public class CoarseDependencyFeatures extends FE.FeatureExtractor implements FE.TokenFE,
		FE.EdgeFE {

	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);

	}

	public int treeDistance(int src, int dest) {
		int distance=0;
		int currentHead=sent.syntacticDependencies.deps()[src].head();
		while(currentHead >= 0) {
			distance++;
			if (currentHead == dest) {
				return distance;
			}
			currentHead=sent.syntacticDependencies.deps()[currentHead].head();
		}
		return -1;
	}
	
	public void features(int tokenIdx, FeatureAdder fa) {

	}

	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {

		int distance=treeDistance(srcTokenIdx, destTokenIdx);
		if (distance > -1) {
			fa.add("isDependencyAncestor");
			
			if (distance < 10) {
				fa.add(U.sf("dependencyAncestorDistance:%s", distance));
			}
		}
		
		distance=treeDistance(destTokenIdx, srcTokenIdx);
		if (distance > -1) {
			fa.add("isDependencyDescendent");
			
			if (distance < 10) {
				fa.add(U.sf("dependencyDescendentDistance:%s", distance));
			}
		}
		
	}
	
}
