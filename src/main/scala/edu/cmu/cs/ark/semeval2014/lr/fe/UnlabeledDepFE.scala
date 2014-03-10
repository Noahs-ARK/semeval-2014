// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.common.InputAnnotatedSentence
import edu.cmu.cs.ark.semeval2014.nlp.DependencyParse
import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder

/**
 * Adds features based on the ordered list of syntactic dependents of the src token.
 * A "*" indicates the head/srcToken, and a "+" indicates the destToken (if it is a direct child).
 * e.g. "subcat:nsubj-VBN*-dobj+-prep"
 */
class UnlabeledDepFE extends FE.FeatureExtractor with FE.EdgeFE {
  private var deps: DependencyParse = _

  override def setupSentence(s: InputAnnotatedSentence) {
    super.setupSentence(s)
    deps = s.syntacticDependencies
  }

  def features(srcTokenIdx: Int, destTokenIdx: Int, fa: FeatureAdder) {
    val path = deps.dependencyPath(srcTokenIdx, destTokenIdx)
    val unlabeledPathString = for ((upPath, downPath) <- path) yield {
      "^" * upPath.length + "v" * downPath.length
    }
    val unlabeledPathAndDirString = for ((upPath, downPath) <- path) yield {
      val up = upPath.sliding(2).collect({ case List(child, par) => if (child < par) "^R" else "^L" })
      val down = downPath.sliding(2).collect({ case List(par, child) => if (par < child) "vR" else "vL" })
      up.mkString("") + down.mkString("")
    }
    fa.add("unlabeledDepPath:" + unlabeledPathString.getOrElse(""))
    fa.add("unlabeledDepAndDirPath:" + unlabeledPathAndDirString.getOrElse(""))
  }
}
