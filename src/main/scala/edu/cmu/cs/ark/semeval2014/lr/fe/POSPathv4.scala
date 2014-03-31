package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse
import java.lang.Math.min
import java.lang.Math.max
import edu.cmu.cs.ark.semeval2014.nlp.MorphaLemmatizer

class POSPathv4 extends FE.FeatureExtractor with FE.EdgeFE {
    val VERSION = "PPv4="
    val morpha = new MorphaLemmatizer()
    private var lemmas: Array[String] = _

    override def setupSentence(s: InputAnnotatedSentence) {
        sent = s
        lemmas = sent.sentence.zip(sent.pos).map(x => morpha.getLemma(x._1, x._2))
    }

    override def features(word1Index: Int, word2Index: Int, fa: FeatureAdder) {
        val (start, end) = (min(word1Index, word2Index), max(word1Index, word2Index))
        val pathStr = sent.pos.slice(start, end).distinct.mkString("_")
        val (lemma1, lemma2) = (lemmas(word1Index), lemmas(word2Index))
        fa.add("l1=" + lemma1 + "+" + VERSION + pathStr) // path with src word
        fa.add("l2=" + lemma2 + "+" + VERSION + pathStr) // path with dest word
        fa.add(VERSION + pathStr) // path without words
    }
}

