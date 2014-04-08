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
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._

class TrainObj(val options : Map[Symbol, String]) extends edu.cmu.cs.ark.semeval2014.amr.Train.TrainObj(options) {

    private val labelset : Array[String] = getLabelset(options).map(x => x._1)  // we only need the labels, not determinism constrains

    //val decoder = Decoder(options)
    //val oracle = new Oracle(getFeatures(options), labelset)
    //val costAug = new CostAugmented(Decoder(options), options.getOrElse('trainingCostScale,"1.0").toDouble, options.getOrElse('precRecallTradeoff,"0.5").toDouble)
    //val countPer = new CountPercepts(getFeatures(options), labelset)

    def decode(i: Int, weights: FeatureVector) : (FeatureVector, Double, String) = {
        val decoder = Decoder(options)
        decoder.features.weights = weights
        val result = decoder.decode(Input(inputAnnotatedSentences(i), inputGraphs(i)))
        return (result.features, result.score, result.graph.toConll(inputAnnotatedSentences(i)))
    }

    def oracle(i: Int, weights: FeatureVector) : (FeatureVector, Double) = {
        val oracle = new Oracle(getFeatures(options), labelset)
        oracle.features.weights = weights
        val result = oracle.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i)))
        return (result.features, result.score)
    }

    def costAugmented(i: Int, weights: FeatureVector, scale: Double) : (FeatureVector, Double) = {
        val decoder = Decoder(options)
        decoder.features.weights = weights
        val costAug = new CostAugmented(Decoder(options), scale, options.getOrElse('precRecallTradeoff,"0.5").toDouble)
        costAug.features.weights = weights
        val result = costAug.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i)))
        return (result.features, result.score)
    }

    def countPercepts(i: Int) : FeatureVector = {
        val countPer = new CountPercepts(getFeatures(options), labelset)
        return countPer.decode(Input(inputAnnotatedSentences(i), inputGraphs(i))).features
    }

    def train {
        train(FeatureVector(labelset))
    }

    def f1SufficientStatistics(i: Int, weights: FeatureVector) : (Double, Double, Double) = {
        // returns (num_correct, num_predicted, num_gold)
        val decoder = Decoder(options)
        decoder.features.weights = weights
        val result = decoder.decode(Input(inputAnnotatedSentences(i), inputGraphs(i)))
        
        val oracle = new Oracle(getFeatures(options), labelset)
        oracle.features.weights = weights
        val oracleResult = oracle.decode(Input(inputAnnotatedSentences(i), oracleGraphs(i)))

        return SDPGraph.evaluate(result.graph.asInstanceOf[SDPGraph], oracleResult.graph.asInstanceOf[SDPGraph])
    }
}

