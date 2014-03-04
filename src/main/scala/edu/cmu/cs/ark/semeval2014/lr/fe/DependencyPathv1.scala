package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import DependencyPathv1._
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse

object DependencyPathv1 {
    val MAX_LENGTH = 6
    val VERSION = "DPv1="
}

class DependencyPathv1 extends FE.FeatureExtractor with FE.EdgeFE {
    private var deps: DependencyParse = _

    override def setupSentence(s: InputAnnotatedSentence) {
        sent = s
        deps = s.syntacticDependencies
    }

  override def features(word1Index: Int, word2Index: Int, fa: FeatureAdder) {
        val path = deps.dependencyPath(word1Index, word2Index)
        val pathStr = if (path.isDefined && path.get._1.size + path.get._2.size <= MAX_LENGTH) {
          dependencyPathString(path.get, sent.pos).mkString("_")
        } else {
          "NONE"
        }
        val (word1, word2) = (sent.sentence(word1Index), sent.sentence(word2Index))
        fa.add("W1=" + word1 + "+" + VERSION + pathStr) // path with src word
        fa.add("W2=" + word2 + "+" + VERSION + pathStr) // path with dest word
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

