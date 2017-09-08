// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr.fe

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import edu.cmu.cs.ark.semeval2014.utils.Corpus
import edu.cmu.cs.ark.semeval2014.common.ConstitLoader

class ConstitFETest extends FlatSpec with ShouldMatchers {
  val SENTENCE_FIXTURE_FILE = "src/test/resources/one_sentence.dm.sdp.dependencies"
  val CONSTIT_FIXTURE_FILE = "src/test/resources/one_sentence.sexpr"
  val sentenceFixture = Corpus.getInputAnnotatedSentences(SENTENCE_FIXTURE_FILE).head
  val constitFixtures = ConstitLoader.loadSexprFile(CONSTIT_FIXTURE_FILE)
  sentenceFixture.constitTree = constitFixtures.get(sentenceFixture.sentenceId)

  "A ConstitFE" should "get a direct object path correct" in {
    val fe = new ConstitFE
    val fa = new InMemoryFeatureAdder

    fe.setupSentence(sentenceFixture)
    fe.features(3, 5, fa)  // "boosted" -> "investment"
    println(fa.featureValues)
    fa.featureValues.keySet.size should equal (3)
    fa.featureValues.keySet should contain ("ConstitPath=VBN^VP_VPvNP_NPvNN")
    fa.featureValues.keySet should contain ("W1=boosted+ConstitPath=VBN^VP_VPvNP_NPvNN")
    fa.featureValues.keySet should contain ("W2=investment+ConstitPath=VBN^VP_VPvNP_NPvNN")
  }

  it should "get a subj path correct" in {
    val fe = new ConstitFE
    val fa = new InMemoryFeatureAdder

    fe.setupSentence(sentenceFixture)
    fe.features(3, 1, fa) // "boosted" -> "investment"
    println(fa.featureValues)
    fa.featureValues.keySet.size should equal(3)
    fa.featureValues.keySet should contain("ConstitPath=VBN^VP_VP^S_SvNP_NPvNNS")
    fa.featureValues.keySet should contain("W1=boosted+ConstitPath=VBN^VP_VP^S_SvNP_NPvNNS")
    fa.featureValues.keySet should contain("W2=devices+ConstitPath=VBN^VP_VP^S_SvNP_NPvNNS")
  }
}
