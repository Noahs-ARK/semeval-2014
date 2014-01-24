package edu.cmu.cs.ark.semeval2014.lr;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import edu.cmu.cs.ark.semeval2014.lr.fe.FE;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.lr.fe.WordFormFE;
import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.io.GraphReader;
import util.Arr;
import util.BasicFileIO;
import util.LBFGS;
import util.Timer;
import util.U;
import util.Vocabulary;
import util.misc.Pair;
import util.misc.Triple;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;
//import edu.cmu.cs.ark.semeval2014.common.FeatureExtractor1;
//import edu.cmu.cs.ark.semeval2014.common.FeatureExtractor2;

public class LRParser {
	
	// 1. Data structures
	static Graph[] graphs;
	static InputAnnotatedSentence[] inputSentences = null; // full dataset
	static ArrayList<int[][]> graphMatrixes = null;  // full dataset
	
	// 'mb' prefix means it only contains items for the current minibatch.
	static ArrayList<NumberizedSentence> mbNumberizedSentences = null;
	static ArrayList<int[][]> mbGraphMatrixes = null;

	
	
	// 2. Feature system and model parameters
//	static List<FE.FeatureExtractor1> allFE1 = new ArrayList<>();
	static List<FE.FeatureExtractor2> allFE2 = new ArrayList<>();
	static Vocabulary labelVocab = new Vocabulary();
	static Vocabulary featVocab;
	static double[] coefs; // ok let's do the giant flattened form. DO NOT USE coefs.length IT IS CAPACITY NOT FEATURE CARDINALITY

	
	// 3. Model parameter-ish options
	static int maxEdgeDistance = 10;
	static double l2reg = .01;  // this is per-sentence scaled
	static double noedgeWeight = 0.3;
	
	// 4. Runtime options
	static boolean verboseFeatures = false;
	static int numMBInnerIters = 30;  // iterations within a minibatch
	static int numMBOuterIters = 2;  // iterations over entire dataset
	static int numOnlineIters = 20;
	static int minibatchSize = 100;  // number of sentences within a minibatch (to be loaded into memory at once)
	
	
	
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

		double t0,dur;
		if (mode.equals("train")) {
			featVocab = new Vocabulary();
			for (int k=0; k<labelVocab.size(); k++) {
				featVocab.num(labelBiasName(k));
			}
			
//					t0 = System.currentTimeMillis();
//			extractFeaturesForAll();
//					dur = System.currentTimeMillis() - t0;
//					U.pf("\nFE TIME %.1f sec,  %.1f ms/sent\n", dur/1e3, dur/inputSentences.length);
//			lockdownVocabAndAllocateCoefs();
			
					t0 = System.currentTimeMillis();
			trainingOuterloopOnline(modelFile);
//			trainingOuterloopMB();
					dur = System.currentTimeMillis() - t0;
					U.pf("TRAINLOOP TIME %.1f sec\n", dur/1e3);

			saveModel(modelFile);
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
	static boolean badDistance(int i, int j) {
		return i==j || Math.abs(i-j) > maxEdgeDistance;
	}
	
	static int totalPairs = 0;
	
	static void extractFeaturesForAll() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		mbNumberizedSentences = new ArrayList<>();
		for (int i=0; i<inputSentences.length; i++) {
			U.pf(".");
			mbNumberizedSentences.add( extractFeatures(i) );
		}
		U.pf("\n");
		if (NumberizedSentence.totalNNZ > 0) {
			U.pf("Total NNZ %d\n", NumberizedSentence.totalNNZ);
		}
		if (totalPairs > 0) {
			U.pf("Total pairs %d\n", totalPairs);
		}
	}
	
	static class FullAdder extends FE.FeatureAdder {
		int i=-1, j=-1, labelID=-1;
		NumberizedSentence ns;
		
		@Override
		public void add(String featname, double value) {
//			if (verboseFeatures) {
//				U.pf("WORDS %s:%d -> %s:%d\tGOLD %s\tEDGEFEAT %s %s\n", 
//						is.sentence()[i[0]], i[0], is.sentence()[j[0]], j[0],
//						goldEdgeMatrix!=null ? labelVocab.name(goldEdgeMatrix[i[0]][j[0]]) : null,
//						featname, value);
//			}
			int featnum = featVocab.num(featname);
			if (featnum==-1) return;
			ns.add(i,j, featnum, labelID, value);
		}
	}
	
//	static class LabelConjAdder extends FE.FeatureAdder {
//		int i=-1, j=-1;
//		NumberizedSentence ns;
//		@Override
//		public void add(String featname, double value) {
//			int featnum = featVocab.num(featname);
//			if (featnum==-1) return;
//			for (int label=0; label<labelVocab.size(); label++) {
//				assert false : "wrong";
//				ns.argFeatures[i][j].add(new FVLItem(featnum, label, value));	
//			}
//		}
//	}

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
	
	/**
	 * goldEdgeMatrix is only for feature extractor debugging verbose reports 
	 */
	static NumberizedSentence extractFeatures(
			InputAnnotatedSentence is, int[][] goldEdgeMatrix
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		NumberizedSentence ns = new NumberizedSentence( size(is) );
		FullAdder adder = new FullAdder();
		adder.ns=ns;
		
		for (FE.FeatureExtractor2 fe : allFE2) {
			fe.setupSentence(is);
		}
		
		for (adder.i=0; adder.i<ns.T; adder.i++) {
			for (adder.j=0; adder.j<ns.T; adder.j++) {
				
				if (badDistance(adder.i,adder.j)) continue;
				totalPairs++;
				
				for (adder.labelID=0; adder.labelID<labelVocab.size(); adder.labelID++) {
					ns.add(adder.i, adder.j, labelBiasFeatnum(adder.labelID), adder.labelID, 1.0);
					String label = labelVocab.name(adder.labelID);
					for (FE.FeatureExtractor2 fe : allFE2) {
						fe.features(adder.i, adder.j, label, adder);
					}
				}
			}
		}
		return ns;
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
    		trainOnlineIter(outer==0);
    		saveModel(U.sf("%s.iter%s",modelFilePrefix, outer+1));
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
    static void trainOnlineIter(boolean firstIter) {
    	double t0=System.currentTimeMillis();
        double ll = 0;
        for (int snum=0; snum<inputSentences.length; snum++) {
        	U.pf(".");
            
            NumberizedSentence ns = extractFeatures(snum);
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
        U.pf("ll %.1f\n", ll);
        if (firstIter) {
        	U.pf("%d features\n", featVocab.size());
        	double dur = System.currentTimeMillis() - t0;
        	U.pf("%.1f sec, %.1f ms/sent\n", dur/1000, dur/inputSentences.length);
        }
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
		ArrayList< Triple<Integer,Integer,String> > edgelist;
		String[][] edgeMatrix;
	}
	static MyGraph decodeEdgeprobsToGraph(InputAnnotatedSentence sent, double[][][] probs) {
//		Graph g = new Graph("#"+sent.sentenceId());
		MyGraph g = new MyGraph();
		g.isChildOfSomething = new boolean[size(sent)];
		g.isPred = new boolean[size(sent)];
		
		g.edgelist = new ArrayList<>();
		g.edgeMatrix = new String[size(sent)][size(sent)];
		for (int i=0; i<size(sent); i++) {
			for (int j=0; j<size(sent); j++) {
				if (badDistance(i,j)) continue;
				int predlabel = Arr.argmax(probs[i][j]);
				Triple<Integer,Integer,String> tt = new Triple(i,j,labelVocab.name(predlabel));
				if (tt.third.equals("NOEDGE")) continue;
				
				g.edgelist.add(tt);
				g.edgeMatrix[i][j] = tt.third;
				g.isPred[i] = true;
				g.isChildOfSomething[j] = true;
			}
		}
		
		return g;
	}
	
	///////////////////////////////////////////////////////////
	
	static void initializeFeatureExtractors() {
		allFE2.add(new WordFormFE());
	}

}
