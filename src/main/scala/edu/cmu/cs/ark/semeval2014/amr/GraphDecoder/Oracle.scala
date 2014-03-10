package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common._
import edu.cmu.cs.ark.semeval2014.amr._

class Oracle(featureNames: List[String]) extends Decoder {
    val features = new Features(featureNames)

    def decode(input: Input) : DecoderResult = {
        features.input = input
        val graph = input.graph
        var feats = new FeatureVector()

        for { node1 <- graph.nodes
              (label, node2) <- node1.relations } {
            features.setupNodes(node1, node2)
            feats += features.localFeatures(node1, node2, label)
        }

        return DecoderResult(graph, feats, features.weights.dot(feats))
    }
}

