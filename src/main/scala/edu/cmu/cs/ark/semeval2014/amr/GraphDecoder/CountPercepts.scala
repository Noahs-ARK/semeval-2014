package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import edu.cmu.cs.ark.semeval2014.amr._

class CountPercepts(featureNames: List[String], labelSet: Array[String]) extends Decoder {
    val features = new Features(featureNames, labelSet)

    def decode(input: Input) : DecoderResult = {
        features.input = input
        val nodes = input.graph.nodes
        var perceptVector = FeatureVector(labelSet)
        val ones = ValuesList(1.0, (0 until labelSet.size).map(x => Conjoined(x, 1.0)).toList)

        var numPercepts = 0.0
        for { node1 <- nodes
              node2 <- nodes } {
            features.setupNodes(node1, node2)
            val percepts = features.localFeatures(node1, node2)
            numPercepts += percepts.size
            perceptVector += percepts.map(x => (x._1, ones))
        }

        return DecoderResult(input.graph, perceptVector, numPercepts)
    }
}

