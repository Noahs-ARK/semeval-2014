package edu.cmu.cs.ark.semeval2014.utils

import edu.cmu.cs.ark.semeval2014.common.AnnotatedSentence
import scala.io.Source

object Corpus {
    def splitOnNewline(iterator: Iterator[String]) : Iterator[String] = {   // This treats more than one newline in a row as a single newline
        for {
            x <- iterator if x != ""
            p = (x :: iterator.takeWhile(_ != "").toList).mkString("\n")
        } yield p
    }

    def getAnnotatedSentences(filename: String) : Array[AnnotatedSentence] = {
        return splitOnNewline(Source.fromFile(filename).getLines).map(x => AnnotatedSentence.fromString(x)).toArray
    }
}

