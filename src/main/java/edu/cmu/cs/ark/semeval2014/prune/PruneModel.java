package edu.cmu.cs.ark.semeval2014.prune;

import java.util.List;

import sdp.graph.Edge;
import sdp.graph.Graph;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

public class PruneModel {
	
	InputAnnotatedSentence[] inputSents;
	List<Graph> graphs;

	public PruneModel(List<Graph> g,
			InputAnnotatedSentence[] iS) {
		inputSents = iS;
		graphs = g;
	}
	
	public void trainModel(){
		System.out.println("Size of inputSents: " + inputSents.length);
		System.out.println("Size of graphs: " + graphs.size());
		
		for (int i = 0; i < graphs.size(); i++){
			for (Edge e : graphs.get(i).getEdges()) {
				System.out.println(e);
			}
			System.exit(0);
		}
	}

}
