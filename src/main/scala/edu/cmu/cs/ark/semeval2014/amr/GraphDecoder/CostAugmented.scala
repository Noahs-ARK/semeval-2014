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
//import scala.collection.mutable.Map
import scala.collection.concurrent.{TrieMap => Map}
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._

class CostAugmented(val decoder: Decoder, costScale: Double, precRecTradeoff: Double) extends Decoder {
    // precRecTradeoff: 1 = only prec errors, 0 = only recall errors
    val features = decoder.features
    decoder.features.addFeatureFunction("CostAugEdgeId")

    def decode(input: Input) : DecoderResult = {    // WARNING: input should be same as input to oracle decoder
        val oracleDecoder = new Oracle(decoder.features.featureNames,       // "CostAugEdgeId" already in featureNames
                                       decoder.features.weights.labelset)
        val oracle = oracleDecoder.decode(input)
        val addCost = oracle.features.filter(x => x.startsWith("CA:"))
        for ((feat, values) <- addCost.fmap) {
            values.unconjoined = 1.0
            values.conjoined = Map()
        }
        features.weights += (2.0 * precRecTradeoff * costScale) * addCost  // add costScale to edge weights that don't match oracle (penalize precision type errors)
        features.weights -= (2.0 * (1.0 - 2.0 * precRecTradeoff) * costScale) * oracle.features.filter(x => x.startsWith("CA:")) // subtract costScale from ones that match (penalize recall type errors)
        val result = decoder.decode(Input(input.inputAnnotatedSentence, input.graph.duplicate.clearEdges))
        val score = features.weights.dot(result.features)
        features.weights -= (2.0 * precRecTradeoff * costScale) * addCost  // undo the changes
        features.weights += (2.0 * (1.0-2.0 * precRecTradeoff) * costScale) * oracle.features.filter(x => x.startsWith("CA:"))
        val feats = result.features.filter(x => !x.startsWith("CA:"))
        return DecoderResult(result.graph, feats, score)
    }
}

