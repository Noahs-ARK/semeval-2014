package edu.cmu.cs.ark.semeval2014.prune;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.graph.Node;
import util.Vocabulary;

public class PcedtPruner {

    Vocabulary labelVocab;
    List<int[][]> graphMatrices;

    public PcedtPruner(Vocabulary labelVocab, List<int[][]> graphMatrices) {
        this.labelVocab = labelVocab;
        this.graphMatrices = graphMatrices;
    }

    /**
     * Preprocessing step on graphMatrices - converts pcedt formalism into tree
     */
    public void modifyGraphMatrices() {

        for (int mno = 0; mno < graphMatrices.size(); mno++) {
            System.out.println(mno);
            int[][] graphMatrix = graphMatrices.get(mno);

            Map<Integer, List<Integer>> problemNodes = new HashMap<Integer, List<Integer>>();

            // find the children of and
            for (int parent = 0; parent < graphMatrix.length; parent++) {
                for (int child = 0; child < graphMatrix[0].length; child++) {

                    int label = graphMatrix[parent][child];
                    String name = labelVocab.name(label);

                    List<Integer> c;
                    if (name.contains("member")) {
                        if (problemNodes.containsKey(parent) == false) {
                            c = new ArrayList<Integer>();
                        } else {
                            c = problemNodes.get(parent);
                        }
                        c.add(child);
                        problemNodes.put(parent, c);
                    }
                }
            }

            // find the other common parent of those children
            for (int andParent : problemNodes.keySet()) {
                List<Integer> otherParent = new ArrayList<Integer>();
                List<Integer> andChildren = problemNodes.get(andParent);
                if (andChildren.size() < 2) {
                    continue;
                }
                for (int child : andChildren) {
                    for (int parent = 0; parent < graphMatrix[0].length; parent += 1) {
                        if (parent == andParent) {
                            continue;
                        } else if (graphMatrix[parent][child] != 0) {
                            otherParent.add(parent);
                            break;
                        }
                    }
                }
                if (otherParent.size() == 0) { // andchildren have no other
                                                // common parent
                    continue;
                }
                System.out.println(otherParent);

                // add an edge from other common parent to and
                int cp = otherParent.get(0);
                int label = graphMatrix[cp][andChildren.get(0)];
                graphMatrix[cp][andParent] = label;
                // remove edges from other common parent to andChildren
                for (int andChild : andChildren) {
                    graphMatrix[cp][andChild] = 0;
                }
            }

            // convert into graph for printing

        }
    }

    public static Graph modifyGraph(Graph g) {
        Graph newGraph = new Graph(g.id);
        Map<Integer, List<Integer>> problemNodes = new HashMap<Integer, List<Integer>>();

        // find the children of and
        for (Node child : g.getNodes()) {
            for (Edge incoming : child.getIncomingEdges()) {
                if (incoming.label.contains("member")) {
                    List<Integer> andChildren;
                    if (problemNodes.containsKey(incoming.source)) {
                        andChildren = problemNodes.get(incoming.source);
                    } else {
                        andChildren = new ArrayList<Integer>();
                    }
                    andChildren.add(child.id);
                    problemNodes.put(incoming.source, andChildren);
                }
            }
        }

        // find the other common parent of those children
        List<Integer> edgesForRemoval = new ArrayList<Integer>();
        int edgeId = 0;
        for (int andParent : problemNodes.keySet()) {
            if (problemNodes.get(andParent).size() < 2) {
                continue;
            }
            List<Integer> commonParent = new ArrayList<Integer>();
            String label = "NOEDGE";
            for (int andChild : problemNodes.get(andParent)) {
                for (Edge incoming : g.getNode(andChild).incomingEdges) {
                    if (incoming.source == andParent) {
                        continue;
                    }
                    commonParent.add(incoming.source);
                    edgesForRemoval.add(incoming.id);
                    label = incoming.label;
                    break;
                }
            }
            if (commonParent.size() < 2) {
                continue;
            }

            Edge newEdge = new Edge(edgeId, commonParent.get(0),
                    andParent, label);
            edgeId++; // does it matter?

            Node andParentNode = g.getNode(andParent);
            andParentNode.addIncomingEdge(newEdge); // should I add this back to
                                                    // the graph?

            System.out.println(g.getNodes().size());

            System.out.println(newEdge.source);
            System.out.println(newEdge.target);
            // System.out.println(newEdge.label);
            newGraph.addEdge(newEdge.source, newEdge.target, newEdge.label); // this
                                                                             // throws
                                                                             // an
                                                                             // error,
                                                                             // why?
        }

        for (Node n : g.getNodes()) {
            newGraph.addNode(n.form, n.lemma, n.pos, n.isTop, n.isPred);
        }

        for (Edge e : g.getEdges()) {
            if (edgesForRemoval.contains(e.id)) {
                continue;
            }
            newGraph.addEdge(e.source, e.target, e.label);
        }
        return newGraph;
    }
}
