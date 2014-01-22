package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import edu.cmu.cs.ark.semeval2014.common.FeatureVector
import edu.cmu.cs.ark.semeval2014.amr.{Input, DecoderResult}

class Oracle(featureNames: List[String])
    extends Decoder(featureNames) {
    // Base class has defined:
    // val features: Features

    private var inputSave: Input = _
    def input : Input = inputSave
    def input_= (i: Input) {
        inputSave = i
        features.input = i
    }

    def decode() : DecoderResult = {
        val graph = input.graph.get
        var feats = new FeatureVector()

        for { node1 <- graph.nodes
              (label, node2) <- node1.relations } {
            feats += features.localFeatures(node1, node2, label)
        }
        feats += features.rootFeatures(graph.root)

        return DecoderResult(graph, feats, features.weights.dot(feats))
    }
}

