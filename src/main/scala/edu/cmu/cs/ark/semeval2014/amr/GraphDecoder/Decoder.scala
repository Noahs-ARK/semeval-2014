package edu.cmu.cs.ark.semeval2014.amr.GraphDecoder

import edu.cmu.cs.ark.semeval2014.amr._

abstract class Decoder {
    val features : Features

    def decode(i: Input) : DecoderResult
}

