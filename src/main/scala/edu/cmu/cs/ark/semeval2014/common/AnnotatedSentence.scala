package edu.cmu.cs.ark.semeval2014.common


case class AnnotatedSentence(sentence: Array[String],
                             dependencies: Annotation[Array[Dependency]],
                             pos: Annotation[Array[String]])
