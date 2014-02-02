package edu.cmu.cs.ark.semeval2014.lr;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicFeatures;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicLabelFeatures;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE;
import edu.cmu.cs.ark.semeval2014.lr.fe.LinearOrderFeatures;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;
import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.io.GraphReader;
import util.Arr;
import util.BasicFileIO;
import util.U;
import util.Vocabulary;
import util.misc.Pair;
import util.misc.Triple;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static edu.cmu.cs.ark.semeval2014.lr.MiscUtil.unbox;

public class LRParser {
	public static final String NO_EDGE = "NOEDGE";

	// 1. Data structures
	static InputAnnotatedSentence[] inputSentences = null; // full dataset
	static List<int[][]> graphMatrices = null;  // full dataset
	
	// 2. Feature system and model parameters
	static List<FE.FeatureExtractor> allFE = new ArrayList<>();
	static List<FE.LabelFE> labelFeatureExtractors = new ArrayList<>();

	static Model model;
	static float[] ssGrad;  // adagrad history info. parallel to coefs[].

	@Parameter(names="-learningRate")
	static double learningRate = .1;
	
	// 3. Model parameter-ish options
	static int maxEdgeDistance = 10;
	@Parameter(names="-l2reg")
	static double l2reg = 1;
	@Parameter(names="-noedgeWeight")
	static double noedgeWeight = 0.3;
	
	// 4. Runtime options
	@Parameter(names="-verboseFeatures")
	static boolean verboseFeatures = false;
	@Parameter(names="-useFeatureCache", arity=1)
    static boolean useFeatureCache = true;
    @Parameter(names="-saveEvery")
    static int saveEvery = 10;  // -1 to disable intermediate model saves
    @Parameter(names="-numIters")
	static int numIters = 30;
	
    @Parameter(names="-mode", required=true)
	static String mode;
    @Parameter(names="-model",required=true)
	static String modelFile;
    @Parameter(names={"-sdpInput","-sdpOutput"}, required=true)
	static String sdpFile;
    @Parameter(names="-depInput", required=true)
	static String depFile;

	public static void main(String[] args) throws IOException {
		new JCommander(new LRParser(), args);  // seems to write to the static members.

		assert mode.equals("train") || mode.equals("test");

		// Data loading
		inputSentences = Corpus.getInputAnnotatedSentences(depFile);
		U.pf("%d input sentences\n", inputSentences.length);

		initializeFeatureExtractors();
		for (FE.FeatureExtractor fe : allFE) {
			assert (fe instanceof FE.TokenFE) || (fe instanceof FE.EdgeFE) : "all feature extractors need to implement one of the interfaces!";
			fe.initializeAtStartup();
		}

		if (mode.equals("train")) {
			trainModel();
		} else if (mode.equals("test")) {
			model = Model.load(modelFile);
			U.pf("Writing predictions to %s\n", sdpFile);
			double t0, dur;
			t0 = System.currentTimeMillis();
			makePredictions(model, sdpFile);
			dur = System.currentTimeMillis() - t0;
			U.pf("\nPRED TIME %.1f sec, %.1f ms/sent\n", dur/1e3, dur/inputSentences.length);
		}
	}

	private static void trainModel() throws IOException {
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
			assert sent.sentenceId().equals(graph.id.replace("#",""));
			graphMatrices.add(convertGraphToAdjacencyMatrix(graph, size(sent), labelVocab));
		}

		final Vocabulary perceptVocab = new Vocabulary();
		perceptVocab.num("***BIAS***");
		model = new Model(labelVocab, labelFeatureVocab, featuresByLabel, perceptVocab);

		t0 = System.currentTimeMillis();
		trainingOuterLoopOnline(model, modelFile);
		dur = System.currentTimeMillis() - t0;
		U.pf("TRAINLOOP TIME %.1f sec\n", dur/1e3);

		model.save(modelFile);
		if (useFeatureCache)
			Files.delete(Paths.get(featureCacheFile));
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
			final LabelFeatureAdder adder = new LabelFeatureAdder(labelFeatVocab);
			for (FE.LabelFE fe : labelFeatureExtractors) {
				fe.features(labelVocab.name(labelIdx), adder);
			}
			featsByLabel.add(adder.getFeatures());
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

	static int size(InputAnnotatedSentence s) { 
		return s.sentence().length;
	}
	public static boolean badDistance(int i, int j) {
		return i==j || Math.abs(i-j) > maxEdgeDistance;
	}
	
	static long totalPairs = 0;  // only for diagnosis
	
	static class TokenFeatAdder extends FE.FeatureAdder {
		int i=-1;
		NumberizedSentence ns;
		InputAnnotatedSentence is; // only for debugging
		final Vocabulary perceptVocab;

		TokenFeatAdder(Vocabulary perceptVocab) {
			this.perceptVocab = perceptVocab;
		}

		@Override
		public void add(String featname, double value) {
			if (verboseFeatures) {
				U.pf("NODEFEAT\t%s:%d\t%s\n", is.sentence()[i], i, featname);
			}

			// this is kinda a hack, put it in both directions for every edge.
			// we could use smarter data structures rather than the full matrix
			// of edge featvecs to represent this more compactly.

			String ff;
			int featnum;
			
			ff = U.sf("%s::ashead", featname);
			featnum = perceptVocab.num(ff);
			if (featnum!=-1) {
				for (int j=0; j<ns.T; j++) {
					if (badDistance(i,j)) continue;
					ns.add(i,j, featnum, value);
				}
			}
			
			ff = U.sf("%s::aschild", featname);
			featnum = perceptVocab.num(ff);
			if (featnum!=-1) {
				for (int j=0; j<ns.T; j++) {
					if (badDistance(j,i)) continue;
					ns.add(j,i, featnum, value);
				}
			}
				
		}
	}

	static class EdgeFeatAdder extends FE.FeatureAdder {
		int i=-1, j=-1;
		NumberizedSentence ns;
		// these are only for debugging
		InputAnnotatedSentence is;
		int[][] goldEdgeMatrix;
		final Vocabulary perceptVocab;

		EdgeFeatAdder(Vocabulary perceptVocab) {
			this.perceptVocab = perceptVocab;
		}

		@Override
		public void add(String featname, double value) {
			int perceptnum = perceptVocab.num(featname);
			if (perceptnum==-1) return;
			
			ns.add(i,j, perceptnum, value);
			
			if (verboseFeatures) {
				U.pf("WORDS %s:%d -> %s:%d\tGOLD %s\tEDGEFEAT %s %s\n", is.sentence()[i], i, is.sentence()[j], j,
						goldEdgeMatrix!=null ? model.labelVocab.name(goldEdgeMatrix[i][j]) : null, featname, value);
			}

		}
	}

	static class LabelFeatureAdder extends FE.FeatureAdder {
		private final Vocabulary labelFeatureVocab;
		private final Set<Integer> features = new HashSet<>();

		public LabelFeatureAdder(Vocabulary labelFeatureVocab) {
			this.labelFeatureVocab = labelFeatureVocab;
		}

		@Override
		public void add(String featname, double value) {
			features.add(labelFeatureVocab.num(featname));
		}

		public int[] getFeatures() {
			return unbox(features);
		}
	}

	/**
	 * goldEdgeMatrix is only for feature extractor debugging verbose reports 
	 */
	static NumberizedSentence extractFeatures(Model model, InputAnnotatedSentence is, int[][] goldEdgeMatrix) {

		NumberizedSentence ns = new NumberizedSentence( size(is) );
		TokenFeatAdder adder1 = new TokenFeatAdder(model.perceptVocab);
		EdgeFeatAdder adder2 = new EdgeFeatAdder(model.perceptVocab);
		adder1.ns=adder2.ns=ns;
		
		// only for verbose feature extraction reporting
		adder1.is = adder2.is=is;
		adder2.goldEdgeMatrix = goldEdgeMatrix;
		
		for (FE.FeatureExtractor fe : allFE) {
			fe.setupSentence(is);
		}
		
		for (adder2.i=0; adder2.i<ns.T; adder2.i++) {
			
			adder1.i = adder2.i;
			for (FE.FeatureExtractor fe : allFE) {
				if (fe instanceof FE.TokenFE) {
					((FE.TokenFE) fe).features(adder1.i, adder1);
				}
			}
			for (adder2.j=0; adder2.j<ns.T; adder2.j++) {
				if (badDistance(adder2.i,adder2.j)) continue;
				
				// bias term
				ns.add(adder2.i, adder2.j, 0, 1.0);
				
				// edge features
				for (FE.FeatureExtractor fe : allFE) {
					if (fe instanceof FE.EdgeFE) {
						((FE.EdgeFE) fe).features(adder2.i, adder2.j, adder2);
					}
				}
			}
		}
		return ns;
	}
	
	static NumberizedSentence extractFeatures(Model model, int snum) {
		return extractFeatures(model, inputSentences[snum], graphMatrices !=null ? graphMatrices.get(snum) : null);
	}

    static void trainingOuterLoopOnline(Model model, String modelFilePrefix) throws IOException {
    	for (int outer=0; outer<numIters; outer++) {
    		U.pf("iter %3d ", outer);  System.out.flush();
    		double t0 = System.currentTimeMillis();
    		
    		if (outer==0) {
    			cacheReadMode = false;
    			openCacheForWriting();
    		} else {
    			cacheReadMode = true;
    			resetCacheReader();
    		}
    		
    		trainOnlineIter(model, outer==0);
    		
        	double dur = System.currentTimeMillis() - t0;
        	U.pf("%.1f sec, %.1f ms/sent\n", dur/1000, dur/inputSentences.length);
    		
        	if (saveEvery >= 0 && outer % saveEvery == 0)
        		model.save(U.sf("%s.iter%s", modelFilePrefix, outer));
    		
    		if (outer==0) {
    			closeCacheAfterWriting();
    		}
    		
    		if (outer==0) U.pf("%d percepts, %d nnz\n", model.perceptVocab.size(), NumberizedSentence.totalNNZ);
    	}
    }

    static void growCoefsIfNecessary() {
    	if (ssGrad==null) {
    		int n = Math.min(10000, model.perceptVocab.size());
    		model.coefs = new float[n*model.labelFeatureVocab.size()];
    		ssGrad = new float[n*model.labelFeatureVocab.size()];
    	}
    	else if (model.labelFeatureVocab.size()*model.perceptVocab.size() > model.coefs.length) {
    		int newLen = (int) Math.ceil(1.2 * model.perceptVocab.size()) * model.labelFeatureVocab.size();
			model.coefs = NumberizedSentence.growToLength(model.coefs, newLen);
            ssGrad = NumberizedSentence.growToLength(ssGrad, newLen);
            assert model.coefs.length==ssGrad.length;
        }
    }
	
    /** From the new gradient value, update this feature's learning rate and return it. */
    static double adagradStoreRate(int featnum, double g) {
        ssGrad[featnum] += g*g;
        if (ssGrad[featnum] < 1e-2) return 10.0; // 1/sqrt(.01)
        return 1.0 / Math.sqrt(ssGrad[featnum]);
    }
    
    
    /** adagrad: http://www.ark.cs.cmu.edu/cdyer/adagrad.pdf */ 
    static void trainOnlineIter(Model model, boolean firstIter) throws FileNotFoundException {
		assert model.labelVocab.isLocked() : "since we have autolabelconj, can't tolerate label vocab expanding during a training pass.";
		assert model.labelFeatureVocab.isLocked() : "since we have autolabelconj, can't tolerate label vocab expanding during a training pass.";

		double ll = 0;
        for (int snum=0; snum<inputSentences.length; snum++) {
        	U.pf(".");
            
            NumberizedSentence ns = getNextExample(snum);
            if (firstIter) {
                growCoefsIfNecessary();
            }
    		int[][] edgeMatrix = graphMatrices.get(snum);
            ll += updateExampleLogreg(ns, edgeMatrix);
            
            if (firstIter && snum>0 && snum % 1000 == 0) {
            	U.pf("%d sents, %.3fm percepts, %.3fm finefeats allocated, %.1f MB mem used\n", 
            			snum+1, model.perceptVocab.size()/1e6, model.coefs.length/1e6,
            			Runtime.getRuntime().totalMemory()/1e6
            			);
            }
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

	static double updateExampleLogreg(NumberizedSentence sentence, int[][] edgeMatrix) {
		final int noEdgeIdx = model.labelVocab.num(NO_EDGE);
		double ll = 0;

		double[][][] probs = inferEdgeProbs(sentence);
		
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

	/** returns:  (#tokens x #tokens x #labelvocab)
	 * for token i and token j, prob dist over the possible edge labels.
	 */
	static double[][][] inferEdgeProbs(NumberizedSentence ns) {
		double[][][] scores = inferEdgeScores(ns);
		// transform in-place into probs
		for (int i=0; i<ns.T; i++) {
			for (int j=0; j<ns.T; j++) {
				if (badDistance(i,j)) continue;
				Arr.softmaxInPlace(scores[i][j]);
			}
		}
		return scores;
	}

	/** returns:  (#tokens x #tokens x #labelvocab)
	 * for token i and token j, nonneg scores (unnorm probs) per edge label
	 */
	static double[][][] inferEdgeScores(NumberizedSentence ns) {
		double[][][] scores = new double[ns.T][ns.T][model.labelVocab.size()];
		for (int kk=0; kk<ns.nnz; kk++) {
			for (int label=0; label<model.labelVocab.size(); label++) {
				for (int labelFeatureIdx : model.featuresByLabel.get(label)) {
					final int featureIdx = model.coefIdx(ns.perceptnum(kk), labelFeatureIdx);
					scores[ns.i(kk)][ns.j(kk)][label] += model.coefs[featureIdx] * ns.value(kk);
				}
			}
		}
		return scores;
	}

	static void makePredictions(Model model, String outputFile) {
		try(PrintWriter out = new PrintWriter(BasicFileIO.openFileToWriteUTF8(outputFile))) {
			for (InputAnnotatedSentence sent : inputSentences) {
				NumberizedSentence ns = extractFeatures(model, sent, null);
				double[][][] probs = inferEdgeProbs(ns);
				MyGraph g = decodeEdgeProbsToGraph(sent, probs);

				U.pf(".");

				out.println("#" + sent.sentenceId());
				for (int i = 0; i < size(sent); i++) {
					boolean istop = !g.isChildOfSomething[i] && g.isPred[i];
					out.printf("%d\t%s\tlemmaz\t%s\t%s\t%s", i + 1, sent.sentence()[i], sent.pos()[i],
							istop ? "+" : "-", g.isPred[i] ? "+" : "-");
					for (int head = 0; head < size(sent); head++) {
						if (!g.isPred[head]) continue;
						// ok now we're in a predicate column that may be dominating this node
						String label = g.edgeMatrix[head][i];
						label = label == null ? "_" : label;
						out.print("\t" + label);
					}
					out.print("\n");
				}
				out.print("\n");
				out.flush();
			}
		}
	}
	
	static class MyGraph {
		boolean[] isChildOfSomething;
		boolean[] isPred;
		List< Triple<Integer,Integer,String> > edgelist;
		String[][] edgeMatrix;
		MyGraph(int sentenceLength, List<Triple<Integer,Integer,String>>_edgelist) {
			edgelist = _edgelist;
			isChildOfSomething = new boolean[sentenceLength];
			isPred = new boolean[sentenceLength];
			edgeMatrix = new String[sentenceLength][sentenceLength];
			for (Triple<Integer,Integer,String> tt : _edgelist) {
				int i=tt.first, j=tt.second;
				edgeMatrix[i][j] = tt.third;
				isPred[i] = true;
				isChildOfSomething[j] = true;
			}
		}
	}
	static MyGraph decodeEdgeProbsToGraph(InputAnnotatedSentence sent, double[][][] probs) {
		final List<Triple<Integer,Integer,String>> edgeList = new ArrayList<>();
		for (int i=0; i<size(sent); i++) {
			for (int j=0; j<size(sent); j++) {
				if (badDistance(i,j)) continue;
				int predLabel = Arr.argmax(probs[i][j]);
				Triple<Integer,Integer,String> tt = new Triple<>(i, j, model.labelVocab.name(predLabel));
				if (tt.third.equals(NO_EDGE)) continue;
				edgeList.add(tt);
			}
		}
		return new MyGraph(size(sent), edgeList);
	}
	////////////////////////////////
	
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
    		if (useFeatureCache) { 
    			kryo.writeObject(kryoOutput, ns);
    		}
    		return ns;
    	}
    }
    static void openCacheForWriting() throws FileNotFoundException {
    	if (!useFeatureCache) return;
        kryoOutput = new Output(new FileOutputStream(featureCacheFile));
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
	
	static void initializeFeatureExtractors() {
		allFE.add(new BasicFeatures());
		allFE.add(new LinearOrderFeatures());
	}

	static void initializeLabelFeatureExtractors() {
		labelFeatureExtractors.add(new BasicLabelFeatures.PassThroughFe());
//		labelFeatureExtractors.add(new BasicLabelFeatures.IsEdgeFe());
//		labelFeatureExtractors.add(new BasicLabelFeatures.DmFe());
	}
}
