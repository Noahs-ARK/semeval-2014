package edu.cmu.cs.ark.semeval2014.common;

import java.util.Arrays;

import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse;

/** see also scala InputAnnotatedSentenceParser */
public class InputAnnotatedSentence {

	// Gold-standard annotations and information
	public String sentenceId;
	/** tokens in the sentence */
	public String[] sentence;
	public boolean[] isTop;

	public DependencyParse syntacticDependencies;
	public String[] pos;

	// Values predicted by preprocessor systems.  leave these null at initialization.
	public int[] predicatePredictions;
	public int[] singletonPredictions;
	public double[] topnessPredProbs;
	public double[] singletonPredProbs;

	public InputAnnotatedSentence(int size) {
		sentence = new String[size];
		pos = new String[size];
		isTop = new boolean[size];
	}

	/** number of tokens in sentence */
	public int size() { return sentence.length; }
}
