package edu.cmu.cs.ark.semeval2014.amr

import edu.cmu.cs.ark.semeval2014.common.SyntacticDependency
import edu.cmu.cs.ark.semeval2014.amr.graph.Graph

case class Input(sentence: Array[String],
                 dependencies: Annotation[Array[SyntacticDependency]],
                 pos: Annotation[Array[String]],
                 graph: Option[Graph])
