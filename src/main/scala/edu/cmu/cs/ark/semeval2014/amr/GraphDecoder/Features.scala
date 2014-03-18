package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import java.lang.Math
import java.lang.Math.log
import java.lang.Math.min
import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common.logger
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.common.FastFeatureVector._
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.lr.LRParser.initializeFeatureExtractors
import edu.cmu.cs.ark.semeval2014.lr.fe.FE

class Features(var featureNames: List[String], labelSet: Array[String]) {
    var weights = new FeatureVector(labelSet)   // TODO: maybe weights should be passed in to the constructor
    private var inputAnnotatedSentence: InputAnnotatedSentence = _
    private var graph: Graph = _
    private val allFE : Array[FE.FeatureExtractor] = initializeFeatureExtractors().toArray.map(_.asInstanceOf[FE.FeatureExtractor])
    for (ff <- allFE) {
        ff.initializeAtStartup
    }

    def input: Input = Input(inputAnnotatedSentence, graph)
    def input_= (i: Input) {
        inputAnnotatedSentence = i.inputAnnotatedSentence
        graph = i.graph
        precompute
    }

    type FeatureFunction = (Node, Node, List[(String, Value)]) => List[(String, Value)]

    val ffTable = Map[String, FeatureFunction](
        "CostAugEdgeId" -> ffCostAugEdgeId,
        "LRLabelWithId" -> ffLRLabelWithId,
        "BiasFeatures" -> ffBiasFeatures,
        "SharedTaskFeatures" -> ffSharedTaskFeatures
    )

    def precompute() {
        for (ff <- allFE) {
            ff.setupSentence(inputAnnotatedSentence)
        }
    }

    def ffCostAugEdgeId(node1: Node, node2: Node, feats: List[(String, Value)]) : List[(String, Value)] = {
        // Used for cost augmented decoding
        return ("CA:Id1="+node1.id+"+Id2="+node2.id, Value(0.0, 1.0)) :: feats
    }

    def ffLRLabelWithId(node1: Node, node2: Node, feats: List[(String, Value)]) : List[(String, Value)] = {
        // Used for Langragian Relaxation
        return ("LR:Id1="+node1.id, Value(0.0, 1.0)) :: feats
    }

    def ffBiasFeatures(node1: Node, node2: Node, feats: List[(String, Value)]) : List[(String, Value)] = {
        return ("Bias", Value(0.01, 0.01)) :: feats     // Bias features are unregularized.  Adjusting these values only adjusts the condition number of the optimization problem.
    }

    class TokenFeatureAdder extends FE.FeatureAdder {
        var features : List[(String, Double)] = List()
        def add(feature: String, value: Double) {
            features = (feature, value) :: features
        }
    }

    class EdgeFeatureAdder extends FE.FeatureAdder {
        var features : List[(String, Double)] = List()
        def add(feature: String, value: Double) {
            features = (feature, value) :: features
        }
    }

    val tokenFeatures = new TokenFeatureAdder()
    val edgeFeatures = new EdgeFeatureAdder()

    def setupNodes(node1: Node, node2: Node) {
        tokenFeatures.features = List()
        edgeFeatures.features = List()
        for (ff <- allFE) {
            if (ff.isInstanceOf[FE.TokenFE]) {
                ff.asInstanceOf[FE.TokenFE].features(node1.position, tokenFeatures)
                ff.asInstanceOf[FE.TokenFE].features(node2.position, tokenFeatures)
            } else if (ff.isInstanceOf[FE.EdgeFE]) {
                ff.asInstanceOf[FE.EdgeFE].features(node1.position, node2.position, edgeFeatures)
            } else {
                System.err.println("******** WARNING: skipping unimplemented feature extractor.  LabelFE's are not implemented yet. ********")
            }
        }
    }

    def ffSharedTaskFeatures(node1: Node, node2: Node, feats: List[(String, Value)]) : List[(String, Value)] = {
        // WARNING: setupNodes must be called before this function is called
        // node1 is the edge tail (i.e. semantic head), and node2 the edge head (i.e. semantic dependent)
        var features = feats
        for ((feat, value) <- tokenFeatures.features) {
            features = (feat, Value(value, value)) :: features
        }
        for ((feat, value) <- edgeFeatures.features) {
            features = (feat, Value(value, value)) :: features
        }
        return features
    }

    var featureFunctions : List[FeatureFunction] = featureNames.map(x => ffTable(x))

    def setFeatures(featureNames: List[String]) {
        featureFunctions = featureNames.map(x => ffTable(x))
    }

    def addFeatureFunction(featureName: String) {
        if (!featureNames.contains(featureName)) {
            featureNames = featureName :: featureNames
            featureFunctions = ffTable(featureName) :: featureFunctions
        }
    }

    def localFeatures(node1: Node, node2: Node) : List[(String, Value)] = {
        var feats : List[(String, Value)] = List()
        for (ff <- featureFunctions) {
            feats = ff(node1, node2, feats)
        }
        return feats
    }

    def localFeatures(node1: Node, node2: Node, label: Int) : List[(String, ValuesList)] = {
        return localFeatures(node1, node2).map(
            x => (x._1, ValuesList(x._2.unconjoined, List(Conjoined(label, x._2.conjoined)))))
    }

    def localFeatures(node1: Node, node2: Node, label: String) : List[(String, ValuesList)] = {
        return localFeatures(node1, node2, weights.labelToIndex(label))
    }

    def localScore(node1: Node, node2: Node, label: String) : Double = {
        return weights.dot(localFeatures(node1, node2, weights.labelToIndex(label)))
    }
}

