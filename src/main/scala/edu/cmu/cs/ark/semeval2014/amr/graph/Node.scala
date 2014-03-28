package edu.cmu.cs.ark.semeval2014.amr.graph

import scala.collection.mutable.ArrayBuffer
import edu.cmu.cs.ark.semeval2014.common.logger

case class Node(
    var id: String,                         // unique id for each node in graph
    val concept: String,                    // name of the node (used when printing output)
    var relations: List[(String, Node)],    // outgoing edges
    var position: Int                       // position of the node in the sentence
)

