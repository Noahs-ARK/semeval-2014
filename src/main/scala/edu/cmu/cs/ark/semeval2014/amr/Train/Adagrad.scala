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
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._

class Adagrad extends Optimizer {
    def learnParameters(gradient: (Int, Int) => FeatureVector,
                        weights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2strength: Double,
                        trainingObserver: Int => Boolean,
                        avg: Boolean) : FeatureVector = {
        var avg_weights = FeatureVector(weights.labelset)
        var sumSq = FeatureVector(weights.labelset)         // G_{i,i}
        var pass = 0
        while (pass < passes && trainingObserver(pass)) {
            logger(0,"Pass "+(pass+1).toString)
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                // normally we would do weights -= stepsize * gradient(t)
                // but instead we do this: (see equation 8 in SocherBauerManningNg_ACL2013.pdf)
                val grad = gradient(pass, t)
                sumSq.update(grad, (feat, label, x , y) => x + y * y)
                weights.update(grad, (feat, label, x, y) => {
                    val sq = sumSq(feat, label)
                    if (sq > 0.0) {
                        x - stepsize * y / sqrt(sumSq(feat, label))
                    } else {
                        x
                    }
                })
                //logger(0, "*** sumSq ***")
                //logger(0, sumSq.toString)
                //logger(0, "*** weights ***")
                //logger(0, weights.toString)
                if (l2strength != 0.0) {
                    sumSq.update(weights, (feat, label, x , y) => x + l2strength * l2strength * y * y)
                    weights.update(weights, (feat, label, x, y) => {
                        val sq = sumSq(feat, label)
                        if (sq > 0.0) {
                            x - stepsize * l2strength * y / sqrt(sumSq(feat, label))
                        } else {
                            x
                        }
                    })
                //logger(0, "(after l2 update) *** sumSq ***")
                //logger(0, sumSq.toString)
                //logger(0, "*** weights ***")
                //logger(0, weights.toString)
                }
            }
            avg_weights += weights
            pass += 1
        }
        if(avg) { avg_weights } else { weights }
    }
}

