package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import scala.collection.mutable.Set
import scala.collection.mutable.PriorityQueue
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common._
import edu.cmu.cs.ark.semeval2014.amr.graph._

class Alg2(featureNames: List[String], labelSet: Array[(String, Int)], connected: Boolean = true) extends Decoder {
    val features = new Features(featureNames)

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
        var graph = input.graph.get.duplicate
        val nodes : Array[Node] = graph.nodes.filter(_.name != None).toArray
        edgeWeights = weightMatrix(nodes, labelSet)
    }

    def weightMatrix(nodes: Array[Node], labels: Array[(String, Int)]) : Array[Array[Array[(String, Double)]]] = {
        for ((node1, index1) <- nodes.zipWithIndex) yield {
            for ((node2, index2) <- nodes.zipWithIndex) yield {
                if (index1 == index2) {
                    Array((":self", 0.0)) // we won't add this to the queue anyway, so it's ok
                } else {
                    labels.map(x => (x._1, features.localScore(node1, node2, x._1)))
                }
            }
        }
    }

    def decode(i: Input) : DecoderResult = { 
        input = i 
        decode 
    }

    def decode() : DecoderResult = {
        // Assumes that Node.relations has been setup correctly for the graph fragments
        var graph = input.graph.get.duplicate
        val nodes : Array[Node] = graph.nodes.filter(_.name != None).toArray

        // Each node is numbered by its index in 'nodes'
        // Each set is numbered by its index in 'setArray'
        // 'set' contains the index of the set that each node is assigned to
        // At the start each node is in its own set
        val set : Array[Int] = nodes.zipWithIndex.map(_._2)
        val setArray : Array[Set[Int]] = nodes.zipWithIndex.map(x => Set(x._2))
        def getSet(nodeIndex : Int) : Set[Int] = { setArray(set(nodeIndex)) }

        var score = 0.0
        var feats = new FeatureVector()
        def addEdge(node1: Node, index1: Int, node2: Node, index2: Int, label: String, weight: Double, addRelation: Boolean = true) {
            if (!node1.relations.exists(x => ((x._1 == label) && (x._2.id == node2.id))) || !addRelation) { // Prevent adding an edge twice
                logger(1, "Adding edge ("+node1.concept+", "+label +", "+node2.concept + ") with weight "+weight.toString)
                if (addRelation) {
                    node1.relations = (label, node2) :: node1.relations
                }
                feats += features.localFeatures(node1, node2, label)
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
                addEdge(node1, index1, node2, index2, label, features.localScore(node1, node2, label), addRelation=false)
            } else {
                feats += features.localFeatures(node1, node2, label)
                score += features.localScore(node1, node2, label)
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
                    val (label, weight) = labelWeights.map(x => (x._1, x._2 + features.weights.dot(features.ffLRLabelWithId(node1, node2, x._1)))).maxBy(_._2)
                    if (weight > 0) {   // Add if positive
                        addEdge(node1, index1, node2, index2, label, weight)
                    }
                    (label, weight)
                }
            }
        }

        // Uncomment to print neighbors matrix
        /*logger(1, "Neighbors matrix")
        for { (node1, index1) <- nodes.zipWithIndex
              ((label, weight), index2) <- neighbors(index1).zipWithIndex } {
            logger(1,"neighbors("+index1.toString+","+index2.toString+")="+label+" "+weight.toString)
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
                //logger(2, queue.toString)
                val (weight, index1, index2, label) = queue.dequeue
                if (set(index1) != set(index2)) {
                    addEdge(nodes(index1), index1, nodes(index2), index2, label, weight)
                }
            }
        }
        
        //logger(1, "nodes = "+nodes.toList)
        if(nodes.size > 0) {
            if (features.rootFeatureFunctions.size != 0) {
                graph.root = nodes.map(x => (x, features.rootScore(x))).maxBy(_._2)._1
            } else {
                //logger(1, "Setting root to "+nodes(0).id)
                graph.root = nodes(0)
            }
            feats += features.rootFeatures(graph.root)

            nodes.map(node => { node.relations = node.relations.reverse })
            if (connected) {
                graph.makeTopologicalOrdering()
            }
        } else {
            graph = Graph.empty()
        }

        return DecoderResult(graph, feats, score)
    }
}

