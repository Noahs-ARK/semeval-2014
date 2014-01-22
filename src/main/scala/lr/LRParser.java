package lr;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import lr.fe.FE;
import lr.fe.WordFormFE;
import lr.fe.FE.FeatureAdder;

import sdp.graph.Graph;
import sdp.io.GraphReader;
import util.U;
import util.Vocabulary;
import util.misc.Pair;
//import edu.cmu.cs.ark.semeval2014.common.FeatureExtractor1;
//import edu.cmu.cs.ark.semeval2014.common.FeatureExtractor2;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;

public class LRParser {
	
	// 1. Data structures
	static Graph[] graphs;
	static InputAnnotatedSentence[] inputSentences = null;
	static NumberizedSentence[] nbdSentences = null;
	
	// 2. Feature system and model parameters
	static List<FE.FeatureExtractor1> allFE1 = new ArrayList<>();
	static List<FE.FeatureExtractor2> allFE2 = new ArrayList<>();
	static Vocabulary predfeatVocab = new Vocabulary();
	static Vocabulary argfeatVocab = new Vocabulary();
	// predicate identification is binary logreg
	static double[] predCoefs;  // length = predfeat vocab
	static double predBias;
	// arg identification is multiclass logreg, in flattened form
	static double[] argCoefs;  // length = argfeat vocab
	static double argBias;

	// 3. Runtime options
	static boolean verboseFeatures = false;
	
	
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
			
			U.pf("Reading graphs from %s\n", sdpFile);
			ArrayList<Graph> graphsAL = new ArrayList<>();
	        GraphReader reader = new GraphReader(sdpFile);
	        Graph graph;
	        while ((graph = reader.readGraph()) != null) {
	        	U.pf("id %s\n", graph.id);
	        	graphsAL.add(graph);
	        }
	        reader.close();
	        
	        graphs = graphsAL.toArray(new Graph[0]);
	        
	        assert graphs.length == inputSentences.length;
	        for (int i=0; i<graphs.length; i++) {
		        // TODO check IDs that arrays really are parallel	        	
	        }
	        
		}

		// Feature extraction
		
		if (mode.equals("train")) {
			predfeatVocab = new Vocabulary();
			argfeatVocab = new Vocabulary();
			extractFeatures();
			trainLoop();
			writeModel(modelFile);
		}
		else if (mode.equals("test")) {
			readModel(modelFile);
			extractFeatures();
			U.pf("Writing predictions to %s\n", sdpFile);
			makePredictions(sdpFile);
		}
	}
	
	static void extractFeatures() throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		nbdSentences = new NumberizedSentence[inputSentences.length];
		for (int i=0; i<inputSentences.length; i++) {
			nbdSentences[i] = extractFeatures(inputSentences[i]);
		}
	}
	static int size(InputAnnotatedSentence s) { 
		return s.sentence().length;
	}
	static NumberizedSentence extractFeatures(final InputAnnotatedSentence is) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		final NumberizedSentence ns = new NumberizedSentence( size(is) );
		
		final int[] i = new int[]{ 0 };   // workaround stupid java closure rules that need 'final'
		for (; i[0] < ns.T; i[0]++) {
			FeatureAdder callback = new FE.FeatureAdder() {
				@Override
				public void add(String featname, double value) {
					if (verboseFeatures) {
						U.pf("WORD %s\tPREDFEAT %s %s\n", is.sentence()[i[0]], featname, value);
					}
					int featnum = predfeatVocab.num(featname);
					ns.predicateFeatures[i[0]].add(Pair.makePair(featnum, value));
				}
			};
			for (FE.FeatureExtractor1 fe : allFE1) {
				fe.features(is, i[0], callback);
			}
		}
		return ns;
	}
	
	static void writeModel(String modelFile) {
		
	}
	static void readModel(String modelFile) {
	}
	
	static void trainLoop() {
		assert false : "todo";
	}
	static void makePredictions(String outputFile) {
		assert false : "todo";
	}
	
	
	///////////////////////////////////////////////////////////
	
	static void initializeFeatureExtractors() {
		allFE1.add(new WordFormFE());
	}

}
