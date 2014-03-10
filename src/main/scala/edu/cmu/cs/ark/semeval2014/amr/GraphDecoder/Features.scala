package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import java.lang.Math
import java.lang.Math.log
import java.lang.Math.min
import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common._
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.lr.LRParser.initializeFeatureExtractors
import edu.cmu.cs.ark.semeval2014.lr.fe.FE

class Features(var featureNames: List[String]) {
    var weights = new FeatureVector()
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

    type FeatureFunction = (Node, Node, String) => FeatureVector
    type RootFeatureFunction = (Node) => FeatureVector

    val ffTable = Map[String, FeatureFunction](
        "CostAugEdgeId" -> ffCostAugEdgeId,
        "LRLabelWithId" -> ffLRLabelWithId,
        "SharedTaskFeatures" -> ffSharedTaskFeatures
    )

    def precompute() {
        for (ff <- allFE) {
            ff.setupSentence(inputAnnotatedSentence)
        }
    }

    def ffCostAugEdgeId(node1: Node, node2: Node, label: String) : FeatureVector = {        // Used for cost augmented decoding
        return FeatureVector(Map(("CA:Id1="+node1.id+"+Id2="+node2.id+"+L="+label) -> 1.0))
    }

    def ffLRLabelWithId(node1: Node, node2: Node, label: String) : FeatureVector = {        // Used for Langragian Relaxation
        return FeatureVector(Map(("Id1="+node1.id+"+L="+label) -> 1.0))
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

    def ffSharedTaskFeatures(node1: Node, node2: Node, label: String) : FeatureVector = {   // Computes all other features
        // WARNING: setupNodes must be called before this function is called
        // node1 is the edge tail (i.e. semantic head), and node2 the edge head (i.e. semantic dependent)
        val feats = FeatureVector()
        for ((feat, value) <- tokenFeatures.features) {
            feats.fmap(feat) = value
            feats.fmap(feat+"+L="+label) = value
        }
        for ((feat, value) <- edgeFeatures.features) {
            feats.fmap(feat) = value
            feats.fmap(feat+"+L="+label) = value
        }
        return feats
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

    def localFeatures(node1: Node, node2: Node, label: String) : FeatureVector = {
        val feats = new FeatureVector()
        for (ff <- featureFunctions) {
            feats += ff(node1, node2, label)
        }
        return feats
    }

    def localScore(node1: Node, node2: Node, label: String) : Double = {
        var score = 0.0
        for (ff <- featureFunctions) {
            score += weights.dot(ff(node1, node2, label))
        }
        return score
    }
}

