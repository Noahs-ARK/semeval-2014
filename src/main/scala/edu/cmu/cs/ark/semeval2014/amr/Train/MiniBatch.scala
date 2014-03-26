package edu.cmu.cs.ark.semeval2014.amr.Train

import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
import java.lang.Math.floor
import java.lang.Math.ceil
import java.lang.Math.min
import java.lang.Math.max
import scala.io.Source
import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import scala.util.parsing.combinator._
import scala.util.Random
import scala.math.sqrt
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import scala.collection.parallel._
import scala.concurrent.forkjoin.ForkJoinPool

class MiniBatch(optimizer: Optimizer, miniBatchSize: Int) extends Optimizer {
    def learnParameters(gradient: (Option[Int], Int, FeatureVector) => FeatureVector,
                        initialWeights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2reg: Double,
                        noreg: List[String],
                        trainingObserver: (Int, FeatureVector) => Boolean,
                        avg: Boolean) : FeatureVector = {
        val numMiniBatches = ceil(trainingSize.toDouble / miniBatchSize.toDouble).toInt
        val trainShuffle : Array[Array[Int]] = Range(0, passes).map(x => Random.shuffle(Range(0, trainingSize).toList).toArray).toArray
        val miniGradient : (Option[Int], Int, FeatureVector) => FeatureVector = (pass, i, weights) => {
            assert(i < numMiniBatches, "MiniBatch optimizer mini-batch index too large")
            val par = Range(i*miniBatchSize, min((i+1)*miniBatchSize, trainingSize)).par
            par.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(4))
            if (pass != None) {
                par.map(x => gradient(None, trainShuffle(pass.get)(x), weights)).reduce((a, b) => { a += b; a })
            } else {
                par.map(x => gradient(None, x, weights)).reduce((a, b) => { a += b; a })    // Don't randomize if pass = None
            }
        }
        return optimizer.learnParameters(miniGradient,
                                         initialWeights,
                                         numMiniBatches,
                                         passes,
                                         stepsize / miniBatchSize.toDouble,
                                         l2reg * miniBatchSize,
                                         noreg,
                                         trainingObserver,
                                         avg)
    }
}

