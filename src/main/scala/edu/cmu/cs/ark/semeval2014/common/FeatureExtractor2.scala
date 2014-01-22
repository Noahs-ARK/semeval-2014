package edu.cmu.cs.ark.semeval2014.common

// Abstract class for implementing feature extractors for two words and a dependency label

//  This class translates to Java code:
//    public abstract class FeatureExtractor1 {
//      public FeatureExtractor1(AnnotatedSentence);  // Constructor is passed the input annotated sentence
//      public abstract FeatureVector features(int, int, String);  // should compute the features
//    }

abstract class FeatureExtractor2(snt: AnnotatedSentence) {
    def features(word1: Int, word2: Int, label: String) : FeatureVector
}

