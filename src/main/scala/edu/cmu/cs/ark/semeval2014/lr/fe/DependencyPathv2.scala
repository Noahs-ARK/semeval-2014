package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import DependencyPathv2._
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse
import edu.cmu.cs.ark.semeval2014.nlp.MorphaLemmatizer

object DependencyPathv2 {
    val MAX_LENGTH = 6
    val VERSION = "DPv2="
    val morpha = new MorphaLemmatizer()
}

class DependencyPathv2 extends FE.FeatureExtractor with FE.EdgeFE {
    private var deps: DependencyParse = _
    private var lemmas: Array[String] = _

    override def setupSentence(s: InputAnnotatedSentence) {
        sent = s
        deps = s.syntacticDependencies
        lemmas = sent.sentence.zip(sent.pos).map(x => morpha.getLemma(x._1, x._2))
    }

  override def features(word1Index: Int, word2Index: Int, fa: FeatureAdder) {
        val path = deps.dependencyPath(word1Index, word2Index)
        val tags = sent.pos.zip(sent.sentence).map(x => if(x._1 == "IN") { x._2 } else { x._1 })
        val pathStr = if (path.isDefined && path.get._1.size + path.get._2.size <= MAX_LENGTH) {
          dependencyPathString(path.get, tags).mkString("_")
        } else {
          "NONE"
        }
        val (lemma1, lemma2) = (lemmas(word1Index), lemmas(word2Index))
        fa.add("l1=" + lemma1 + "+" + VERSION + pathStr) // path with src word
        fa.add("l2=" + lemma2 + "+" + VERSION + pathStr) // path with dest word
        fa.add(VERSION + pathStr) // path without words
    }

    private def dependencyPathString(path: (List[Int], List[Int]), labels: Array[String]) : List[String] = {
        var pathList : List[String] = List()
        for (List(word1, word2) <- path._1.sliding(2)) {
            pathList = labels(word1) + "_" + deps.relations(word2, word1) + ">_" + labels(word2) :: pathList
        }
        for (List(word1, word2) <- path._2.sliding(2)) {
            pathList = labels(word1) + "_" + deps.relations(word1, word2) + "<_" + labels(word2) :: pathList
        }
        return pathList.reverse
    }
}

