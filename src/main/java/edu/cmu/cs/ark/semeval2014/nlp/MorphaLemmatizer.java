package edu.cmu.cs.ark.semeval2014.nlp;

import uk.ac.susx.informatics.Morpha;

import java.io.IOException;
import java.io.StringReader;

/**
 * Threadsafe wrapper around uk.ac.susx.informatics.Morpha that simply returns the
 * input token in the case of some common buggy exceptions.
 */
public class MorphaLemmatizer {
	public String getLemma(String word, String postag) {
		assert word != null;
		assert postag != null;
		if (word.isEmpty()) return "";
		final String token = word.toLowerCase();
		final String tokenAndPostag = String.format("%s_%s", token.replaceAll("_", "-"), postag.toUpperCase());
		try {
			return new Morpha(new StringReader(tokenAndPostag), true).next();
		} catch (IOException e) {
			return token;
		} catch (Error e) {
			if (e.getMessage().equals("Error: could not match input")) {
				return token;
			} else {
				throw e;
			}
		}
	}
}
