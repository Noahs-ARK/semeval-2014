package edu.cmu.cs.ark.semeval2014.lr;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import util.Arr;
import util.Vocabulary;
import util.misc.Triple;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** ok, decoding logic has bled into this class. */
public class MyGraph {
	boolean[] isChildOfSomething;
	boolean[] isPred;
	boolean[] isTop;
	
	List< Triple<Integer,Integer,String> > edgelist;
	String[][] edgeMatrix;
	MyGraph(int sentenceLength, List<Triple<Integer, Integer, String>> _edgelist) {
		edgelist = _edgelist;
		isChildOfSomething = new boolean[sentenceLength];
		isPred = new boolean[sentenceLength];
		isTop = new boolean[sentenceLength];
		edgeMatrix = new String[sentenceLength][sentenceLength];
		for (Triple<Integer,Integer,String> tt : _edgelist) {
			int i=tt.first, j=tt.second;
			edgeMatrix[i][j] = tt.third;
			isPred[i] = true;
			isChildOfSomething[j] = true;
		}
	}
	
	public static void decideTopsStupid(MyGraph g, InputAnnotatedSentence sent) {
		for (int i=0; i<g.isPred.length; i++) {
			g.isTop[i] = !g.isChildOfSomething[i] && g.isPred[i];
		}
	}

	public static void decideTops(MyGraph g, InputAnnotatedSentence sent) {
		
		Arr.fill(g.isTop, false);
		
		int n = g.isPred.length;
		double[] topnesses = new double[n];
		
		for (int i=0; i<n; i++) {
			if (!g.isPred[i]) {
				topnesses[i] = -1e6;
			}
			else {
				topnesses[i] = LRParser.topClassifier.topness(sent, i);
			}
		}
		int i = Arr.argmax(topnesses);
		g.isTop[i] = true;
	}
	
	public static MyGraph decodeEdgeProbsToGraph(InputAnnotatedSentence sent, double[][][] probs, Vocabulary labelVocab, boolean doPostproc) {
		int noedgeID = labelVocab.num(LRParser.NO_EDGE);
		final List<Triple<Integer,Integer,String>> edgeList = new ArrayList<>();
		for (int i = 0; i < sent.size(); i++) {
			for (int j = 0; j < sent.size(); j++) {
				if (LRParser.badDistance(i, j)) continue;
				int predLabel = Arr.argmax(probs[i][j]);
				if (predLabel==noedgeID) continue;

				if (doPostproc) {
					// single direction consistency
					int labelOtherDir = Arr.argmax(probs[j][i]);
					if (labelOtherDir != noedgeID && Arr.max(probs[j][i]) > Arr.max(probs[i][j])) {
						continue;
					}
				}

				Triple<Integer,Integer,String> tt = new Triple<>(i, j, labelVocab.name(predLabel));
				edgeList.add(tt);
			}
		}
		return new MyGraph(sent.size(), edgeList);
	}
	
	public void print(PrintWriter out, InputAnnotatedSentence sent) {
		out.println("#" + sent.sentenceId);
		for (int i = 0; i < sent.size(); i++) {
			out.printf("%d\t%s\tlemmaz\t%s\t%s\t%s", i + 1, sent.sentence[i], sent.pos[i],
					isTop[i] ? "+" : "-", isPred[i] ? "+" : "-");
			for (int head = 0; head < sent.size(); head++) {
				if (!isPred[head]) continue;
				// ok now we're in a predicate column that may be dominating this node
				String label = edgeMatrix[head][i];
				label = label == null ? "_" : label;
				out.print("\t" + label);
			}
			out.print("\n");
		}
		out.print("\n");
		out.flush();
	}
}
