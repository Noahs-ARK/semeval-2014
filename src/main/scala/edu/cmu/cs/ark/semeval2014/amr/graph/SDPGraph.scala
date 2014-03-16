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

    def preds : Set[Int] = {
        return Set() ++ nodes.filter(_.relations.size > 0).map(x => x.position).toSet
    }

    def toConll(sent: InputAnnotatedSentence) : String = {
        //logger(1, "nodes = "+nodes.toList.map(x => (x.concept, x.position)).toString)
        val output : ArrayBuffer[String] = ArrayBuffer()
        output += "#" + sent.sentenceId
        val nodeArray : Array[Option[Node]] = (0 until sent.size).map(x => None).toArray
        val isPred : Array[Boolean] = (0 until sent.size).map(x => false).toArray
        for (node <- nodes) {
            if (node.relations.size > 0) {
                isPred(node.position) = true
                nodeArray(node.position) = Some(node)
            }
        }
        //logger(1, "nodeArray = " + nodeArray.toList.map(x => if(x == None) { None } else { Some((x.get.concept,x.get.position)) }))
        //logger(1, "isPred = "+isPred.toList.zip(nodeArray.toList.map(x => if(x == None) { None } else { Some((x.get.concept,x.get.position)) })))
        //logger(1, "isPred = "+isPred.toList.toString)
        for (i <- 0 until sent.size) {
            var str = "%d\t%s\tlemmaz\t%s\t%s\t%s".format(i + 1, sent.sentence(i), sent.pos(i),
                if(sent.isTop(i)) { "+" } else { "-" }, if(isPred(i)) { "+" } else { "-" })
            for (head <- 0 until sent.size
                 if (isPred(head))) {
                val relation = nodeArray(head).get.relations.find(x => x._2.position == i)
                str = str + "\t" + ( if(relation == None) { "_" } else { relation.get._1 } )
            }
            output += str
        }
        return output.mkString("\n")
    }

    override def toString : String = {
        val output : ArrayBuffer[String] = ArrayBuffer()
        val nodeArray : Array[Option[Node]] = (0 until nodes.size).map(x => None).toArray
        val isPred : Array[Boolean] = (0 until nodes.size).map(x => false).toArray
        for (node <- nodes) {
            if (node.relations.size > 0) {
                isPred(node.position) = true
                nodeArray(node.position) = Some(node)
            }
        }
        for (i <- 0 until nodes.size) {
            var str = "%d\t%s\t%s".format(i + 1, nodes(i).concept, if(isPred(i)) { "+" } else { "-" })
            for (head <- 0 until nodes.size
                 if (isPred(head))) {
                val relation = nodeArray(head).get.relations.find(x => x._2.position == i)
                str = str + "\t" + ( if(relation == None) { "_" } else { relation.get._1 } )
            }
            output += str
        }
        return output.mkString("\n")
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

    def fromGold(sdpInput: Array[String], clearRelations: Boolean) : SDPGraph = {
        // 1  Pierre  Pierre  NNP -    +     _   _   _   _   _   _   _   _   _   _   _
        // id form    lemma   pos top pred arg1 arg2
        // spd strings should have no trailing \n or whitespace
        var sdp = sdpInput.filter(!_.matches("^#.*"))
        var fields = sdp.map(x => x.split("\t"))
        val len = sdp.size
        var singleton : Array[Boolean] = {
            sdp.map(x => x.matches("""[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t-\t.(\t_)*"""))   // . . . . . - . _ _ _ ... is a singleton
        }
        //logger(0, sdp.mkString("\n"))
        val nodeArray = (0 until len).map(i => if(!singleton(i)) { Some(Node(fields(i)(0), fields(i)(1), List(), i)) } else { None } ).toArray
        val predicates = (0 until len).filter(i => fields(i)(5) == "+").map(i => nodeArray(i).get).toList
        if (!clearRelations) {
            for ((dependent, i) <- nodeArray.zipWithIndex if dependent != None) {
                for ((relation, j) <- fields(i).drop(6).zipWithIndex) {
                    if (relation != "_") {
                        predicates(j).relations = (relation, dependent.get) :: predicates(j).relations
                    }
                }
            }
        }
        return SDPGraph(nodeArray.filter(_ != None).map(_.get))
    }
}

