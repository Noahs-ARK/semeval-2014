package edu.cmu.cs.ark.semeval2014.lr;

import java.util.ArrayList;

import util.Arr;

public class NumberizedSentence {
	int T;  // sentence length

	int[] iIndexes;
	int[] jIndexes;
	int[] featnums;
	int[] labels;
	float[] values;
	int nnz;
	
	void add(int i, int j, int featnum, int label, double value) {
		growIfNecessary();
		iIndexes[nnz] = i;
		jIndexes[nnz] = j;
		featnums[nnz] = featnum;
		labels[nnz] = label;
		values[nnz] = (float) value;
		nnz++;
		totalNNZ++;
	}
	int i(int kk) { return iIndexes[kk]; }
	int j(int kk) { return jIndexes[kk]; }
	int featnum(int kk) { return featnums[kk]; }
	int label(int kk) { return labels[kk]; }
	float value(int kk) { return values[kk]; }
	
	static int totalNNZ = 0;
	
	static final int INIT_SIZE = 100;
	static final double GROWTH_MULTIPLIER = 1.5;
	
	public NumberizedSentence() {
	}
	NumberizedSentence(int sentenceLength) {
		T = sentenceLength;
		
		nnz=0;
		iIndexes = new int[INIT_SIZE];
		jIndexes = new int[INIT_SIZE];
		featnums = new int[INIT_SIZE];
		labels = new int[INIT_SIZE];
		values = new float[INIT_SIZE];
		
	}
	
	static int[] grow(int[] x) {
		int[] y = new int[(int) Math.floor(GROWTH_MULTIPLIER * x.length)];
		System.arraycopy(x,0, y,0, x.length);
		return y;
	}
	static float[] grow(float[] x) {
		float[] y = new float[(int) Math.floor(GROWTH_MULTIPLIER * x.length)];
		System.arraycopy(x,0, y,0, x.length);
		return y;
	}
	static double[] grow(double[] x, double growth_multiplier) {
		double[] y = new double[(int) Math.floor(growth_multiplier * x.length)];
		System.arraycopy(x,0, y,0, x.length);
		return y;
	}
	static double[] growToLength(double[] x, int newlength) {
		assert newlength >= x.length;
		double[] y = new double[newlength];
		System.arraycopy(x,0, y,0, x.length);
		return y;
	}

	void growIfNecessary() {
		int curCapacity = iIndexes.length;
		if (nnz == curCapacity) {
			iIndexes = grow(iIndexes);
			jIndexes = grow(jIndexes);
			featnums = grow(featnums);
			labels = grow(labels);
			values = grow(values);
		}
	}
}
