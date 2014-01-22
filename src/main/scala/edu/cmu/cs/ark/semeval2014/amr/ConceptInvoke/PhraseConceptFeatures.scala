package edu.cmu.cs.ark.semeval2014.amr.ConceptInvoke


case class PhraseConceptFeatures(count: Double,
                                 conceptGivenPhrase: Double) {

    def this(string: String) = this(
        string.split(" ").find(x => x.matches("N=.*")).getOrElse("=0.0").split("=")(1).toDouble,
        string.split(" ").find(x => x.matches("""c\|p=.*""")).getOrElse("=0.0").split("=")(1).toDouble)

}

