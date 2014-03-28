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
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import edu.cmu.cs.ark.semeval2014.utils._
import scala.concurrent.forkjoin.ForkJoinPool
import scala.collection.parallel._

abstract class TrainObj(options: Map[Symbol, String])  {

    def decode(i: Int, weights: FeatureVector) : (FeatureVector, Double)
    def oracle(i: Int, weights: FeatureVector) : (FeatureVector, Double)
    def costAugmented(i: Int, weights: FeatureVector, scale: Double) : (FeatureVector, Double)
    def countPercepts(i: Int) : FeatureVector
    def train : Unit
    def f1SufficientStatistics(i: Int, weights: FeatureVector) : (Double, Double, Double)

    ////////////////// Training Setup ////////////////

    val passes = options.getOrElse('trainingPasses, "30").toInt
    val stepsize = options.getOrElse('trainingStepsize, "1.0").toDouble
    val regularizerStrength = options.getOrElse('trainingRegularizerStrength, "0.0").toDouble
    var loss = options.getOrElse('trainingLoss, "SVM")
    if (!options.contains('model)) {
        System.err.println("Error: No model filename specified"); sys.exit(1)
    }

    var inputAnnotatedSentences = Input.loadInputAnnotatedSentences(options)
    var inputGraphs = Input.loadSDPGraphs(options, oracle = false)
    var oracleGraphs = Input.loadSDPGraphs(options, oracle = true)
    assert(inputAnnotatedSentences.size == inputGraphs.size && inputGraphs.size == oracleGraphs.size, "sdp and dep file lengths do not match")

    val HOLSPreTrainSize = 100

    var optimizer: Optimizer = options.getOrElse('trainingOptimizer, "Adagrad") match {
        case "Adagrad" => new Adagrad()
        case "HOLS" => new HOLS(options, (x,y) => countPercepts(y), (i, w) => f1SufficientStatistics(i+HOLSPreTrainSize,w), inputGraphs.size-HOLSPreTrainSize)
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
        val scale = options.getOrElse('trainingCostScale,"1.0").toDouble
        if (loss == "Perceptron") {
            val (grad, score) = decode(i, weights)
            val o = oracle(i, weights)
            grad -= o._1
            (grad, score - o._2)
        } else if (loss == "SVM") {
            val (grad, score) = costAugmented(i, weights, scale)
            val o = oracle(i, weights)
            grad -= o._1
            (grad, score - o._2)
        } else if (loss == "Ramp1") {
            val (grad, score) = costAugmented(i, weights, scale)
            val o = decode(i, weights)
            grad -= o._1
            (grad, score - o._2)
        } else if (loss == "Ramp2") {
            val (grad, score) = decode(i, weights)
            val o = costAugmented(i, weights, -1.0 * scale)
            grad -= o._1
            (grad, score - o._2)
        } else if (loss == "Ramp3") {
            val (grad, score) = costAugmented(i, weights, scale)
            val o = costAugmented(i, weights, -1.0 * scale)
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
        evalDev(weights)
        return true
    }

    def evalDev(weights: FeatureVector) {   // TODO: doesn't account for regularizer
        val devfile = "data/splits/sec20.pcedt.sdp"
        val (iASSave, iGSave, oGSave) = (inputAnnotatedSentences, inputGraphs, oracleGraphs)
        inputAnnotatedSentences = Corpus.getInputAnnotatedSentences(devfile+".dependencies")
        inputGraphs = Corpus.splitOnNewline(fromFile(devfile, "utf-8").getLines).map(
            x => SDPGraph.fromGold(x.split("\n"), true)).toArray
        oracleGraphs = Corpus.splitOnNewline(fromFile(devfile, "utf-8").getLines).map(
            x => SDPGraph.fromGold(x.split("\n"), false)).toArray
        assert(inputAnnotatedSentences.size == inputGraphs.size && inputGraphs.size == oracleGraphs.size, "sdp and dep file lengths do not match")

        //val numThreads = options.getOrElse('numThreads,"1").toInt
        val par = Range(0, inputAnnotatedSentences.size).par
        par.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(20)) // TODO: use numThread option
        val loss = par.map(i => gradient(i, weights)).reduce((a, b) => ({ a._1 += b._1; a._1 }, a._2 + b._2))._2 / inputAnnotatedSentences.size.toDouble
    
        logger(0, "Dev loss: " + loss.toString)

        inputAnnotatedSentences = iASSave
        inputGraphs = iGSave
        oracleGraphs = oGSave
    }

    def train(initialWeights: FeatureVector) {
        if (options.getOrElse('trainingOptimizer, "Adagrad") == "HOLS") {
            trainHOLS(initialWeights, HOLSPreTrainSize)
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
        val lossSave = loss
        loss = "Perceptron"
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
        loss = lossSave

        evalDev(weights)

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

