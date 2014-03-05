package edu.cmu.cs.ark.semeval2014.utils

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import scala.io.Source
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentenceParser
import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentenceParser

object Corpus {
    def splitOnNewline(iterator: Iterator[String]) : Iterator[String] = {   // This treats more than one newline in a row as a single newline
        for {
            x <- iterator if x != ""
            p = (x :: iterator.takeWhile(_ != "").toList).mkString("\n")
        } yield p
    }

    def getInputAnnotatedSentences(filename: String) : Array[InputAnnotatedSentence] = {
        return splitOnNewline(Source.fromFile(filename).getLines).map(x => InputAnnotatedSentenceParser.fromString(x)).toArray
    }
}

