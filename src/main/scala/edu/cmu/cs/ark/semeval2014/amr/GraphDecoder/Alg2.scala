package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder
import edu.cmu.cs.ark.semeval2014.amr._

import scala.collection.mutable.Set
import scala.collection.mutable.PriorityQueue
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import edu.cmu.cs.ark.semeval2014.amr.graph._

class Alg2(featureNames: List[String], labelSet: Array[(String, Int)], connected: Boolean = true) extends Decoder {
    val features = new Features(featureNames, labelSet.map(_._1))

    private var inputSave: Input = _
    def input : Input = inputSave
    def input_= (i: Input) {
        inputSave = i
        features.input = i
        precomputeEdgeWeights
    }

    private var edgeWeights : Array[Array[Array[(String, Double)]]] = Array()

    def precomputeEdgeWeights() {
        // WARNING: THIS CODE ASSUMES THAT THE LAGRANGE MULTIPLIERS ARE SET TO ZERO
        // TODO: fix this so errors don't occur
        var graph = input.graph.duplicate
        val nodes : Array[Node] = graph.nodes.toArray
        edgeWeights = weightMatrix(nodes, labelSet)
    }

    // TODO: remove string label from edgeWeights (it's uncessary)
    def weightMatrix(nodes: Array[Node], labels: Array[(String, Int)]) : Array[Array[Array[(String, Double)]]] = {
        val edgeWeights : Array[Array[Array[(String, Double)]]] = nodes.map(x => Array.fill(0)(Array.fill(0)("",0.0)))
        for (i <- 0 until nodes.size) {
            edgeWeights(i) = nodes.map(x => Array.fill(0)(("",0.0)))
            for (j <- 0 until nodes.size) {
                if (i == j) {
                    edgeWeights(i)(j) = Array((":self", 0.0)) // we won't add this to the queue anyway, so it's ok
                } else {
                    edgeWeights(i)(j) = Array.fill(labelSet.size)(("", 0.0))
                    features.setupNodes(nodes(i), nodes(j))
                    val feats = features.localFeatures(nodes(i), nodes(j))
                    features.weights.iterateOverLabels(feats,
                        x => edgeWeights(i)(j)(x.labelIndex) = (features.weights.labelset(x.labelIndex), x.value))
                }
            }
        }
        return edgeWeights
    }

    def decode(i: Input) : DecoderResult = { 
        input = i 
        decode 
    }

    def decode() : DecoderResult = {
        // Assumes that Node.relations has been setup correctly for the graph fragments
        var graph = input.graph.duplicate
        val nodes : Array[Node] = graph.nodes.toArray

        // Each node is numbered by its index in 'nodes'
        // Each set is numbered by its index in 'setArray'
        // 'set' contains the index of the set that each node is assigned to
        // At the start each node is in its own set
        val set : Array[Int] = nodes.zipWithIndex.map(_._2)
        val setArray : Array[Set[Int]] = nodes.zipWithIndex.map(x => Set(x._2))
        def getSet(nodeIndex : Int) : Set[Int] = { setArray(set(nodeIndex)) }

        var score = 0.0
        var feats = FeatureVector(features.weights.labelset)
        def addEdge(node1: Node, index1: Int, node2: Node, index2: Int, label: String, weight: Double, addRelation: Boolean = true) {
            if (!node1.relations.exists(x => ((x._1 == label) && (x._2.id == node2.id))) || !addRelation) { // Prevent adding an edge twice
                logger(1, "Adding edge ("+node1.concept+", "+label +", "+node2.concept + ") with weight "+weight.toString)
                if (addRelation) {
                    node1.relations = (label, node2) :: node1.relations
                }
                features.setupNodes(node1, node2)
                feats += features.localFeatures(node1, node2, features.weights.labelToIndex(label))
                score += weight
            }
            //logger(1, "set = " + set.toList)
            //logger(1, "nodes = " + nodes.map(x => x.concept).toList)
            //logger(1, "setArray = " + setArray.toList)
            if (set(index1) != set(index2)) {   // If different sets, then merge them
                //logger(1, "Merging sets")
                getSet(index1) ++= getSet(index2)
                val set2 = getSet(index2)
                for (index <- set2) {
                    set(index) = set(index1)
                }
                set2.clear()
            }
            //logger(1, "set = " + set.toList)
            //logger(1, "nodes = " + nodes.map(x => x.concept).toList)
            //logger(1, "setArray = " + setArray.toList)
        }

        //logger(1, "Adding edges already there")
        val nodeIds : Array[String] = nodes.map(_.id)
        for { (node1, index1) <- nodes.zipWithIndex
              (label, node2) <- node1.relations } {
            //logger(1, "1: node1 = "+node1.concept+" "+node1.id)
            //logger(1, "1: node2 = "+node2.concept+" "+node2.id)
            if (nodeIds.indexWhere(_ == node2.id) != -1) {
                val index2 = nodeIds.indexWhere(_ == node2.id)
                features.setupNodes(node1, node2)
                addEdge(node1, index1, node2, index2, label, features.weights.dot(features.localFeatures(node1, node2, label)), addRelation=false)
            } else {
                features.setupNodes(node1, node2)
                val temp = features.localFeatures(node1, node2, label)
                feats += temp
                score += features.weights.dot(temp)
            }
        }

        //logger(1, "set = " + set.toList)
        //logger(1, "nodes = " + nodes.map(x => x.concept).toList)
        //logger(1, "setArray = " + setArray.toList)

        //logger(1, "Adding positive edges")
        val neighbors : Array[Array[(String, Double)]] = {
            for ((nodes2, index1) <- edgeWeights.zipWithIndex) yield {
                val node1 = nodes(index1)
                for ((labelWeights, index2) <- nodes2.zipWithIndex) yield {
                    val node2 = nodes(index2)
                    val (label, weight) = labelWeights.view.zipWithIndex.map(x => (x._1._1, x._1._2 + { val (f,v) = features.ffLRLabelWithId(node1, node2, List())(0); features.weights(f, Some(x._2)) * v.conjoined } )).maxBy(_._2)
                    if (weight > 0) {   // Add if positive
                        addEdge(node1, index1, node2, index2, label, weight)
                    }
                    (label, weight)
                }
            }
        }

        // Uncomment to print neighbors matrix
        /*logger(0, "Neighbors matrix")
        for { (node1, index1) <- nodes.zipWithIndex
              ((label, weight), index2) <- neighbors(index1).zipWithIndex } {
            logger(0,"neighbors("+index1.toString+","+index2.toString+")="+label+" "+weight.toString)
        }*/

        // Add negative weights to the queue
        //logger(1, "Adding negative edges")
        val queue = new PriorityQueue[(Double, Int, Int, String)]()(Ordering.by(x => x._1))
        if (connected && set.size != 0 && getSet(0).size != nodes.size) {
            for { (node1, index1) <- nodes.zipWithIndex
                  ((label, weight), index2) <- neighbors(index1).zipWithIndex
                  if index1 != index2 && weight <= 0 && set(index1) != set(index2) } {
                queue.enqueue((weight, index1, index2, label))
            }
        }

        // Kruskal's algorithm
        if (connected) {    // if we need to produce a connected graph
            //logger(1, "queue = " + queue.toString)
            //logger(1, "set = " + set.toList)
            //logger(1, "nodes = " + nodes.map(x => x.concept).toList)
            //logger(1, "setArray = " + setArray.toList)
            while (set.size != 0 && getSet(0).size != nodes.size) {
                //logger(1, getSet(0).toList)
                //logger(1, set.toList)
                //logger(1, queue.toString)
                val (weight, index1, index2, label) = queue.dequeue // if there is a deque error (empty queue), it may be because the weight vector has some NaNs
                if (set(index1) != set(index2)) {
                    addEdge(nodes(index1), index1, nodes(index2), index2, label, weight)
                }
            }
        }
        //logger(0, "Alg2 score = "+score.toString)
        return DecoderResult(graph, feats, score)
    }
}

