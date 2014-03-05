package edu.cmu.cs.ark.semeval2014.lr.fe;

import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import util.U;

/** for simple testing */
public class JustPOS extends FE.FeatureExtractor implements FE.TokenFE, FE.EdgeFE {

	@Override
	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {
        final String srcPostag = sent.pos[srcTokenIdx];
        final String destPostag = sent.pos[destTokenIdx];
		fa.add(U.sf("pos:bg:%s_%s", srcPostag, destPostag));
	}

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		final String postag = sent.pos[tokenIdx];
        fa.add(U.sf("pos:%s", postag));
	}
	
}
