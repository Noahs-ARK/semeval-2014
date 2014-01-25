package edu.cmu.cs.ark.semeval2014.lr.fe;

import java.util.HashSet;
import java.util.Set;

import util.BasicFileIO;
import util.U;
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder;

public class BasicFeatures extends FE.FeatureExtractor implements FE.TokenFE, FE.EdgeFE {
	
	Set<String> whitelist;
	
	@Override
	public void initializeAtStartup() {
		whitelist = new HashSet<>();
		for (String line : BasicFileIO.openFileLines("resources/train_vocab.txt")) {
			String[] parts =line.trim().split(" +");
			int count = Integer.parseInt(parts[0]);
			if (count >= 100) {
				whitelist.add(parts[1]);
			}
		}
		
	}
	@Override
	public void features(int word1, int word2, String label, FeatureAdder fa) {
		String w1 = sent.sentence()[word1].toLowerCase();
		String w2 = sent.sentence()[word2].toLowerCase();
		if (whitelist.contains(w1) && whitelist.contains(w2)) {
			fa.add(U.sf("lc:bg:%s_%s_%s", w1.toLowerCase(), w2.toLowerCase(), label));
		} else {
			fa.add(U.sf("lc:bg:OOV_%s", label));
		}

		String p1 = sent.pos()[word1];
		String p2 = sent.pos()[word2];
		fa.add(U.sf("pos:bg:%s_%s_%s", p1, p2, label));
		
		String dir = word1>word2 ? "dir=i>j" : "dir=i<j";
		fa.add(dir);
		fa.add(U.sf("pos:bg:%s_%s_%s_%s", p1, p2, dir, label));
	}

	@Override
	public void features(int word1, FeatureAdder fa) {
		String p1 = sent.pos()[word1];
		fa.add(U.sf("pos:%s", p1));
		
		String w1 = sent.pos()[word1].toLowerCase();
		w1 = whitelist.contains(w1) ? w1 : "OOV";
		fa.add("lc:" + w1);
	}

}
