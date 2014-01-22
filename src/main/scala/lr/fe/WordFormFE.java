package lr.fe;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import lr.fe.FE.FeatureAdder;

public class WordFormFE implements FE.FeatureExtractor1 {

	@Override
	public void features(InputAnnotatedSentence sent, int word1, FeatureAdder fa) {
		String w = sent.sentence()[word1];
		fa.add("raw:" + w);
		fa.add("lc:" + w.toLowerCase());
	}
	
}
