package edu.cmu.cs.ark.semeval2014.amr.graph

import edu.cmu.cs.ark.semeval2014.common._
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

/*case class Node(
    var id: String,                         // unique id for each node in graph
    val concept: String,                    // name of the node (used when printing output)
    var relations: List[(String, Node)],    // outgoing edges
    var position: Int                       // position of the node in the sentence
) */

case class SDPGraph(val nodes : Array[Node]) extends Graph {
    def duplicate() : Graph = {
        val newNodes : Array[Node] = nodes.map(x => Node(x.id, x.concept, List(), x.position))
        val getNodeById = nodes.map(x => (x.id, x)).toMap
        for (node1 <- nodes) {
            val newNode1 = getNodeById(node1.id)
            var relations : List[(String, Node)] = node1.relations.map({ case (relation, node2) => (relation, getNodeById(node2.id))})
            newNode1.relations = relations
        }
        return SDPGraph(newNodes)
    }

    def addEdge(node1: Node, node2: Node, relation: String) {
        node1.relations = (relation, node2) :: node1.relations
    }

    def clearEdges : Graph = {
        for (node <- nodes) {
            node.relations = List()
        }
        return this
    }

    def toConll(inputAnnotatedSentence: InputAnnotatedSentence) : String = {
        return "" // TODO
    }
}

object SDPGraph {
    def empty : SDPGraph = {
        return SDPGraph(Array())
    }

    def fromInputAnnotatedSentence(input: InputAnnotatedSentence) : SDPGraph = {
        val nodes : ArrayBuffer[Node] = new ArrayBuffer()
        for (i <- 0 until input.sentence.size) {
            if (input.singletonPredictions(i) == 0) {   // if not singleton (1 means singleton)
                nodes += Node(i.toString, input.sentence(i), List(), i)
            }
        }
        return SDPGraph(nodes.toArray)
    }

    def fromGold(sdp: Array[String], clearRelations: Boolean) : SDPGraph = {
        // 1  Pierre  Pierre  NNP -    +     _   _   _   _   _   _   _   _   _   _   _
        // id form    lemma   pos top pred arg1 arg2
        // spd strings should have no trailing \n or whitespace
        var fields = sdp.map(x => x.split("\t"))
        val len = sdp.size
        var singleton : Array[Boolean] = {
            sdp.map(x => x.matches("""[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t-\t.(\t_)*"""))   // ... - . _ _ _ ... is a singleton
        }
        val nodes = (0 until len).filter(!singleton(_)).map(i => Node(fields(i)(0), fields(i)(1), List(), i)).toArray
        val predicates = (0 until len).filter(i => fields(i)(5) == "+").map(i => nodes(i)).toList
        if (!clearRelations) {
            for ((dependent, i) <- nodes.zipWithIndex) {
                for ((relation, j) <- fields(i).drop(6).zipWithIndex) {
                    if (relation != "_") {
                        predicates(j).relations = (relation, dependent) :: predicates(j).relations
                    }
                }
            }
        }
        return SDPGraph(nodes)
    }
}

