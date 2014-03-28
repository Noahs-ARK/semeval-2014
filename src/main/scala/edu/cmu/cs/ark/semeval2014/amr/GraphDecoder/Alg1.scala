package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import scala.collection.mutable.ArrayBuffer
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._

class Alg1(featureNames: List[String], labelSet: Array[(String, Int)], connectedConstraint: String = "none") extends Decoder {
    // Base class has defined:
    // val features: Features
    val features = new Features(featureNames, labelSet.map(_._1))

    def decode(input: Input) : DecoderResult = {
        // Assumes that Node.relations has been setup correctly for the graph fragments
        features.input = input  // WARNING: This needs to be called before graphObj is created, because when graphObj is created we compute the features of the edges that are already present in the graph fragments
        var graph = input.graph.duplicate
        val nodes : Array[Node] = graph.nodes.toArray                  // TODO: test to see if a view is faster
        val graphObj = new GraphObj(graph, nodes.toArray, features)    // graphObj keeps track of the edge weights and the connectivity of the graph as we add edges

        logger(1, "Alg1")
        //logger(2, "weights = " + features.weights)

        for { (node1, index1) <- nodes.zipWithIndex
              relations = node1.relations.map(_._1)
              ((label, maxCardinality), labelIndex) <- labelSet.zipWithIndex } {

            if (relations.count(_ ==label) == 0) {   // relations.count(_ == label) counts the edges that are already in the graph fragments
                // Search over the nodes, and pick the ones with highest score
                val nodes2 : Array[(Node, Int, Double)] = nodes.zipWithIndex.filter(x => x._2 != index1).map(x => (x._1, x._2, graphObj.localScore(index1, x._2, labelIndex))).filter(x => x._3 > 0 && x._1.id != node1.id).sortBy(-_._3).take(maxCardinality)

                for ((node2, index2, weight) <- nodes2) {
                    logger(1, "Adding edge ("+node1.concept+", "+label +", "+node2.concept + ") with weight "+weight.toString)
                    graphObj.addEdge(node1, index1, node2, index2, label, weight)
                }
                if (nodes2.size > 0) {
                    logger(2, "node1 = " + node1.concept)
                    logger(2, "label = " + label)
                    logger(2, "nodes2 = " + nodes.toString)
                    //logger(1, "feats = " + feats.toString)
                }
            } else if (relations.count(_ == label) < maxCardinality) {   // relations.count(_ == label) counts the edges that are already in the graph fragments
                // Search over the nodes, and pick the ones with highest score
                val relationIds : List[String] = node1.relations.map(_._2.id)   // we assume if there is a relation already in the fragment, we can't add another relation type between the two nodes
                val nodes2 : Array[(Node, Int, Double)] = nodes.zipWithIndex.filter(x => x._2 != index1).map(x => (x._1, x._2, graphObj.localScore(index1, x._2, labelIndex))).filter(x => x._3 > 0 && x._1.id != node1.id && !relationIds.contains(x._1.id)).sortBy(-_._3).take(maxCardinality - relations.count(_ == label))
                for ((node2, index2, weight) <- nodes2) {
                    logger(1, "Adding edge ("+node1.concept+", "+label +", "+node2.concept + ") with weight "+weight.toString)
                    graphObj.addEdge(node1, index1, node2, index2, label, weight)
                }
                if (nodes2.size > 0) {
                    logger(2, "node1 = " + node1.concept)
                    logger(2, "label = " + label)
                    logger(2, "nodes2 = " + nodes.toString)
                    //logger(1, "feats = " + feats.toString)
                }
            }
        }

        return DecoderResult(graph, graphObj.feats, graphObj.score)
    }
}

