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

class SSGD extends Optimizer {
    def learnParameters(gradient: (Int, Int) => FeatureVector,
                        weights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        l2reg: Double,
                        noreg: List[String],
                        trainingObserver: Int => Boolean,
                        avg: Boolean) : FeatureVector = {
        var avg_weights = FeatureVector(weights.labelset)
        var i = 0
        var scaling_trick = 1.0
        while (i < passes && trainingObserver(i)) {
            logger(0,"Pass "+(i+1).toString)
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
                // Usual update:
                //  weights -= stepsize * gradient(i, t)
                //  if (l2reg != 0.0) {
                //      weights -= (stepsize * l2reg) * weights
                //  }
                /************** Scaling trick ***************/
                //  true weights are scaling_trick * weights
                /********************************************/
                weights -= (stepsize / ((1.0-2.0*stepsize*l2reg)*scaling_trick)) * gradient(i, t)
                for (feature <- noreg if weights.fmap.contains(feature)) {
                    val values = weights.fmap(feature)
                    values.unconjoined /= (1.0 - 2.0 * stepsize * l2reg)  // so that value * scaling_trick = true weights after scaling_trick gets updated ( = value * scaling_trick(t) / scaling_trick(t+1) )
                    values.conjoined = values.conjoined.map(x => (x._1, x._2 / (1.0 - 2.0 * stepsize * l2reg)))
                }
                scaling_trick *= (1.0 - 2.0 * stepsize * l2reg)
            }
            // Undo scaling trick
            weights *= scaling_trick
            scaling_trick = 1.0
            avg_weights += weights
            i += 1
        }
        if(avg) { avg_weights } else { weights }
    }
}

