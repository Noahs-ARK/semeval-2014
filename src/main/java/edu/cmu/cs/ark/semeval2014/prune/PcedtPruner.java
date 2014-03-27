package edu.cmu.cs.ark.semeval2014.prune;

import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.graph.Node;
import util.Vocabulary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PcedtPruner {

    Vocabulary labelVocab;
    List<int[][]> graphMatrices;

    public PcedtPruner(Vocabulary labelVocab, List<int[][]> graphMatrices) {
        this.labelVocab = labelVocab;
        this.graphMatrices = graphMatrices;
    }

    /**
     * Pre-processing step on graphMatrices - converts pcedt formalism into tree
     */
    public static Graph modifyGraph(Graph g) {
        Graph newGraph = new Graph(g.id);

        // map between and parent and its children
        Map<Integer, List<Integer>> problemNodes = new HashMap<>();

        // children of and which are also tops
        List<Integer> topNodes = new ArrayList<>();

        // and parent which should now be the top instead
        int newTopNode = -100;

        // find the children of and
        for (Node child : g.getNodes()) {
            for (Edge incoming : child.getIncomingEdges()) {
                if (incoming.label.contains("member")) {
                    List<Integer> andChildren;
                    if (problemNodes.containsKey(incoming.source)) {
                        andChildren = problemNodes.get(incoming.source);
                    } else {
                        andChildren = new ArrayList<>();
                    }
                    andChildren.add(child.id);
                    problemNodes.put(incoming.source, andChildren);

                    if (child.isTop) {
                        newTopNode = incoming.source;
                        topNodes.add(child.id);
                    }
                }
            }
        }

        // add all nodes to the new graph
        // node id matters because we are going to add corresponding edges
        for (Node n : g.getNodes()) {
            if (topNodes.contains(n.id)) {
                newGraph.addNode(n.form, n.lemma, n.pos, false, n.isPred);
            } else if (n.id == newTopNode) {
                newGraph.addNode(n.form, n.lemma, n.pos, true, n.isPred);
            } else {
                newGraph.addNode(n.form, n.lemma, n.pos, n.isTop, n.isPred);
            }
        }

        // find the other common parent of those children
        List<Integer> edgesForRemoval = new ArrayList<>();
        int edgeId = 0;
        for (int andParent : problemNodes.keySet()) {
            if (problemNodes.get(andParent).size() < 2) {
                continue;
            }
            List<Integer> commonParent = new ArrayList<>();
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

            Edge newEdge = new Edge(edgeId, commonParent.get(0), andParent,
                    label);
            edgeId++; // edge ids do not matter really

            Node andParentNode = g.getNode(andParent);
            andParentNode.addIncomingEdge(newEdge); // should I add this back to
                                                    // the graph - not required,
                                                    // edges take care
            newGraph.addEdge(newEdge.source, newEdge.target, newEdge.label);
        }

        for (Edge e : g.getEdges()) {
            if (edgesForRemoval.contains(e.id)) {
                continue;
            }
            newGraph.addEdge(e.source, e.target, e.label);
        }
        return newGraph;
    }

    // 1. add two edges to the children of and, from the parent of and
    // 2. if and is the top node, make its children the top nodes
    public static Graph postProcess(Graph g) {
        Graph newGraph = new Graph(g.id);

        // change top node
        int oldTop = -100;
        List<Integer> newTops = new ArrayList<>();

        // and with 2 or more children - fix

        // new edges to be added:
        Map<Integer, String> newEdges = new HashMap<>();
        // edges to be removed:
        List<Integer> edgesForRemoval = new ArrayList<>();
        for (Node andNode : g.getNodes()) {
            List<Integer> memberChildren = new ArrayList<>();
            for (Edge outgoing : andNode.getOutgoingEdges()) {
                if (outgoing.label.contains("member")) {
                    memberChildren.add(outgoing.target);
                }
            }

            if (memberChildren.size() < 2) { // check!!!
                // not an and node
                continue;
            }

//            System.out.println("incoming to and: "
//                    + andNode.incomingEdges.size());

            // changing the top to others
            if (andNode.incomingEdges.size() == 0 && andNode.isTop) {
                oldTop = andNode.id;
                newTops.addAll(memberChildren);
            }

            for (Edge incoming : andNode.incomingEdges) {
                edgesForRemoval.add(incoming.id);
                for (int memberChild : memberChildren) {
                    newEdges.put(memberChild, incoming.source + "_"
                            + incoming.label);
                }
            }
        }


        for (Node n : g.getNodes()) {
            if (n.id == oldTop) {
                newGraph.addNode(n.form, n.lemma, n.pos, false, n.isPred);
            } else if (newTops.contains(n.id)) {
                newGraph.addNode(n.form, n.lemma, n.pos, true, n.isPred);
            } else {
                newGraph.addNode(n.form, n.lemma, n.pos, n.isTop, n.isPred);
            }
        }

        // add all original edges
        for (Edge e : g.getEdges()) {
            if (edgesForRemoval.contains(e.id)) {
                continue;
            }
            newGraph.addEdge(e.source, e.target, e.label);
        }
        // add all new edges
        for (int target : newEdges.keySet()) {
            String[] es = newEdges.get(target).split("_");

            int source = Integer.parseInt(es[0]);
            String label = es[1];
            newGraph.addEdge(source, target, label);
        }
        return newGraph;
    }
}
