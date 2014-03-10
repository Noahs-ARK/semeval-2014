package edu.cmu.cs.ark.semeval2014.amr

import scala.io.Source.fromFile
import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.common._
import edu.cmu.cs.ark.semeval2014.utils._
import edu.cmu.cs.ark.semeval2014.amr.graph._

case class Input(inputAnnotatedSentence: InputAnnotatedSentence,
                 graph: Graph)

object Input {
    def loadInputAnnotatedSentences(options: Map[Symbol, String]) : Array[InputAnnotatedSentence] = {
        if (!options.contains('depInput)) {
            System.err.println("Error: please specify -depInput filename"); sys.exit(1)
        }
        return Corpus.getInputAnnotatedSentences(options('depInput))
    }
    def loadSDPGraphs(options: Map[Symbol, String], oracle: Boolean = false) : Array[SDPGraph] = {
        if (!options.contains('sdpInput)) {
            System.err.println("Error: please specify -sdpInput filename"); sys.exit(1)
        }
        val sdp = Corpus.splitOnNewline(fromFile(options('sdpInput)).getLines).map(
            x => SDPGraph.fromGold(x.split("\n"), !oracle)).toArray
        return sdp
    }
}

