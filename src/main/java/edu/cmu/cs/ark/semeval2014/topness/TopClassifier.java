package edu.cmu.cs.ark.semeval2014.topness;

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence;
import edu.cmu.cs.ark.semeval2014.utils.Corpus;
import mltools.classifier.BinaryLogreg;
import scala.Option;
import util.U;

import java.io.IOException;

public class TopClassifier implements TopnessScorer {
	BinaryLogreg<TokenCtx> logreg;
	
	static class TokenCtx {
		int t = -1;
		InputAnnotatedSentence sent;
		TokenCtx(int _t, InputAnnotatedSentence _sent) {
			t=_t; sent=_sent;
		}
	}
	
	public TopClassifier() {
		logreg = new BinaryLogreg<>();
		logreg.featureExtractors.add(new TopFeats());
	}
	
	static class TopFeats extends mltools.classifier.FeatureExtractor<TokenCtx> {
		@Override
		public void computeFeatures(TokenCtx ex, mltools.classifier.FeatureExtractor.FeatureAdder fa) {
			final int tokenIdx = ex.t;
			String pos = ex.sent.pos[tokenIdx];
			fa.add("pos=" + pos);
//			fa.add("pos=" + pos + "&lcword=" + ex.sent.sentence()[ex.t].toLowerCase(), 0.2);
			fa.add("t=" + tokenIdx);
			fa.add("pos=" + pos + "&t=" + tokenIdx);
			final Option<Object> oDepth = ex.sent.syntacticDependencies.depths().apply(tokenIdx);
			fa.add("depth=" + (oDepth.isDefined() ? oDepth.get() : "NULL"));
			
//			int q5 = (int) Math.floor(ex.t*1.0 / ex.sent.size() * 5);
//			fa.add("q5=" + ex.t);
//			fa.add("pos_and_q5=" + pos + "&" + q5);
		}
	}
	
	public void train(String depFile, String modelOutputFile) {
		U.p("Training topness classifier");
		
		InputAnnotatedSentence[] inputSentences = Corpus.getInputAnnotatedSentences(depFile);
		for (InputAnnotatedSentence s1 : inputSentences) {
			for (int t=0; t<s1.size(); t++) {
				boolean y = s1.isTop[t];
				logreg.addTrainingExample(y, new TokenCtx(t, s1));
			}
		}
//		logreg.verbose = true;
		logreg.doTraining(modelOutputFile);
	}
	public void loadModel(String modelFile) throws IOException {
		logreg.loadModel(modelFile);
	}
	
	public double[] predictTopnessProbs(InputAnnotatedSentence sent) {
		double[] probs = new double[sent.size()];
		for (int t=0; t<probs.length; t++) {
			probs[t] = logreg.predictLabelProb(new TokenCtx(t, sent));
		}
		return probs;
	}
	
	public static void main(String[] args) {
		
		TopClassifier c = new TopClassifier();
//		c.train(args[0]);
		
	}
	
	@Override
	public double topness(InputAnnotatedSentence sent, int t) {
		return logreg.predictLabelProb(new TokenCtx(t, sent));
	}
}
