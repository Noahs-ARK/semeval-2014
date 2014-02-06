package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import java.lang.Math.min
import DependencyPathv1._

object DependencyPathv1 {
    val MAX_LENGTH = 6
    val VERSION = "DPv1="
}

class DependencyPathv1 extends FE.FeatureExtractor with FE.EdgeFE {
    private var rootDependencyPaths: Seq[List[Int]] = _
    private var heads: Map[Int, Int] = _
    private var relations: Map[(Int, Int), String] = _

    override def setupSentence(s: InputAnnotatedSentence) {
        sent = s
        heads = Map() ++ sent.syntacticDependencies.map(dep => dep.dependent -> dep.head)
        relations = Map() ++ sent.syntacticDependencies.map(dep => (dep.head, dep.dependent) -> dep.relation)
        rootDependencyPaths = sent.syntacticDependencies.indices.map(rootDependencyPath(_))
    }

    override def features(word1Index: Int, word2Index: Int, fa: FeatureAdder) {
        val path = dependencyPath(word1Index, word2Index)
        val (word1, word2) = (sent.sentence(word1Index), sent.sentence(word2Index))
        val pathStr = if (path._1.size + path._2.size <= MAX_LENGTH) {
          dependencyPathString(path, sent.pos).mkString("_")
        } else {
          "NONE"
        }
        fa.add("W1=" + word1 + "+" + VERSION + pathStr) // path with src word
        fa.add("W2=" + word2 + "+" + VERSION + pathStr) // path with dest word
        fa.add(VERSION + pathStr) // path without words
    }

    private def dependencyPath(word1: Int, word2: Int) : (List[Int], List[Int]) = {
        // Return type list one is path from word1 to common head
        // Return type list two is path from common head to word2
        // Includes the common head in both lists
        val prefix = longestCommonPrefixLength(rootDependencyPaths(word1), rootDependencyPaths(word2))
        return (rootDependencyPaths(word1).drop(prefix-1).reverse, rootDependencyPaths(word2).drop(prefix-1))
    }

    private def rootDependencyPath(word: Int, path: List[Int] = List()) : List[Int] = {
        // Returns path to root (integers) as a list in reverse order (including the word we started at)
        assert(word >= -1, "Seriously, somebody needs to fix the dependency tree.  There is a token with head = "+(word+1).toString+".  There should be no words with heads < 0.")
        if (word == -1) {
            path
        } else {
            val dep = heads.get(word)
            assert(dep != None, "The dependency tree seems broken.  I can't find the head of "+sent.sentence(word)+" in position "+word)
            rootDependencyPath(dep.get, word :: path)
        }
    }

    private def dependencyPathString(path: (List[Int], List[Int]), labels: Array[String]) : List[String] = {
        var pathList : List[String] = List()
        for (List(word1, word2) <- path._1.sliding(2)) {
            pathList = labels(word1) + "_" + relations(word2, word1) + ">_" + labels(word2) :: pathList
        }
        for (List(word1, word2) <- path._2.sliding(2)) {
            pathList = labels(word1) + "_" + relations(word1, word2) + "<_" + labels(word2) :: pathList
        }
        return pathList.reverse
    }

    private def longestCommonPrefixLength[T](s1: Seq[T], s2: Seq[T]) : Int = {
        // from http://stackoverflow.com/questions/8104479/how-to-find-the-longest-common-prefix-of-two-strings-in-scala
        val maxSize = min(s1.size, s2.size)
        var i = 0
        while (i < maxSize && s1(i) == s2(i)) {
            i += 1
        }
        return i
    }

}

