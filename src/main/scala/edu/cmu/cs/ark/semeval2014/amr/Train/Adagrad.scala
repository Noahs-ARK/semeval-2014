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
    def learnParameters(gradient: (Option[Int], Int, FeatureVector) => FeatureVector,
                        initialWeights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2strength: Double,
                        noreg: List[String],
                        trainingObserver: (Int, FeatureVector) => Boolean,
                        avg: Boolean) : FeatureVector = {
        val weights = FeatureVector(initialWeights.labelset)
        weights += initialWeights
        var avg_weights = FeatureVector(weights.labelset)
        var sumSq = FeatureVector(weights.labelset)         // G_{i,i}
        var pass = 0
        while (pass < passes && trainingObserver(pass,weights)) {
            logger(0,"Pass "+(pass+1).toString)
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                // normally we would do weights -= stepsize * gradient(t)
                // but instead we do this: (see equation 8 in SocherBauerManningNg_ACL2013.pdf)
                val grad = gradient(Some(pass), t, weights)
                sumSq.update(grad, (feat, label, x , y) => x + y * y)
                weights.update(grad, (feat, label, x, y) => {
                    val sq = sumSq(feat, label)
                    if (sq > 0.0) {
                        x - stepsize * y / sqrt(sumSq(feat, label))
                    } else {
                        x
                    }
                })
                if (l2strength != 0.0) {
                    val values = noreg.map(feat => (feat, weights.fmap(feat)))
                    noreg.map(feat => weights.fmap.remove(feat))
                    sumSq.update(weights, (feat, label, x , y) => x + l2strength * l2strength * y * y)
                    weights.update(weights, (feat, label, x, y) => {
                        val sq = sumSq(feat, label)
                        if (sq > 0.0) {
                            x - stepsize * l2strength * y / sqrt(sumSq(feat, label))
                        } else {
                            x
                        }
                    })
                    values.map(x => { weights.fmap(x._1) = x._2 })
                }
            }
            avg_weights += weights
            pass += 1
        }
        if(avg) { avg_weights } else { weights }
    }
}

