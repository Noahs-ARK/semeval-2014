package edu.cmu.cs.ark.semeval2014.prune;

import java.util.*;

import util.U;
import util.Vocabulary;
import util.misc.Pair;


import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.LRParser;
import edu.cmu.cs.ark.semeval2014.lr.Model;
import edu.cmu.cs.ark.semeval2014.lr.NumberizedSentence;
import edu.cmu.cs.ark.semeval2014.lr.LRParser.LabelFeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.LRParser.TokenFeatAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.DmFe;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.IsEdgeFe;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.PasFe;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.PassThroughFe;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.PcedtFE;

public class Prune {
	private int numIter = 1;
	private List<int[]> singletons;
	private Map<String, Double> singletonWeights;
	private List<int[]> predicates;
	private Map<String, Double> predicateWeights;
	private List<int[]> tops;
	private InputAnnotatedSentence[] inputSentences;
	private List<FE.FeatureExtractor> allFE = new ArrayList<>();
	
	// model parameters
	private Vocabulary labelVocab;
	
	private List<FE.LabelFE> labelFeatureExtractors = new ArrayList<>();
	private final String TRUE = "t";
	private final String FALSE = "f";
	//private final Vocabulary labelVocab;
		
	// featuresByLabel: map from the labels to the features computed from the labels
	// labelFeatureVocab maps from the features computed from the labels to a number representing that feature
	
	public Prune(InputAnnotatedSentence[] inputSentences){
		this.inputSentences = inputSentences;

		labelVocab = new Vocabulary();
		labelVocab.num(TRUE);
		labelVocab.num(FALSE);
	}
	
	/*
	// TODO: This is incomplete
	private List<int[]> convertGraphToBinaryTops(List<int[][]> graphs, Vocabulary graphLabelVocab){
		List<int[]> tops = new ArrayList<int[]>();
		int counter = 0; 
		for (int[][] g : graphs){
			printSentenceAndGraph(inputSentences[counter],g);
			int[] ts = new int[g.length];
			for (int i = 0; i < ts.length; i++){
				ts[i] = 0;
			}
			for (int i = 0; i < g.length; i++){
				for (int j = 0 ; j < g.length; j++){
					//TODO: something here to convert from the graphs to a list of tops
				}
			}
			counter++;
			if (counter == 2)
				System.exit(1);
		}
		return tops;
	}
	*/
	
	// to find if a token is a singleton, the ith row and ith column should be entirely no-edges
	// returns: a list of int arrays. Each array correspnds to a sentence, and each element of a given array corresponds to a word. If an 
	// 		element of the array is 1, the associated token is a singleton.
	private List<int[]> convertGraphToBinarySingletons(List<int[][]> graphs, Vocabulary graphLabelVocab){
		List<int[]> singletons = new ArrayList<>();
		for (int[][] g : graphs){
			int[] singles = new int[g.length];
			// to instantiate the array of singletons
			for (int i = 0; i < singles.length; i++){
				singles[i] = labelVocab.num(TRUE);
			}
			for (int i = 0; i < g.length; i++){
				for (int j = 0; j < g.length; j++){
					if (g[i][j] != graphLabelVocab.num(LRParser.NO_EDGE)){
						singles[i] = labelVocab.num(FALSE);
						singles[j] = labelVocab.num(FALSE);
					}
				}
			}
			
			// for testing, to print the singleton array, the graph, and the sentence:
			/*
			printSentenceAndGraph(inputSentences[0], g);
			System.out.print("   ");
			for (int i = 0; i < singles.length; i++){
				System.out.print(singles[i] + "  ");
			}
			*/
			singletons.add(singles);
			//System.exit(0);
		}
		return singletons;
	}
	
	
	// Args: takes a list of graphs and a Vocabulary that maps from labels -> #s (the #s are the way labels are represented in the graph
	// To find if a token is a predicate, the ith row should be entirely no-edges.
	// returns: a list of int arrays. Each array correspnds to a sentence, and each element of a given array corresponds to a word. If an 
	// 		element of the array is 1, the associated token is a predicate.
	// Note: the predicates are those tokens that are either 1) singletons, or 2) leaf-nodes in the graph. 
	private List<int[]> convertGraphToBinaryPredicates(List<int[][]> graphs, Vocabulary graphLabelVocab){
		List<int[]> predicates = new ArrayList<>();
		for (int[][] g : graphs){
			int[] preds = new int[g.length];
			// to instantiate the array of singletons
			for (int i = 0; i < preds.length; i++){
				preds[i] = labelVocab.num(FALSE);
			}
			for (int i = 0; i < g.length; i++){
				for (int j = 0; j < g.length; j++){
					if (g[i][j] != graphLabelVocab.num(LRParser.NO_EDGE)){
						preds[i] = labelVocab.num(TRUE);
					}
				}
			}
			predicates.add(preds);
			//System.exit(0);
		}
		return predicates;
	}
	
	void initializeLabelFeatureExtractors() {
		// this is the identity feature. This allows us to simply get the label itself as it's feature
		labelFeatureExtractors.add(new PassThroughFe());
	}
	
	private Pair<Vocabulary, List<int[]>> extractAllLabelFeatures(
			Vocabulary labelVocab,
			List<FE.LabelFE> labelFeatureExtractors)
	{
		final Vocabulary labelFeatVocab = new Vocabulary();
		final List<int[]> featsByLabel = new ArrayList<>(labelVocab.size());
		for (int labelIdx = 0; labelIdx < labelVocab.size(); labelIdx++) {
			final LRParser.LabelFeatureAdder adder = new LRParser.LabelFeatureAdder(labelFeatVocab);
			for (FE.LabelFE fe : labelFeatureExtractors) {
				fe.features(labelVocab.name(labelIdx), adder);
			}
			featsByLabel.add(adder.getFeatures());
		}
		labelFeatVocab.lock();
		return Pair.makePair(labelFeatVocab, featsByLabel);
	}
	
	// to learn the weight vectors 
	public void trainModels(Vocabulary lv, List<int[][]> graphMatrices){
		initialize(graphMatrices, lv);
		
		// to learn the weights for the singletons
		singletonWeights = new HashMap<String, Double>();
		initializeWeights(singletonWeights);
		trainingOuterLoopOnline(singletonWeights, singletons);
		
		// to learn the weights for the predicates
		predicateWeights = new HashMap<String, Double>();
		initializeWeights(predicateWeights);
		trainingOuterLoopOnline(predicateWeights, predicates);
		
		System.out.println("Singleton weights:" );
		printUniqueWeights(singletonWeights);
		//System.out.println("Predicate weights:");
		//printUniqueWeights(predicateWeights);

		System.out.println("Train error for singletons:");
		trainError(singletonWeights, singletons);

		System.out.println("Train error for predicates:");
		trainError(predicateWeights, predicates);
		

	}
	
	private void trainError(Map<String, Double> weights,
			List<int[]> test) {
		int correct = 0;
		int incorrect = 0;
        for (int snum=inputSentences.length-200; snum<inputSentences.length; snum++) {
        	U.pf(".");
        	List<Map<String, Set<String>>> feats = ghettoFeats(snum);
    		int[] sequenceOfLabels = test.get(snum);
    		Viterbi v = new Viterbi(weights);
    		String[] labels = v.decode(feats);
    		for (int i = 0; i < sequenceOfLabels.length; i++){
    			if (Integer.parseInt(labels[i+1]) == sequenceOfLabels[i])
    				correct++;
    			else
    				incorrect++;
    		}
        }
        System.out.println("The number of correct labels: " + correct);
        System.out.println("The number of incorrect labels: " + incorrect);
        System.out.println("The totaly number of labels: " + (correct + incorrect));
        System.out.println("Correct / (correct + incorrect): " + correct * 1.0 / (incorrect + correct));
        System.out.println();
	}

	private void initialize(List<int[][]> graphMatrices, Vocabulary lv) {
		singletons = convertGraphToBinarySingletons(graphMatrices, lv);
		predicates = convertGraphToBinaryPredicates(graphMatrices, lv);
		tops = null; //convertGraphToBinaryTops(graphMatrices, lv);
		
	}

	private void printUniqueWeights(Map<String, Double> weights){
        for (String w : weights.keySet()){
        	if (weights.get(w) != 0.0)
        		System.out.println(w + ": " + weights.get(w));
        }
	}
	
	private void initializeWeights(Map<String, Double> weights){
		for (int i = 0; i < inputSentences.length; i++){
			List<Map<String, Set<String>>> feats = ghettoFeats(i);
			for (int j = 0; j < feats.size(); j++){
				for (String l : feats.get(j).keySet()){
					for (String w : feats.get(j).get(l)){
						weights.put(w, 0.0);						
						//weights.put(w + "_" + l, 0.0);
						
					}
				}
			}
		}
		// to initialize the transitions
		for (String prev : labelVocab.names()){
			for (String cur : labelVocab.names()){
				weights.put(labelVocab.num(prev) + "_" + labelVocab.num(cur), 0.0);
			}
		}
	}

	// the outer training loop. loops over the data numIter times.
	private void trainingOuterLoopOnline(Map<String, Double> weights, List<int[]> train) {
		for (int i = 0; i < numIter; i++){
			trainOnlineIter(weights, train);
		}
	}

	// the inner training loop. Within the dataset, loops over each example.
	private void trainOnlineIter(Map<String, Double> weights, List<int[]> train ) {
        for (int snum=0; snum<inputSentences.length - 200; snum++) {
        	U.pf(".");
        	List<Map<String, Set<String>>> feats = ghettoFeats(snum);
    		int[] sequenceOfLabels = train.get(snum);
    		ghettoPerceptronUpdate(sequenceOfLabels, feats, weights);
        }
	}
	
	private void ghettoPerceptronUpdate(int[] sequenceOfLabels, List<Map<String, Set<String>>> feats, Map<String, Double> weights ){
		runViterbi(feats, weights, sequenceOfLabels);
	}
	
	private void runViterbi(List<Map<String, Set<String>>> feats, Map<String, Double> weights, int[] gold ) {
		Viterbi v = new Viterbi(weights);
		String[] labels = v.decode(feats);

		// downweighting the predicted transition weights
		for (int i = 1; i < labels.length - 1; i++){
			weights.put(labels[i] + "_" + labels[i+1], weights.get(labels[i] + "_" + labels[i+1]) -1 );
		}
		
		// downweighting the predicted emission weights
		for (int i = 1; i < feats.size() - 1; i++){
			for (String f : feats.get(i).get(labels[i])){
				weights.put(f, weights.get(f) - 1);
			}
		}
		
		// upweighting the gold transition weightns
		for (int i = 0; i < gold.length-1; i++){
			String ith = Integer.toString(gold[i]);
			String ithPlusOne = Integer.toString(gold[i+1]);
			weights.put(ith + "_" + ithPlusOne, weights.get(ith + "_" + ithPlusOne) +1 );
		}
		
		// upweighting the gold emission weights
		for (int i = 1; i < feats.size() - 1; i++){
			String g = Integer.toString(gold[i - 1]);
			for (String f : feats.get(i).get(g)){
				weights.put(f, weights.get(f) + 1);
			}
		}
	}


	private List<Map<String, Set<String>>> ghettoFeats(int snum){
		
		List<Map<String, Set<String>>> feats = new ArrayList<Map<String, Set<String>>>();
		
		Map<String, Set<String>> start = new HashMap<String, Set<String>>();
		start.put("<START>", new HashSet<String>());
		feats.add(start);

		for (int i = 0; i < inputSentences[snum].sentence().length; i++){
			// adding the token itself as a feature
			Set<String> wordFeats = new HashSet<String>();
			wordFeats.add("token=" + inputSentences[snum].sentence()[i]);
			
			Set<String> posFeats = new HashSet<String>();
			posFeats.add("pos=" + inputSentences[snum].pos()[i]);
			
			
			Map<String, Set<String>> featsByLabel = initializeFeats();
			makeFeatsByLabel(wordFeats, featsByLabel);
			makeFeatsByLabel(posFeats, featsByLabel);
			
			feats.add(featsByLabel);
		}
		Map<String, Set<String>> stop = new HashMap<String, Set<String>>();
		stop.put("<STOP>", new HashSet<String>());
		feats.add(stop);
		
		return feats;
	}
	
	private Map<String, Set<String>> initializeFeats(){
		Map<String, Set<String>> featsByLabel = new HashMap<String, Set<String>>();
		Set<String> conjoinedTrue = new HashSet<String>();
		Set<String> conjoinedFalse = new HashSet<String>();
		featsByLabel.put(Integer.toString(labelVocab.num(TRUE)), conjoinedTrue);
		featsByLabel.put(Integer.toString(labelVocab.num(FALSE)), conjoinedFalse);
		return featsByLabel;
	}
	
	private void makeFeatsByLabel(Set<String> wordFeats, Map<String, Set<String>> featsByLabel){
		for (String s : wordFeats){
			featsByLabel.get(Integer.toString(labelVocab.num(TRUE))).add(s + "_" + labelVocab.num(TRUE));
			featsByLabel.get(Integer.toString(labelVocab.num(FALSE))).add(s + "_" + labelVocab.num(FALSE));
		}
	}
	
    private void updateExamplePerceptron(NumberizedSentence ns,
			int[] sequenceOfLabels) {
    	System.out.println();
    	System.out.println("printing NumberizedSentence! nnz is " + ns.nnz + " long. Format:");
    	System.out.println("iIndex, jIndex, perceptnum, values");
    	int counter = 0;
    	int maxinnz = 0;
    	int maxi = 0;
    	int maxjnnz = 0;
    	int maxj = 0;
    	int countNumValueIsOne = 0;
    	int same = 0;
		for (int i = 0; i < ns.nnz; i++){
			/*
			System.out.print(ns.iIndexes[i] + ", ");
			System.out.print(ns.jIndexes[i] + ", ");
			System.out.print(ns.perceptnums[i] + ", ");
			System.out.print(ns.values[i]);
			//System.out.print(labelVocab.name(ns.perceptnums[i]));
			System.out.println();
			counter ++;
			if (counter > 20) 
				break;
				*/
			if (ns.values[i] == 1.0){
				countNumValueIsOne++;
			}
			if (ns.iIndexes[i] > maxi){
				maxi = ns.iIndexes[i];
				maxinnz = i;
			}
			if (ns.jIndexes[i] > maxj){
				maxj = ns.jIndexes[i];
				maxjnnz = i;
			}
			if (ns.iIndexes[i] == ns.jIndexes[i]){
				same++;
			}
		}
		System.out.println("maxi: " + maxi);
		System.out.println("maxinnz: " + maxinnz);
		System.out.println("maxj: " + maxj);
		System.out.println("maxjnnz: " + maxjnnz);
		System.out.println("Number of timse value = 1: " + countNumValueIsOne);
		System.out.println("Nmuber of times i==j: " + same);
		
		System.exit(0);
	}


	NumberizedSentence getNextExample(int snum, PruneModel pm) {
    	//return new NumberizedSentence();
    	/*
    	if (useFeatureCache && cacheReadMode) {
    		return kryo.readObject(kryoInput, NumberizedSentence.class);
    	} else {
    	*/
    		NumberizedSentence ns = 
    				extractFeatures(pm, inputSentences[snum]);
    		return ns;
    		/*
    		if (useFeatureCache) { 
    			kryo.writeObject(kryoOutput, ns);
    		}
    		
    	}
    	*/
    }
    
	NumberizedSentence extractFeatures(PruneModel pm, InputAnnotatedSentence is) {
		//final int biasIdx = model.perceptVocab.num(BIAS_NAME);
		
		NumberizedSentence ns = new NumberizedSentence( is.size() );
		
		TokenFeatAdder tokenAdder = new TokenFeatAdder(pm.perceptVocab);
		tokenAdder.ns=ns;
		
		// only for verbose feature extraction reporting
		tokenAdder.is =is;
		
		for (FE.FeatureExtractor fe : allFE) {
			fe.setupSentence(is);
		}
		
		for (tokenAdder.i=0; tokenAdder.i<ns.T; tokenAdder.i++) {
			
			//tokenAdder.i = edgeAdder.i;
			for (FE.FeatureExtractor fe : allFE) {
				if (fe instanceof FE.TokenFE) {
					((FE.TokenFE) fe).features(tokenAdder.i, tokenAdder);
				}
			}
		}
		return ns;
	}
	
	private void printSentenceAndGraph(InputAnnotatedSentence inputSentences, int[][] g){
		for (int i = 0; i < inputSentences.sentence().length; i++){
			System.out.print(i + ":" + inputSentences.sentence()[i] + " ");
		}
		System.out.println("\n");
		System.out.print("   ");
		for (int i = 0; i < g.length; i++){
			if (i < 10)
				System.out.print(i + "  ");
			else
				System.out.print(i + " ");
		}
		System.out.println();
		for (int i = 0; i < g.length; i++){
			if (i < 10)
				System.out.print(i + "  ");
			else
				System.out.print(i + " ");
			for (int j = 0; j < g.length; j++){
				if (g[i][j] > 9){
					System.out.print(g[i][j] + " ");
				} else {
					System.out.print(g[i][j] + "  ");
				}
			}
			System.out.println();
		}
		System.out.println();
	}
}
