package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.common.FeatureVector
import edu.cmu.cs.ark.semeval2014.amr.Input


/**************************** Feature Functions *****************************/
// TODO: Would this be faster if the ffTable was replaced with boolean variables and 
// lots of if statements?

// TODO: the input to the feature function is just Input and Span.  Change to use Span?

class Features(featureNames: List[String]) {
    var weights = new FeatureVector()

    type FeatureFunction = (Input, PhraseConceptPair, Int, Int) => FeatureVector

    val ffTable = Map[String, FeatureFunction](
        "bias" -> ffBias,
        "length" -> ffLength,
        "count" -> ffCount,
        "conceptGivenPhrase" -> ffConceptGivenPhrase,
        "phraseConceptPair" -> ffPhraseConceptPair,
        "pairWith2WordContext" -> ffPairWith2WordContext
    )

    def ffBias(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        return FeatureVector(Map("bias" -> 1.0))
    }

    def ffLength(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        return FeatureVector(Map("len" -> concept.words.size))
    }

    def ffCount(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        return FeatureVector(Map("N" -> concept.features.count))
    }

    def ffConceptGivenPhrase(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        return FeatureVector(Map("c|p" -> concept.features.conceptGivenPhrase))
    }

    def ffPhraseConceptPair(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        return FeatureVector(Map("CP="+concept.words.mkString("_")+"=>"+concept.graphFrag.replaceAllLiterally(" ","_") -> 1.0))
    }

    def ffPairWith2WordContext(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        val cp = "CP="+concept.words.mkString("_")+"=>"+concept.graphFrag.replaceAllLiterally(" ","_")
        val feats = new FeatureVector()
        if (start > 0) {
            feats.fmap(cp+"+"+"W-1="+input.sentence(start-1)) = 1.0
        }
        if (end < input.sentence.size) {
            feats.fmap(cp+"+"+"W+1="+input.sentence(end)) = 1.0
        }
        return feats
    }

    var featureFunctions : List[FeatureFunction] = featureNames.map(x => ffTable(x)) // TODO: error checking on lookup

    def localFeatures(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : FeatureVector = {
        // Calculate the local features
        val feats = new FeatureVector()
        for (ff <- featureFunctions) {
            feats += ff(input, concept, start, end)
        }
        return feats
    }

    def localScore(input: Input, concept: PhraseConceptPair, start: Int, end: Int) : Double = {
        var score = 0.0
        for (ff <- featureFunctions) {
            score += weights.dot(ff(input, concept, start, end))
        }
        return score
    }

}

