package edu.cmu.cs.ark.semeval2014.amr.Train

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
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import edu.cmu.cs.ark.semeval2014.utils._

abstract class TrainObj(options: Map[Symbol, String])  {

    def decode(i: Int, weights: FeatureVector) : (FeatureVector, Double)
    def oracle(i: Int, weights: FeatureVector) : (FeatureVector, Double)
    def costAugmented(i: Int, weights: FeatureVector) : (FeatureVector, Double)
    def countPercepts(i: Int) : FeatureVector
    def train : Unit

    ////////////////// Training Setup ////////////////

    val passes = options.getOrElse('trainingPasses, "30").toInt
    val stepsize = options.getOrElse('trainingStepsize, "1.0").toDouble
    val regularizerStrength = options.getOrElse('trainingRegularizerStrength, "0.0").toDouble
    val loss = options.getOrElse('trainingLoss, "SVM")
    if (!options.contains('model)) {
        System.err.println("Error: No model filename specified"); sys.exit(1)
    }

    val inputAnnotatedSentences = Input.loadInputAnnotatedSentences(options)
    val inputGraphs = Input.loadSDPGraphs(options, oracle = false)
    val oracleGraphs = Input.loadSDPGraphs(options, oracle = true)
    assert(inputAnnotatedSentences.size == inputGraphs.size && inputGraphs.size == oracleGraphs.size, "sdp and dep file lengths do not match")

    var optimizer: Optimizer = options.getOrElse('trainingOptimizer, "Adagrad") match {
        case "Adagrad" => new Adagrad()
        case "HOLS" => new HOLS(options, (x,y) => countPercepts(y), inputGraphs.size)
        case "SSGD" => new SSGD()
        case x => { System.err.println("Error: unknown training optimizer " + x); sys.exit(1) }
    }

    if (options.getOrElse('trainingMiniBatchSize,"1").toInt > 1) {
        optimizer = new MiniBatch(optimizer, options('trainingMiniBatchSize).toInt)
    }

/*  Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() {
            System.err.print("Writing out weights... ")
            val file = new java.io.PrintWriter(new java.io.File(options('model)), "UTF-8")
            try { file.print(weights.toString) }
            finally { file.close }
            System.err.println("done")
        }
    }) */

    /////////////////////////////////////////////////

    def gradient(i: Int, weights: FeatureVector) : (FeatureVector, Double) = {
        if (loss == "Perceptron") {
            val (grad, score) = decode(i, weights)
            val o = oracle(i, weights)
            grad -= o._1
            (grad, score - o._2)
        } else if (loss == "SVM") {
            val (grad, score) = costAugmented(i, weights)
            val o = oracle(i, weights)
            grad -= o._1
            (grad, score - o._2)
        } else {
            System.err.println("Error: unknown training loss " + loss); sys.exit(1)
            (FeatureVector(weights.labelset), 0.0)
        }
    }

    def trainingObserver(pass: Int, weights: FeatureVector) : Boolean = {
        if (options.contains('trainingSaveInterval) && pass % options('trainingSaveInterval).toInt == 0 && pass > 0) {
            val file = new java.io.PrintWriter(new java.io.File(options('model) + ".iter" + pass.toString), "UTF-8")
            try { file.print(weights.toString) }
            finally { file.close }
        }
        return true
    }

    def train(initialWeights: FeatureVector) {
        if (options.getOrElse('trainingOptimizer, "Adagrad") == "HOLS") {
            trainHOLS(initialWeights, 100)
        } else {
            val weights = optimizer.learnParameters(
                (i,w) => gradient(i,w),
                initialWeights,
                inputGraphs.size,
                passes,
                stepsize,
                options.getOrElse('trainingL2RegularizerStrength, "0.0").toDouble,
                List("Bias"),   // don't regularize the bias terms
                trainingObserver,
                avg = false)
            System.err.print("Writing out weights... ")
            val file = new java.io.PrintWriter(new java.io.File(options('model)), "UTF-8")
            try { file.print(weights.toString) }
            finally { file.close }
            System.err.println("done")
        }
    }

    def trainHOLS(initialWeights: FeatureVector, preTrainSize: Int) {
        var weights = initialWeights
        logger(0, "*********** HOLS pre-training with Adagrad ************")
        weights = (new Adagrad()).learnParameters(
            (i, w) => gradient(i, w),
            weights,
            preTrainSize,
            10,
            stepsize,
            0.0,
            List("Bias"),   // don't regularize the bias terms
            (p, w) => true,
            avg = false)
        logger(0, "weights l2 = "+weights.l2norm.toString)
        var file = new java.io.PrintWriter(new java.io.File(options('model)+".pretrain"), "UTF-8")
        try { file.print(weights.toString) }
        finally { file.close }

        weights = optimizer.learnParameters(
            (i, w) => gradient(i + preTrainSize, w),
            weights,
            inputGraphs.size - preTrainSize,
            passes,
            stepsize,
            0.0,
            List("Bias"),   // don't regularize the bias terms
            trainingObserver,
            avg = false)
        System.err.print("Writing out weights... ")
        file = new java.io.PrintWriter(new java.io.File(options('model)), "UTF-8")
        try { file.print(weights.toString) }
        finally { file.close }
        System.err.println("done")
    }
}

