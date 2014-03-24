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

class HOLS(splitSize: Int, countPercepts: (Option[Int], Int) => FeatureVector) extends Optimizer {
    def learnParameters(gradient: (Option[Int], Int, FeatureVector) => FeatureVector,
                        initialWeights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2reg: Double,
                        noreg: List[String],
                        trainingObserver: (Int, FeatureVector) => Boolean,
                        avg: Boolean) : FeatureVector = {
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
        val gradients : Array[Array[FeatureVector]] = Array.fill(passes)(splits.map(x => FeatureVector(labelset)))
        val totalGradients : Array[FeatureVector] = Array.fill(passes)(FeatureVector(labelset))
        val percepts : Array[FeatureVector] = splits.map(x => FeatureVector(labelset))
        for (t <- 0 until trainingSize) {
            percepts(getSplit(t)) += countPercepts(None, t) // TODO: make pass = 0 not randomized
        }
        val counts = FeatureVector(labelset)
        percepts.map(x => counts += x)
        while (pass < passes && trainingObserver(pass, fullWeights)) {
            logger(0,"Pass "+(pass+1).toString)

            def computeMyWeights(split: Int, myPercepts: FeatureVector) : FeatureVector = {
                val myW = FeatureVector(labelset)
                myW.plusEqFilter(initialWeights, myPercepts.fmap.keysIterator)
                for (i <- 0 to pass) {
                    myW.plusEqFilter(alphas(pass) * totalGradients(pass), myPercepts.fmap.keysIterator)
                    myW -= alphas(pass) * gradients(i)(split)   // no need to filter since gradients(i) is only supported where the percepts are
                }
                return myW
            }

            // Compute gradients in splits
            var t = 0
            for (i <- 0 until splits.size) {
                val myWeights = computeMyWeights(i, percepts(i))
                for (j <- 0 until splits(i).size) {
                    gradients(pass)(i) += gradient(None, t, myWeights)
                    t += 1
                }
                gradients(pass)(i).dotDivide(counts)    // divide by percept count
            }
            (0 until numSplits).map(j => totalGradients(pass) += gradients(pass)(j))

            // Do the line search
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                val split = getSplit(t)
                for (p <- 0 to pass) {
                    totalGradients(p) -= gradients(p)(split)
                }
                val myGradient = gradient(None, t, computeMyWeights(split, countPercepts(None,t)))
                myGradient.dotDivide(counts)            // divide by percept count
                for (p <- 0 to pass) {
                    alphas(p) += stepsize * totalGradients(p).dot(myGradient) / sqrt(t)
                }
                for (p <- 0 to pass) {
                    totalGradients(p) += gradients(p)(split)
                }
            }

            pass += 1
        }

        def fullWeights : FeatureVector = {
            val fullW = FeatureVector(initialWeights.labelset)
            fullW += initialWeights
            for (i <- 0 until passes) {
                fullW += alphas(i) * totalGradients(i)
            }
            return fullW
        }

        return fullWeights
    }
}

