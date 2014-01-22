package edu.cmu.cs.ark.semeval2014.common

case class AnnotatedSentence(sentence: Array[String],
                             dependencies: Array[Dependency],
                             pos: Array[String])
