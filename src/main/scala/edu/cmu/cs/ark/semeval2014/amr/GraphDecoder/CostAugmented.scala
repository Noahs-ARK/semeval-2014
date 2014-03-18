package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder
import edu.cmu.cs.ark.semeval2014.amr._

import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
import java.lang.Math.floor
import java.lang.Math.min
import java.lang.Math.max
import scala.io.Source
import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

class CostAugmented(val decoder: Decoder, costScale: Double) extends Decoder {
    val features = decoder.features
    decoder.features.addFeatureFunction("CostAugEdgeId")

    def decode(input: Input) : DecoderResult = {    // WARNING: input should be same as input to oracle decoder
        val oracleDecoder = new Oracle(decoder.features.featureNames,       // "CostAugEdgeId" already in featureNames
                                       decoder.features.weights.labelset)
        val oracle = oracleDecoder.decode(input)
        features.weights -= costScale * oracle.features.filter(x => x.startsWith("CA:"))
        val result = decoder.decode(Input(input.inputAnnotatedSentence, input.graph.duplicate.clearEdges))
        features.weights += costScale * oracle.features.filter(x => x.startsWith("CA:"))
        val feats = result.features.filter(x => !x.startsWith("CA:"))
        return DecoderResult(result.graph, feats, features.weights.dot(feats))
    }
}

