package edu.cmu.cs.ark.semeval2014.prune;

import edu.cmu.cs.ark.semeval2014.lr.fe.FE;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;

public class PruneFeatsForSemparser extends FE.FeatureExtractor implements FE.TokenFE {

	@Override
	public void features(int tokenIdx, FeatureAdder fa) {
		if (sent.singletons()[tokenIdx]==null || sent.predicates()[tokenIdx]==null) {
			throw new RuntimeException("the preprocessor 'pruner' was not run?");
		}
		fa.add("IsSingletonPrediction=" + sent.singletons()[tokenIdx]);
		fa.add("IsPredicatePrediction=" + sent.predicates()[tokenIdx]);
	}

}
