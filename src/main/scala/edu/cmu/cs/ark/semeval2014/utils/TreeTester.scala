// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.utils

import sdp.graph.{Node, InspectedGraph, Graph}
import collection.JavaConversions._
import sdp.io.GraphReader
import resource.managed

object TreeTester {
  /** Tests whether g is a tree (ignoring singletons) */
  def isTree(g: Graph): Boolean = {
    val ig = new InspectedGraph(g)
    ig.isForest && (g.getNodes.count(n => !n.hasIncomingEdges && n.hasOutgoingEdges) == 1)
  }

  def hasMultipleNonMemberIncomingEdges(g: Graph): Boolean = {
    g.getNodes.exists(hasMultipleNonMemberIncomingEdges)
  }

  def hasMultipleNonMemberIncomingEdges(n: Node): Boolean = {
    n.getIncomingEdges.count(e => !e.label.endsWith(".member")) > 1
  }
}

object TreeTesterApp extends App {
  import TreeTester._

  val filename = args(0)
  for (reader <- managed(new GraphReader(filename))) {
    val graphs = Iterator.continually(reader.readGraph).takeWhile(null !=)
    val (totalNumGraphs, totalNumTrees, totalMultis) = graphs.foldLeft((0, 0, 0)) {
      case ((numGraphs, numTrees, numMultis), g) =>
        (numGraphs + 1,
          numTrees + (if (isTree(g)) 1 else 0),
          numMultis + (if (hasMultipleNonMemberIncomingEdges(g)) 1 else 0))
    }
    System.err.println("number of graphs: %d".format(totalNumGraphs))
    System.err.println("number of trees: %d".format(totalNumTrees))
    System.err.println("number of graphs with nodes that have multiple non-\"member\" parents: %d".format(totalMultis))
  }
}
