package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.nlp.MorphaLemmatizer;
import util.BasicFileIO;
import util.U;

import java.util.HashSet;
import java.util.Set;

public class BasicFeatures extends FE.FeatureExtractor implements FE.TokenFE, FE.EdgeFE {
	private final MorphaLemmatizer morpha = new MorphaLemmatizer();
//	Set<String> whitelist;
	private String[] lemmas;

	@Override
	public void initializeAtStartup() {
//		whitelist = new HashSet<>();
//		for (String line : BasicFileIO.openFileLines("resources/train_vocab.txt")) {
//			String[] parts =line.trim().split(" +");
//			int count = Integer.parseInt(parts[0]);
//			if (count >= 100) {
//				whitelist.add(parts[1]);
//			}
//		}
	}

	@Override
	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);
		setLemmaPostags();
	}

	private void setLemmaPostags() {
		final int n = sent.sentence.length;
		lemmas = new String[n];
		for (int tokIdx = 0; tokIdx < n; tokIdx++) {
			final String postag = sent.pos[tokIdx];
			final String word = sent.sentence[tokIdx];
			final String lemma = morpha.getLemma(word, postag);
			lemmas[tokIdx] = lemma;
		}
	}

	@Override
	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {
        final String srcPostag = sent.pos[srcTokenIdx];
        final String destPostag = sent.pos[destTokenIdx];
		final String srcLemma = lemmas[srcTokenIdx];
		final String destLemma = lemmas[destTokenIdx];
        final String dir = srcTokenIdx > destTokenIdx ? "dir=i>j" : "dir=i<j";

        fa.add(U.sf("lem:bg:%s_%s", srcLemma, destLemma));
        fa.add(U.sf("lem:bg:%s_%s_%s_%s", srcLemma, srcPostag, destLemma, destPostag));
		fa.add(U.sf("pos:bg:%s_%s", srcPostag, destPostag));
		fa.add(U.sf("pos:bg:%s_%s_%s", srcPostag, destPostag, dir));
	}

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		final String postag = sent.pos[tokenIdx];
        final String lemma = lemmas[tokenIdx];
        fa.add(U.sf("pos:%s", postag));
		fa.add(U.sf("lem:%s", lemma));
		fa.add(U.sf("lem:%s_%s", lemma, postag));
	}
}
