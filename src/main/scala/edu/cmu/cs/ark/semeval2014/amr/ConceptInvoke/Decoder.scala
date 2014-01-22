package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


import edu.cmu.cs.ark.semeval2014.amr.{Input, DecoderResult}

abstract class Decoder(featureNames: List[String]) {
    val features = new Features(featureNames) // maybe this should be renamed ff?

    def decode(input: Input) : DecoderResult
}

