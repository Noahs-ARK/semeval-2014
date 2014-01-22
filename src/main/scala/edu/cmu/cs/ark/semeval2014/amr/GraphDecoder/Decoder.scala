package edu.cmu.lti.nlp.amr.GraphDecoder
import edu.cmu.lti.nlp.amr._

import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.io.BufferedOutputStream
import java.io.OutputStreamWriter
import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
import java.lang.Math.floor
import java.lang.Math.min
import java.lang.Math.max
import scala.io.Source
import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

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

