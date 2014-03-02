package edu.cmu.cs.ark.semeval2014.common

import scala.collection.mutable.Map

// In Java, the important members of this class are:
// public class InputAnnotatedSentence {
//     public InputAnnotatedSentence(String[], SyntacticDependency[], String[]);  // Constructor
//     public static InputAnnotatedSentence fromString(String);                   // Static constructor
//     public String[] sentence();
//     public SyntacticDependency[] syntaticDependencies();
//     public String[] pos();
// }

case class InputAnnotatedSentence(sentenceId: String,
                                  sentence: Array[String],
                                  syntacticDependencies: Array[SyntacticDependency],
                                  pos: Array[String],
                                  predicates: Array[Integer],
                                  singletons: Array[Integer]) {
  def size = sentence.length
}

object InputAnnotatedSentence {

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
        val lines = allLines.filterNot(x => x.matches("^#.*"))
        val annotations = InputAnnotatedSentence(
                            if (allLines.size > 0 && allLines(0).matches("#.*")) { allLines(0).split("\t")(0).tail } else { "" },
                            new Array(lines.size),
                            if (lines.size > 0 && lines(0).matches("""^DEPS\t.*|.*\t###\tDEPS\t.*""")) { new Array(lines.size) } else { new Array(0) },
                            new Array(lines.size),
                            new Array(lines.size),
                            new Array(lines.size))
        for ((line, i) <- lines.zipWithIndex) {
            try {
            val field = splitLine(line)
            annotations.sentence(i) = field("SDP")(1)
            annotations.pos(i) = field("SDP")(3)
            if (field.contains("DEPS")) {
                annotations.syntacticDependencies(i) = SyntacticDependency.fromSemEval8(field("DEPS").mkString("\t"))
            }
            } catch {
                case _ : Throwable => throw new RuntimeException("Error processing line:\n"+line)
            }
        }
        return annotations
    }

    def splitLine(line: String) : Map[String, Array[String]] = {
        val map : Map[String, Array[String]] = Map()
        for (split <- line.split("""\t###\t""")) {
            map(split.split("\t")(0)) = split.split("\t").tail
        }
        return map
    }
}

