package edu.cmu.cs.ark.semeval2014.common

// In Java, this class is:
// public class AnnotatedSentence {
//     public AnnotatedSentence(String[], Dependency[], String[]);
//     public String[] sentence();
//     public Dependency[] dependencies();
//     public String[] pos();
// }

case class AnnotatedSentence(sentence: Array[String],
                             dependencies: Array[Dependency],
                             pos: Array[String])
