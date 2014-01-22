package lr;

import java.util.ArrayList;

import util.U;
import util.misc.Pair;

/** holds base feature vectors. current design does NOT have conjunctions against labels or nothing like that. is this ok? */
public class NumberizedSentence {
	int T;  // sentence length
	
	/** for each token: featurevector for whether it is a predicate. */
	ArrayList< Pair<Integer,Double> >[] predicateFeatures;
	/** for each (token1 x token2): conditional on token1 being a predicate, featurevector for token2 being an argument. */
	ArrayList< Pair<Integer,Double> >[][] argFeatures;
	
	@SuppressWarnings("unchecked")
	NumberizedSentence(int sentenceLength) {
		T = sentenceLength;
		predicateFeatures = (ArrayList< Pair<Integer,Double> >[] ) new ArrayList[T];
		argFeatures = (ArrayList< Pair<Integer,Double> >[][]) new ArrayList[T][T];
		
		for (int i=0; i<T; i++) {
			predicateFeatures[i] = new ArrayList<>();
			for (int j=0; j<T; j++) {
				argFeatures[i][j] = new ArrayList<>();
			}
		}
	}
}
