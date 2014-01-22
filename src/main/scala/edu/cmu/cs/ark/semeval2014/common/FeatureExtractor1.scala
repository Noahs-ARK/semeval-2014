package edu.cmu.cs.ark.semeval2014.common

// Abstract class for implementing feature extractors for single words

//  This class translates to Java code:
//    public abstract class FeatureExtractor1 {
//      public FeatureExtractor1(AnnotatedSentence);  // Constructor is passed the input annotated sentence
//      public AnnotatedSentence snt();               // input annotated sentence is member function "snt"
//      public abstract FeatureVector features(int);  // should compute the features
//    }

abstract class FeatureExtractor1(val snt: AnnotatedSentence) {
    def features(word: Int) : FeatureVector
}

