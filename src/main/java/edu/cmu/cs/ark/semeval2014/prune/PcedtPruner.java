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
     * Pre-processing step on graphMatrices - converts pcedt formalism into tree
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

        // map between and parent and its children
        Map<Integer, List<Integer>> problemNodes = new HashMap<Integer, List<Integer>>();

        // children of and which are also tops
        List<Integer> topNodes = new ArrayList<Integer>();

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
                        andChildren = new ArrayList<Integer>();
                    }
                    andChildren.add(child.id);
                    problemNodes.put(incoming.source, andChildren);

                    if (child.isTop == true) {
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
    public static Graph postProcessOld(Graph g) {
        Graph newGraph = new Graph(g.id);

        List<Integer> edgesForRemoval = new ArrayList<Integer>();
        int oldTopNode = -100;
        List<Integer> newTopNodes = new ArrayList<Integer>();
        Map<String, String> edgesToBeAdded = new HashMap<String, String>(); // target_source,
                                                                            // label

        // find parent of and and the children of and
        int numMembers = 0;
        boolean needToChangeTop = false;
        for (Node n : g.getNodes()) {
            for (Edge incoming : n.getIncomingEdges()) {
                if (incoming.label.contains("member")) { // node n is a child of
                                                         // the and node
                    int andNode = incoming.source;
                    
                    if (g.getNode(andNode).getIncomingEdges().size() == 0) { // and is the top node
                        oldTopNode = andNode;
                        System.out.println(g.getNode(andNode).isTop);
                        newTopNodes.add(n.id);
                        needToChangeTop = true;
                        continue;
                    }
                    Edge incomingToAnd = g.getNode(andNode).getIncomingEdges()
                            .get(0);
                    int andParent = incomingToAnd.source;
                    edgesForRemoval.add(incomingToAnd.id);

                    edgesToBeAdded.put(n.id + "_" + andParent,
                            incomingToAnd.label);
                    numMembers += 1;
                }
            }
        }
        System.out.println(newTopNodes);
        System.out.println(oldTopNode);

        // add all nodes
        for (Node n : g.getNodes()) {
            System.out.println("Node: " + n.id);
            if (n.id == oldTopNode && newTopNodes.size() > 1) {
                System.out.println("oldTopNode");
                newGraph.addNode(n.form, n.lemma, n.pos, false, n.isPred);
            } else if (newTopNodes.contains(n.id) && newTopNodes.size() > 1) {
                System.out.println("new top node");
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
        for (String edge : edgesToBeAdded.keySet()) {
            String[] es = edge.split("_");
            int target = Integer.parseInt(es[0]);
            int source = Integer.parseInt(es[1]);
            String label = edgesToBeAdded.get(edge);
            newGraph.addEdge(source, target, label);
        }
        return newGraph;
    }

    public static Graph postProcess(Graph g) {
        Graph newGraph = new Graph(g.id);

        // change top node
        int oldTop = -100;
        List<Integer> newTops = new ArrayList<Integer>();

        // and with 2 or more children - fix

        // new edges to be added:
        Map<Integer, String> newEdges = new HashMap<Integer, String>();
        // edges to be removed:
        List<Integer> edgesForRemoval = new ArrayList<Integer>();
        for (Node andNode : g.getNodes()) {
            List<Integer> memberChildren = new ArrayList<Integer>();
            for (Edge outgoing : andNode.getOutgoingEdges()) {
                if (outgoing.label.contains("member")) {
                    memberChildren.add(outgoing.target);
                }
            }

            if (memberChildren.size() < 2) { // check!!!
                // not an and node
                continue;
            }

            System.out.println("incoming to and: "
                    + andNode.incomingEdges.size());

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
