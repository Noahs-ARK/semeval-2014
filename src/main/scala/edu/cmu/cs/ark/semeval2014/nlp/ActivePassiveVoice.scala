// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.nlp

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import Voice._

sealed abstract class Voice(val name: String) {
  override def toString = name
}
object Voice {
  object Passive extends Voice("PAS")
  object Active extends Voice("ACT")
  object NoVoice extends Voice("")
}

/**
 * Detects ACTIVE/PASSIVE voice (detected using heuristics taken from SEMAFOR)
 */
class ActivePassiveVoice {
  def findVoice(tokenIdx: Int, sent: InputAnnotatedSentence): Voice = {
    val word = sent.sentence(tokenIdx)
    val postag = sent.pos(tokenIdx)

    if (!postag.startsWith("V")) {
      NoVoice
    } else {
      if (word.toLowerCase.equals("been")) {
        Active
      } else if (!postag.equals("VBN")) {
        Active
      } else {
        findVoiceInParents(sent.syntacticDependencies(tokenIdx).head, sent)
      }
    }
  }

  private def findVoiceInParents(tokenIdx: Int, sent: InputAnnotatedSentence): Voice = {
    if (tokenIdx == -1) {
      Passive
    } else {
      val word = sent.sentence(tokenIdx).toLowerCase
      val pos = sent.pos(tokenIdx)

      if (pos.startsWith("NN")) {
        Passive
      } else if (word.matches("am|are|is|was|were|be|been|being")) {
        Passive
      } else if (word.matches("ha(ve|s|d|ving)")) {
        Active
      } else if (pos.matches("VBZ|VBD|VBP|MD")) {
        Passive
      } else {
        findVoiceInParents(sent.syntacticDependencies(tokenIdx).head, sent)
      }
    }
  }

  def getAllVoices(sent: InputAnnotatedSentence): Array[Voice] = {
    (0 until sent.size).map(findVoice(_, sent)).toArray
  }
}
