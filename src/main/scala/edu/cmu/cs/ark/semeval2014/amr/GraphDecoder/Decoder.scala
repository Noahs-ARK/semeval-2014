package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder


import edu.cmu.cs.ark.semeval2014.amr.Input
import edu.cmu.cs.ark.semeval2014.amr.DecoderResult

abstract class Decoder(featureNames: List[String]) {
    val features = new Features(featureNames) // maybe this should be renamed ff?

    def input : Input
    def input_= (i: Input)

    def decode() : DecoderResult
    def decode(i: Input) : DecoderResult = {
        input = i
        decode
    }
}

