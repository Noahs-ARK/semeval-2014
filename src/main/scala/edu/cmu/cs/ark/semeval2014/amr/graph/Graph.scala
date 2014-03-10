package edu.cmu.cs.ark.semeval2014.amr.graph

import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import scala.util.parsing.combinator._
import edu.cmu.cs.ark.semeval2014.common._

abstract class Graph {
    def nodes : Array[Node]
    def duplicate : Graph
    def addEdge(node1: Node, node2: Node, label: String)
    def clearEdges : Graph
    def toConll(inputAnnotatedSentence: InputAnnotatedSentence) : String
}

object Graph {
    def empty : Graph = SDPGraph.empty
}

