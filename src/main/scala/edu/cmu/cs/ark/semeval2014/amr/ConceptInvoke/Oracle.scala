package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


import edu.cmu.cs.ark.semeval2014.common.FeatureVector
import edu.cmu.cs.ark.semeval2014.amr.{Input, DecoderResult}
import edu.cmu.cs.ark.logger

/*** Defined in package.scala ***
type PhraseConceptPair = (List[String], String, PhraseConceptFeatures)
********************************/

class Oracle(featureNames: List[String],
             phraseConceptPairs: Array[PhraseConceptPair],
             useNER: Boolean = true)
    extends Decoder(featureNames) {
    // Base class has defined:
    // val features: Features

    val conceptInvoker = new Concepts(phraseConceptPairs)

    def decode(input: Input) : DecoderResult = {
        assert(input.graph != None, "Error: stage1 oracle decoder was not given a graph")
        val graph = input.graph.get
        val sentence = input.sentence
        var score = 0.0
        val feats = new FeatureVector()

        logger(1, "\n--- Oracle Decoder ---\n")
        logger(1, "Spans: "+graph.spans.toList)

        for (span <- graph.spans) {
            val words = span.words.split(" ").toList
            val conceptList = conceptInvoker.invoke(input, span.start)
            //logger(1, "words = "+words.toString)
            //logger(1, "conceptList = "+conceptList.toString)
            val matching = conceptList.filter(x => x.words == words && x.graphFrag == span.amr.prettyString(detail = 0, pretty = false))
            for (concept <- matching) {
                val f = features.localFeatures(input, concept, span.start, span.end)
                feats += f
                score += features.weights.dot(f)
                logger(1, "\nphraseConceptPair: "+concept.toString)
                logger(1, "feats:\n"+f.toString)
                logger(1, "score:\n"+score.toString)
            }
        }

        logger(1, "Oracle feats:\n"+feats.toString)

        return DecoderResult(graph, feats, score)
    }

}

