package edu.cmu.cs.ark.semeval2014.common.Train

import edu.cmu.cs.ark.semeval2014.common.FeatureVector

abstract class Optimizer {
    def learnParameters(gradient: Int => FeatureVector,
                        weights: FeatureVector,
                        trainingSize: Int,
                        passes: Int,
                        stepsize: Double,
                        avg: Boolean) : FeatureVector
}

