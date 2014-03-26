// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.{ConstitNode, ConstitTree, InputAnnotatedSentence}
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import scala.collection.JavaConversions._
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse

object ConstitFE {
  val MAX_LENGTH = 8
}
/**
 * Extracts features based on the constituency parse.
 * A "*" indicates the head/srcToken, and a "+" indicates the destToken (if it is a direct child).
 * e.g. "subcat:nsubj-VBN*-dobj+-prep"
 */
class ConstitFE extends FE.FeatureExtractor with FE.EdgeFE {
  import ConstitFE.MAX_LENGTH

  var tree: Option[ConstitTree] = _

  override def setupSentence(s: InputAnnotatedSentence) {
    super.setupSentence(s)
    tree = Option(s.constitTree)
  }

  def getPathToRoot(tokenIdx: Int): Option[List[ConstitNode]] = {
    for (t <- tree;
         terminal <- t.nodeBySpan(tokenIdx)(tokenIdx).headOption) yield {
      getPathToRoot(terminal)
    }
  }

  def getPathToRoot(c: ConstitNode, path: List[ConstitNode] = List()): List[ConstitNode] = {
    if (c.parent == null) {
      c :: path
    } else {
      getPathToRoot(c.parent, c :: path)
    }
  }

  def getPath(srcTokenIdx: Int, destTokenIdx: Int): Option[(List[ConstitNode], List[ConstitNode])] = {
    for (path1 <- getPathToRoot(srcTokenIdx);
         path2 <- getPathToRoot(destTokenIdx)) yield {
      val prefix = DependencyParse.longestCommonPrefixLength(path1, path2)
      (path1.drop(prefix - 1).reverse, path2.drop(prefix - 1))
    }
  }

  private def getPathString(up: List[ConstitNode], down: List[ConstitNode]): List[String] = {
    val upList = for (List(word1, word2) <- up.sliding(2) if word1.tag != word2.tag) yield {
      word1.tag + "^" + word2.tag
    }
    val downList = for (List(word1, word2) <- down.sliding(2) if word1.tag != word2.tag) yield {
      word1.tag + "v" + word2.tag
    }
    (upList ++ downList).toList
  }

  /** Edge features */
  def features(srcTokenIdx: Int, destTokenIdx: Int, fa: FeatureAdder) = {
    val path = getPath(srcTokenIdx, destTokenIdx)
    val pathStr = (
      for ((up, down) <- path;
           str = getPathString(up, down)
           if str.size <= MAX_LENGTH) yield {
        str.mkString("_")
      }
    ) getOrElse "NONE"
    val (word1, word2) = (sent.sentence(srcTokenIdx), sent.sentence(destTokenIdx))
    fa.add("W1=" + word1 + "+ConstitPath=" + pathStr)
    fa.add("W2=" + word2 + "+ConstitPath=" + pathStr)
    fa.add("ConstitPath=" + pathStr)
  }
}
