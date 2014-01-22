package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.common.AnnotatedSentence

class Concepts(phraseConceptPairs: Array[PhraseConceptPair],
               useNER: Boolean = true,
               useDateExpr: Boolean = true) {

// This class contains the code used to invoke concepts.
// Concepts are invoked by calling the invoke() method, which returns a list of all
// the concepts that match a span starting at index i of the tokenized sentence.

    val conceptTable: Map[String, List[PhraseConceptPair]] = Map()  // maps the first word in the phrase to a list of phraseConceptPairs
    for (pair <- phraseConceptPairs) {
        val word = pair.words(0)
        conceptTable(word) = pair :: conceptTable.getOrElse(word, List())
        //logger(2, "conceptTable("+word+") = "+conceptTable(word))
    }

    private var tokens : Array[String] = Array()  // stores sentence.drop(i) (used in the dateEntity code to make it more concise)

    def invoke(input: AnnotatedSentence, i: Int) : List[PhraseConceptPair] = {
        // returns a list of all concepts that can be invoke starting at 
        // position i in input.sentence (i.e. position i in the tokenized input)
        // Note: none of the concepts returned have spans that go past the end of the sentence
        val sentence = input.sentence

        val conceptList = conceptTable.getOrElse(sentence(i), List()).filter(x => x.words == sentence.slice(i, i + x.words.size).toList) // TODO: this this case insensitive??
        return conceptList
    }
}

