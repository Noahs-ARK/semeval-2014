package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse
import java.lang.Math.min
import java.lang.Math.max

class POSPathv3 extends FE.FeatureExtractor with FE.EdgeFE {
    val VERSION = "PPv3="

    override def setupSentence(s: InputAnnotatedSentence) {
        sent = s
    }

    override def features(word1Index: Int, word2Index: Int, fa: FeatureAdder) {
        val (start, end) = (min(word1Index, word2Index), max(word1Index, word2Index))
        val pathStr = sent.pos.slice(start, end).distinct.mkString("_")
        val (word1, word2) = (sent.sentence(word1Index), sent.sentence(word2Index))
        fa.add("W1=" + word1 + "+" + VERSION + pathStr) // path with src word
        fa.add("W2=" + word2 + "+" + VERSION + pathStr) // path with dest word
        fa.add(VERSION + pathStr) // path without words
    }
}

