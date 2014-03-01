package edu.cmu.cs.ark.semeval2014.ner;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ie.AbstractSequenceClassifier;
import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;

/*
 * Run Stanford NER on .sdp-formatted files.  Print to stdout in format:
 * 
 * <sentenceId> <tokenId> <token> <3-class NER label> <7-class NER label>
 * 
 * Requires stanford-ner.jar in classpath, english.all.3class.distsim.crf.ser.gz and 
 * english.muc.7class.distsim.crf.ser.gz in classifiers/
 * 
 * Download models from: http://nlp.stanford.edu/software/stanford-ner-2014-01-04.zip
 * 
 */
public class StanfordNER {

	static PrintStream out;

	static {
		try {
			out = new PrintStream(System.out, true, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public static void read(String infile) {
		String serializedClassifier = "classifiers/english.all.3class.distsim.crf.ser.gz";
		String serializedClassifier7 = "classifiers/english.muc.7class.distsim.crf.ser.gz";

		Properties basicProperties = new Properties();
		basicProperties.put("tokenizerFactory",
				"edu.stanford.nlp.process.WhitespaceTokenizer");
		basicProperties.put("tokenizerOptions", "tokenizeNLs=true");

		AbstractSequenceClassifier<CoreLabel> classifier = null;
		AbstractSequenceClassifier<CoreLabel> classifier7 = null;
		
		try {
			classifier = (AbstractSequenceClassifier<CoreLabel>) CRFClassifier
					.getClassifier(serializedClassifier, basicProperties);
			classifier7 = (AbstractSequenceClassifier<CoreLabel>) CRFClassifier
					.getClassifier(serializedClassifier7, basicProperties);

		} catch (ClassCastException e1) {
			e1.printStackTrace();
		} catch (IOException e1) {
			e1.printStackTrace();
		} catch (ClassNotFoundException e1) {
			e1.printStackTrace();
		}

		String sentenceIdPattern = "^#(\\d+)";
		int lastId = -1;
		int size = 0;
		try {
			BufferedReader in1 = new BufferedReader(new InputStreamReader(
					new FileInputStream(infile), "UTF-8"));
			String str1;

			String line = "";
			List<String> tokenCheck = new ArrayList<String>();
			
			while ((str1 = in1.readLine()) != null) {

				Matcher sentenceIdMatcher = Pattern.compile(sentenceIdPattern).matcher(str1);
				boolean match = sentenceIdMatcher.find();

				if (str1.matches("^\\d.*")) {

					String[] cols = str1.trim().split("\t");
					String token = cols[1];
					line += token + " ";
					tokenCheck.add(token);
					size++;
				} else if (match && size > 0) {

					int id = Integer.valueOf(sentenceIdMatcher.group(1));

					List<CoreLabel> lcl = new ArrayList<CoreLabel>();
					List<CoreLabel> lcl7 = new ArrayList<CoreLabel>();
					for (List<CoreLabel> tmp : classifier.classify(line.trim())) {
						lcl.addAll(tmp);
					}
					for (List<CoreLabel> tmp : classifier7
							.classify(line.trim())) {
						lcl7.addAll(tmp);
					}

					for (int i = 0; i < size; i++) {
						CoreLabel cl = lcl.get(i);
						CoreLabel cl7 = lcl7.get(i);

						String token = cl.originalText();
						assert token.equals(tokenCheck.get(i));
						String ner = cl
								.get(CoreAnnotations.AnswerAnnotation.class);
						String ner7 = cl7
								.get(CoreAnnotations.AnswerAnnotation.class);
						out.println(lastId + "\t" + (i + 1) + "\t" + token
								+ "\t" + ner + "\t" + ner7);
					}
					line = "";
					lastId = id;
					size = 0;
					tokenCheck = new ArrayList<String>();
				} else if (match) {
					int id = Integer.valueOf(sentenceIdMatcher.group(1));
					lastId = id;
				}
			}

			in1.close();

			List<CoreLabel> lcl = new ArrayList<CoreLabel>();
			List<CoreLabel> lcl7 = new ArrayList<CoreLabel>();
			for (List<CoreLabel> tmp : classifier.classify(line.trim())) {
				lcl.addAll(tmp);
			}
			for (List<CoreLabel> tmp : classifier7.classify(line.trim())) {
				lcl7.addAll(tmp);
			}

			for (int i = 0; i < size; i++) {
				CoreLabel cl = lcl.get(i);
				CoreLabel cl7 = lcl7.get(i);

				String token = cl.originalText();

				assert token.equals(tokenCheck.get(i));
				String ner = cl.get(CoreAnnotations.AnswerAnnotation.class);
				String ner7 = cl7.get(CoreAnnotations.AnswerAnnotation.class);
				out.println(lastId + "\t" + (i + 1) + "\t" + token + "\t" + ner
						+ "\t" + ner7);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws IOException {
		String file = args[0];
		read(file);
	}

}
