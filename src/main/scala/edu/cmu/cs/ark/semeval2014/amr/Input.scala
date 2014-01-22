package edu.cmu.cs.ark.semeval2014.amr

import edu.cmu.cs.ark.semeval2014.common.{Dependency, Annotation}
import edu.cmu.cs.ark.semeval2014.amr.graph.Graph

case class Input(sentence: Array[String],
                 dependencies: Annotation[Array[Dependency]],
                 pos: Annotation[Array[String]],
                 graph: Option[Graph])
