package edu.cmu.cs.ark.semeval2014.common

abstract class FeatureExtractor1(input: Input) {
    def features(word: Int) : FeatureVector
}

