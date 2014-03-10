package mltools.classifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import mltools.classifier.FeatureExtractor.FeatureAdder;


import util.Arr;
import util.BasicFileIO;
import util.LBFGS;
import util.U;
import util.Vocabulary;
import util.LBFGS.Status;
import util.misc.Pair;

public class BinaryLogreg<ExampleType> {
	public List<FeatureExtractor<ExampleType>> featureExtractors;
	public Vocabulary featVocab;

	public double biasCoef = 0;
	/** size J */
	public double[] featCoefs;

	/** client is responsible for filling this up. String is the label. */
	public List<BinaryLabeledExample<ExampleType>> trainingData;
	private List<ModelExample> trainingModelExamples;
	public double penalty = 1;
	public int maxIter = 500;
	public double tol = 1e-5;

	public double l1penalty() { return 0; }
	public double l2penalty() { return penalty; }
	
	/** verbose about training? */
	public boolean verbose = false;
	/** verbose about feature extraction? */
	public boolean dumpMode = false;
	
	public BinaryLogreg() {
		featureExtractors = new ArrayList<>();
		featVocab = new Vocabulary();
		trainingData = new ArrayList<>();
	}
	
	public void addTrainingExample(boolean label, ExampleType ex) {
		trainingData.add(BinaryLabeledExample.make(label, ex));
	}
	
	//// inference.... is pretty simple for this model ////
	
	/** the labels' unnorm logprobs (negative energy)
	 * returns vector \in R^K */
	double calcLabelScore(ModelExample ex) {
		double s = biasCoef;
		for (Pair<Integer,Double> fv : ex.observationFeatures) {
				s += fv.second * featCoefs[fv.first];
		}
		return s;
	}
	/** returns vector \in Simplex(K) */
	double calcLabelProb(ModelExample ex) {
		double s = calcLabelScore(ex);
		return 1.0 / (1 + Math.exp(-s));
	}
	public double predictLabelProb(ExampleType ex) {
		ModelExample mx = new ModelExample();
		extractFeatures(ex, mx);
		return calcLabelProb(mx);
	}
	
	//// feature dimension reshaping management ... only to make optimizer software happier. ////
	
	public int biasFeature_to_flatID() {
		return 0;
	}
	public int observationFeature_to_flatID(int featID) {
		return 1 + featID;
	}
	public int flatIDsize() {
		return 1 + featVocab.size();
	}
	
	public double[] convertCoefsToFlat() {
		double[] flatCoefs = new double[flatIDsize()];
		flatCoefs[biasFeature_to_flatID()] = biasCoef;
		for (int feat=0; feat < featVocab.size(); feat++) {
			flatCoefs[observationFeature_to_flatID(feat)] = featCoefs[feat];
		}
		return flatCoefs;
	}
	
	public void setCoefsFromFlat(double[] flatCoefs) {
		biasCoef = flatCoefs[0];
		for (int feat=0; feat < featVocab.size(); feat++) {
			featCoefs[feat] = flatCoefs[observationFeature_to_flatID(feat)];
		}
	}
	
	////////////////// only for training mode /////////////////////

	
	public void lockdownVocabAndAllocateCoefs() {
		featVocab.lock();
		U.pf("%d feature types\n", featVocab.size());
		allocateCoefs(featVocab.size());
	}

	public void allocateCoefs(int numFeats) {
		featCoefs = new double[numFeats];
	}

	public void sgdLoop(int numiter, double rate) {
		for (int iter=0; iter<numiter; iter++) {
			double ll = 0;
			for (ModelExample ex : trainingModelExamples) {
				ll += sgdUpdate(ex, rate);
			}
			if (verbose) {
				U.pf("sgd iter: LL %g\n", ll);	
			} else {
				U.pf("s");	
			}
		}
	}

	/** the numberized sparse <ExT>featurevector representation of an example */
	private static class ModelExample {
		public int label = -1;
		public ArrayList< Pair<Integer, Double>> observationFeatures = new ArrayList<>();
	}
	
	public void optimizationLoop() {
		double[] flatCoefs = convertCoefsToFlat();
		LBFGS.Params params = new LBFGS.Params();
		params.max_iterations = maxIter;
		params.orthantwise_c = l1penalty();
		params.orthantwise_start = observationFeature_to_flatID(0);
		params.delta = params.epsilon = tol;   // only 'delta' will matter
		params.m = 6;
		
		LBFGS.Result r = LBFGS.lbfgs(flatCoefs, new GradientCalculator(),
			new LBFGS.ProgressCallback() {
				@Override
				public int apply(double[] x, double[] g, double fx,
						double xnorm, double gnorm, double step, int n, int iterNum, Status ls) {
					if (verbose) {
						U.pf("iter %d: obj=%g active=%d gnorm=%g\n", iterNum, fx, Arr.countNonZero(x), gnorm);
					} else {
						U.pf(".");
					}
					return 0;
				}
		}, params);
		System.out.println(" Finished status=" + r.status);
		setCoefsFromFlat(flatCoefs);
	}

	private class GradientCalculator implements LBFGS.Function {
		@Override
		public double evaluate(double[] flatCoefs, double[] g, int n, double step) {
			setCoefsFromFlat(flatCoefs);
			Arr.fill(g,0);
			double loglik = 0;
			for (ModelExample ex : trainingModelExamples) {
				loglik += computeGradientAndLL(ex, g);
			}
			Arr.multiplyInPlace(g, -1);
			addL2regularizerGradient(g, flatCoefs);
			return -loglik + regularizerValue(flatCoefs);
		}
	}

	/** return the label's loglik before the update */ 
	public double sgdUpdate(ModelExample ex, double rate) {
		double p = calcLabelProb(ex);
		int y = ex.label;
		double resid = y-p;
		biasCoef += rate*resid;
		for (Pair<Integer,Double> fv : ex.observationFeatures) {
			featCoefs[fv.first] += rate*resid*fv.second;
		}
		return Math.log(y==1 ? p : (1-p));
	}


	/**
	 * Training-only
	 * 
	 * add-in loglik gradient (direction of higher likelihood), and return the loglik of the sentence
	 **/
	public double computeGradientAndLL(ModelExample ex, double[] grad) {
		assert grad.length == flatIDsize();
		double ll = 0;
		double p = calcLabelProb(ex);
		int y = ex.label;
		double resid = y - p;
		biasCoef += resid;
		for (Pair<Integer,Double> fv : ex.observationFeatures) {
			grad[observationFeature_to_flatID(fv.first)] += resid;
		}
		return ll;
	}

	/** this is actually *negative* loglik, unlike all other routines which are positive */
	private void addL2regularizerGradient(double[] grad, double[] flatCoefs) {
		double l2pen = l2penalty();
		assert grad.length == flatCoefs.length;
		for (int f=0; f < flatCoefs.length; f++) {
			grad[f] += l2pen * flatCoefs[f]; 
		}
	}

	/**
	 * lambda_2 * (1/2) sum (beta_j)^2  +  lambda_1 * sum |beta_j|
	 * the library only wants the first term
	 */
	 private double regularizerValue(double[] flatCoefs) {
		double l2_term = 0;
		for (int f=0; f < flatCoefs.length; f++) {
			l2_term += Math.pow(flatCoefs[f], 2);
		}
		return 0.5*l2penalty()*l2_term;
	}

	 public void doTraining(String modelOutputFilename) {
		assert trainingData.size() > 0 : "Please fill up trainingData before starting training";
		
		U.p("Extracting features");
		trainingModelExamples = new ArrayList<>();
		for (BinaryLabeledExample<ExampleType> lx : trainingData) {
			ModelExample mx = new ModelExample();
			mx.label = lx.label ? 1 : 0;
			if (dumpMode)  U.pf("LABEL=%s\tEXAMPLE\t%s\n", lx.label, lx.example);
			extractFeatures(lx.example, mx);
			trainingModelExamples.add(mx);
		}
		
		lockdownVocabAndAllocateCoefs();
		
		U.p("Training");
		sgdLoop(10, 0.1);
		optimizationLoop();
		
		try {
			U.p("Saving model to " + modelOutputFilename);
			saveModelAsText(modelOutputFilename);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	 private void extractFeatures(ExampleType ix, final ModelExample mx) {
		 assert featureExtractors.size() > 0 : "No feature extractors defined";
		 class MyAdder extends FeatureAdder {
			@Override
			public void add(String featurename, double value) {
				int featnum = featVocab.num(featurename);
				if (featnum==-1) return;
				if (dumpMode) U.pf("F\t%s\t%s\n", featurename, value);
				mx.observationFeatures.add(U.pair(featnum,value));
			}
		 }
		 FeatureAdder myAdder = new MyAdder();
		 for (FeatureExtractor fe : featureExtractors) {
			 fe.computeFeatures(ix, myAdder);
		 }
	 }
	 
	double calcObj() {
		 GradientCalculator g = new GradientCalculator();
		 double[] b = convertCoefsToFlat();
		 return g.evaluate(b, new double[flatIDsize()], flatIDsize(), -1);
	 }

	//////////////////// model serialization routines //////////////////////

	public void saveModelAsText(String outputFilename) throws IOException {
		BufferedWriter writer = BasicFileIO.openFileToWriteUTF8(outputFilename);
		PrintWriter out = new PrintWriter(writer);

		out.printf("BIAS\t%.4g\n", biasCoef);
		
		assert featVocab.size() == featCoefs.length;
		for (int f=0; f < featVocab.size(); f++) {
			if (featCoefs[f]==0) continue;
			out.printf("F\t%s\t%.4g\n", featVocab.name(f), featCoefs[f]);
		}
		out.close();
		writer.close();
	}

	public void loadModel(String filename) throws IOException {
		BufferedReader reader = BasicFileIO.openFileOrResource(filename);
		String line;
		
		ArrayList< Pair<Integer, Double> > obsCoefs  = new ArrayList<>();

		while ( (line = reader.readLine()) != null ) {
			String[] parts = line.split("\t");
			if (parts[0].equals("BIAS")) {
				double value = Double.valueOf(parts[1]);
				this.biasCoef = value;
			}
			else if (parts[0].equals("F")) {
				int feat = this.featVocab.num(parts[1]);
				double w = Double.valueOf(parts[2]);
				obsCoefs.add(U.pair(feat,w));
			}
			else { throw new RuntimeException("bad model line format"); }
		}
		
		this.lockdownVocabAndAllocateCoefs();
		
		for (Pair<Integer,Double> x : obsCoefs) {
			this.featCoefs[x.first] = x.second;
		}
		reader.close();
	}
	public static BinaryLogreg loadNewModel(String filename) throws IOException {
		BinaryLogreg model = new BinaryLogreg();
		model.loadNewModel(filename);
		return model;
	}

	private static void test1() throws IOException {
		class MyObject {
			int a,b,c;
			MyObject(int a, int b, int c) { this.a=a; this.b=b; this.c=c; }
		}
		
		BinaryLogreg<MyObject> m = new BinaryLogreg<>();
		m.penalty = 1e-5;
		m.featureExtractors.add(new FeatureExtractor<MyObject>() {
			@Override
			public void computeFeatures(MyObject ex, FeatureAdder fa) {
				fa.add("feat_a", ex.a);
				fa.add("feat_b", ex.b);
				fa.add("feat_c", ex.c);
			}
		});
		m.trainingData.add(BinaryLabeledExample.make(false, new MyObject(0,0,2)));
		m.trainingData.add(BinaryLabeledExample.make(false, new MyObject(0,0,1)));
		m.trainingData.add(BinaryLabeledExample.make(true, new MyObject(3,0,0)));
		m.trainingData.add(BinaryLabeledExample.make(true, new MyObject(5,0,1)));
		m.doTraining("test.model");
		
		BinaryLogreg<MyObject> m2 = BinaryLogreg.loadNewModel("test.model");
		m2.featureExtractors = m.featureExtractors;
		for (BinaryLabeledExample<MyObject> ex : m.trainingData) {
			U.p(m2.predictLabelProb(ex.example));
		}
	}
	
	public static void main(String[] args) throws IOException { test1(); }
}

