package edu.cmu.cs.ark.semeval2014.lr.fe;

import util.U;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;

public class WordFormFE implements FE.FeatureExtractor2 {

	@Override
	public void features(InputAnnotatedSentence sent, int word1, int word2, String label, FeatureAdder fa) {
//		String w1 = sent.sentence()[word1];
//		String w2 = sent.sentence()[word2];
//		fa.add(U.sf("lc:head:%s_%s", w1.toLowerCase(), label));
//		fa.add(U.sf("lc:child:%s_%s", w2.toLowerCase(), label));
//		fa.add(U.sf("lc:bg:%s_%s_%s", w1.toLowerCase(), w2.toLowerCase(), label));
//		
		String p1 = sent.pos()[word1];
		String p2 = sent.pos()[word2];
		fa.add(U.sf("pos:head:%s_%s", p1, label));
		fa.add(U.sf("pos:child:%s_%s", p2, label));
		fa.add(U.sf("pos:bg:%s_%s_%s", p1, p2, label));
	}

//	@Override
//	public void features(InputAnnotatedSentence sent, int word1, FeatureAdder fa) {
//		String w = sent.sentence()[word1];
//		fa.add("raw:" + w);
//		fa.add("lc:" + w.toLowerCase());
//	}
	
}
