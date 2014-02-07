// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014

import java.io.PrintWriter
import edu.cmu.cs.ark.semeval2014.lr.{Model, MyGraph}
import edu.cmu.cs.ark.semeval2014.lr.LRParser.extractFeatures
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import util.{U, BasicFileIO}
import resource.managed

object ParallelParser {
  val GROUP_SIZE = 16

  def makePredictions(model: Model, inputSentences: TraversableOnce[InputAnnotatedSentence], outputFile: String) {
    for (out <- managed(new PrintWriter(BasicFileIO.openFileToWriteUTF8(outputFile)))) {
      for (sentGroup <- inputSentences.toIterator.grouped(GROUP_SIZE)) {
        // parse graphs in parallel
        val graphs = sentGroup.par.map(predict(model, _))
        // write them to file in sequence
        for ((graph, sent) <- graphs.seq zip sentGroup) {
          graph.print(out, sent)
          U.pf(".")
        }
      }
    }
  }

  // gah, why is an Array not a Traversable?
  def makePredictions(model: Model, inputSentences: Array[InputAnnotatedSentence], outputFile: String) {
    makePredictions(model, inputSentences.toIterator, outputFile)
  }

  def predict(model: Model, sent: InputAnnotatedSentence): MyGraph = {
    val ns = extractFeatures(model, sent, null)
    MyGraph.decodeEdgeProbsToGraph(sent, model.inferEdgeProbs(ns), model.labelVocab)
  }
}
