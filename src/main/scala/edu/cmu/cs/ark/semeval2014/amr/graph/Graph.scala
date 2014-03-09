package edu.cmu.cs.ark.semeval2014.amr.graph

import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import scala.collection.immutable.Queue
import scala.util.parsing.combinator._
import edu.cmu.cs.ark.semeval2014.common.logger

abstract class Graph {
    def nodes : Iterator[Node]
    def duplicate : Graph
}

object Graph {
    def empty : Graph
}

