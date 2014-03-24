package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
import java.lang.Math.floor
import java.lang.Math.min
import java.lang.Math.max
import scala.io.Source
import scala.io.Source.stdin
import scala.io.Source.fromFile
import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import scala.util.parsing.combinator._
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._

class TrainObj(val options : Map[Symbol, String]) extends edu.cmu.cs.ark.semeval2014.amr.Train.TrainObj(options) {

    val decoder = Decoder(options)
    val oracle = new Oracle(getFeatures(options), decoder.features.weights.labelset)
    val costAug = new CostAugmented(Decoder(options), options.getOrElse('trainingCostScale,"1.0").toDouble)
    val weights = decoder.features.weights
    oracle.features.weights = weights
    costAug.features.weights = weights

    val outputFormat = options.getOrElse('outputFormat,"triples").split(",").toList

    def decode(i: Int) : FeatureVector = {
        return decoder.decode(Input(inputAnnotatedSentences(i), inputGraphs(i))).features
    }

    def oracle(i: Int) : FeatureVector = {
        return oracle.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i))).features
    }

    def costAugmented(i: Int) : FeatureVector = {
        return costAug.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i))).features
    }
}

