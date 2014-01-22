package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder


import edu.cmu.cs.ark.semeval2014.common.AnnotatedSentence
import edu.cmu.cs.ark.semeval2014.amr.DecoderResult

abstract class Decoder(featureNames: List[String]) {
    val features = new Features(featureNames) // maybe this should be renamed ff?

    def input : AnnotatedSentence
    def input_= (i: AnnotatedSentence)

    def decode() : DecoderResult
    def decode(i: AnnotatedSentence) : DecoderResult = {
        input = i
        decode
    }
}

