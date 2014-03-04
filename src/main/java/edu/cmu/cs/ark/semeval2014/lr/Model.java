package edu.cmu.cs.ark.semeval2014.lr;

import util.Arr;
import util.BasicFileIO;
import util.U;
import util.Vocabulary;
import util.misc.Triple;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/** note the behavior of this class does depend on globals in LRParser !  */
public class Model {
	private static final String LABEL_VOCAB_HEADER = "LABELVOCAB";
	private static final String NUM_HASH_BUCKETS_HEADER = "NUM_HASH_BUCKETS";
	private static final String LABEL_FEATURE_VOCAB_HEADER = "LABEL_FEATURE_VOCAB";
	private static final String FEATURES_BY_LABEL_HEADER = "FEATURES_BY_LABEL";
	private static final String COEFFICIENTS_HEADER = "C";
	private static final String HASH_COEF_HEADER = "H";
	private static final double MINIMUM_WEIGHT_THRESHOLD = 1e-7;

	/** This is for label-side features */
	public final Vocabulary labelVocab;
	/** This is for label-side features */
	final Vocabulary labelFeatureVocab;
	/** This is for label-side features */
	final List<int[]> featuresByLabel;
	
	/** this is unused under feature hashing */
	final Vocabulary perceptVocab;
	/** flattened form: #percepts * #labelFeatures */
	float[] coefs; // 

	public Model(
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

	public Model(
			Vocabulary labelVocab,
			Vocabulary labelFeatureVocab,
			List<int[]> featuresByLabel,
			Vocabulary perceptVocab) {
		this(
				labelVocab,
				labelFeatureVocab,
				featuresByLabel,
				perceptVocab,
				null);
	}

	/** returns:  (#tokens x #tokens x #labelvocab)
	 * for token i and token j, prob dist over the possible edge labels.
	 */
	public double[][][] inferEdgeProbs(NumberizedSentence ns) {
		double[][][] scores = inferEdgeScores(ns);
		// transform in-place into probs
		for (int i=0; i<ns.T; i++) {
			for (int j=0; j<ns.T; j++) {
				if (LRParser.badDistance(i, j)) continue;
				Arr.softmaxInPlace(scores[i][j]);
			}
		}
		return scores;
	}

	/** returns:  (#tokens x #tokens x #labelvocab)
	 * for token i and token j, nonneg scores (unnorm probs) per edge label
	 */
	double[][][] inferEdgeScores(NumberizedSentence ns) {
		double[][][] scores = new double[ns.T][ns.T][labelVocab.size()];
		for (int kk=0; kk<ns.nnz; kk++) {
			for (int label=0; label< labelVocab.size(); label++) {
				for (int labelFeatureIdx : featuresByLabel.get(label)) {
					final int featureIdx = coefIdx(ns.perceptnum(kk), labelFeatureIdx);
					scores[ns.i(kk)][ns.j(kk)][label] += coefs[featureIdx] * ns.value(kk);
				}
			}
		}
		return scores;
	}

	/** Assume everything is potentially a full-range 4byte int.
	 * Following Bloch 2nd ed., "Effective Java" item 9 */
	static int hashTwoInts(int x, int y) {
	    int hash = 17;
	    hash = hash * 31 + new Integer(x).hashCode();
	    hash = hash * 31 + new Integer(y).hashCode();
	    return hash;
	}
	
	/** index into coefs. */
	int coefIdx(int perceptIdx, int labelFeatureIdx) {
		if (LRParser.useHashing){
			// perceptIdx is a random hashed number.  but labelFeatureIdx is not.
			// so let's just hash both and we get a new hash
			int h1 = hashTwoInts(perceptIdx, labelFeatureIdx);
			int h2 = Math.abs(h1 % ( (int) LRParser.numHashBuckets));
			assert h2 >= 0 : U.sf("%s %s || %s || %s\n", perceptIdx, labelFeatureIdx, h1, h2);
			return h2;
		}
		else {
			assert perceptIdx >= 0;
			return coefIdx(labelFeatureVocab, perceptIdx, labelFeatureIdx);
			
		}
	}

	private static int coefIdx(Vocabulary labelFeatureVocab, int perceptIdx, int labelFeatureIdx) {
		return perceptIdx * labelFeatureVocab.size() + labelFeatureIdx;
	}

	static Model load(String modelFile) throws IOException {
		final Vocabulary labelVocab = new Vocabulary();
		final Vocabulary perceptVocab = new Vocabulary();
		final Vocabulary labelFeatureVocab = new Vocabulary();
		final List<int[]> featuresByLabel = new ArrayList<>();

		final ArrayList<Triple<Integer, Integer, Float>> coefTuples = new ArrayList<>();
		float[] coefs = null;
		
		try (BufferedReader reader = BasicFileIO.openFileOrResource(modelFile)) {
			String line;
			while ((line = reader.readLine()) != null) {
				final String[] parts = line.split("\t");
				switch (parts[0]) {
					case LABEL_VOCAB_HEADER:
						final String[] labels = parts[1].trim().split(" ");
						for (String x : labels) labelVocab.num(x);
						labelVocab.lock();
						break;
					case LABEL_FEATURE_VOCAB_HEADER:
						final String[] labelFeatures = parts[1].trim().split(" ");
						for (String x : labelFeatures) labelFeatureVocab.num(x);
						labelFeatureVocab.lock();
						break;
					case FEATURES_BY_LABEL_HEADER:
						final String[] featureStrs = parts[1].trim().split(" ");
						final int[] features = new int[featureStrs.length];
						for (int i = 0; i < featureStrs.length; i++) {
							features[i] = Integer.parseInt(featureStrs[i]);
						}
						featuresByLabel.add(features);
						break;
						
					case COEFFICIENTS_HEADER:
						int perceptIdx = perceptVocab.num(parts[1]);
						int labelFeatureIdx = labelFeatureVocab.numStrict(parts[2]);
						float value = Float.parseFloat(parts[3]);
						coefTuples.add(U.triple(perceptIdx, labelFeatureIdx, value));
						break;
						
					case NUM_HASH_BUCKETS_HEADER:
						LRParser.useHashing = true;
						LRParser.numHashBuckets = Integer.parseInt(parts[1]);
						coefs = new float[(int) LRParser.numHashBuckets];
						break;
					case HASH_COEF_HEADER:
						int featHash = Integer.parseInt(parts[1]);
						float val = Float.parseFloat(parts[2]);
						coefs[featHash] = val;
						break;
						
					default:
						throw new RuntimeException("bad model line format");
				}
			}
		}
		labelVocab.lock();
		labelFeatureVocab.lock();
		perceptVocab.lock();
		
		if ( ! LRParser.useHashing) {
			coefs = new float[perceptVocab.size() * labelFeatureVocab.size()];
			for (Triple<Integer, Integer, Float> x : coefTuples) {
				coefs[coefIdx(labelFeatureVocab, x.first, x.second)] = x.third;
			}
		}
		U.pf("Label vocab (size %d): %s\n", labelVocab.size(), labelVocab.names());
		U.pf("Label feature vocab (size %d): %s\n", labelFeatureVocab.size(), labelVocab.names());
		U.pf("Num features: %d\n", perceptVocab.size());
		return new Model(labelVocab, labelFeatureVocab, featuresByLabel, perceptVocab, coefs);
	}

	void save(String modelFile) throws IOException {
		U.pf("Saving model to %s\n", modelFile);
		try (PrintWriter out = new PrintWriter(BasicFileIO.openFileToWriteUTF8(modelFile))) {
			out.append(LABEL_VOCAB_HEADER).append("\t");
			for (String x : labelVocab.names()) {
				out.append(x).append(" ");
			}
			out.append("\n");
			out.append(LABEL_FEATURE_VOCAB_HEADER).append("\t");
			for (String feature : labelFeatureVocab.names()) {
				out.append(feature).append(" ");
			}
			out.append("\n");
			for (int[] featureIdxs : featuresByLabel) {
				out.append(FEATURES_BY_LABEL_HEADER).append("\t");
				for (int featureIdx : featureIdxs) {
					out.append(Integer.toString(featureIdx)).append(" ");
				}
				out.append("\n");
			}
			if (LRParser.useHashing) {
				out.printf("%s\t%d\n", NUM_HASH_BUCKETS_HEADER, (int) LRParser.numHashBuckets);
				assert LRParser.numHashBuckets == coefs.length;
				for (int h=0; h < coefs.length; h++) {
					if (Math.abs(coefs[h]) < MINIMUM_WEIGHT_THRESHOLD) continue;
					out.printf("%s\t%d\t%s\n", HASH_COEF_HEADER, h, coefs[h]);
				}
			}
			else {
				for (int f = 0; f < perceptVocab.size(); f++) {
					for (int k = 0; k < labelFeatureVocab.size(); k++) {
						float coef = coefs[coefIdx(f, k)];
						if (Math.abs(coef) < MINIMUM_WEIGHT_THRESHOLD) continue; // throw out parameters below threshold
						out.printf("%s\t%s\t%s\t%s\n", COEFFICIENTS_HEADER, perceptVocab.name(f), labelFeatureVocab.name(k), coef);
					}
				}
			}
		}
	}
}
