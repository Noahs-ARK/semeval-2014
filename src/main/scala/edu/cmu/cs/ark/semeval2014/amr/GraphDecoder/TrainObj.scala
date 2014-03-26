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

    private val labelset : Array[String] = getLabelset(options).map(x => x._1)  // we only need the labels, not determinism constrains

    def decode(i: Int, weights: FeatureVector) : FeatureVector = {
        val decoder = Decoder(options)
        decoder.features.weights = weights
        return decoder.decode(Input(inputAnnotatedSentences(i), inputGraphs(i))).features
    }

    def oracle(i: Int, weights: FeatureVector) : FeatureVector = {
        val oracle = new Oracle(getFeatures(options), labelset)
        oracle.features.weights = weights
        return oracle.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i))).features
    }

    def costAugmented(i: Int, weights: FeatureVector) : FeatureVector = {
        val decoder = Decoder(options)
        val costAug = new CostAugmented(Decoder(options), options.getOrElse('trainingCostScale,"1.0").toDouble)
        costAug.features.weights = weights
        return costAug.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i))).features
    }

    def countPercepts(i: Int) : FeatureVector = {
        val countPer = new CountPercepts(getFeatures(options), labelset)
        return countPer.decode(Input(inputAnnotatedSentences(i), inputGraphs(i))).features
    }

    def train {
        train(FeatureVector(labelset))
    }
}

