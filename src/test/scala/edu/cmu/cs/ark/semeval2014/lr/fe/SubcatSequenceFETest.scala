// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr.fe

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import edu.cmu.cs.ark.semeval2014.utils.Corpus

class SubcatSequenceFETest extends FlatSpec with ShouldMatchers {
  val FIXTURE_FILE = "src/test/resources/one_sentence.dm.sdp.dependencies"
  val sentenceFixture = Corpus.getInputAnnotatedSentences(FIXTURE_FILE).head

  "A SubcatSequenceFE" should "get a fixture right" in {
    val fe = new SubcatSequenceFE
    val fa = new InMemoryFeatureAdder

    fe.setupSentence(sentenceFixture)
    fe.features(3, 5, fa)

    fa.featureValues.keySet.size should equal (2)
    fa.featureValues.keySet should contain ("subcat:nsubj-VBN*-dobj+-prep-prep-prep")
    fa.featureValues.keySet should contain ("subcatWithPOS:nsubj_NNS-VBN*-dobj_NN+-prep_IN-prep_TO-prep_IN")
  }
}
