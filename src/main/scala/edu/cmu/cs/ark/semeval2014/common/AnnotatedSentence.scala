package edu.cmu.cs.ark.semeval2014.common

import edu.cmu.cs.ark.semeval2014.amr.graph.Graph

case class AnnotatedSentence(sentence: Array[String],
                             dependencies: Annotation[Array[Dependency]],
                             pos: Annotation[Array[String]],
                             graph: Option[Graph]) // TODO: not needed
