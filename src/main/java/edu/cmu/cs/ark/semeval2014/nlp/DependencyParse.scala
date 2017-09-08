package edu.cmu.cs.ark.semeval2014.nlp

import edu.cmu.cs.ark.semeval2014.common.SyntacticDependency
import java.lang.Math._
import scala.Some
import DependencyParse._
import scala.collection.immutable.IndexedSeq

case class DependencyParse(deps: Array[SyntacticDependency]) {
  lazy val heads: Map[Int, Int] = Map() ++ deps.map(dep => dep.dependent -> dep.head)
  lazy val relations: Map[(Int, Int), String] = Map() ++ deps.map(dep => (dep.head, dep.dependent) -> dep.relation)
  lazy val rootDependencyPaths: IndexedSeq[Option[List[Int]]] = deps.indices.map(rootDependencyPath(_))
  lazy val depths: IndexedSeq[Option[Int]] = rootDependencyPaths.map(_.map(_.size))

  /**
   * Returns path to root (integers) as a list in reverse order
   * (including the word we started at)
   */
  def rootDependencyPath(word: Int, path: List[Int] = List()): Option[List[Int]] = {
    if (word == -1) {
      Some(path)
    } else {
      val dep = heads.get(word)
      if (dep == None || dep.get < -1 || path.size > deps.size) {
        None
      } else {
        rootDependencyPath(dep.get, word :: path)
      }
    }
  }

  def dependencyPath(word1: Int, word2: Int): Option[(List[Int], List[Int])] = {
    // Return type list one is path from word1 to common head
    // Return type list two is path from common head to word2
    // Includes the common head in both lists
    for (path1 <- rootDependencyPaths(word1);
         path2 <- rootDependencyPaths(word2)) yield {
      val prefix = longestCommonPrefixLength(path1, path2)
      (path1.drop(prefix - 1).reverse, path2.drop(prefix - 1))
    }
  }
}
object DependencyParse {
  def longestCommonPrefixLength[T](s1: Seq[T], s2: Seq[T]) : Int = {
    // from http://stackoverflow.com/questions/8104479/how-to-find-the-longest-common-prefix-of-two-strings-in-scala
    val maxSize = min(s1.size, s2.size)
    var i = 0
    while (i < maxSize && s1(i) == s2(i)) {
      i += 1
    }
    i
  }
}
