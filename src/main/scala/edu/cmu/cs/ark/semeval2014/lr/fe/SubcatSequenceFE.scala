// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.{SyntacticDependency, InputAnnotatedSentence}
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder

/**
 * Adds features based on the ordered list of syntactic dependents of the src token.
 * A "*" indicates the head/srcToken, and a "+" indicates the destToken (if it is a direct child).
 * e.g. "subcat:nsubj-VBN*-dobj+-prep"
 */
class SubcatSequenceFE extends FE.FeatureExtractor with FE.EdgeFE {
  val MAX_DEPS = 6
  val IGNORED_DEP_LABELS = Set("punct", "cc", "aux", "conj")

  var leftRightDepsByHead: Map[Int, (Seq[SyntacticDependency], Seq[SyntacticDependency])] = _

  override def setupSentence(s: InputAnnotatedSentence) {
    super.setupSentence(s)
    leftRightDepsByHead = getSubcatSequences(s)
  }

  def getSubcatSequences(s: InputAnnotatedSentence): Map[Int, (Seq[SyntacticDependency], Seq[SyntacticDependency])] = {
    // throw out useless deps, truncate, and group by head
    val depsByHead = s.syntacticDependencies.deps.toSeq.
      filter(d => !IGNORED_DEP_LABELS.contains(d.relation)).
      groupBy(_.head)
    // split left deps from right deps
    depsByHead.mapValues(_.take(MAX_DEPS).partition(d => d.dependent < d.head))
  }

  def features(srcTokenIdx: Int, destTokenIdx: Int, fa: FeatureAdder) {
    def withoutPostags(d: SyntacticDependency): String = {
      d.relation + (if (d.dependent == destTokenIdx) "+" else "")
    }
    def withPostags(d: SyntacticDependency): String = {
      d.relation + "_" + sent.pos(d.dependent) + (if (d.dependent == destTokenIdx) "+" else "")
    }

    val (leftDeps, rightDeps) = leftRightDepsByHead.getOrElse(srcTokenIdx, (Nil, Nil))
    val postag = sent.pos(srcTokenIdx) + "*"
    val depStr = (leftDeps.map(withoutPostags) :+ postag) ++ rightDeps.map(withoutPostags)
    val depStrWithPostags = (leftDeps.map(withPostags) :+ postag) ++ rightDeps.map(withPostags)
    // TODO: these could be finer grained... i.e. include head lemma.pos, include destToken, include prepositions
    // they could also include versions with final prepositions thrown away (i.e. only core roles?)
    fa.add("subcat:" + depStr.mkString("-"))
    fa.add("subcatWithPOS:" + depStrWithPostags.mkString("-"))
  }
}
