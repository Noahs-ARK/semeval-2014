package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.min
import java.lang.Math.signum
import java.lang.Math.floor

class DistanceIntervals extends FE.FeatureExtractor with FE.EdgeFE {
    override def features(word1Index: Int, word2Index: Int, fa: FeatureAdder) {
        val distance = abs(word1Index - word2Index).toInt
        val direction = signum(dist).toInt
        // distance intervals
        for (i <- Range(1, distance-1)) {
            fa.add("Di="+(direction*i).toString)
        }
        // log distance intervals
        for (i <- Range(1, floor(log(distance+1)/log(1.39)).toInt)) {
            fa.add("logDi="+(direction*i).toString)
        }
    }
}
