package edu.cmu.cs.ark.semeval2014.amr.Train

import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
import java.lang.Math.ceil
import java.lang.Math.floor
import java.lang.Math.min
import java.lang.Math.max
import scala.io.Source
import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import scala.util.Random
import scala.math.sqrt
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._

class HOLS(splitSize: Int, countPercepts: (Int, Int) => FeatureVector) extends Optimizer {
    def learnParameters(gradient: (Int, Int, FeatureVector) => FeatureVector,
                        initialWeights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2reg: Double,
                        noreg: List[String],
                        trainingObserver: Int => Boolean,
                        avg: Boolean) : FeatureVector = {
        val fullWeights = FeatureVector(initialWeights.labelset)
        fullWeights += initialWeights
        val numSplits : Int = ceil(trainingSize.toDouble / splitSize.toDouble).toInt
        val labelset = initialWeights.labelset
        val splits : Array[Array[Int]] = Array.fill(ceil(trainingSize.toDouble / splitSize.toDouble).toInt)(Array())
        for (i <- 0 until splits.size) {
            splits(i) = Range(i*splitSize, min((i+1)*splitSize, trainingSize)).toArray
        }
        //val splits = (0 until numSplits).toArray
        val getSplit : Array[Int] = (0 until trainingSize).map(x => x/splitSize).toArray
        var pass = 0
        val alphas : Array[Double] = Array.fill(passes)(0.0)
        val gradients : Array[Array[FeatureVector]] = Array.fill(passes)(weights.map(x => FeatureVector(labelset)))
        val totalGradients : Array[FeatureVector] = Array.fill(passes)(FeatureVector(labelset))
        val percepts : Array[FeatureVector] = splits.map(x => FeatureVector(labelset))
        for (t <- 0 until trainingSize) {
            percepts(getSplit(t)) += countPercepts(0, t) // TODO: make pass = 0 not randomized
        }
        while (pass < passes && trainingObserver(i)) {
            logger(0,"Pass "+(pass+1).toString)

            def computeMyWeights(split: Int) : FeatureVector = {
                val myW = FeatureVector(labelSet)
                myW.plusEqFilter(initialWeights, percepts(split).fmap.keys)
                for (i <- 0 to pass) {
                    myW.plusEqFilter(alphas(pass) * totalGradients(pass), percepts(split).fmap.keys)
                    myW -= alphas(pass) * gradients(i)(split)   // no need to filter since gradients(i) is only supported where the percepts are
                }
            }

            // Compute gradients in splits
            var t = 0
            for (i <- 0 until splits.size) {
                val myWeights = computeMyWeights(i)
                for (j <- 0 until splits(i).size) {
                    gradients(pass)(i) += gradient(0, t, myWeights)
                    t += 1
                }
                // TODO: divide by percept count
            }
            splits.map(j => totalGradients(pass) += gradients(j))

            // Do the line search
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                val split = getSplit(t)
                for (p <- 0 to pass) {
                    totalGradients(p) -= gradients(p)(split)
                }
                val myGradient = gradient(0, t, computeMyWeights(split))
                // TODO: divide by percept count
                for (p <- 0 to pass) {
                    alphas(p) += stepsize * totalGradients(p).dot(myGradient) / sqrt(t)
                }
                for (p <- 0 to pass) {
                    totalGradients(p) += gradients(p)(split)
                }
            }

            pass += 1
        }

        val fullWeights = FeatureVector(initialWeights.labelset)
        fullWeights += initialWeights
        for (i <- 0 until passes) {
            fullWeights += alphas(i) * totalGradients(i)
        }
        return fullWeights
    }
}

