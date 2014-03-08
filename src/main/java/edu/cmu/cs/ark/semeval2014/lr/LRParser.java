package edu.cmu.cs.ark.semeval2014.lr;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Lists;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.cmu.cs.ark.semeval2014.ParallelParser;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.*;
import edu.cmu.cs.ark.semeval2014.prune.Prune;
import edu.cmu.cs.ark.semeval2014.prune.PruneFeatsForSemparser;
import edu.cmu.cs.ark.semeval2014.topness.DetermTopness;
import edu.cmu.cs.ark.semeval2014.topness.TopClassifier;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;
import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.io.GraphReader;
import util.U;
import util.Vocabulary;
import util.misc.Pair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures.*;

/*
 * Dimensionality strategies.
 * 
 * Without feature hashing:
 *    percepts  ==>  signed int, \in 0..(#percepttypes - 1)
 *    labels ==> signed int, \in 0..(#labeltypes - 1)
 *    coefs ==> dimensino labeltypes*#percepttypes
 *  
 *  With feature hashing:
 *  a coef index is a modulo'd hash of both the percepthash and the labelID.
 *    percepts  ==>  a hash, that is any signed int.  we never store this in the model file.
 *    labels ==> signed int \in 0..(#labeltypes - 1)
 *    coefs ==> dimension #hashbuckets.
 *  the perceptVocab object still exists, but is not used.
 */


public class LRParser {
	public static final String NO_EDGE = "NOEDGE";
	private static final String BIAS_NAME = "***BIAS***";

	// 1. Data structures
	static InputAnnotatedSentence[] inputSentences = null; // full dataset
	static List<int[][]> graphMatrices = null;  // full dataset

	static List<FE.LabelFE> labelFeatureExtractors = new ArrayList<>();

	static Model model;
	static float[] ssGrad;  // adagrad history info. parallel to coefs[].

	static TopClassifier topClassifier = new TopClassifier();
    static Prune preprocessor;
    static HashMap<String, double[]> wordToVector;

	@Parameter(names="-learningRate")
	static double learningRate = .1;

	// 3. Model parameter-ish options
	static int maxEdgeDistance = 10;
	@Parameter(names="-l2reg")
	static double l2reg = 0.5;
	@Parameter(names="-noedgeWeight", description="defaults to formalism-specific value")
	static double noedgeWeight = -1;
	@Parameter(names="-formalism", required=true)
	static String formalism;

	// 4. Runtime options
	@Parameter(names="-verboseFeatures")
	static boolean verboseFeatures = false;
	@Parameter(names="-useFeatureCache", arity=1)
    static boolean useFeatureCache = true;
    @Parameter(names="-saveEvery")
    static int saveEvery = 10;  // -1 to disable intermediate model saves
    @Parameter(names="-numIters")
	static int numIters = 30;
    
    @Parameter(names="-useHashing", description="only specify this when training. at testtime, whether it's a hash-based model is detected from the model file.")
    static boolean useHashing = false;
    @Parameter(names="-numHashBuckets", description="only specify this when training. at testtime, this is read from the model file.  ---  Note mem usage is 4 times higher than this, so maybe use 1e9 on a server?")
    static double numHashBuckets = 100e6;

	// label feature flags
	@Parameter(names = "-useDmLabelFeatures")
	static boolean useDmLabelFeatures = false;
	@Parameter(names = "-usePasLabelFeatures")
	static boolean usePasLabelFeatures = false;
	@Parameter(names = "-usePcedtLabelFeatures")
	static boolean usePcedtLabelFeatures = false;

	@Parameter(names="-mode", required=true)
	static String mode;
    @Parameter(names="-model",required=true)
	static String modelFile;
    @Parameter(names={"-sdpInput","-sdpOutput"}, required=true)
    static String sdpFile;
    @Parameter(names="-depInput", required=true)
	static String depFile;
    @Parameter(names="-wordVectors", required=true)
	static String wordVecFile;
    
    
    static long numPairs = 0, numTokens = 0; // purely for diagnosis
    
    static void validateParameters() {
    	assert numHashBuckets > 0 : "must have positive number of hashbuckets";
    	assert numHashBuckets < Integer.MAX_VALUE : "numhashbuckets must be a signed 4byte integer, so less than 2 billion or so";
		assert mode.equals("train") || mode.equals("test") : "Need to say either train or test mode.";
		assert formalism.equals("pas") || formalism.equals("dm") || formalism.equals("pcedt");
    }
    
    public static void main(String[] args) throws IOException {
		new JCommander(new LRParser(), args);  // seems to write to the static members.
		validateParameters();
		setDefaultNoedgeWeights();

		// Data loading
		inputSentences = Corpus.getInputAnnotatedSentences(depFile);
		U.pf("%d input sentences\n", inputSentences.length);
		
		wordToVector = WordVectors.loadWordVectors(wordVecFile);
		preprocessor = new Prune(inputSentences, modelFile);

		if (mode.equals("train")) {
			topClassifier.train(depFile, modelFile + ".topmodel");
			trainModel();
		}
		else if (mode.equals("test")) {
			topClassifier.loadModel(modelFile + ".topmodel");
			model = Model.load(modelFile);
			preprocessInputSentences();
			U.pf("Writing predictions to %s\n", sdpFile);
			double t0, dur;
			t0 = System.currentTimeMillis();
			ParallelParser.makePredictions(model, inputSentences, sdpFile);
			dur = System.currentTimeMillis() - t0;
			U.pf("\nPRED TIME %.1f sec, %.1f ms/sent\n", dur/1e3, dur/inputSentences.length);
		}
	}

	// this loads in the learned weights for the preprocessing models
	// then predicts the 'predicates' and 'singelton' classes within the
	// inputSentences that are already stored in p.
	private static void preprocessInputSentences(){
		preprocessor.loadModels();
		preprocessor.predictIntoInputs();
	}

	static void setDefaultNoedgeWeights() {
		if (noedgeWeight == -1) {
			noedgeWeight = 
					formalism.equals("pas") ? 0.4 :
					formalism.equals("dm") ? 0.3 :
					formalism.equals("pcedt") ? 0.3 :
					-1;
				assert noedgeWeight != -1;
		}
		U.pf("Set noedgeWeight = %s\n", noedgeWeight);
	}

	private static Model trainModel() throws IOException {
		double t0;
		double dur;
		U.pf("Reading graphs from %s\n", sdpFile);
		final List<Graph> graphs = readGraphs(sdpFile);

		// build up the edge label vocabulary
		Vocabulary labelVocab = new Vocabulary();
		labelVocab.num(NO_EDGE);
		for (Graph graph : graphs) {
			for (Edge e : graph.getEdges()) {
				labelVocab.num(e.label);
			}
		}
		labelVocab.lock();

		// build up label feature vocab
		initializeLabelFeatureExtractors();
		final Pair<Vocabulary, List<int[]>> vocabAndFeatsByLabel =
				extractAllLabelFeatures(labelVocab, labelFeatureExtractors);
		final Vocabulary labelFeatureVocab = vocabAndFeatsByLabel.first;
		final List<int[]> featuresByLabel = vocabAndFeatsByLabel.second;

		assert graphs.size() == inputSentences.length;

		// convert graphs to adjacency matrices
		graphMatrices = new ArrayList<>();
		for (int snum=0; snum<graphs.size(); snum++) {
			final InputAnnotatedSentence sent = inputSentences[snum];
			final Graph graph = graphs.get(snum);
//			assert sent.sentenceId.equals(graph.id.replace("#",""));
			graphMatrices.add(convertGraphToAdjacencyMatrix(graph, sent.size(), labelVocab));
		}

		// Preprocessor training & prediction ... its predictions will be used as semparser features.
		// Note that its predictions are stored in the inputSentences.
		preprocessor.trainModels(labelVocab, graphMatrices);
		preprocessor.predictIntoInputs();
//		preprocessor.dumpDecisions(10);

		// Train the edge-based semparser.
		final Vocabulary perceptVocab = new Vocabulary();
		perceptVocab.num(BIAS_NAME);
		model = new Model(labelVocab, labelFeatureVocab, featuresByLabel, perceptVocab);

		t0 = System.currentTimeMillis();
		trainingOuterLoopOnline();
		dur = System.currentTimeMillis() - t0;
		U.pf("TRAINLOOP TIME %.1f sec\n", dur/1e3);

		model.save(modelFile);
		if (useFeatureCache)
			Files.delete(Paths.get(featureCacheFile));
		return model;
	}

	private static int[][] convertGraphToAdjacencyMatrix(Graph graph, int n, Vocabulary labelVocab) {
		int[][] edgeMatrix = new int[n][n];
		for (int[] row : edgeMatrix) {
			Arrays.fill(row, labelVocab.num(NO_EDGE));
		}
		for (Edge e : graph.getEdges()) {
			edgeMatrix[e.source-1][e.target-1] = labelVocab.num(e.label);
		}
		return edgeMatrix;
	}

	private static Pair<Vocabulary, List<int[]>> extractAllLabelFeatures(
			Vocabulary labelVocab,
			List<FE.LabelFE> labelFeatureExtractors)
	{
		final Vocabulary labelFeatVocab = new Vocabulary();
		final List<int[]> featsByLabel = new ArrayList<>(labelVocab.size());
		for (int labelIdx = 0; labelIdx < labelVocab.size(); labelIdx++) {
			final InMemoryNumberizedFeatureAdder adder = new InMemoryNumberizedFeatureAdder(labelFeatVocab);
			for (FE.LabelFE fe : labelFeatureExtractors) {
				fe.features(labelVocab.name(labelIdx), adder);
			}
			featsByLabel.add(adder.features());
		}
		labelFeatVocab.lock();
		return Pair.makePair(labelFeatVocab, featsByLabel);
	}

	private static List<Graph> readGraphs(String sdpFile) throws IOException {
		final ArrayList<Graph> graphs = new ArrayList<>();
		try (GraphReader reader = new GraphReader(sdpFile)) {
			Graph graph;
			while ((graph = reader.readGraph()) != null) {
				graphs.add(graph);
			}
		}
		return graphs;
	}

	public static boolean badDistance(int i, int j) {
		return i==j || Math.abs(i-j) > maxEdgeDistance;
	}

	static long totalPairs = 0;  // only for diagnosis

	static class TokenFeatAdder extends FE.FeatureAdder {
		int i=-1;
		NumberizedSentence ns;
		InputAnnotatedSentence is; // only for debugging

		@Override
		public void add(String featname, double value) {
			if (verboseFeatures) {
				U.pf("NODEFEAT\t%s:%d\t%s\n", is.sentence[i], i, featname);
			}

			// this is kinda a hack, put it in both directions for every edge.
			// we could use smarter data structures rather than the full matrix
			// of edge featvecs to represent this more compactly.

			String ff;
			int featnum;

			ff = U.sf("%s::ashead", featname);
			featnum = perceptNum(ff);
			if (LRParser.useHashing || featnum!=-1) {
				for (int j=0; j<ns.T; j++) {
					if (badDistance(i,j)) continue;
					ns.add(i,j, featnum, value);
				}
			}

			ff = U.sf("%s::aschild", featname);
			featnum = perceptNum(ff);
			if (LRParser.useHashing || featnum!=-1) {
				for (int j=0; j<ns.T; j++) {
					if (badDistance(j,i)) continue;
					ns.add(j,i, featnum, value);
				}
			}
		}
	}

	/** under hashing, this could be a negative number. */
	static int perceptNum(String perceptName) {
		if ( ! LRParser.useHashing) {
			return model.perceptVocab.num(perceptName);
		}
		else {
//			return perceptName.hashCode();
			byte[] b = perceptName.getBytes();
			return MurmurHash.hash32(b, b.length);
		}
	}

	static class EdgeFeatAdder extends FE.FeatureAdder {
		int i=-1, j=-1;
		NumberizedSentence ns;
		// these are only for debugging
		InputAnnotatedSentence is;
		int[][] goldEdgeMatrix;

		@Override
		public void add(String featname, double value) {
			int perceptnum = perceptNum(featname);
			if (perceptnum==-1) return;

			ns.add(i,j, perceptnum, value);

			if (verboseFeatures) {
				U.pf("WORDS %s:%d -> %s:%d\tGOLD %s\tEDGEFEAT %s %s\n", is.sentence[i], i, is.sentence[j], j,
						goldEdgeMatrix!=null ? model.labelVocab.name(goldEdgeMatrix[i][j]) : null, featname, value);
			}

		}
	}

	/**
	 * goldEdgeMatrix is only for feature extractor debugging verbose reports 
	 */
	public static NumberizedSentence extractFeatures(Model model, InputAnnotatedSentence is, int[][] goldEdgeMatrix) {
		final int biasIdx = model.perceptVocab.num(BIAS_NAME);

		NumberizedSentence ns = new NumberizedSentence( is.size() );
		TokenFeatAdder tokenAdder = new TokenFeatAdder();
		EdgeFeatAdder edgeAdder = new EdgeFeatAdder();
		tokenAdder.ns=edgeAdder.ns=ns;

		// only for verbose feature extraction reporting
		tokenAdder.is = edgeAdder.is=is;
		edgeAdder.goldEdgeMatrix = goldEdgeMatrix;

		final List<FE.FeatureExtractor> featureExtractors = initializeFeatureExtractors();
		for (FE.FeatureExtractor fe : featureExtractors) {
			assert (fe instanceof FE.TokenFE) || (fe instanceof FE.EdgeFE) : "all feature extractors need to implement one of the interfaces!";
			fe.initializeAtStartup();
			fe.setupSentence(is);
		}

		for (edgeAdder.i=0; edgeAdder.i<ns.T; edgeAdder.i++) {

			tokenAdder.i = edgeAdder.i;
			for (FE.FeatureExtractor fe : featureExtractors) {
				if (fe instanceof FE.TokenFE) {
					((FE.TokenFE) fe).features(tokenAdder.i, tokenAdder);
				}
			}
			for (edgeAdder.j=0; edgeAdder.j<ns.T; edgeAdder.j++) {
				if (badDistance(edgeAdder.i,edgeAdder.j)) continue;
				numPairs++;

				// bias term
				ns.add(edgeAdder.i, edgeAdder.j, biasIdx, 1.0);

				// edge features
				for (FE.FeatureExtractor fe : featureExtractors) {
					if (fe instanceof FE.EdgeFE) {
						((FE.EdgeFE) fe).features(edgeAdder.i, edgeAdder.j, edgeAdder);
					}
				}
			}
		}
		return ns;
	}

	static NumberizedSentence extractFeatures(Model model, int snum) {
		return extractFeatures(model, inputSentences[snum], graphMatrices !=null ? graphMatrices.get(snum) : null);
	}

    static void trainingOuterLoopOnline() throws IOException {
    	
    	U.pf("First pass: extracting features, no model updates.\n");
		cacheReadMode = false;
		openCacheForWriting();
    	featureExtractionPass();
		closeCacheAfterWriting();
    	allocateCoefs();
        U.pf("\n");
        U.pf("%d sentences, %d tokens, %.2f tokens/sent, %d pairs (candidate edges), %.2f pairs/sent\n", inputSentences.length, numTokens, numTokens*1.0/inputSentences.length, numPairs, numPairs*1.0/inputSentences.length);
		U.pf("%d percepts, %d nnz\n", model.perceptVocab.size(), NumberizedSentence.totalNNZ);
		cacheReadMode = true;

    	for (int outer=0; outer<numIters; outer++) {
    		U.pf("iter %3d ", outer);  System.out.flush();
    		double t0 = System.currentTimeMillis();
    		
			resetCacheReader();
    		trainOnlineIter();
    		
        	double dur = System.currentTimeMillis() - t0;
        	U.pf("%.1f sec, %.1f ms/sent\n", dur/1000, dur/inputSentences.length);
    		
        	if (saveEvery >= 0 && outer % saveEvery == 0)
        		model.save(U.sf("%s.iter%s", modelFile, outer));
    		
    	}
    }

    static void allocateCoefs() {
    	int len = -1;
    	if (useHashing) {
    		len = (int) numHashBuckets;
//    		U.p("should be blank, percept vocab = " + model.perceptVocab.toString());
    	}
    	else {
        	len = model.perceptVocab.size() * model.labelFeatureVocab.size();
    	}
    	model.coefs = new float[len];
    	ssGrad = new float[len];
    	model.perceptVocab.lock();
    	model.labelFeatureVocab.lock();
		model.calculateLabelHashes();
    }

    /** From the new gradient value, update this feature's learning rate and return it. */
    static double adagradStoreRate(int featnum, double g) {
        ssGrad[featnum] += g*g;
        if (ssGrad[featnum] < 1e-2) return 10.0; // 1/sqrt(.01)
        return 1.0 / Math.sqrt(ssGrad[featnum]);
    }
    
    static void featureExtractionPass() {
        for (int snum=0; snum<inputSentences.length; snum++) {
        	U.pf(".");
        	extractFeaturesForExampleAndWriteToCache(snum);
            if (snum>0 && snum % 1000 == 0) {
            	U.pf("%d sents, %.3fm percepts, %.1f MB mem used\n", 
            			snum+1, model.perceptVocab.size()/1e6,
            			Runtime.getRuntime().totalMemory()/1e6
            			);
            }
            numTokens += inputSentences[snum].size();
        }
    }
    
    /** adagrad: http://www.ark.cs.cmu.edu/cdyer/adagrad.pdf */ 
    static void trainOnlineIter() throws FileNotFoundException {
		assert model.labelVocab.isLocked() : "since we have autolabelconj, can't tolerate label vocab expanding during a training pass.";
		assert model.labelFeatureVocab.isLocked() : "since we have autolabelconj, can't tolerate label vocab expanding during a training pass.";

		double ll = 0;
        for (int snum=0; snum<inputSentences.length; snum++) {
        	U.pf(".");
            
            NumberizedSentence ns = getNextExample(snum);
    		int[][] edgeMatrix = graphMatrices.get(snum);
            ll += updateExampleLogReg(ns, edgeMatrix);
        }
        //  logprior  =  - (1/2) lambda || beta ||^2
        //  gradient =  - lambda beta
        for (int f=0; f< model.coefs.length; f++) {
            ll -= 0.5 * l2reg * model.coefs[f]*model.coefs[f];
            double g = l2reg * model.coefs[f];
			model.coefs[f] -= adagradStoreRate(f,g) * learningRate * g;
        }
        U.pf("ll %.1f  ", ll);
    }

	static double updateExampleLogReg(NumberizedSentence sentence, int[][] edgeMatrix) {
		final int noEdgeIdx = model.labelVocab.num(NO_EDGE);
		double ll = 0;

		double[][][] probs = model.inferEdgeProbs(sentence);

		for (int kk = 0; kk < sentence.nnz; kk++) {
		    int i = sentence.i(kk);
			int j = sentence.j(kk);
			int perceptNum = sentence.perceptnum(kk);
			final int goldLabelIdx = edgeMatrix[i][j];
			// manually downweight the NO_EDGE label
			final double w = goldLabelIdx == noEdgeIdx ? noedgeWeight : 1.0;

		    for (int label = 0; label < model.labelVocab.size(); label++) {
				int isObserved = goldLabelIdx == label ? 1 : 0;
				double resid = isObserved - probs[i][j][label];
				double g = w * resid * sentence.value(kk);
				for (int labelFeatureIdx : model.featuresByLabel.get(label)) {
					int ffnum = model.coefIdx(perceptNum, labelFeatureIdx);
					double rate = adagradStoreRate(ffnum, g);
					model.coefs[ffnum] += learningRate * rate * g;
				}
		    }
		}

		// loglik is completely unnecessary for optimization, just nice for diagnosis.
		for (int i=0;i<sentence.T;i++) {
		    for (int j=0; j<sentence.T;j++) {
		        if (badDistance(i,j)) continue;
		        double w = edgeMatrix[i][j]==0 ? noedgeWeight : 1.0;
		        ll += w * Math.log(probs[i][j][edgeMatrix[i][j]]);
		    }
		}
		return ll;
	}

	public static MyGraph decodeToGraph(InputAnnotatedSentence sent, NumberizedSentence ns) {
	    MyGraph g = MyGraph.decodeEdgeProbsToGraph(
	    		sent, model.inferEdgeProbs(ns), model.labelVocab, true);
	    MyGraph.decideTops(g, sent);
//	    MyGraph.decideTopsStupid(g, sent);
	    return g;
	}


	// START feature cache stuff
    // uses https://github.com/EsotericSoftware/kryo found from http://stackoverflow.com/questions/239280/which-is-the-best-alternative-for-java-serialization
    
    static Kryo kryo;
    static { kryo = new Kryo(); }
    static boolean cacheReadMode = false;
    static Input kryoInput;
    static Output kryoOutput;
    static String featureCacheFile;
    static { featureCacheFile = "featcache." + MiscUtil.getProcessId("bla") + ".bin"; }
    
    /** this should work with or without caching enabled.
     * for caching, assume accesses are in order!!
     */
    static NumberizedSentence getNextExample(int snum) {
    	if (useFeatureCache && cacheReadMode) {
    		return kryo.readObject(kryoInput, NumberizedSentence.class);
    	} else {
    		NumberizedSentence ns = extractFeatures(model, snum);
    		return ns;
    	}
    }
    static void openCacheForWriting() throws FileNotFoundException {
    	if (!useFeatureCache) return;
        kryoOutput = new Output(new FileOutputStream(featureCacheFile));
    }
    static void extractFeaturesForExampleAndWriteToCache(int snum) {
		NumberizedSentence ns = extractFeatures(model, snum);
		kryo.writeObject(kryoOutput, ns);
    }
    static void closeCacheAfterWriting() {
    	if (!useFeatureCache) return;
    	kryoOutput.close();
    	long size = new File(featureCacheFile).length();
    	U.pf("Feature cache (%s) is %.1f MB, %.2f MB/sent\n", 
    			featureCacheFile, size*1.0/1e6, size*1.0/1e6/inputSentences.length);
    }
    static void resetCacheReader() throws FileNotFoundException {
    	if (!useFeatureCache) return;
    	if (kryoInput != null) {
        	kryoInput.close();
    	}
    	kryoInput = new Input(new FileInputStream(featureCacheFile));
    }
    
    // END feature cache stuff
    

	///////////////////////////////////////////////////////////

	static List<FE.FeatureExtractor> initializeFeatureExtractors() {
		final List<FE.FeatureExtractor> allFE = new ArrayList<>();
		allFE.add(new BasicFeatures());
		allFE.add(new LinearOrderFeatures());
		allFE.add(new CoarseDependencyFeatures());
		allFE.add(new DependencyPathv1());
		allFE.add(new SubcatSequenceFE());
		allFE.add(new WordVectors(wordToVector));
//		allFE.add(new PruneFeatsForSemparser());
		return allFE;
	}

	static void initializeLabelFeatureExtractors() {
		// always use the name of the label itself
		labelFeatureExtractors.add(new PassThroughFe());
		if (useDmLabelFeatures) {
			labelFeatureExtractors.add(new DmFe());
		}
		if (usePasLabelFeatures) {
			labelFeatureExtractors.add(new PasFe());
		}
		if (usePcedtLabelFeatures) {
			labelFeatureExtractors.add(new PcedtFE());
		}
	}
}
