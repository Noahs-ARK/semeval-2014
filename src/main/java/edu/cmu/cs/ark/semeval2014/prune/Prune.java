package edu.cmu.cs.ark.semeval2014.prune;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mltools.classifier.BinaryLogreg;
import scala.Option;
import util.Arr;
import util.U;
import util.Vocabulary;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.LRParser;
import edu.cmu.cs.ark.semeval2014.lr.NumberizedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.PassThroughFe;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicFeatures;
import edu.cmu.cs.ark.semeval2014.lr.fe.CoarseDependencyFeatures;
import edu.cmu.cs.ark.semeval2014.lr.fe.DependencyPathv1;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureExtractor;
import edu.cmu.cs.ark.semeval2014.lr.fe.LinearOrderFeatures;
import edu.cmu.cs.ark.semeval2014.lr.fe.SubcatSequenceFE;
import edu.cmu.cs.ark.semeval2014.lr.fe.UnlabeledDepFE;
import edu.cmu.cs.ark.semeval2014.util.CounterMap;

public class Prune {
	private int numIter = 10;
	private List<int[]> trainingSingletonIndicators;
	private List<int[]> trainingPredicateIndicators;
	private PruneModel singletonModel;
	private PruneModel predicateModel;
	private BinaryLogreg<TokenCtx> singletonLR;
	private InputAnnotatedSentence[] inputSentences;
	private List<FE.FeatureExtractor> allFE;
	//private List<FeatureExtractor> singletonLRFeatures;
	
	// model parameters
	private Vocabulary labelVocab;
	
	private List<FE.LabelFE> labelFeatureExtractors = new ArrayList<>();
	private final String TRUE = "t";
	private final String FALSE = "f";
	private final String modelFileName;
	private final String singletonFileName = "singletonModel.ser";
	private final String predicateFileName = "predicateModel.ser";
	private final String singletonLRFileName = "singletonLR.txt";
		
	// featuresByLabel: map from the labels to the features computed from the labels
	// labelFeatureVocab maps from the features computed from the labels to a number representing that feature
	
	public Prune(InputAnnotatedSentence[] inputSentences, String modelFileName){
		this.inputSentences = inputSentences;
		this.modelFileName = modelFileName;

		labelVocab = new Vocabulary();
		labelVocab.num(FALSE);
		labelVocab.num(TRUE);
		
		initializeSingletonLR();

	}
	
	private void initializeSingletonLR(){
		List<FeatureExtractor> singletonLRFeatures = new ArrayList<FE.FeatureExtractor>();
		initializeFeatureExtractorsForLR(singletonLRFeatures);
		
		singletonLR = new BinaryLogreg<>();
		
		singletonLR.featureExtractors.add(new SingletonLRFeats(singletonLRFeatures));
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
	private List<int[]> convertGraphsToSingletonIndicators(List<int[][]> graphs, Vocabulary graphLabelVocab){
		List<int[]> singletons = new ArrayList<>();
		for (int[][] g : graphs){
			singletons.add(convertGraphToSingletonIndicators(g, graphLabelVocab));
		}
		return singletons;
	}
	
	public int[] convertGraphToSingletonIndicators(int[][] g, Vocabulary graphLabelVocab){
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
		return singles;
	}
	
	
	// Args: takes a list of graphs and a Vocabulary that maps from labels -> #s (the #s are the way labels are represented in the graph
	// To find if a token is a predicate, the ith row should be entirely no-edges.
	// returns: a list of int arrays. Each array correspnds to a sentence, and each element of a given array corresponds to a word. If an 
	// 		element of the array is 1, the associated token is a predicate.
	// Note: predicates are tokens that have a child -- they are NOT singletons and NOT leafnodes.
	private List<int[]> convertGraphsToPredicateIndicators(List<int[][]> graphs, Vocabulary graphLabelVocab){
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
		}
		return predicates;
	}
	
	void initializeLabelFeatureExtractors() {
		// this is the identity feature. This allows us to simply get the label itself as it's feature
		labelFeatureExtractors.add(new PassThroughFe());
	}
	
	// to learn the weight vectors 
	public void trainModels(Vocabulary lv, List<int[][]> graphMatrices){
		allFE = initializeFeatureExtractors();
		for (FE.FeatureExtractor fe : allFE) {
			assert (fe instanceof FE.TokenFE) || (fe instanceof FE.EdgeFE) : "all feature extractors need to implement one of the interfaces!";
			fe.initializeAtStartup();
		}
		
		U.pf("Training preproc ('Prune') models.\n");
		initialize(graphMatrices, lv);
		
		// to learn the weights for the singletons
		singletonModel = new PruneModel();
		initializeWeights(singletonModel);
		trainingOuterLoopOnline(singletonModel, trainingSingletonIndicators);
		singletonModel.save(modelFileName + "." + singletonFileName);
		
		//trainError(sModel.weights, singletons);
		
		// to learn the weights for the predicates
		predicateModel = new PruneModel();
		initializeWeights(predicateModel);
		trainingOuterLoopOnline(predicateModel, trainingPredicateIndicators);
		predicateModel.save(modelFileName + "." + predicateFileName);
		
		//trainError(pModel.weights, predicates);
//		dumpDecisions(10);
		
		addSingletonLRTrainingData();
		singletonLR.doTraining(modelFileName + "." + singletonLRFileName);
		
	}
	
	
	public void dumpDecisions(int snum) {
		InputAnnotatedSentence sent = inputSentences[snum];
		U.pf("\nSENTENCE %s\n", sent.sentenceId);
		for (int t=0; t<inputSentences[snum].size(); t++) {
			U.pf("issg(g,p,pr) = %d,%d,%.3f  ispred(g,p) = %d,%d  ||| %s\n", 
					trainingSingletonIndicators.get(snum)[t], sent.singletonPredictions[t],
					sent.singletonPredProbs[t],
					trainingPredicateIndicators.get(snum)[t], sent.predicatePredictions[t], 
					sent.sentence[t]);
		}
	}

	private void trainError(Map<String, Double> weights,
			List<int[]> test) {
		int correct = 0;
		int incorrect = 0;
        for (int snum=0; snum<inputSentences.length; snum++) {
        	if (snum % 100==0) U.pf(".");
        	List<Map<String, Set<String>>> feats = computeFeats(snum);
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
		trainingSingletonIndicators = convertGraphsToSingletonIndicators(graphMatrices, lv);
		trainingPredicateIndicators = convertGraphsToPredicateIndicators(graphMatrices, lv);
	}

	private void printUniqueWeights(Map<String, Double> weights){
        for (String w : weights.keySet()){
        	if (weights.get(w) != 0.0)
        		System.out.println(w + ": " + weights.get(w));
        }
	}
	
	private void initializeWeights(PruneModel model){
		for (int i = 0; i < inputSentences.length; i++){
			List<Map<String, Set<String>>> feats = computeFeats(i);
			for (int j = 0; j < feats.size(); j++){
				for (String l : feats.get(j).keySet()){
					for (String w : feats.get(j).get(l)){
						model.weights.put(w, 0.0);						
						//weights.put(w + "_" + l, 0.0);
						
					}
				}
			}
		}
		// to initialize the transitions
		for (String prev : labelVocab.names()){
			for (String cur : labelVocab.names()){
				model.weights.put(labelVocab.num(prev) + "_" + labelVocab.num(cur), 0.0);
			}
		}
	}

	// the outer training loop. loops over the data numIter times.
	private void trainingOuterLoopOnline(PruneModel singletonModel, List<int[]> train) {
		for (int i = 0; i < numIter; i++){
			trainOnlineIter(singletonModel, train);
		}
	}

	// the inner training loop. Within the dataset, loops over each example.
	private void trainOnlineIter(PruneModel model, List<int[]> train ) {
        for (int snum=0; snum<inputSentences.length; snum++) {
        	if (snum % 100==0) U.pf(".");
        	List<Map<String, Set<String>>> feats = computeFeats(snum);
    		int[] sequenceOfLabels = train.get(snum);
    		ghettoPerceptronUpdate(sequenceOfLabels, feats, model);
        }
	}
	
	private void ghettoPerceptronUpdate(int[] sequenceOfLabels, List<Map<String, Set<String>>> feats, PruneModel singletonModel ){
		runViterbi(feats, singletonModel, sequenceOfLabels);
	}
	
	private void runViterbi(List<Map<String, Set<String>>> feats, PruneModel model, int[] gold ) {
		Viterbi v = new Viterbi(model.weights);
		String[] labels = v.decode(feats);

		// downweighting the predicted transition weights
		for (int i = 1; i < labels.length - 1; i++){
			model.weights.put(labels[i] + "_" + labels[i+1], model.weights.get(labels[i] + "_" + labels[i+1]) -1 );
		}
		
		// downweighting the predicted emission weights
		for (int i = 1; i < feats.size() - 1; i++){
			for (String f : feats.get(i).get(labels[i])){
				model.weights.put(f, model.weights.get(f) - 1);
			}
		}
		
		// upweighting the gold transition weightns
		for (int i = 0; i < gold.length-1; i++){
			String ith = Integer.toString(gold[i]);
			String ithPlusOne = Integer.toString(gold[i+1]);
			model.weights.put(ith + "_" + ithPlusOne, model.weights.get(ith + "_" + ithPlusOne) +1 );
		}
		
		// upweighting the gold emission weights
		for (int i = 1; i < feats.size() - 1; i++){
			String g = Integer.toString(gold[i - 1]);
			for (String f : feats.get(i).get(g)){
				model.weights.put(f, model.weights.get(f) + 1);
			}
		}
	}
	
	class TokenFeatAdder extends FE.FeatureAdder {
		public List<Map<String, Set<String>>> feats = new ArrayList<Map<String, Set<String>>>();
		private Map<String, Set<String>> singleTokenFeats = initializeFeats();

		@Override
		public void add(String featName, double value) {
			HashSet<String> featSet = new HashSet<String>();
			featSet.add(featName);
			makeFeatsByLabel(featSet, singleTokenFeats);
		}

		public void completeToken(){
			feats.add(singleTokenFeats);
			singleTokenFeats = initializeFeats();
		}
	}
	
	private List<Map<String, Set<String>>> computeFeats(int snum){
		
		TokenFeatAdder featureAdder = new TokenFeatAdder();
		
		for (FE.FeatureExtractor fe : allFE) {
			fe.setupSentence(inputSentences[snum]);
		}
		
		Map<String, Set<String>> start = new HashMap<String, Set<String>>();
		start.put("<START>", new HashSet<String>());
		featureAdder.feats.add(start);
		
		for (int i = 0; i < inputSentences[snum].size(); i++){
			for (FE.FeatureExtractor fe : allFE){
				assert (fe instanceof FE.TokenFE);
				((FE.TokenFE) fe).features(i, featureAdder);
			}
			featureAdder.completeToken();
		}

		Map<String, Set<String>> stop = new HashMap<String, Set<String>>();
		stop.put("<STOP>", new HashSet<String>());
		featureAdder.feats.add(stop);
				
		return featureAdder.feats;
	}
	
	private List<FE.FeatureExtractor> initializeFeatureExtractors() {
		final List<FE.FeatureExtractor> allFE = new ArrayList<>();
		allFE.add(new BasicFeatures());
		//allFE.add(new LinearOrderFeatures());
		//allFE.add(new CoarseDependencyFeatures());
		//allFE.add(new DependencyPathv1());
		//allFE.add(new SubcatSequenceFE());
		//allFE.add(new UnlabeledDepFE());
		return allFE;
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
	
	private void printSentenceAndGraph(InputAnnotatedSentence inputSentences, int[][] g){
		for (int i = 0; i < inputSentences.sentence.length; i++){
			System.out.print(i + ":" + inputSentences.sentence[i] + " ");
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

	public void loadModels() {
		singletonModel = new PruneModel();
		singletonModel.load(modelFileName + "." + singletonFileName);
		
		predicateModel = new PruneModel();
		predicateModel.load(modelFileName + "." + predicateFileName);
		
		try {
			singletonLR.loadModel(modelFileName + "." + singletonLRFileName);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/** Do predictions and save them in the input sentence objects. 
	 * @param graphMatrices 
	 * @param labelVocab2 */
	public void predictIntoInputs(List<int[][]> graphMatrices, Vocabulary graphLabelVocab){
		predictForInputSentences(singletonModel, predicateModel);
		computePrecisionAndRecall(graphMatrices, graphLabelVocab);
	}
	
	// @param takes no params
	// simply predicts, does not compute precision and recall. 
	public void predictIntoInputs(){
		predictForInputSentences(singletonModel, predicateModel);
		
	}

	private void computePrecisionAndRecall(List<int[][]> graphMatrices, Vocabulary graphLabelVocab) {
		// generate the gold singletons and predicates
		List<int[]> goldPreds = convertGraphsToPredicateIndicators(graphMatrices, graphLabelVocab);
		List<int[]> goldSingles = convertGraphsToSingletonIndicators(graphMatrices, graphLabelVocab);

		/* keys:
		goldTrue
		goldFalse
		truePos
		falseNeg
		trueNeg
		falsePos
		*/
		CounterMap<String> predCounts = new CounterMap<String>();
		CounterMap<String> singleCounts = new CounterMap<String>();
		CounterMap<String> singleLRCounts = new CounterMap<String>();		

		System.out.println();
		System.out.println("Length of graphMatrices: " + graphMatrices.size());
		System.out.println("Length of goldPreds: " + goldPreds.size());
		System.out.println("Length of inputSentences: " + inputSentences.length);
				
		for (int i = 0; i < inputSentences.length; i++){
			computePAndR(goldSingles.get(i), inputSentences[i].singletonPredictions, singleCounts);
			computePAndR(goldPreds.get(i), inputSentences[i].predicatePredictions, predCounts);
			int[] singletonLROutput = computeSingletonProbs(inputSentences[i].singletonPredProbs);
			computePAndR(goldSingles.get(i), singletonLROutput, singleLRCounts);
		}
		System.out.println();
		System.out.println("Computed precision and recall for predicates and singletons!");
		printPAndR(predCounts, "Predicates");
		printPAndR(singleCounts, "Singletons");
		printPAndR(singleLRCounts, "Logistic Regression Singletons");
		System.out.println();
	}
	
	private int[] computeSingletonProbs(double[] probs){
		int[] thresholded = new int[probs.length];
		for (int i = 0; i < probs.length; i++){
			if (probs[i] > LRParser.singletonPruneThresh)
				thresholded[i] = 1;
			else
				thresholded[i] = 0;
		}
		return thresholded;
	}
	
	
	
	// precision:  tp / (tp + fp) 100
	// recall: tp / (tp + fn)
	private void printPAndR(CounterMap<String> predCounts, String predName){
		double roundAmount = 1000.0;
		double precision = Math.round(roundAmount *predCounts.value("truePos") / (predCounts.value("truePos") + predCounts.value("falsePos")) / roundAmount);
		double recall = Math.round(roundAmount * predCounts.value("truePos") / (predCounts.value("truePos") + predCounts.value("falseNeg")))/ roundAmount;
		System.out.println("The " + predName + " precision and recall:");
		System.out.println("Total false: " + predCounts.value("goldPos"));
		System.out.println("Total true: " + predCounts.value("goldNeg"));
		System.out.println("Precision: " + predCounts.value("truePos") + "/(" 
				+ predCounts.value("truePos") + "+" + predCounts.value("falsePos") + ") = " + precision);
		System.out.println("Recall: " + predCounts.value("truePos") + "/(" 
				+ predCounts.value("truePos") + "+" + predCounts.value("falseNeg") + ") = " + recall);
		// F1 = 2 * precision*recall / (precision + recall)
		System.out.println("F1: " + Math.round(roundAmount * ((2 * precision * recall) / (precision + recall))) / roundAmount);
		System.out.println("Accuracy: " + Math.round(roundAmount * ((predCounts.value("truePos") + predCounts.value("trueNeg")) / 
				(predCounts.value("goldPos") + predCounts.value("goldNeg")))) / roundAmount);
		System.out.println("Baseline: " + Math.round(roundAmount * (Math.max(predCounts.value("goldPos"), predCounts.value("goldNeg")) 
				/ (predCounts.value("goldPos") + predCounts.value("goldNeg")))) / roundAmount);
		System.out.println();
	}
	
	/* keys for counters:
	goldTrue
	goldFalse
	truePos
	falseNeg
	trueNeg
	falsePos
	*/
	private void computePAndR(int[] gold, int[] predictions, CounterMap<String> counters){
		for (int i = 0; i < gold.length; i++){
			if (gold[i] == 1) 
				counters.increment("goldPos");
			else 
				counters.increment("goldNeg");
			if (gold[i] == 1 && predictions[i] == 1)
				counters.increment("truePos");
			else if (gold[i] == 1 && predictions[i] == 0)
				counters.increment("falseNeg");
			else if (gold[i] == 0 && predictions[i] == 0)
				counters.increment("trueNeg");
			else if (gold[i] == 0 && predictions[i] == 1)
				counters.increment("falsePos");
		}
	}

	private void predictForInputSentences(PruneModel singletonModel, PruneModel predicateModel) {
		for (int i = 0; i < inputSentences.length; i++){
			int[] singles = predict(singletonModel, i);
			int[] preds = predict(predicateModel, i);
			inputSentences[i].singletonPredictions = Arr.copy(singles);
			inputSentences[i].predicatePredictions = Arr.copy(preds);

			inputSentences[i].singletonPredProbs = predictSingletonProbs(inputSentences[i]);
		}
	}
	double[] predictSingletonProbs(InputAnnotatedSentence sent) {
		double[] sgProbs = new double[sent.size()];
		for (int t=0; t<sent.size(); t++) {
			sgProbs[t] = singletonLR.predictLabelProb(new TokenCtx(t,sent));
		}
		//precisionRecall();
		return sgProbs;
	}

	/** return predicted labels, as integers
	 * todo eventually: clean up messiness with labels vs integers and all that.  why not just use the raw integer numberings? */
	private int[] predict(PruneModel model, int snum) {
        	List<Map<String, Set<String>>> feats = computeFeats(snum);
        	// run viterbi
    		Viterbi v = new Viterbi(model.weights);
    		String[] labelsAsStrings = v.decode(feats);
    		int[] predLabels = new int[labelsAsStrings.length-1];
    		for (int i = 1; i < labelsAsStrings.length; i++){
    			predLabels[i-1] = Integer.parseInt(labelsAsStrings[i]);
    		}
    		return predLabels;
	}
	


	/////////////////////////////////
	// stuff for binarylogreg system
	
	static class TokenCtx {
		int t = -1;
		InputAnnotatedSentence sent;
		TokenCtx(int _t, InputAnnotatedSentence _sent) {
			t=_t; sent=_sent;
		}
	}
	
	static class SingletonLRFeatAdder extends FE.FeatureAdder {
		mltools.classifier.FeatureExtractor.FeatureAdder fa;
		
		public SingletonLRFeatAdder(mltools.classifier.FeatureExtractor.FeatureAdder _fa){
			fa = _fa;
		}
		
		@Override
		public void add(String featName, double value) {
			fa.add(featName, value);
		}
	}

	static class SingletonLRFeats extends mltools.classifier.FeatureExtractor<TokenCtx> {
		// set of feature extractors
		List<FeatureExtractor> singletonLRFeatures;
		// feature adder
		
		
		public SingletonLRFeats(List<FeatureExtractor> _featureExtractors){
			singletonLRFeatures = _featureExtractors;
		}
		
		@Override
		public void computeFeatures(TokenCtx ex, mltools.classifier.FeatureExtractor.FeatureAdder fa) {
			for (FE.FeatureExtractor fe : singletonLRFeatures) {
				fe.setupSentence(ex.sent);
			}
			
			// instantiate a new feature adder
			SingletonLRFeatAdder featAdder = new SingletonLRFeatAdder(fa);
			
			
			// for each feature extractor
			
			// pass feature adder to the feature extractor (which adds the feature to the feature adder)
			
			for (FE.FeatureExtractor fe : singletonLRFeatures){
				assert (fe instanceof FE.TokenFE);
				((FE.TokenFE) fe).features(ex.t, featAdder);
			}
			
			/*
			final int tokenIdx = ex.t;
			String pos = ex.sent.pos[tokenIdx];
			fa.add("pos=" + pos);
//			fa.add("pos=" + pos + "&lcword=" + ex.sent.sentence()[ex.t].toLowerCase(), 0.2);
			fa.add("t=" + tokenIdx);
			fa.add("t=-" + (ex.sent.size()-tokenIdx-1));
			fa.add("pos=" + pos + "&t=" + tokenIdx);
			// TODO dep relation coming out of it
			final Option<Object> oDepth = ex.sent.syntacticDependencies.depths().apply(tokenIdx);
			fa.add("depth=" + (oDepth.isDefined() ? oDepth.get() : "NULL"));
			*/
		}
	}
	
	void addSingletonLRTrainingData() {
		for (int snum=0; snum<trainingSingletonIndicators.size(); snum++) {
			int[] sgInd = trainingSingletonIndicators.get(snum);
			InputAnnotatedSentence sent = inputSentences[snum];
			
			for (int t=0; t<sgInd.length; t++) {
				singletonLR.addTrainingExample(sgInd[t]==1, new TokenCtx(t,sent));
			}
		}
	}
	
	private void initializeFeatureExtractorsForLR(List<FeatureExtractor> featureExtractors) {
		featureExtractors.add(new BasicFeatures());
		//allFE.add(new LinearOrderFeatures());
		//allFE.add(new CoarseDependencyFeatures());
		//allFE.add(new DependencyPathv1());
		//allFE.add(new SubcatSequenceFE());
		//allFE.add(new UnlabeledDepFE());
	}

}
