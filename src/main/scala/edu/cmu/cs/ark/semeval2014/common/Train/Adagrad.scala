package edu.cmu.cs.ark.semeval2014.common.Train

import edu.cmu.cs.ark.semeval2014.common.FeatureVector

import scala.util.Random
import scala.math.sqrt

class Adagrad extends Optimizer {
    def learnParameters(gradient: Int => FeatureVector,
                        weights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        avg: Boolean) : FeatureVector = {
        var avg_weights = FeatureVector()
        var sumSq = FeatureVector()         // G_{i,i}
        for (i <- Range(1,passes+1)) {
            logger(0,"Pass "+i.toString)
            for (t <- Random.shuffle(Range(0, trainingSize).toList)) {
            //for (t <- Range(0, trainingSize)) {
                // normally we would do weights -= stepsize * gradient(t)
                // but instead we do this: (see equation 8 in SocherBauerManningNg_ACL2013.pdf)
                for ((feat, value) <- gradient(t).fmap
                     if value != 0.0 ) {
                    sumSq.fmap(feat) = sumSq.fmap.getOrElse(feat, 0.0) + value * value
                    weights.fmap(feat) = weights.fmap.getOrElse(feat, 0.0) - stepsize * value / sqrt(sumSq.fmap(feat))
                }
            }
            avg_weights += weights
        }
        if(avg) { avg_weights } else { weights }
    }
}

