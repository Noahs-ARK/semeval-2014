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

    def decode(i: Int) : FeatureVector
    def oracle(i: Int) : FeatureVector
    def costAugmented(i: Int) : FeatureVector
    val weights : FeatureVector

    ////////////////// Training Setup ////////////////

    val passes = options.getOrElse('trainingPasses, "30").toInt
    val stepsize = options.getOrElse('trainingStepsize, "1.0").toDouble
    val regularizerStrength = options.getOrElse('trainingRegularizerStrength, "0.0").toDouble
    val loss = options.getOrElse('trainingLoss, "SVM")
    if (!options.contains('model)) {
        System.err.println("Error: No model filename specified"); sys.exit(1)
    }
    val optimizer: Optimizer = options.getOrElse('trainingOptimizer, "Adagrad") match {
        case "SSGD" => new SSGD()
        case "Adagrad" => new Adagrad()
        case x => { System.err.println("Error: unknown training optimizer " + x); sys.exit(1) }
    }

    val inputAnnotatedSentences = Input.loadInputAnnotatedSentences(options)
    val inputGraphs = Input.loadSDPGraphs(options, oracle = false)
    val oracleGraphs = Input.loadSDPGraphs(options, oracle = true)
    assert(inputAnnotatedSentences.size == inputGraphs.size && inputGraphs.size == oracleGraphs.size, "sdp and dep file lengths do not match")

    Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run() {
            System.err.print("Writing out weights... ")
            val file = new java.io.PrintWriter(new java.io.File(options('model)), "UTF-8")
            try { file.print(weights.toString) }
            finally { file.close }
            System.err.println("done")
        }
    })

    /////////////////////////////////////////////////

    def gradient(i: Int) : FeatureVector = {
        if (loss == "Perceptron") {
            val grad = decode(i)
            grad -= oracle(i)
            grad
        } else if (loss == "SVM") {
            val grad = costAugmented(i)
            grad -= oracle(i)
            grad
        } else {
            System.err.println("Error: unknown training loss " + loss); sys.exit(1)
            FeatureVector(weights.labelset)
        }
    }

    def trainingObserver(pass: Int) : Boolean = {
        if (options.contains('trainingSaveInterval) && pass % options('trainingSaveInterval).toInt == 0 && pass > 0) {
            val file = new java.io.PrintWriter(new java.io.File(options('model) + ".iter" + pass.toString), "UTF-8")
            try { file.print(weights.toString) }
            finally { file.close }
        }
        return true
    }

    def train() {
        optimizer.learnParameters(
            i => gradient(i),
            weights,
            inputGraphs.size,
            passes,
            stepsize,
            options.getOrElse('trainingL2RegularizerStrength, "0.0").toDouble,
            trainingObserver,
            avg = false)
    }
}

