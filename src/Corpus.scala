package edu.cmu.lti.nlp.amr

import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer

object Corpus {
    def splitOnNewline(iterator: Iterator[String]) : Iterator[String] = {   // This treats more than one newline in a row as a single newline
        for {
            x <- iterator if x != ""
            p = (x :: iterator.takeWhile(_ != "").toList).mkString("\n")
        } yield p
    }
}

