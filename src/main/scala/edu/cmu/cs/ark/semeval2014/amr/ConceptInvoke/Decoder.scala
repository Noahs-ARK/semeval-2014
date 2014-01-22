package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


import edu.cmu.cs.ark.semeval2014.common.AnnotatedSentence
import edu.cmu.cs.ark.semeval2014.amr.DecoderResult

abstract class Decoder(featureNames: List[String]) {
    val features = new Features(featureNames) // maybe this should be renamed ff?

    def decode(input: AnnotatedSentence) : DecoderResult
}

