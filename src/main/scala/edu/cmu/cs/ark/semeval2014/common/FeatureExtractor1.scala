package edu.cmu.cs.ark.semeval2014.common

abstract class FeatureExtractor1(snt: AnnotatedSentence) {
    def features(word: Int) : FeatureVector
}

