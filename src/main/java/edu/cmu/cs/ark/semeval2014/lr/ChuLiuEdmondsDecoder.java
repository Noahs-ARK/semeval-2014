// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr;

import edu.cmu.cs.ark.cle.ChuLiuEdmonds;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.prune.PcedtPruner;
import sdp.graph.Graph;
import util.Arr;
import util.Vocabulary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static java.lang.Double.NEGATIVE_INFINITY;

public class ChuLiuEdmondsDecoder {
    private static final double SINGLETON_THRESHOLD = .5;

    public static MyGraph decodeEdgeProbsToGraph(InputAnnotatedSentence sent,
                                                 double[][][] probs,
                                                 Vocabulary labelVocab) {
        final int noEdgeID = labelVocab.num(LRParser.NO_EDGE);
        assert noEdgeID == 0;
        final List<Integer> unprunedNodes = new ArrayList<>();
        for (int i = 0; i < sent.size(); i++) {
            if (sent.singletonPredProbs[i] < SINGLETON_THRESHOLD) {
                unprunedNodes.add(i);
            }
        }
        final int n = unprunedNodes.size();
        final double[] topness = new double[n];
        for (int i = 0; i < n; i++) {
            topness[i] = LRParser.topClassifier.topness(sent, unprunedNodes.get(i));
        }
        int top = Arr.argmax(topness);
        final double[][] bestEdgeWeights = new double[n][n];
        final int[][] bestEdgeLabels = new int[n][n];
        // throw out all but the best edge between any two nodes
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                final int src = unprunedNodes.get(i);
                final int dest = unprunedNodes.get(j);
                if (LRParser.badPair(sent, src, dest)) {
                    bestEdgeLabels[i][j] = noEdgeID;
                    bestEdgeWeights[i][j] = NEGATIVE_INFINITY;
                } else {
                    // only consider actual edges (not NO_EDGE)
                    final double[] realEdgeProbs = Arrays.copyOfRange(probs[src][dest], 1, probs[src][dest].length);
                    final int bestLabel = Arr.argmax(realEdgeProbs) + 1;
                    bestEdgeLabels[i][j] = bestLabel;
                    bestEdgeWeights[i][j] = Math.log(probs[src][dest][bestLabel]);
//                    bestEdgeWeights[i][j] = probs[src][dest][bestLabel];
                }
            }
        }
        final Map<Integer, Integer> maxSpanningTree = ChuLiuEdmonds.getMaxSpanningTree(bestEdgeWeights, top).val;
        final Graph g = convertToGraph(maxSpanningTree, sent, unprunedNodes, bestEdgeLabels, labelVocab);
        final MyGraph myGraph = MyGraph.fromGraph(PcedtPruner.postProcess(g));
        myGraph.isTop = new boolean[sent.size()];
        myGraph.isTop[unprunedNodes.get(top)] = true;
        return myGraph;
    }

    private static Graph convertToGraph(Map<Integer, Integer> maxSpanningTree,
                                        InputAnnotatedSentence sent,
                                        List<Integer> unprunedNodes,
                                        int[][] bestEdgeLabels,
                                        Vocabulary labelVocab) {
        final Graph g = new Graph(sent.sentenceId);
        for (int i = 0; i < sent.size(); i++) g.addNode("", "", "", false, false);
        for (int child : maxSpanningTree.keySet()) {
            final int parent = maxSpanningTree.get(child);
            g.addEdge(
                    unprunedNodes.get(parent),
                    unprunedNodes.get(child),
                    labelVocab.name(bestEdgeLabels[parent][child])
            );
        }
        return g;
    }
}
