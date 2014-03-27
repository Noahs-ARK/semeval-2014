package edu.cmu.cs.ark.semeval2014.lr;

import edu.cmu.cs.ark.cle.ChuLiuEdmonds;
import edu.cmu.cs.ark.cle.Pair;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.prune.PcedtPruner;
import sdp.graph.Graph;
import util.Arr;
import util.Vocabulary;
import util.misc.Triple;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Double.NEGATIVE_INFINITY;

/**
 * TODO: add a dummy root node (use weights from "top" predictor?)
 * TODO: prune predicted singletons before running CLE
 * @author sthomson@cs.cmu.edu
 */
public class ChuLiuEdmondsDecoder {
    public static MyGraph decodeEdgeProbsToGraph(InputAnnotatedSentence sent,
                                                 double[][][] probs,
                                                 Vocabulary labelVocab) {
        final Pair<double[][], int[][]> weightsAndLabels = getInputForCle(sent, probs, labelVocab);
        final double[][] bestEdgeWeights = weightsAndLabels.first;
        final int[][] bestEdgeLabels = weightsAndLabels.second;
        final int root = 0; // FIXME
        final Map<Integer, Integer> maxSpanningTree = ChuLiuEdmonds.getMaxSpanningTree(bestEdgeWeights, root).val;
        final List<Triple<Integer, Integer, String>> edgeList =
                convertToEdgeList(labelVocab, bestEdgeLabels, maxSpanningTree);
        final Graph g = new Graph(sent.sentenceId);
        for (int i = 0; i < sent.size(); i++) g.addNode("", "", "", false, false);
        for (Triple<Integer, Integer, String> edge : edgeList) {
            g.addEdge(edge.first, edge.second, edge.third);
        }
        return MyGraph.fromGraph(PcedtPruner.postProcess(g));
    }

    private static List<Triple<Integer, Integer, String>> convertToEdgeList(Vocabulary labelVocab,
                                                                            int[][] bestEdgeLabels,
                                                                            Map<Integer, Integer> maxSpanningTree) {
        final List<Triple<Integer, Integer, String>> edgeList = new ArrayList<>();
        for (int child : maxSpanningTree.keySet()) {
            final int parent = maxSpanningTree.get(child);
            final Triple<Integer, Integer, String> tt =
                    new Triple<>(parent, child, labelVocab.name(bestEdgeLabels[parent][child]));

            edgeList.add(tt);
        }
        return edgeList;
    }

    private static Pair<double[][], int[][]> getInputForCle(InputAnnotatedSentence sent, double[][][] probs, Vocabulary labelVocab) {
        final int noEdgeID = labelVocab.num(LRParser.NO_EDGE);
        final double[][] bestEdgeWeights = new double[sent.size()][sent.size()];
        final int[][] bestEdgeLabels = new int[sent.size()][sent.size()];
        // only consider actual edges (not NO_EDGE)
        for (int i = 0; i < sent.size(); i++) {
            for (int j = 0; j < sent.size(); j++) {
                probs[i][j][noEdgeID] = NEGATIVE_INFINITY;
            }
        }
        // throw out all but the best edge between any two nodes
        for (int i = 0; i < sent.size(); i++) {
            for (int j = 0; j < sent.size(); j++) {
                if (LRParser.badPair(sent, i, j)) {
                    bestEdgeLabels[i][j] = noEdgeID;
                    bestEdgeWeights[i][j] = NEGATIVE_INFINITY;
                    continue;
                }
                final int bestLabel = Arr.argmax(probs[i][j]);
                bestEdgeLabels[i][j] = bestLabel;
                bestEdgeWeights[i][j] = probs[i][j][bestLabel];
            }
        }
        return Pair.of(bestEdgeWeights, bestEdgeLabels);
    }
}
