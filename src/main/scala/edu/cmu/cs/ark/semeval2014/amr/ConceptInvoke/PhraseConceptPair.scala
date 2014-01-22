package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


case class PhraseConceptPair(words: List[String], graphFrag: String, features: PhraseConceptFeatures) {

/* The format of the phrase-concept table is
expert ||| (person :ARG1-of expert-41) ||| Count=4 ConceptGivenPhrase=0.3077
*/

    def this(string: String) = this(
        string.split(""" \|\|\| """)(0).split(" ").toList,
        string.split(""" \|\|\| """)(1),
        new PhraseConceptFeatures(string.split(""" \|\|\| """)(2))
    )

}

