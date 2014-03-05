package edu.cmu.cs.ark.semeval2014.prune;

import java.util.Map;
import java.util.Set;


public class FeatureVector {
	
	// to score a given feature vector for a word.
	public static double score(Set<String> features, String prevTag, Map<String, Double> w) {
		double score = 0;
		for (String feat : features){
			if (feat.contains("Ti-1=")){
				if (feat.contains("Ti-1=" + prevTag) || feat.contains("Ti-1=<START>")){
					if (w.containsKey(feat))
						score += w.get(feat);
				}
			} else {
				if (w.containsKey(feat))
					score += w.get(feat);
			}
				
		}
		return score;
	}
}