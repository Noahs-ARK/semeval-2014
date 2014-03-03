package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import util.U;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.common.SyntacticDependency;

public class CoarseDependencyFeatures extends FE.FeatureExtractor implements FE.TokenFE,
		FE.EdgeFE {

	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);

	}

	public int treeDistance(int src, int dest) {
		int distance=0;
		int currentHead=sent.syntacticDependencies()[src].head();
		while(currentHead != -1) {
			distance++;
			if (currentHead == dest) {
				return distance;
			}
			currentHead=sent.syntacticDependencies()[currentHead].head();
		}
		return -1;
	}
	
	public void features(int tokenIdx, FeatureAdder fa) {

	}

	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {

		int distance=treeDistance(srcTokenIdx, destTokenIdx);
		if (distance > -1) {
			fa.add("ancestor");
			
			if (distance < 10) {
				fa.add(U.sf("ancestorDistance:%s", distance));
			}
		}
		
		distance=treeDistance(destTokenIdx, srcTokenIdx);
		if (distance > -1) {
			fa.add("child");
			
			if (distance < 10) {
				fa.add(U.sf("childDistance:%s", distance));
			}
		}
		
	}
	
}
