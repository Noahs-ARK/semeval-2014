package edu.cmu.cs.ark.semeval2014.common

import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse
import scala.Array.canBuildFrom


object InputAnnotatedSentenceParser {

// fromString loads an annotated sentence from a sentence in this format:
// Annotations are split with \t###\t, fields are split with \t, and the first field is a marker for the type of annotation
// Lines beginning with # are ignored.
// Annotations that cannot be represented over tokens can be added as comments.
// Ex:
// #20001001
// DEPS    1   NNP 2   nn  ### SDP 1   Pierre  Pierre  NNP -   +   _   _   _   _   _   _   _   _   _   _   _
// DEPS    2   NNP 9   nsubj   ### SDP 2   Vinken  _generic_proper_ne_ NNP -   -   compound    _   _   ARG1    ARG1    _   _   _   _   _   _
// DEPS    3   ,   2   punct   ### SDP 3   ,   _   ,   -   -   _   _   _   _   _   _   _   _   _   _   _
// DEPS    4   CD  5   num ### SDP 4   61  _generic_card_ne_   CD  -   +   _   _   _   _   _   _   _   _   _   _   _
// DEPS    5   NNS 6   npadvmod    ### SDP 5   years   year    NNS -   +   _   ARG1    _   _   _   _   _   _   _   _   _
// DEPS    6   JJ  2   amod    ### SDP 6   old old JJ  -   +   _   _   measure _   _   _   _   _   _   _   _

    def fromString(string: String) : InputAnnotatedSentence = {
        val allLines = string.split("\n")
        assert( allLines.size > 0 )
        val lines = allLines.filterNot(x => x.matches("^#.*"))
        val sent = new InputAnnotatedSentence(lines.size)
        sent.sentenceId = if (allLines.size > 0 && allLines(0).matches("#.*")) { allLines(0).split("\t")(0).tail } else { "" }
        sent.syntacticDependencies = DependencyParse(new Array(lines.size))
        for ((line, i) <- lines.zipWithIndex) {
        	val field = splitLine(line)
			sent.sentence(i) = field("SDP")(1)
			sent.pos(i) = field("SDP")(3)
			sent.isTop(i) = field("SDP")(4)=="+"
			if (field.contains("DEPS")) {
				sent.syntacticDependencies.deps(i) = SyntacticDependency.fromSemEval8(field("DEPS").mkString("\t"))
			}
        }
        return sent
    }

    def splitLine(line: String) : Map[String, Array[String]] = {
        val map : Map[String, Array[String]] = Map()
        for (split <- line.split("""\t###\t""")) {
            map(split.split("\t")(0)) = split.split("\t").tail
        }
        return map
    }
}

