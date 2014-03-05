package edu.cmu.cs.ark.semeval2014.topness;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

public class DetermTopness implements TopnessScorer {

	@Override
	public double topness(InputAnnotatedSentence sent, int t) {
		double relpos = 1.0*t / sent.size();
		String pos = sent.pos[t];
		
		// umm, VB vs MD could be formalism dependent. a modal logician would say MD should be top, right?
		double prio = 
				pos.startsWith("VB") ? 1000 :
				pos.startsWith("MD") ? 800 :
				pos.startsWith("NN") || pos.startsWith("PRP") ? 500 :
				0;
		return prio - relpos;
	}

}
