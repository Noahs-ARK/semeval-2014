package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import util.U;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;

/**
 * Features using Stanford NER tags (read from an external file, keyed to
 * sentenceId + tokenId). Uses both 3-class (Location, Person, Org) and 7-class
 * (Location, Organization, Person, Money, Percent, Date) tags. This only seems
 * to be helpful for the PCEDT format (which has fine-grained labels for TWHEN,
 * etc.)
 * 
 * Requires NER data to be in local folder resources/ner.txt.  Data is in:
 * 
 * cab:/cab1/corpora/LDC2013E167/resources/ner.txt
 * 
 * @author dbamman
 * 
 */
public class NERFeatures extends FE.FeatureExtractor implements FE.TokenFE,
		FE.EdgeFE {

	static Map<Integer, Map<Integer, String>> ner3Features;
	static Map<Integer, Map<Integer, String>> ner7Features;
	static Set<String> targetLabels;

	private String[] currentNer3;
	private String[] currentNer7;

	static boolean initialized = false;

	/**
	 * Read in NER-tagged data. Format is: <sentenceId> <tokenId> <token>
	 * <Stanford 3-way NER> <Stanford 7-way NER>.
	 * 
	 * Note initializeAtStartup gets called once per sentence in LRParser. Check
	 * to only initialize here once.
	 */
	public void initializeAtStartup() {

		if (!initialized) {
			initialized = true;
			synchronizedInitialize();
		}
	}

	/*
	 * Initialize when using multiple threads (as during parallel decoding at
	 * test time).
	 */
	public static synchronized void synchronizedInitialize() {

		// just use these NER classes
		targetLabels = new HashSet<String>();
		targetLabels.add("LOCATION");
		targetLabels.add("DATE");
		targetLabels.add("TIME");

		System.err.println("reading NER dict");

		ner3Features = new HashMap<Integer, Map<Integer, String>>();
		ner7Features = new HashMap<Integer, Map<Integer, String>>();

		String infile = "resources/ner.txt";
		try {
			BufferedReader in1 = new BufferedReader(new InputStreamReader(
					new FileInputStream(infile), "UTF-8"));
			String str1;

			while ((str1 = in1.readLine()) != null) {
				String[] parts = str1.trim().split("\t");
				if (parts.length > 1) {
					int sentenceId = Integer.valueOf(parts[0]);
					// 0 offset
					int tokenId = Integer.valueOf(parts[1]) - 1;
					String ner3 = parts[3];
					String ner7 = parts[4];

					Map<Integer, String> tokFeats3 = null;
					if (ner3Features.containsKey(sentenceId)) {
						tokFeats3 = ner3Features.get(sentenceId);
					} else {
						tokFeats3 = new HashMap<Integer, String>();
					}
					tokFeats3.put(tokenId, ner3);
					ner3Features.put(sentenceId, tokFeats3);

					Map<Integer, String> tokFeats7 = null;
					if (ner7Features.containsKey(sentenceId)) {
						tokFeats7 = ner7Features.get(sentenceId);
					} else {
						tokFeats7 = new HashMap<Integer, String>();
					}
					tokFeats7.put(tokenId, ner7);
					ner7Features.put(sentenceId, tokFeats7);
				}

			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Pull the NER tags out of the HashMap and keep them in an array for faster
	 * iterating
	 */
	public void setupSentence(InputAnnotatedSentence _sent) {
		super.setupSentence(_sent);
		int id = Integer.valueOf(sent.sentenceId);

		currentNer3 = new String[sent.pos.length];
		currentNer7 = new String[sent.pos.length];

		if (ner3Features.containsKey(id)) {
			Map<Integer, String> feats = ner3Features.get(id);
			for (Integer i : feats.keySet()) {
				String ner = feats.get(i);
				currentNer3[i] = ner;
			}
		} else {
			currentNer3 = null;
		}

		if (ner7Features.containsKey(id)) {
			Map<Integer, String> feats = ner7Features.get(id);
			for (Integer i : feats.keySet()) {
				String ner = feats.get(i);
				currentNer7[i] = ner;
			}
		} else {
			currentNer7 = null;
		}

	}

	/*
	 * Child features. LOCATION/DATE/TIME labels seem to be the most (only?)
	 * useful ones.
	 */
	public void features(int tokenIdx, FeatureAdder fa) {

		singletonNER7InTarget(tokenIdx, fa);

		// singletonNER3(tokenIdx, fa);
		// singletonNER7(tokenIdx, fa);

	}

	/*
	 * Add NER of the child as a feature if it's among the target classes
	 */
	public void singletonNER7InTarget(int tokenIdx, FeatureAdder fa) {
		if (currentNer7 != null) {
			String srcNER = currentNer7[tokenIdx];
			if (targetLabels.contains(srcNER)) {
				fa.add(U.sf("ner7:%s", srcNER));
			}
		}
	}

	/*
	 * Add Stanford NER3 tag
	 */
	public void singletonNER3(int tokenIdx, FeatureAdder fa) {
		if (currentNer3 != null) {
			String srcNER = currentNer3[tokenIdx];
			fa.add(U.sf("ner3:%s", srcNER));
		}
	}

	/*
	 * Add Stanford NER7 tag
	 */
	public void singletonNER7(int tokenIdx, FeatureAdder fa) {
		if (currentNer7 != null) {
			String srcNER = currentNer7[tokenIdx];
			fa.add(U.sf("ner7:%s", srcNER));
		}
	}

	/*
	 * Child/parent features. None of these seem to be terribly helpful.
	 */
	public void features(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {

		// childNERInTargetHeadLex(srcTokenIdx, destTokenIdx, fa);
		// tupleNER3(srcTokenIdx, destTokenIdx, fa);
		// tupleNER7(srcTokenIdx, destTokenIdx, fa);

	}

	/*
	 * Add feature if the child NER is among targets + parent word
	 */
	public void childNERInTargetHeadLex(int srcTokenIdx, int destTokenIdx,
			FeatureAdder fa) {
		if (currentNer7 != null) {
			String srcNER = currentNer7[srcTokenIdx];

			if (targetLabels.contains(srcNER)) {
				final String destLex = sent.sentence[destTokenIdx];

				fa.add(U.sf("ner7lex:%s_%s", srcNER, destLex));
			}
		}
	}

	/*
	 * Stanford NER3 for both child and parent
	 */
	public void tupleNER3(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {
		if (currentNer3 != null) {
			String srcNER = currentNer3[srcTokenIdx];
			String destNER = currentNer3[destTokenIdx];
			fa.add(U.sf("ner3:%s_%s", srcNER, destNER));
		}
	}

	/*
	 * Stanford NER7 for both child and paretn
	 */
	public void tupleNER7(int srcTokenIdx, int destTokenIdx, FeatureAdder fa) {
		if (currentNer7 != null) {
			String srcNER = currentNer7[srcTokenIdx];
			String destNER = currentNer7[destTokenIdx];
			fa.add(U.sf("ner7:%s_%s", srcNER, destNER));
		}
	}
}
