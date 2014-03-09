package edu.cmu.cs.ark.semeval2014.amr.graph

import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

/*case class Node(
    var id: String,                         // unique id for each node in graph
    val concept: String,                    // name of the node (used when printing output)
    var relations: List[(String, Node)],    // outgoing edges
    var position: Int                       // position of the node in the sentence
) */

class SDPGraph(val nodes : ArrayBuffer[Node]) extends Graph {
}

object SDPGraph extends Graph {
    def empty : Graph = {
        
    }

    def fromInput(input: InputAnnotatedSentence) : SDPGraph = {
        for (i <- 0 until input.sentence.size)) {
            if (input.singletonPredictions(i) == 0) {   // if not singleton (1 means singleton)
                nodes += Node(i.toString, input.sentence(i), List(), i)
            }
        }
    }

    def fromGold(input: InputAnnotatedSentence, spd: Array[String], goldSingletons: Boolean = false) : SDPGraph = {
        assert(input.sentence.size == spd.size, "Input sentence length does not match spd file sentence length\ninput.sentence: "+input.sentence.toList.toString+"\nspd sentence: "+sdp.toList.toString)
        // 1  Pierre  Pierre  NNP -    +     _   _   _   _   _   _   _   _   _   _   _
        // id form    lemma   pos top pred arg1 arg2
        // spd strings should have no trailing \n or whitespace
        var fields = sdp.map(x => x.split("\t"))
        val len = input.sentence.size
        var singleton : Array[Bool] = if (goldSingletons) {
            sdp.map(x => x.matches("""[^\t]*\t[^\t]*\t[^\t]*\t[^\t]*\t-\t.(\t_)*"""))   // ... - . _ _ _ ... is a singleton
        } else {
            input.sentence.singletonPredictions.map(_ != 0)
        }
        val nodes = (0 until len).filter(!singleton(_)).map(i => Node(fields(i)(0), fields(i)(1), List(), i)).toList
        val getNodeById = nodes.toMap
        val predicates = (0 until len).filter(i => fields(i)(5) == "+").map(i => nodes(i)).toList
        for ((dependent, i) <- nodes.zipWithIndex) {
            for ((relation, j) <- fields(i).drop(6).zipWithIndex) {
                if (relation != "_") {
                    predicates(j).relations = predicates(j).relations ::: (relation, dependent)
                }
            }
        }
        return SDPGraph(nodes)
    }
}

