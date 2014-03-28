package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import java.lang.Math.abs
import java.lang.Math.max
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import edu.cmu.cs.ark.semeval2014.amr.{Input, DecoderResult}
import edu.cmu.cs.ark.semeval2014.amr.graph.Graph

class LagrangianRelaxation(featureNames: List[String], labelSet: Array[(String, Int)], stepsize: Double, maxIterations: Int)
        extends Decoder {
    // Base class has defined:
    // val features: Features
    val alg2 = new Alg2("LRLabelWithId" :: featureNames, labelSet)
    val features = alg2.features

    val labelConstraint = labelSet.toMap    // TODO: could change to array for speed

    def decode(input: Input) : DecoderResult = {
        alg2.input = input      // (this also sets features.input)
        var result = DecoderResult(Graph.empty, FeatureVector(features.weights.labelset), 0.0)

        val multipliers = FeatureVector(features.weights.labelset)
        var delta = 0.0         // so we know when we have converged
        var counter = 0
        do {
            //logger(2, "weights: \n"+features.weights)
            //logger(1, "multipliers: \n"+multipliers.toString)
            features.weights -= multipliers
            //logger(2, "alg2 weights: \n"+features.weights)
            result = alg2.decode
            //logger(1, "id features: \n"+result.features.filter(x => x.startsWith("LR:Id1=")))
            features.weights += multipliers // undo our adjustment to the weights

            delta = 0.0
            for { (feat, values) <- result.features.fmap
                  if feat.startsWith("LR:Id1=")
                  (labelIndex, value) <- values.conjoined
                } {
                //logger(1, "feat = "+feat)
                //logger(1, "labelIndex = "+labelIndex.toString)
                //logger(1, "value = "+value.toString)
                val multiplier : Double = multipliers(feat, Some(labelIndex))
                val newMultiplier = max(0.0, multiplier - stepsize * (labelConstraint(features.weights.labelset(labelIndex)) - value))
                //logger(1, "newMultiplier = "+newMultiplier)
                delta += abs(newMultiplier - multiplier)
                multipliers.set((feat, Some(labelIndex)) -> newMultiplier)
            }
            counter += 1
        } while (delta != 0.0 && counter < maxIterations)

        if (delta != 0.0) {
            logger(0, "WARNING: Langrangian relaxation did not converge after "+counter.toString+" iterations. Delta = "+delta.toString)
        } else {
            logger(0, "Langrangian relaxation converged after "+counter.toString+" iterations. Delta = "+delta.toString)
        }

        val feats = result.features.filter(x => !x.startsWith("LR:Id1="))
        return DecoderResult(result.graph, feats, features.weights.dot(feats))
    }
}

