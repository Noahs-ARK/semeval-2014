package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import util.BasicFileIO;
import util.U;

public class WordVectors extends FE.FeatureExtractor implements FE.EdgeFE {
	// public static String vectorFileName;// =
	// "resources/word_vectors_norm.txt";
	public HashMap<String, double[]> wordToVector;

	public WordVectors(HashMap<String, double[]> wordToVector) {
		super();
		this.wordToVector = wordToVector;
	}

	/* Make sure the embeddings are pre-normalized */
	public static HashMap<String, double[]> loadWordVectors(
			String vectorFileName) {
		HashMap<String, double[]> wordToVector = new HashMap<>();
		System.err.println("reading word vectors");
		BufferedReader bReader = null;
		bReader = BasicFileIO.openFileToRead(vectorFileName);
		String line = BasicFileIO.getLine(bReader);
		wordToVector = new HashMap<String, double[]>();
		while (line != null) {
			String[] splitline = line.split(" ");
			int vectorLen = splitline.length - 1;
			double[] vector = new double[vectorLen];
			for (int i = 0; i < vectorLen; ++i) {
				vector[i] = Double.parseDouble(splitline[i + 1]);
			}
			wordToVector.put(splitline[0].toLowerCase(), vector);
			line = BasicFileIO.getLine(bReader);
		}
		U.pf("loaded word vectors with %d wordtypes\n", wordToVector.size());
		return wordToVector;
	}

	@Override
	public void features(int srcTokenIdx, int destTokenIdx, FE.FeatureAdder fa) {
		String srcToken = sent.sentence[srcTokenIdx].toLowerCase();
		String destToken = sent.sentence[destTokenIdx].toLowerCase();
		double[] srcWordVector = wordToVector.get(srcToken);
		double[] destWordVector = wordToVector.get(destToken);

		if (srcWordVector == null && srcToken.matches(".*\\d.*"))
			srcWordVector = wordToVector.get("---num---");
		if (destWordVector == null && destToken.matches(".*\\d.*"))
			destWordVector = wordToVector.get("---num---");

		if (srcWordVector == null)
			srcWordVector = wordToVector.get("---unk---");
		if (destWordVector == null)
			destWordVector = wordToVector.get("---unk---");

		if (srcWordVector != null && destWordVector != null) {
			// double dotProd = 0;
			for (int i = 0; i < srcWordVector.length; i++) {
				// dotProd += destWordVector[i] * srcWordVector[i];
				// fa.add(U.sf("VectorDim_%d_prod", i), (destWordVector[i] *
				// srcWordVector[i]));
				fa.add(U.sf("VectorDimDest_%d", i), 10 * destWordVector[i]);
				fa.add(U.sf("VectorDimSrc_%d", i), 10 * srcWordVector[i]);
			}
			// fa.add("VectorDotProd", dotProd);
		}
	}
}
