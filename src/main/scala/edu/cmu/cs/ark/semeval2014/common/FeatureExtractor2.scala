package edu.cmu.cs.ark.semeval2014.common

abstract class FeatureExtractor2(input: Input) {
    def features(word1: Int, word2: Int, label: String) : FeatureVector
}

