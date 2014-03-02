package edu.cmu.cs.ark.semeval2014.prune;


import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.nlp.MorphaLemmatizer;
import util.BasicFileIO;
import util.U;
import edu.cmu.cs.ark.semeval2014.lr.fe.*;

import java.util.HashSet;
import java.util.Set;

public class PruneFeatures extends FE.FeatureExtractor implements FE.TokenFE {
	private final MorphaLemmatizer morpha = new MorphaLemmatizer();
//	Set<String> whitelist;
	private String[] lemmas;

	@Override
	public void initializeAtStartup() {
	}

	@Override
	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);
		setLemmaPostags();
	}

	private void setLemmaPostags() {
		final int n = sent.sentence().length;
		lemmas = new String[n];
		for (int tokIdx = 0; tokIdx < n; tokIdx++) {
			final String postag = sent.pos()[tokIdx];
			final String word = sent.sentence()[tokIdx];
			final String lemma = morpha.getLemma(word, postag);
			lemmas[tokIdx] = lemma;
		}
	}

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		final String postag = sent.pos()[tokenIdx];
        final String lemma = lemmas[tokenIdx];
        fa.add(U.sf("pos:%s", postag));
		//fa.add(U.sf("lem:%s", lemma));
		//fa.add(U.sf("lem:%s_%s", lemma, postag));
	}
}
