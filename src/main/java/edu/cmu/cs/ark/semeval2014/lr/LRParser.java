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
import edu.cmu.cs.ark.semeval2014.utils.Corpus;

public class LRParser {
	
	// 1. Data structures
	static Graph[] graphs;
	static InputAnnotatedSentence[] inputSentences = null; // full dataset
	static ArrayList<int[][]> graphMatrixes = null;  // full dataset
	
	// 2. Feature system and model parameters
	static List<FE.FeatureExtractor> allFE = new ArrayList<>();
	static Vocabulary labelVocab = new Vocabulary();
	static Vocabulary featVocab;
	static double[] coefs; // ok let's do the giant flattened form. DO NOT USE coefs.length IT IS CAPACITY NOT FEATURE CARDINALITY

	
	// 3. Model parameter-ish options
	static int maxEdgeDistance = 10;
	static double l2reg = .01;
	static double noedgeWeight = 0.3;
	
	// 4. Runtime options
	static boolean verboseFeatures = false;
    static boolean useFeatureCache = true;
	static int numOnlineIters = 30;
	
//	static List<Class<FeatureExtractor1>> allFE1 = new ArrayList<>();
//	static List<Class<FeatureExtractor2>> allFE2 = new ArrayList<>();
	
	
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
		}

		double t0,dur;
		if (mode.equals("train")) {
			featVocab = new Vocabulary();
			for (int k=0; k<labelVocab.size(); k++) {
				featVocab.num(labelBiasName(k));
			}
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
	/** returns:  (#tokens x #tokens x #labelvocab)
	 * for token i and token j, prob dist over the possible edge labels.
	 */
	static double[][][] inferEdgeProbs(NumberizedSentence ns) {
		double[][][] scores = new double[ns.T][ns.T][labelVocab.size()];  // intermediate scores, but finally becomes probs for return
		
		for (int kk=0; kk<ns.nnz; kk++) {
			scores[ns.i(kk)][ns.j(kk)][ns.label(kk)] += coefs[ns.featnum(kk)] * ns.value(kk);
		}
		for (int i=0; i<ns.T; i++) {
			for (int j=0; j<ns.T; j++) {
				
				if (badDistance(i,j)) continue;

				// transform in-place into probs
				Arr.softmaxInPlace(scores[i][j]);
			}
		}
		return scores;
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
			
			for (int labelID=0; labelID < labelVocab.size(); labelID++) {
				String labelStr = labelVocab.name(labelID);
				String ff;
				int featnum;
				
				// this is kinda a hack, put it in both directions for every edge. we could use smarter data structures rather than the full matrix of edge featvecs to represent this more compactly.
				
				ff = U.sf("%s::ashead_%s", featname, labelStr);
				featnum = featVocab.num(ff);
				if (featnum!=-1) {
					for (int j=0; j<ns.T; j++) {
						if (badDistance(i,j)) continue;
						ns.add(i,j, featnum, labelID, value);
					}
				}
				
				ff = U.sf("%s::aschild_%s", featname, labelStr);
				featnum = featVocab.num(ff);
				if (featnum!=-1) {
					for (int j=0; j<ns.T; j++) {
						if (badDistance(j,i)) continue;
						ns.add(j,i, featnum, labelID, value);
					}
				}
				
			}
		}
	}
	
	static class EdgeFeatAdder extends FE.FeatureAdder {
		int i=-1, j=-1, labelID=-1;
		NumberizedSentence ns;
		// these are only for debugging
		InputAnnotatedSentence is;
		int[][] goldEdgeMatrix;
		
		@Override
		public void add(String featname, double value) {
			int featnum = featVocab.num(featname);
			if (featnum==-1) return;
			ns.add(i,j, featnum, labelID, value);
			
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
//				totalPairs++;
				
				for (adder2.labelID=0; adder2.labelID<labelVocab.size(); adder2.labelID++) {
					ns.add(adder2.i, adder2.j, labelBiasFeatnum(adder2.labelID), adder2.labelID, 1.0);
					String label = labelVocab.name(adder2.labelID);
					for (FE.FeatureExtractor fe : allFE) {
						if (fe instanceof FE.EdgeFE) {
							((FE.EdgeFE) fe).features(adder2.i, adder2.j, label, adder2);
						}
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
		featVocab.lock();
		labelVocab.lock();
		coefs = new double[featVocab.size()];
	}
	static int labelBiasFeatnum(int label) {
		int n = featVocab.num(labelBiasName( label ));
		assert n != -1 : "wtf";
		return n;
	}
	static String labelBiasName(String label) {
		return "LABELBIAS::" + label;
	}
	static String labelBiasName(int label) {
		return labelBiasName(labelVocab.name(label));
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
//		assert featVocab.size() == coefs.length; // No more!
		assert featVocab.size() <= coefs.length;
		for (int f=0; f<featVocab.size(); f++) {
			out.printf("C\t%s\t%g\n", featVocab.name(f), coefs[f]);
		}
		writer.close();
	}
	static void loadModel(String modelFile) throws IOException {
		// labelVocab, argfeats
		labelVocab = new Vocabulary();
		featVocab = new Vocabulary();
		
		BufferedReader reader = BasicFileIO.openFileOrResource(modelFile);
		String line;
		
		ArrayList<Pair<Integer,Double>> coefTuples = new ArrayList<>(); 

		while ( (line = reader.readLine()) != null ) {
			String[] parts = line.split("\t");
			if (parts[0].equals("LABELVOCAB")) {
				String[] labels = parts[1].trim().split(" ");
				for (String x : labels) labelVocab.num(x);
			}
			else if (parts[0].equals("C")) {
				String featname = parts[1];
				double value = Double.parseDouble(parts[2]);
				int featnum = featVocab.num(featname);
				coefTuples.add(U.pair(featnum, value));
			}
			else { throw new RuntimeException("bad model line format"); }
		}

		lockdownVocabAndAllocateCoefs();
		
		for (Pair<Integer,Double> x : coefTuples) {
			coefs[x.first] = x.second;
		}
		reader.close();
		
		U.pf("Label vocab (size %d): %s\n", labelVocab.size(), labelVocab.names());
		U.pf("Num features: %d\n", featVocab.size());
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
    		
        	if (outer % 5 == 0) saveModel(U.sf("%s.iter%s",modelFilePrefix, outer));
    		
    		if (outer==0) {
    			closeCacheAfterWriting();
    		}
    		
    		if (outer==0) U.pf("%d features, %d nnz\n", featVocab.size(), NumberizedSentence.totalNNZ);
    	}
    }

    
	static double[] ssGrad;
	static double learningRate = .1;
	
    static void growCoefsIfNecessary() {
    	assert coefs==null&&ssGrad==null || coefs.length==ssGrad.length;
    	if (coefs==null) {
    		coefs = new double[Math.max(10000, featVocab.size())];
    		ssGrad = new double[Math.max(10000, featVocab.size())];
    	}
    	else if (featVocab.size() > coefs.length) {
    		int newlen = (int) Math.ceil(1.2 * featVocab.size());
            coefs = NumberizedSentence.growToLength(coefs, newlen);
            ssGrad = NumberizedSentence.growToLength(ssGrad, newlen);
            assert coefs.length==ssGrad.length;
//            U.pf("GROW COEFS TO %d\n", coefs.length);
        }
    }
	
    static double adagradRate(int featnum) {
        if (ssGrad[featnum] < 1e-2) return 1.0/Math.sqrt(1e-2);
        return 1.0 / Math.sqrt(ssGrad[featnum]);
    }
    static void adagradStore(int featnum, double g) {
        ssGrad[featnum] += g*g;
    }
    
    /** adagrad: http://www.ark.cs.cmu.edu/cdyer/adagrad.pdf */ 
    static void trainOnlineIter(boolean firstIter) throws FileNotFoundException {

        double ll = 0;
        for (int snum=0; snum<inputSentences.length; snum++) {
        	U.pf(".");
            
            NumberizedSentence ns = getNextExample(snum);
            if (firstIter) {
                growCoefsIfNecessary();
            }
            int[][] edgeMatrix = graphMatrixes.get(snum);
            
            double[][][] probs = inferEdgeProbs(ns);
            
            for (int kk=0; kk<ns.nnz; kk++) {
                int i=ns.i(kk), j=ns.j(kk);
                double w = edgeMatrix[i][j]==0 ? noedgeWeight : 1.0;
                int observed = edgeMatrix[i][j] == ns.label(kk) ? 1 : 0;
                double resid = observed - probs[i][j][ns.label(kk)];
                double g = w * resid * ns.value(kk);
                
                adagradStore(ns.featnum(kk), g);
                double rate = adagradRate(ns.featnum(kk));

                coefs[ns.featnum(kk)] += learningRate * rate * g;
            }
            
            // loglik is completely unnecessary for optimization, just nice for diagnosis.
            // comment this out for 5% speed gain
            for (int i=0;i<ns.T;i++) {
                for (int j=0; j<ns.T;j++) {
                    if (badDistance(i,j)) continue;
                    double w = edgeMatrix[i][j]==0 ? noedgeWeight : 1.0;
                    ll += w * Math.log(probs[i][j][edgeMatrix[i][j]]);
                }
            }
        }
        //  logprior  =  - (1/2) lambda || beta ||^2
        //  gradient =  - lambda beta
        for (int f=0; f<coefs.length; f++) {
            ll -= 0.5 * l2reg * coefs[f]*coefs[f];
            double g = l2reg * coefs[f];
            adagradStore(f,g);
            coefs[f] -= adagradRate(f) * learningRate * g;
        }
        U.pf("ll %.1f  ", ll);
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
		allFE.add(new BasicFeatures());
	}

}
