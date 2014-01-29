package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

/**
 * Every feature extractor class 
 *     - must extend FeatureExtractor
 *     - must implement one or more of the FeatureExtractor* interfaces
 *  
 * A feature extractor object gets created once at startup.
 * For every sentence, setupSentence() gets called, which by default assigns the InputAnnotatedSentence object to the member 'sent'.
 * It's expected that subclasses will access it there.
 * 
 * If you want to do fancier setup when starting a new sentence, override the setupSentence() method.
 * If you want fancier setup at parser startup time, override initializeAtStartup().
 * 
 */
public class FE {

	public static abstract class FeatureExtractor {
		
		/** the current sentence from which features are being extracted. */
		public InputAnnotatedSentence sent;
		
		/** This gets called once when starting on a new sentence. If you override this, make sure to assign to 'sent'. */
		public void setupSentence(InputAnnotatedSentence _sent) {
			sent = _sent;
		}
		
		/** If you want to load resources or something at parser startup time, override this. */
		public void initializeAtStartup() {
		}
		
	}

	/** If you implement this, you're saying: I know how to extract single-token features. */
	public static interface TokenFE {
		abstract public void features(int tokenIdx, FeatureAdder fa);
	}
	
	/** If you implement this, you're saying: I know how to extract directed edge (token pair) features. */
	public static interface EdgeFE {
		abstract public void features(int srcTokenIdx, int destTokenIdx, String label, FeatureAdder fa);
	}
	
	/** this is the callback thingy that the framework passes in to the FeatureExtractor object. */
	public static abstract class FeatureAdder {
		
		public abstract void add(String featname, double value);
		
		public void add(String featname) {
			add(featname, 1.0);
		}
	}

}
