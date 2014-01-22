package edu.cmu.cs.ark.semeval2014.common

abstract class FeatureExtractor2(snt: AnnotatedSentence) {
    def features(word1: Int, word2: Int, label: String) : FeatureVector
}

