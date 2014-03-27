package edu.cmu.cs.ark.semeval2014.util;

import edu.cmu.cs.ark.semeval2014.lr.LRParser;
import edu.cmu.cs.ark.semeval2014.prune.PcedtPruner;
import sdp.graph.Edge;
import sdp.graph.Graph;
import sdp.io.GraphReader;
import sdp.io.GraphWriter;
//import sdp.io.GraphWriter;
import util.Vocabulary;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenerateGraphsAndVocab {
	private final List<int[][]> graphMatrices;
	private final Vocabulary labelVocab;
	
	public GenerateGraphsAndVocab(String sdpFile, boolean doPcedtTreeProcess) throws IOException {
		List<Graph> graphs = readGraphs(sdpFile);

        final List<Graph> modifiedGraphs = new ArrayList<>();
        if (doPcedtTreeProcess) {
            GraphWriter gw = new GraphWriter("pcedt1.conll");
            for (Graph g : graphs) {
                Graph ng = PcedtPruner.preprocess(g);
                gw.writeGraph(ng);
                modifiedGraphs.add(ng);
            }
            gw.close();
            graphs = modifiedGraphs;
        }
        List<Graph> originalGraphs = new ArrayList<Graph>();
        GraphWriter gw = new GraphWriter("pcedt2.conll");
        
        for (Graph ng : modifiedGraphs) {
            Graph og = PcedtPruner.postProcess(ng);
            gw.writeGraph(og);
            originalGraphs.add(og);
        }
        gw.close();


		labelVocab = generateLabelVocab(graphs);
		graphMatrices = convertGraphsToAdjacencyMatrices(graphs);
	}
	
	private List<Graph> readGraphs(String sdpFile) throws IOException {
		final ArrayList<Graph> graphs = new ArrayList<>();
		try (GraphReader reader = new GraphReader(sdpFile)) {
			Graph graph;
			while ((graph = reader.readGraph()) != null) {
				graphs.add(graph);
			}
		}
		return graphs;
	}
	
	private Vocabulary generateLabelVocab(List<Graph> graphs){
		Vocabulary labelVocab = new Vocabulary();
		// build up the edge label vocabulary
		labelVocab.num(LRParser.NO_EDGE);
		for (Graph graph : graphs) {
			for (Edge e : graph.getEdges()) {
				labelVocab.num(e.label);
			}
		}
		labelVocab.lock();
		return labelVocab;
	}
	
	private List<int[][]> convertGraphsToAdjacencyMatrices(List<Graph> graphs){
		List<int[][]> graphMatrices = new ArrayList<>();
		for (int snum=0; snum<graphs.size(); snum++) {
			final Graph graph = graphs.get(snum);
//			assert sent.sentenceId.equals(graph.id.replace("#",""));
			graphMatrices.add(convertGraphToAdjacencyMatrix(graph, graph.getNNodes() - 1, labelVocab));
		}
		return graphMatrices;
	}
	
	private static int[][] convertGraphToAdjacencyMatrix(Graph graph, int n, Vocabulary labelVocab) {
		int[][] edgeMatrix = new int[n][n];
		for (int[] row : edgeMatrix) {
			Arrays.fill(row, labelVocab.num(LRParser.NO_EDGE));
		}
		for (Edge e : graph.getEdges()) {
			edgeMatrix[e.source-1][e.target-1] = labelVocab.num(e.label);
		}
		return edgeMatrix;
	}
	
	public List<int[][]> getGraphMatrices(){
		return graphMatrices;
	}
	
	public Vocabulary getLabelVocab(){
		return labelVocab;
	}
}
