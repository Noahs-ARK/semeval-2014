package edu.cmu.cs.ark.semeval2014.topness;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

public interface TopnessScorer {
	/** higher number = more top-ity. more top-ness! */
	public double topness(InputAnnotatedSentence sent, int t);
}
