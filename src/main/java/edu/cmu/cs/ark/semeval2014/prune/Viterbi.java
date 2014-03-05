package edu.cmu.cs.ark.semeval2014.prune;

import java.util.*;


//State: Holds a table that has the features for a sentence
//			Holds the weights 
//Actions: builds the table of scores, then traverses it.
public class Viterbi {
	Map<String, Double> w;
	Set<String> featuresForPrinting;
	
	public Viterbi(Map<String, Double> weights){
		w = weights;
	}

	// takes a table containing the features for the sentence
	// returns a string[] that is the tag sequence for the sentence
	public String[] decode(List<Map<String, Set<String>>> sentenceFeats){//, ArrayList<String[]> sentence) {
		// compute score for each element in the table, store in this backpointer table
		List<Map<String, Double>> scores = new ArrayList<Map<String, Double>>();
		List<Map<String, String>> backpointers = new ArrayList<Map<String, String>>();
		Map<String, Double> prevScore = new TreeMap<String, Double>();
		prevScore.put("<START>", 0.0);
		
		for (int i = 1; i < sentenceFeats.size(); i++){
			Map<String, Set<String>> wordFeats = sentenceFeats.get(i);
			
			// to instantiate the current score and backpointer
			Map<String, Double> score = new TreeMap<String, Double>();
			Map<String, String> backpointer = new TreeMap<String, String>();
			
			for (String curTag : wordFeats.keySet()){
				if (i == sentenceFeats.size() - 1)
					curTag = "<STOP>";
				for (String prevTag : sentenceFeats.get(1).keySet()){
					if (i == 1)
						prevTag = "<START>";
					// want to pass the feature vector for the current word, given the tag for the current word
					// to the scorer. Will also pass the current tag, and the previous tag.
					double emission = FeatureVector.score(wordFeats.get(curTag), prevTag, w);
					//System.out.println(prevTag + "_"  + curTag);
					double transition;
					if (i == 1 || i == sentenceFeats.size() - 1)
						transition = 0;
					else
						transition = w.get(prevTag + "_" + curTag);
					double curScore = emission + transition;
					
					double scoreForPrevTag = prevScore.get(prevTag);
					
					updateScoreAndBackpointer(curScore + scoreForPrevTag, score, backpointer, curTag, prevTag);
					/*
					System.out.println("curTag: " + curTag);
					System.out.println("prevTag: " + prevTag);
					System.out.println("curScore: " + curScore);
					System.out.println("prevScore: " + prevScore);
					System.out.print("weights for curScore: ");
					for (String curFeat : wordFeats.get(curTag)){
						System.out.print(curFeat + ":" + w.get(curFeat));
					}
					System.out.println();
					System.out.println("current feats: " + wordFeats.get(curTag));
					System.out.println();
					*/
				}
				if (i == sentenceFeats.size() - 1)
					break;
			}
			//System.out.println();
			scores.add(score);
			prevScore = score;
			backpointers.add(backpointer);
		}
		//System.out.println("\n\n");
		return followBackpointers(backpointers, sentenceFeats);//, sentence);
				
	}

	private String[] followBackpointers(List<Map<String, String>> backpointers, List<Map<String, Set<String>>> sentenceFeats){//, ArrayList<String[]> sentence) {
		String[] tags = new String[backpointers.size()];
		String curTag = "<STOP>";
		//System.out.println("Made it here");
		for (int i = backpointers.size() - 1; i >= 0; i--){
			// adding stuff to the set for printing

		//	System.out.println("Made it insidet he loop!" + i);
			tags[i] = backpointers.get(i).get(curTag);
			curTag = backpointers.get(i).get(curTag);
			//for (String f : sentenceFeats.get(i).get(curTag)) {
				//if (w.containsKey(f)){
				//	featuresForPrinting.add(f + " " + w.get(f));
				//}
			//}
		}
		
		//LoadData.printFeaturesUsed(sentence, featuresForPrinting);
		
		return tags;
	}

	private void updateScoreAndBackpointer(double curScore, Map<String, Double> score, Map<String, String> backpointer,
			String curTag, String prevTag) {
		if (score.keySet().size() == 0 || !score.containsKey(curTag) || 
				curScore > score.get(curTag)){
			score.put(curTag, curScore);
			backpointer.put(curTag, prevTag);
		}
		
	}
	
}