package lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

public class FE {

	/** extract single-token features. */
	public static interface FeatureExtractor1 {
		public void features(InputAnnotatedSentence sent, int word1, FeatureAdder fa);
	}
	
	/** extract features for a token pair, and label output type. */
	public static interface FeatureExtractor2 {
		public void features(InputAnnotatedSentence sent, int word1, int word2, String label, FeatureAdder fa);
	}
	
	/** this is the callback thingy that the framework passes in to the FeatureExtractor object. */
	public static abstract class FeatureAdder {
		
		public abstract void add(String featname, double value);
		
		public void add(String featname) {
			add(featname, 1.0);
		}
	}

}
