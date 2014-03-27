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
import scala.collection.parallel._
import scala.collection.concurrent.TrieMap
import scala.concurrent.forkjoin.ForkJoinPool

class HOLS(options: Map[Symbol, String], countPercepts: (Option[Int], Int) => FeatureVector, fullTrainingSize: Int) extends Optimizer {
    def countPerceptsParallel(t: Int) : FeatureVector = {
        val miniBatchSize = options.getOrElse('trainingMiniBatchSize,"1").toInt
        if (miniBatchSize <= 1) {
            countPercepts(None, t)
        } else {
            val par = Range(t*miniBatchSize, min((t+1)*miniBatchSize, fullTrainingSize)).par
            //par.tasksupport = new ForkJoinTaskSupport(new ForkJoinPool(4))
            par.map(x => countPercepts(None, t)).reduce((a, b) => { a += b; a })
        }
    }

    def learnParameters(gradient: (Option[Int], Int, FeatureVector) => FeatureVector,
                        initialWeights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2reg: Double,
                        noreg: List[String],
                        trainingObserver: (Int, FeatureVector) => Boolean,
                        avg: Boolean) : FeatureVector = {
        val splitSize = 5
        val numSplits : Int = ceil(trainingSize.toDouble / splitSize.toDouble).toInt
        logger(0, "numSplits = " + numSplits.toString)
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
        val totalGradientNorm : Array[Option[Double]] = Array.fill(passes)(None)
        val percepts : Array[FeatureVector] = splits.map(x => FeatureVector(labelset))
        if (initialWeights.l2norm != 0.0) {
            initialWeights *= (1000. / initialWeights.l2norm)
        }
        logger(0, "Computing percepts")
        for (t <- 0 until trainingSize) {
            logger(0, "t="+t.toString)
            percepts(getSplit(t)) += countPerceptsParallel(t)
        }
        logger(0, "Total percept count")
        val counts = FeatureVector(labelset)
        percepts.map(x => counts += x)
        //counts.toFile(options('model) + ".perceptCounts")
        val denseWeights = FeatureVector(labelset)
        denseWeights += initialWeights
        while (pass < passes && trainingObserver(pass, fullWeights)) {
            logger(0,"Pass "+(pass+1).toString)

            def computeMyWeights(split: Int, myPercepts: FeatureVector) : FeatureVector = {
                val myW = FeatureVector(labelset)
                myW.plusEqFilter(denseWeights, myPercepts.fmap.keysIterator)
                for (i <- 0 to pass) {
                    myW.plusEqFilter(alphas(i) * totalGradients(i), myPercepts.fmap.keysIterator)
                    myW -= alphas(i) * gradients(i)(split)   // no need to filter since gradients(i) is only supported where the percepts are
                }
                return myW
            }

            // Compute gradients in splits
            logger(0, "Computing gradient")
            var t = 0
            for (i <- 0 until splits.size) {
                logger(0, "Split "+i.toString)
                val myWeights = computeMyWeights(i, percepts(i))
                for (j <- 0 until splits(i).size) {
                    gradients(pass)(i) += gradient(None, t, myWeights).filter(x => x != "Bias")
                    t += 1
                }
            }
            (0 until numSplits).map(j => totalGradients(pass) += gradients(pass)(j))
            totalGradients(pass).toFile(options('model) + ".iter" + pass.toString + ".gradient")

            // Conditioning
            for (i <- 0 until splits.size) {
                gradients(pass)(i).dotDivide(counts)
            }
            totalGradients(pass).dotDivide(counts)
            totalGradients(pass).toFile(options('model) + ".iter" + pass.toString + ".NormGradient")
            totalGradientNorm(pass) = Some(totalGradients(pass).l2norm)

            // Do the line search
            logger(0, "Line search")
            var ex = 0
            for (n <- 0 until 1) {
              for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                logger(0, "example="+ex.toString)
                val split = getSplit(t)
                for (p <- 0 to pass) {
                    totalGradients(p) -= gradients(p)(split)
                }
                var myGradient = gradient(None, t, computeMyWeights(split, countPercepts(None,t)))
                val denseGradient = FeatureVector(myGradient.labelset, TrieMap("Bias" -> myGradient.fmap("Bias").clone))  // DO THIS BEFORE CONDITIONING
                myGradient.fmap("Bias") = ValuesMap()
                myGradient.dotDivide(counts)            // divide by percept count
                val myNorm = myGradient.l2norm
                for (p <- 0 to pass) {
                    logger(0, "p="+(totalGradients(p).dot(myGradient) / (totalGradientNorm(p).get * myNorm )).toString)
                    alphas(p) -= stepsize * 10.0 * (totalGradients(p).dot(myGradient)/(totalGradientNorm(p).get)) / sqrt(ex+1.0)
                }
                //denseWeights -= (stepsize * 1000000000.0 /* / sqrt(ex+1.0) */ ) * denseGradient
                denseWeights -= (stepsize * 1.0 / sqrt(ex+1.0) ) * denseGradient
                logger(0, "dense:" + denseWeights.toString.split("\n").head)
                for (p <- 0 to pass) {
                    totalGradients(p) += gradients(p)(split)
                }
                logger(0, "alphas="+alphas.toList.toString)
                ex += 1
              }
            }

            pass += 1
        }

        def fullWeights : FeatureVector = {
            val fullW = FeatureVector(labelset)
            fullW += denseWeights
            for (i <- 0 until passes) {
                fullW += alphas(i) * totalGradients(i)
            }
            return fullW
        }

        return fullWeights
    }
}

