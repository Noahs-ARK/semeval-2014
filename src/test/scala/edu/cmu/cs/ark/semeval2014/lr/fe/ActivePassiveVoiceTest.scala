package edu.cmu.cs.ark.semeval2014.lr.fe

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.FlatSpec
import edu.cmu.cs.ark.semeval2014.utils.Corpus
import edu.cmu.cs.ark.semeval2014.nlp.{Voice, ActivePassiveVoice}

class ActivePassiveVoiceTest extends FlatSpec with ShouldMatchers {
  val FIXTURE_FILE = "src/test/resources/one_sentence.dm.sdp.dependencies"
  val sentenceFixture = Corpus.getInputAnnotatedSentences(FIXTURE_FILE).head

  "An ActivePassiveVoice" should "get a fixture right" in {
    val apv = new ActivePassiveVoice

    val voices = apv.getAllVoices(sentenceFixture)

//    println(voices.zipWithIndex.mkString(" "))
    for ((voice, i) <- voices.zipWithIndex) {
      i match {
        case 2 => voice should equal (Voice.Active)  // "have"
        case 3 => voice should equal (Voice.Passive) // "boosted"
        case 27 => voice should equal(Voice.Active) // "are"
        case 28 => voice should equal(Voice.Active) // "growing"
        case _ => voice should equal(Voice.NoVoice)
      }
    }
  }
}
