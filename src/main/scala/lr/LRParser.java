package lr;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import lr.fe.FE;
import lr.fe.FE.FeatureAdder;
import lr.fe.WordFormFE;
import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.io.GraphReader;
import util.Arr;
import util.BasicFileIO;
import util.LBFGS;
import util.U;
import util.Vocabulary;
import util.misc.Pair;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;
//import edu.cmu.cs.ark.semeval2014.common.FeatureExtractor1;
//import edu.cmu.cs.ark.semeval2014.common.FeatureExtractor2;

public class LRParser {
	
	// 1. Data structures
	static Graph[] graphs;
	static InputAnnotatedSentence[] inputSentences = null;
	static NumberizedSentence[] nbdSentences = null;
	static ArrayList<int[][]> graphMatrixes = null;
	
	// 2. Feature system and model parameters
	static List<FE.FeatureExtractor1> allFE1 = new ArrayList<>();
	static List<FE.FeatureExtractor2> allFE2 = new ArrayList<>();
	static Vocabulary labelVocab = new Vocabulary();
	static Vocabulary featVocab;
	static double[] coefs; // ok let's do the giant flattened form.
	
	// 3. Model parameter-ish options
	static int maxEdgeDistance = 10;
	static double l2reg = 1.0;
	
	// 4. Runtime options
	static boolean verboseFeatures = false;
	static int numTrainIters = 30;
	
	
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
			labelVocab.num("NO_EDGE");
			
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
		        // TODO check IDs that arrays really are parallel
	        	InputAnnotatedSentence sent = inputSentences[snum];
	        	graph = graphs[snum];
	        	assert sent.sentenceId().equals(graph.id.replace("#",""));

	        	int[][] edgeMatrix = new int[size(sent)][size(sent)];
	        	for (int i=0; i<size(sent); i++) {
	        		for (int j=0; j<size(sent); j++) {
	        			edgeMatrix[i][j] = labelVocab.num("NO_EDGE");
	        		}
	        	}
	        	for (Edge e : graphs[snum].getEdges()) {
	        		edgeMatrix[e.source][e.target] = labelVocab.num(e.label);
	        	}
	        	graphMatrixes.add(edgeMatrix);
	        }
	        
		}
		
		U.pf("%d input sentences\n", inputSentences.length);

		// Feature extraction
		
		initializeFeatureExtractors();

		if (mode.equals("train")) {
			U.pf("Feature extraction "); System.out.flush();
			featVocab = new Vocabulary();
			for (int k=0; k<labelVocab.size(); k++) {
				featVocab.num(labelBiasName(k));
			}
			extractFeatures(false);
			lockdownVocabAndAllocateCoefs();
			trainLoop();
			saveModel(modelFile);
		}
		else if (mode.equals("test")) {
			loadModel(modelFile);
			extractFeatures(true);
			U.pf("Writing predictions to %s\n", sdpFile);
			makePredictions(sdpFile);
		}
	}
	
	static void extractFeatures(boolean overcomplete) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		nbdSentences = new NumberizedSentence[inputSentences.length];
		for (int i=0; i<inputSentences.length; i++) {
			U.pf(".");
			nbdSentences[i] = extractFeatures(inputSentences[i], overcomplete, 
					overcomplete ? null : graphMatrixes.get(i));
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
		for (int i=0; i<ns.T; i++) {
			for (int j=0; j<ns.T; j++) {
				
				if (badDistance(i,j)) continue;

				// store log-linear scores
				for (int k=0; k<labelVocab.size(); k++) {
					scores[i][j][k] += coefs[featVocab.num(labelBiasName(k))]; 
				}
				
				for (FVItem fvItem : ns.argFeatures[i][j]) {
					scores[i][j][fvItem.label] += coefs[fvItem.featnum] * fvItem.value;
				}
				
				// transform in-place into probs
				Arr.softmaxInPlace(scores[i][j]);
			}
		}
		return scores;
	}
	static boolean badDistance(int i, int j) {
		return i==j || Math.abs(i-j) > maxEdgeDistance;
	}
	/** 
	 * if overcomplete, extracts for ALL possible edge labels. Give null for the goldEdgeMatrix then.
	 * if not overcomplete, only extracts 
	 */
	static NumberizedSentence extractFeatures(final InputAnnotatedSentence is, 
			boolean overcomplete, int[][] goldEdgeMatrix
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		final NumberizedSentence ns = new NumberizedSentence( size(is) );
		
		// workaround stupid java closure rules that need 'final'
		final int[] i = new int[]{0};
		final int[] j = new int[]{0};
		final int[] labelID = new int[]{-1};
		
		for (i[0]=0; i[0]<ns.T; i[0]++) {
			for (j[0]=0; j[0]<ns.T; j[0]++) {
				
				if (i[0]==j[0]) continue;
				if (badDistance(i[0],j[0])) continue;
				
				FeatureAdder callbackEdge = new FE.FeatureAdder() {
					@Override
					public void add(String featname, double value) {
//						featname = "EDGE::" + featname;
						if (verboseFeatures) {
							U.pf("WORDS %s:%d -> %s:%d\tEDGEFEAT %s %s\n", 
									is.sentence()[i[0]], i[0],
									is.sentence()[j[0]], j[0],
									featname, value);
						}
						int featnum = featVocab.num(featname);
						ns.argFeatures[i[0]][j[0]].add(new FVItem(featnum, labelID[0], value));
					}
				};
				
				if (overcomplete) {
					for (labelID[0]=0; labelID[0]<labelVocab.size(); labelID[0]++) {
						ns.argFeatures[i[0]][j[0]].add(new FVItem(labelBiasFeatnum(labelID[0]), labelID[0], 1.0));

						String label = labelVocab.name(labelID[0]);
						for (FE.FeatureExtractor2 fe : allFE2) {
							fe.features(is, i[0], j[0], label, callbackEdge);
						}
					}
				}
				else {
					// look at gold data
					labelID[0] = goldEdgeMatrix[i[0]][j[0]];
					ns.argFeatures[i[0]][j[0]].add(new FVItem(labelBiasFeatnum(labelID[0]), labelID[0], 1.0));

					for (FE.FeatureExtractor2 fe : allFE2) {
						fe.features(is, i[0], j[0], labelVocab.name(goldEdgeMatrix[i[0]][j[0]]), callbackEdge);
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
		return featVocab.num(labelBiasName( label ));
	}
	static String labelBiasName(String label) {
		return "LABELBIAS::" + label;
	}
	static String labelBiasName(int label) {
		return labelBiasName(labelVocab.name(label));
	}
	
	static void saveModel(String modelFile) throws IOException {
		BufferedWriter writer = BasicFileIO.openFileToWriteUTF8(modelFile);
		PrintWriter out = new PrintWriter(writer);

		out.append("LABELVOCAB\t");
		for (String x : labelVocab.names()) {
			out.append(x + " ");
		}
		out.append("\n");
		assert featVocab.size() == coefs.length;
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
				String[] labels = parts[2].trim().split(" ");
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
		U.pf("Num features: %d\n", coefs.length);
	}
	
	
	static void trainLoop() {
		U.pf("Starting training with\n");
		U.pf("Label vocab (size %d): %s\n", labelVocab.size(), labelVocab.names());
		U.pf("Num features: %d\n", coefs.length);

		LBFGS.lbfgs(new double[featVocab.size()], numTrainIters, new LBFGS.Function() {

			@Override
			public double evaluate(double[] newCoefs, double[] grad, int n, double step) {
				Arr.fill(grad,0);
				coefs = Arr.copy(newCoefs);
				// first do it in positive loglik, flip only at end
				
				double ll = 0;
				for (int snum=0; snum<inputSentences.length; snum++) {
					NumberizedSentence ns = nbdSentences[snum];
					int[][] edgeMatrix = graphMatrixes.get(snum);
					
					double[][][] probs = inferEdgeProbs(ns);
					
					for (int i=0;i<ns.T;i++) {
						for (int j=0; j<ns.T;j++) {
							if (badDistance(i,j)) continue;
							ll += Math.log(probs[i][j][edgeMatrix[i][j]]);
							
//							if (Arr.max(probs[i][j]) < 0.9999) U.p(probs[i][j]);
							for (FVItem fvItem : ns.argFeatures[i][j]) {
								int observed = edgeMatrix[i][j] == fvItem.label ? 1 : 0;
								double resid = observed - probs[i][j][fvItem.label];
								grad[fvItem.featnum] += resid * fvItem.value;
							}
						}
					}
				}
				Arr.multiplyInPlace(grad, -1);
				for (int f=0; f<coefs.length; f++) {
					double ss = l2reg * coefs[f]*coefs[f];
					ll -= 0.5*ss;
					grad[f] -= l2reg * coefs[f];
				}
				return -ll;
			}
		});
//		assert false : "todo";
	}
	
	static void makePredictions(String outputFile) {
//		assert false : "todo";
	}
	
	
	///////////////////////////////////////////////////////////
	
	static void initializeFeatureExtractors() {
		allFE2.add(new WordFormFE());
	}

}
