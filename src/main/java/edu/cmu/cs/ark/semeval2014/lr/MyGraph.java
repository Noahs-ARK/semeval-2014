package edu.cmu.cs.ark.semeval2014.lr;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import util.Arr;
import util.Vocabulary;
import util.misc.Triple;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

class MyGraph {
	boolean[] isChildOfSomething;
	boolean[] isPred;
	List< Triple<Integer,Integer,String> > edgelist;
	String[][] edgeMatrix;
	MyGraph(int sentenceLength, List<Triple<Integer, Integer, String>> _edgelist) {
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

	static MyGraph decodeEdgeProbsToGraph(InputAnnotatedSentence sent, double[][][] probs, Vocabulary labelVocab) {
		final List<Triple<Integer,Integer,String>> edgeList = new ArrayList<>();
		for (int i = 0; i < sent.size(); i++) {
			for (int j = 0; j < sent.size(); j++) {
				if (LRParser.badDistance(i, j)) continue;
				int predLabel = Arr.argmax(probs[i][j]);
				Triple<Integer,Integer,String> tt = new Triple<>(i, j, labelVocab.name(predLabel));
				if (tt.third.equals(LRParser.NO_EDGE)) continue;
				edgeList.add(tt);
			}
		}
		return new MyGraph(sent.size(), edgeList);
	}

	void print(PrintWriter out, InputAnnotatedSentence sent) {
		out.println("#" + sent.sentenceId());
		for (int i = 0; i < sent.size(); i++) {
			boolean isTop = !isChildOfSomething[i] && isPred[i];
			out.printf("%d\t%s\tlemmaz\t%s\t%s\t%s", i + 1, sent.sentence()[i], sent.pos()[i],
					isTop ? "+" : "-", isPred[i] ? "+" : "-");
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
