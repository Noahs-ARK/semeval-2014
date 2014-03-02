package edu.cmu.cs.ark.semeval2014.prune;

import java.util.ArrayList;
import java.util.List;

import sdp.graph.Edge;
import sdp.graph.Graph;
import util.Vocabulary;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE;

public class PruneModel {

	final Vocabulary labelVocab;
	final Vocabulary labelFeatureVocab;
	final List<int[]> featuresByLabel;
	final Vocabulary perceptVocab;
	float[] coefs; // flattened form. DO NOT USE coefs.length IT IS CAPACITY NOT FEATURE CARDINALITY

	public PruneModel(
			Vocabulary labelVocab,
			Vocabulary labelFeatureVocab,
			List<int[]> featuresByLabel,
			Vocabulary perceptVocab,
			float[] coefs) {
		this.labelVocab = labelVocab;
		this.labelFeatureVocab = labelFeatureVocab;
		this.featuresByLabel = featuresByLabel;
		this.perceptVocab = perceptVocab;
		this.coefs = coefs;
	}

	public PruneModel(
			Vocabulary labelVocab,
			Vocabulary labelFeatureVocab,
			List<int[]> featuresByLabel,
			Vocabulary perceptVocab) {
		this(
				labelVocab,
				labelFeatureVocab,
				featuresByLabel,
				perceptVocab,
				new float[Math.min(10000, perceptVocab.size()) * perceptVocab.size()]);
	}
	
	public PruneModel(Vocabulary labelVocab, Vocabulary featVocab){
		this(labelVocab, new Vocabulary(), new ArrayList<int[]>(), featVocab, new float[Math.min(10000, featVocab.size()) * featVocab.size()]);
	}

}
