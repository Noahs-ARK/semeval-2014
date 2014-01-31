package edu.cmu.cs.ark.semeval2014.lr;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.io.GraphReader;
import util.Arr;
import util.BasicFileIO;
import util.U;
import util.Vocabulary;
import util.misc.Pair;
import util.misc.Triple;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE;
import edu.cmu.cs.ark.semeval2014.lr.fe.BasicFeatures;
import edu.cmu.cs.ark.semeval2014.lr.fe.JustPOS;
import edu.cmu.cs.ark.semeval2014.lr.fe.LinearOrderFeatures;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;

public class LRParser {
	
	// 1. Data structures
	static Graph[] graphs;
	static InputAnnotatedSentence[] inputSentences = null; // full dataset
	static ArrayList<int[][]> graphMatrixes = null;  // full dataset
	
	// 2. Feature system and model parameters
	static List<FE.FeatureExtractor> allFE = new ArrayList<>();
	static Vocabulary labelVocab = new Vocabulary();
	static Vocabulary perceptVocab;
	static double[] coefs; // flattened form. DO NOT USE coefs.length IT IS CAPACITY NOT FEATURE CARDINALITY
	static double[] ssGrad;  // adagrad history info. parallel to coefs[].
	static double learningRate = .1;
	

	
	// 3. Model parameter-ish options
	static int maxEdgeDistance = 10;
	static double l2reg = .01;
	static double noedgeWeight = 0.3;
	
	// 4. Runtime options
	static boolean verboseFeatures = false;
    static boolean useFeatureCache = true;
    static int saveModelAtEvery = 10;  // -1 to disable intermediate model saves
	static int numOnlineIters = 30;
	
	
	public static void main(String[] args) throws IOException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		String mode = args[0];
		String modelFile = args[1];
		String sdpFile = args[2];
		String depFile = args[3];
		
		assert mode.equals("train") || mode.equals("test");
		
		// Data loading
		
		inputSentences = Corpus.getInputAnnotatedSentences(depFile);

		if (mode.equals("train")) {
			labelVocab.num("NOEDGE");
			
			U.pf("Reading graphs from %s\n", sdpFile);
			
			ArrayList<Graph> graphsAL = new ArrayList<>();
			graphMatrixes = new ArrayList<>();
			
	        GraphReader reader = new GraphReader(sdpFile);
	        Graph graph;
	        while ((graph = reader.readGraph()) != null) {
	        	graphsAL.add(graph);
	        	// also build up the edge label vocabulary
	        	for (Edge e : graph.getEdges()) {
	        		labelVocab.num(e.label);
	        	}
	        }
	        reader.close();
	        
	        labelVocab.lock();
	        graphs = graphsAL.toArray(new Graph[0]);
	        
	        assert graphs.length == inputSentences.length;
	        
	        for (int snum=0; snum<graphs.length; snum++) {
	        	InputAnnotatedSentence sent = inputSentences[snum];
	        	graph = graphs[snum];
	        	assert sent.sentenceId().equals(graph.id.replace("#",""));

	        	int[][] edgeMatrix = new int[size(sent)][size(sent)];
	        	for (int i=0; i<size(sent); i++) {
	        		for (int j=0; j<size(sent); j++) {
	        			edgeMatrix[i][j] = labelVocab.num("NOEDGE");
	        		}
	        	}
	        	for (Edge e : graphs[snum].getEdges()) {
	        		edgeMatrix[e.source-1][e.target-1] = labelVocab.num(e.label);
	        	}
	        	graphMatrixes.add(edgeMatrix);
	        }
	        
		}
		
		U.pf("%d input sentences\n", inputSentences.length);

		initializeFeatureExtractors();
		for (FE.FeatureExtractor fe : allFE) {
			assert (fe instanceof FE.TokenFE) || (fe instanceof FE.EdgeFE) : "all feature extractors need to implement one of the interfaces!";
			fe.initializeAtStartup();
		}

		double t0,dur;
		if (mode.equals("train")) {
			perceptVocab = new Vocabulary();
			perceptVocab.num("***BIAS***");
			
					t0 = System.currentTimeMillis();
			trainingOuterloopOnline(modelFile);
					dur = System.currentTimeMillis() - t0;
					U.pf("TRAINLOOP TIME %.1f sec\n", dur/1e3);

			saveModel(modelFile);
			Files.delete(Paths.get(featureCacheFile));
		}
		else if (mode.equals("test")) {
			loadModel(modelFile);
			U.pf("Writing predictions to %s\n", sdpFile);
					t0 = System.currentTimeMillis();
			makePredictions(sdpFile);
					dur = System.currentTimeMillis() - t0;
					U.pf("\nPRED TIME %.1f sec, %.1f ms/sent\n", dur/1e3, dur/inputSentences.length);
		}
	}
	
	static int size(InputAnnotatedSentence s) { 
		return s.sentence().length;
	}
	public static boolean badDistance(int i, int j) {
		return i==j || Math.abs(i-j) > maxEdgeDistance;
	}
	
	static long totalPairs = 0;  // only for diagnosis
	
	static class TokenFeatAdder extends FE.FeatureAdder {
		int i=-1, labelID=-1;
		NumberizedSentence ns;
		InputAnnotatedSentence is; // only for debugging
		
		@Override
		public void add(String featname, double value) {
			if (verboseFeatures) {
				U.pf("NODEFEAT\t%s:%d\t%s\n", is.sentence()[i], featname);
			}

			// this is kinda a hack, put it in both directions for every edge. we could use smarter data structures rather than the full matrix of edge featvecs to represent this more compactly.

			for (int labelID=0; labelID < labelVocab.size(); labelID++) {
				String ff;
				int featnum;
				
				ff = U.sf("%s::ashead", featname);
				featnum = perceptVocab.num(ff);
				if (featnum!=-1) {
					for (int j=0; j<ns.T; j++) {
						if (badDistance(i,j)) continue;
						ns.add(i,j, featnum, labelID, value);
					}
				}
				
				ff = U.sf("%s::aschild", featname);
				featnum = perceptVocab.num(ff);
				if (featnum!=-1) {
					for (int j=0; j<ns.T; j++) {
						if (badDistance(j,i)) continue;
						ns.add(j,i, featnum, labelID, value);
					}
				}
				
			}
		}
	}
	
	/** "finefeatnum" is a legitimate index into coefs[]. */
	static int finefeatnum(int perceptnum, int label) {
		int K = labelVocab.size();
		return perceptnum*K + label;
	}
	
	static class EdgeFeatAdder extends FE.FeatureAdder {
		int i=-1, j=-1;
		NumberizedSentence ns;
		// these are only for debugging
		InputAnnotatedSentence is;
		int[][] goldEdgeMatrix;
		
		@Override
		public void add(String featname, double value) {
			int perceptnum = perceptVocab.num(featname);
			if (perceptnum==-1) return;
			for (int label=0; label<labelVocab.size(); label++) {
				ns.add(i,j, perceptnum, label, value);
			}
			
			if (verboseFeatures) {
				U.pf("WORDS %s:%d -> %s:%d\tGOLD %s\tEDGEFEAT %s %s\n", is.sentence()[i], i, is.sentence()[j], j, 
						goldEdgeMatrix!=null ? labelVocab.name(goldEdgeMatrix[i][j]) : null, featname, value);
			}

		}
	}
	
	/**
	 * goldEdgeMatrix is only for feature extractor debugging verbose reports 
	 */
	static NumberizedSentence extractFeatures(
			InputAnnotatedSentence is, int[][] goldEdgeMatrix
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		NumberizedSentence ns = new NumberizedSentence( size(is) );
		EdgeFeatAdder adder2 = new EdgeFeatAdder();
		TokenFeatAdder adder1 = new TokenFeatAdder();
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
				for (int k=0; k<labelVocab.size(); k++) {
					ns.add(adder2.i, adder2.j, 0, k, 1.0);
				}
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
	
	static NumberizedSentence extractFeatures(int snum) {
		try {
			
			return extractFeatures(inputSentences[snum], graphMatrixes!=null ? graphMatrixes.get(snum) : null);
			
		} catch (InstantiationException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	static void lockdownVocabAndAllocateCoefs() {
		perceptVocab.lock();
		labelVocab.lock();
		assert perceptVocab.name(0).equals("***BIAS***");
		coefs = new double[perceptVocab.size() * labelVocab.size()];
	}
	
	static void saveModel(String modelFile) throws IOException {
		U.pf("Saving model to %s\n", modelFile);
		
		BufferedWriter writer = BasicFileIO.openFileToWriteUTF8(modelFile);
		PrintWriter out = new PrintWriter(writer);

		out.append("LABELVOCAB\t");
		for (String x : labelVocab.names()) {
			out.append(x + " ");
		}
		out.append("\n");
		for (int f=0; f<perceptVocab.size(); f++) {
			for (int k=0; k<labelVocab.size(); k++) {
				out.printf("C\t%s\t%s\t%g\n", perceptVocab.name(f), labelVocab.name(k), coefs[finefeatnum(f,k)]);
			}
		}
		writer.close();
	}
	static void loadModel(String modelFile) throws IOException {
		// labelVocab, argfeats
		labelVocab = new Vocabulary();
		perceptVocab = new Vocabulary();
		
		BufferedReader reader = BasicFileIO.openFileOrResource(modelFile);
		String line;
		
		ArrayList<Triple<Integer,Integer,Double>> coefTuples = new ArrayList<>(); 

		while ( (line = reader.readLine()) != null ) {
			String[] parts = line.split("\t");
			if (parts[0].equals("LABELVOCAB")) {
				String[] labels = parts[1].trim().split(" ");
				for (String x : labels) labelVocab.num(x);
				labelVocab.lock();
			}
			else if (parts[0].equals("C")) {
				int perceptnum = perceptVocab.num(parts[1]);
				int labelnum = labelVocab.numStrict(parts[2]);
				double value = Double.parseDouble(parts[3]);
				coefTuples.add(U.triple(perceptnum, labelnum, value));
			}
			else { throw new RuntimeException("bad model line format"); }
		}

		lockdownVocabAndAllocateCoefs();
		
		for (Triple<Integer,Integer,Double> x : coefTuples) {
			coefs[finefeatnum(x.first,x.second)] = x.third;
		}
		reader.close();
		
		U.pf("Label vocab (size %d): %s\n", labelVocab.size(), labelVocab.names());
		U.pf("Num features: %d\n", perceptVocab.size());
	}
	
    static void trainingOuterloopOnline(String modelFilePrefix) throws IOException {
    	for (int outer=0; outer<numOnlineIters; outer++) {
    		U.pf("iter %3d ", outer);  System.out.flush();
    		double t0 = System.currentTimeMillis();
    		
    		if (outer==0) {
    			cacheReadMode = false;
    			openCacheForWriting();
    		} else {
    			cacheReadMode = true;
    			resetCacheReader();
    		}
    		
    		trainOnlineIter(outer==0);
    		
        	double dur = System.currentTimeMillis() - t0;
        	U.pf("%.1f sec, %.1f ms/sent\n", dur/1000, dur/inputSentences.length);
    		
        	if (saveModelAtEvery >= 0 && outer % saveModelAtEvery == 0)
        		saveModel(U.sf("%s.iter%s",modelFilePrefix, outer));
    		
    		if (outer==0) {
    			closeCacheAfterWriting();
    		}
    		
    		if (outer==0) U.pf("%d percepts, %d nnz\n", perceptVocab.size(), NumberizedSentence.totalNNZ);
    	}
    }

    static void growCoefsIfNecessary() {
    	assert coefs==null&&ssGrad==null || coefs.length==ssGrad.length;
    	if (coefs==null) {
    		int n = Math.min(10000, perceptVocab.size());
    		coefs = new double[n*labelVocab.size()];
    		ssGrad = new double[n*labelVocab.size()];
    	}
    	else if (labelVocab.size()*perceptVocab.size() > coefs.length) {
    		int newlen = (int) Math.ceil(1.2 * perceptVocab.size()) * labelVocab.size();
            coefs = NumberizedSentence.growToLength(coefs, newlen);
            ssGrad = NumberizedSentence.growToLength(ssGrad, newlen);
            assert coefs.length==ssGrad.length;
//            U.pf("GROW COEFS TO %d\n", coefs.length);
        }
    }
	
    /** From the new gradient value, update this feature's learning rate and return it. */
    static double adagradStoreRate(int featnum, double g) {
        ssGrad[featnum] += g*g;
        if (ssGrad[featnum] < 1e-2) return 10.0; // 1/sqrt(.01)
        return 1.0 / Math.sqrt(ssGrad[featnum]);
    }
    
    
    /** adagrad: http://www.ark.cs.cmu.edu/cdyer/adagrad.pdf */ 
    static void trainOnlineIter(boolean firstIter) throws FileNotFoundException {
    	assert labelVocab.isLocked() : "since we have autolabelconj, can't tolerate label vocab expanding during a training pass.";

        double ll = 0;
        for (int snum=0; snum<inputSentences.length; snum++) {
        	U.pf(".");
            
            NumberizedSentence ns = getNextExample(snum);
            if (firstIter) {
                growCoefsIfNecessary();
            }
    		int[][] edgeMatrix = graphMatrixes.get(snum);
            ll += updateExampleLogreg(ns, edgeMatrix);
            
            if (firstIter && snum>0 && snum % 1000 == 0) {
            	U.pf("%d sents, %.3fm percepts, %.3fm finefeats allocated, %.1f MB mem used\n", 
            			snum+1, perceptVocab.size()/1e6, coefs.length/1e6, 
            			Runtime.getRuntime().totalMemory()/1e6
            			);
            }
        }
        //  logprior  =  - (1/2) lambda || beta ||^2
        //  gradient =  - lambda beta
        for (int f=0; f<coefs.length; f++) {
            ll -= 0.5 * l2reg * coefs[f]*coefs[f];
            double g = l2reg * coefs[f];
            coefs[f] -= adagradStoreRate(f,g) * learningRate * g;
        }
        U.pf("ll %.1f  ", ll);
    }

	static double updateExampleLogreg(NumberizedSentence ns, int[][] edgeMatrix) {
		double ll = 0;
		
		double[][][] probs = inferEdgeProbs(ns);
		
		for (int kk=0; kk<ns.nnz; kk++) {
		    int i=ns.i(kk), j=ns.j(kk);
		    double w = edgeMatrix[i][j]==0 ? noedgeWeight : 1.0;
		    int observed = edgeMatrix[i][j] == ns.label(kk) ? 1 : 0;
		    double resid = observed - probs[i][j][ns.label(kk)];
		    double g = w * resid * ns.value(kk);
		    
		    double rate = adagradStoreRate(ns.perceptnum(kk), g);

		    coefs[finefeatnum(ns.perceptnum(kk),ns.label(kk))] += learningRate * rate * g;
		}
		
		// loglik is completely unnecessary for optimization, just nice for diagnosis.
		for (int i=0;i<ns.T;i++) {
		    for (int j=0; j<ns.T;j++) {
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
		double[][][] scores = new double[ns.T][ns.T][labelVocab.size()];
		for (int kk=0; kk<ns.nnz; kk++) {
			scores[ns.i(kk)][ns.j(kk)][ns.label(kk)] += coefs[finefeatnum(ns.perceptnum(kk),ns.label(kk))] * ns.value(kk);
		}
		return scores;
	}



	static void makePredictions(String outputFile) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, IOException {
		BufferedWriter bw = BasicFileIO.openFileToWriteUTF8(outputFile);
		PrintWriter out = new PrintWriter(bw);
//		GraphWriter gw = new GraphWriter(out);

		for (int snum=0; snum<inputSentences.length; snum++) {
			InputAnnotatedSentence sent = inputSentences[snum];
			NumberizedSentence ns = extractFeatures(sent, null);
			double[][][] probs = inferEdgeProbs(ns);
			MyGraph g = decodeEdgeprobsToGraph(sent, probs);

			U.pf(".");
//			U.pf("\nSENT %d\n", snum);
//			for (Triple<Integer,Integer,String> tt : g.edgelist) {
//				U.pf("%s[%s:%d -> %s:%d]\n", tt.third, sent.sentence()[tt.first], tt.first, sent.sentence()[tt.second], tt.second);
//			}
			
			out.println("#" + sent.sentenceId());
			for (int i=0; i<size(sent); i++) {
				boolean istop = !g.isChildOfSomething[i] && g.isPred[i];
				out.printf("%d\t%s\tlemmaz\t%s\t%s\t%s", i+1, sent.sentence()[i], sent.pos()[i], 
						 istop ? "+" : "-", g.isPred[i] ? "+" : "-");
				for (int head=0; head<size(sent); head++) {
					if (!g.isPred[head]) continue;
					// ok now we're in a predicate column that may be dominating this node
					String label = g.edgeMatrix[head][i];
					label = label==null ? "_" : label;
					out.print("\t" + label);
				}
				out.print("\n");
			}
			out.print("\n");
			out.flush();
		}
		out.close();
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
	static MyGraph decodeEdgeprobsToGraph(InputAnnotatedSentence sent, double[][][] probs) {
//		Graph g = new Graph("#"+sent.sentenceId());
		List<Triple<Integer,Integer,String>> edgelist = new ArrayList<>();
		for (int i=0; i<size(sent); i++) {
			for (int j=0; j<size(sent); j++) {
				if (badDistance(i,j)) continue;
				int predlabel = Arr.argmax(probs[i][j]);
				Triple<Integer,Integer,String> tt = new Triple(i,j,labelVocab.name(predlabel));
				if (tt.third.equals("NOEDGE")) continue;
				edgelist.add(tt);
			}
		}
		return new MyGraph(size(sent), edgelist);
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
    		NumberizedSentence ns = extractFeatures(snum);
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
//		allFE.add(new JustPOS());
		allFE.add(new BasicFeatures());
		allFE.add(new LinearOrderFeatures());
	}

}
