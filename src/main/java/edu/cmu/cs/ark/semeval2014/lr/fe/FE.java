package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

// If you want to do fancier setup when starting a new sentence, override the setupSentence() method

public class FE {

	/** extract single-token features. */
	public static abstract class FeatureExtractor1 {
		/** the current sentence from which features are being extracted. */
		public InputAnnotatedSentence sent;
		public void setupSentence(InputAnnotatedSentence _sent) {
			sent = _sent;
		}
		abstract public void features(InputAnnotatedSentence sent, int word1, FeatureAdder fa);
	}
	
	/** extract features for a token pair, and label output type. */
	public static abstract class FeatureExtractor2 {
		/** the current sentence from which features are being extracted. */
		public InputAnnotatedSentence sent;
		public void setupSentence(InputAnnotatedSentence _sent) {
			sent = _sent;
		}
		abstract public void features(int word1, int word2, String label, FeatureAdder fa);
	}
	
	/** this is the callback thingy that the framework passes in to the FeatureExtractor object. */
	public static abstract class FeatureAdder {
		
		public abstract void add(String featname, double value);
		
		public void add(String featname) {
			add(featname, 1.0);
		}
	}

}
