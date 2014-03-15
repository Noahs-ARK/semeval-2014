package edu.cmu.cs.ark.semeval2014.amr.Train

import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
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
import edu.cmu.cs.ark.semeval2014.common._

class SSGD extends Optimizer {
    def learnParameters(gradient: (Int, Int) => FeatureVector,
                        weights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2reg: Double,
                        trainingObserver: Int => Boolean,
                        avg: Boolean) : FeatureVector = {
        var avg_weights = FeatureVector()
        var i = 0
        while (i < passes && trainingObserver(i)) {
            logger(0,"Pass "+(i+1).toString)
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                weights -= stepsize * gradient(i, t)
                if (l2reg != 0.0) {
                    weights -= (stepsize * l2reg) * weights
                }
            }
            avg_weights += weights
            i += 1
        }
        if(avg) { avg_weights } else { weights }
    }
}

