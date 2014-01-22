// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.utils

import scala.annotation.tailrec

object CycleTester {
  /**
    Takes a directed graph, as a set of nodes and a map from node to its out-adjacent nodes,
    and determines whether the graph contains a cycle.
   */
  @tailrec
  def hasCycle[T](nodes: Traversable[T], outgoingEdges: Map[T, Traversable[T]]): Boolean = {
    if (outgoingEdges.isEmpty) {
      false
    } else {
      val oFirstLeaf: Option[T] = nodes.toIterator.find(v => outgoingEdges.getOrElse(v, Nil).isEmpty).headOption
      oFirstLeaf match {
        case None => true
        case Some(node) => {
          val removed = (outgoingEdges - node) map { case (k, v) => (k, v.toSet - node) }
          hasCycle(nodes.toSet - node, removed)
        }
      }
    }
  }
}
