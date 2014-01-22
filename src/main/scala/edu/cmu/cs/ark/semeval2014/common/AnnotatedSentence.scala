package edu.cmu.cs.ark.semeval2014.common

case class AnnotatedSentence(tokenized: Array[String],
                             dependencies: Array[Dependency],
                             pos: Array[String])

