package edu.cmu.cs.ark.semeval2014.amr

import edu.cmu.cs.ark.semeval2014.amr.graph.Graph
import edu.cmu.cs.ark.semeval2014.common.FeatureVector

case class DecoderResult(graph: Graph, features: FeatureVector, score: Double)

