package lr;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
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
import sdp.io.GraphWriter;
import util.Arr;
import util.BasicFileIO;
import util.LBFGS;
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
	static double noedgeWeight = 0.3;
	
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

		if (mode.equals("train")) {
			U.pf("Feature extraction "); System.out.flush();
			featVocab = new Vocabulary();
			for (int k=0; k<labelVocab.size(); k++) {
				featVocab.num(labelBiasName(k));
			}
			extractFeatures(true);
			lockdownVocabAndAllocateCoefs();
			trainLoop();
			saveModel(modelFile);
		}
		else if (mode.equals("test")) {
			loadModel(modelFile);
			U.pf("Writing predictions to %s\n", sdpFile);
			makePredictions(sdpFile);
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

//				// store log-linear scores
//				for (int k=0; k<labelVocab.size(); k++) {
//					scores[i][j][k] += coefs[labelBiasFeatnum(k)];
//				}
				
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
	
	static void extractFeatures(boolean overcomplete) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		nbdSentences = new NumberizedSentence[inputSentences.length];
		for (int i=0; i<inputSentences.length; i++) {
			U.pf(".");
			nbdSentences[i] = extractFeatures(inputSentences[i], overcomplete, 
					graphMatrixes!=null ? graphMatrixes.get(i) : null
//					overcomplete ? null : graphMatrixes.get(i)
					);
		}
	}

	/** 
	 * if overcomplete, extracts for ALL possible edge labels. Give null for the goldEdgeMatrix then.
	 * if not overcomplete, only extracts 
	 */
	static NumberizedSentence extractFeatures(final InputAnnotatedSentence is, 
			boolean overcomplete, final int[][] goldEdgeMatrix
	) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		final NumberizedSentence ns = new NumberizedSentence( size(is) );
		
		// workaround stupid java closure rules that need 'final'
		final int[] i = new int[]{0};
		final int[] j = new int[]{0};
		final int[] labelID = new int[]{-1};
		
		for (i[0]=0; i[0]<ns.T; i[0]++) {
			for (j[0]=0; j[0]<ns.T; j[0]++) {
				
				if (badDistance(i[0],j[0])) continue;
				
				FeatureAdder callbackEdge = new FE.FeatureAdder() {
					@Override
					public void add(String featname, double value) {
//						featname = "EDGE::" + featname;
						if (verboseFeatures) {
							U.pf("WORDS %s:%d -> %s:%d\tGOLD %s\tEDGEFEAT %s %s\n", 
									is.sentence()[i[0]], i[0],
									is.sentence()[j[0]], j[0],
									goldEdgeMatrix!=null ? labelVocab.name(goldEdgeMatrix[i[0]][j[0]]) : null,
									featname, value);
						}
						int featnum = featVocab.num(featname);
						if (featnum==-1) return;
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
					assert false : "disable this";
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
		U.pf("Num features: %d\n", coefs.length);
	}
	
	
	static void trainLoop() {
		U.pf("Starting training with\n");
		U.pf("Label vocab (size %d): %s\n", labelVocab.size(), labelVocab.names());
		U.pf("Num features: %d\n", coefs.length);

		double initcoefs[] = new double[featVocab.size()];
		LBFGS.Result r = LBFGS.lbfgs(initcoefs, numTrainIters, new LBFGS.Function() {

			@Override
			public double evaluate(double[] newCoefs, double[] grad, int n, double step) {
				Arr.fill(grad,0);  // gonna go in ascent direction, flip only at end
				coefs = Arr.copy(newCoefs);
				double ll = 0;
				for (int snum=0; snum<inputSentences.length; snum++) {
					NumberizedSentence ns = nbdSentences[snum];
					int[][] edgeMatrix = graphMatrixes.get(snum);
					
					double[][][] probs = inferEdgeProbs(ns);
					
					for (int i=0;i<ns.T;i++) {
						for (int j=0; j<ns.T;j++) {
							if (badDistance(i,j)) continue;
							assert Math.abs( Arr.sum(probs[i][j])  - 1 ) < 1e-5;
							double w = edgeMatrix[i][j]==0 ? noedgeWeight : 1.0;
							ll += w * Math.log(probs[i][j][edgeMatrix[i][j]]);
							
//							if (Arr.max(probs[i][j]) < 0.9999) U.p(probs[i][j]);
							for (FVItem fvItem : ns.argFeatures[i][j]) {
								int observed = edgeMatrix[i][j] == fvItem.label ? 1 : 0;
								double resid = observed - probs[i][j][fvItem.label];
								grad[fvItem.featnum] += w * resid * fvItem.value;
							}
						}
					}
				}
				//  logprior  =  - (1/2) lambda || beta ||^2
				//  gradient =  - lambda beta
				for (int f=0; f<coefs.length; f++) {
					ll -= 0.5 * l2reg * coefs[f]*coefs[f];
					grad[f] -= l2reg * coefs[f];
				}
				Arr.multiplyInPlace(grad, -1);
				return -ll;
			}
		});
		
		U.pf("LBFGS status %s\n", r.status);
		coefs = initcoefs;
	}
	
	static void makePredictions(String outputFile) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, IOException {
		BufferedWriter bw = BasicFileIO.openFileToWriteUTF8(outputFile);
		PrintWriter out = new PrintWriter(bw);
//		GraphWriter gw = new GraphWriter(out);

		for (int snum=0; snum<inputSentences.length; snum++) {
			InputAnnotatedSentence sent = inputSentences[snum];
			NumberizedSentence ns = extractFeatures(sent, true, null);
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
