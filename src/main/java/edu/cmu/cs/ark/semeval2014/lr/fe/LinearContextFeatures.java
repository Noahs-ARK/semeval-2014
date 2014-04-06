package edu.cmu.cs.ark.semeval2014.lr.fe;

/**
 * Features for the parts of speech/lemma of the tokens in a window around the target word (for node features)
 * and source/destination words (for pairwise features)
 * 
 * 
 */
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.nlp.MorphaLemmatizer;
import util.BasicFileIO;
import util.U;

import java.util.HashSet;
import java.util.Set;

public class LinearContextFeatures extends FE.FeatureExtractor implements FE.TokenFE,
		FE.EdgeFE {
	private final MorphaLemmatizer morpha = new MorphaLemmatizer();
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
		
		String srcLeft1Pos = "LEFTWALL";
		String destLeft1Pos = "LEFTWALL";

		if (srcTokenIdx > 0) {
			srcLeft1Pos = sent.pos[srcTokenIdx - 1];	
		}
		if (destTokenIdx > 0) {
			destLeft1Pos = sent.pos[destTokenIdx - 1];	
		}

		String srcRight1Pos = "RIGHTWALL";
		String destRight1Pos = "RIGHTWALL";
		
		if (srcTokenIdx < sent.pos.length-1) {
			srcRight1Pos = sent.pos[srcTokenIdx + 1];
		}
		if (destTokenIdx < sent.pos.length-1) {
			destRight1Pos = sent.pos[destTokenIdx + 1];
		}

		fa.add(U.sf("pos:trigram:%s:%s:%s_%s:%s:%s", srcLeft1Pos, srcPostag, srcRight1Pos, destLeft1Pos, destPostag, destRight1Pos));
		fa.add(U.sf("pos:trigram:left:%s:%s_%s:%s", srcLeft1Pos, srcPostag, destLeft1Pos, destPostag));
		fa.add(U.sf("pos:trigram:right:%s:%s_%s:%s", srcPostag, srcRight1Pos, destPostag, destRight1Pos));
	
	}

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		final String postag = sent.pos[tokenIdx];
		final String lemma = lemmas[tokenIdx];

		String left1Pos = "LEFTWALL";
		String left1Lemma = "LEFTWALL";
		
		String left2Pos = "LEFTWALL";
		String left2Lemma = "LEFTWALL";
		
		String right1Pos = "RIGHTWALL";
		String right1Lemma = "RIGHTWALL";
		
		String right2Pos = "RIGHTWALL";
		String right2Lemma = "RIGHTWALL";
		
		if (tokenIdx > 0) {
			left1Pos = sent.pos[tokenIdx - 1];
			left1Lemma = lemmas[tokenIdx - 1];
			if (tokenIdx > 1) {
				left2Pos = sent.pos[tokenIdx - 2];
				left2Lemma = lemmas[tokenIdx - 2];
			}
		}
		if (tokenIdx < sent.pos.length-1) {
			right1Pos = sent.pos[tokenIdx + 1];
			right1Lemma = lemmas[tokenIdx + 1];
			if (tokenIdx < sent.pos.length-2) {
				right2Pos = sent.pos[tokenIdx + 2];
				right2Lemma = lemmas[tokenIdx + 2];
			}
		}

		// left and right pos + lemma at distance 1
		fa.add(U.sf("left1pos:%s", left1Pos));
		fa.add(U.sf("left1lem:%s", left1Lemma));

		fa.add(U.sf("right1pos:%s", right1Pos));
		fa.add(U.sf("right1lem:%s", right1Lemma));
		
		// left and right pos + lemma at distance 2
		fa.add(U.sf("left2pos:%s", left2Pos));
		fa.add(U.sf("left2lem:%s", left2Lemma));
		
		fa.add(U.sf("right2pos:%s", right2Pos));
		fa.add(U.sf("right2lem:%s", right2Lemma));
		
		// left and right pos/lemma at distance 1 + current pos/lemma
		fa.add(U.sf("left_bigram_center_pos:%s:%s", left1Pos, postag));
		//fa.add(U.sf("left_bigram_center_lemma:%s", left1Lemma, lemma));

		fa.add(U.sf("right_bigram_center_pos:%s:%s", postag, right1Pos));
		//fa.add(U.sf("right_bigram_center_lemma:%s", lemma, right1Lemma));
		
		// left bigram pos + current pos
		fa.add(U.sf("left_trigram_center_pos:%s:%s:%s", left2Pos, left1Pos, postag));
		fa.add(U.sf("right_trigram_center_pos:%s:%s:%s", postag, right1Pos, right2Pos));

		

	}
}
