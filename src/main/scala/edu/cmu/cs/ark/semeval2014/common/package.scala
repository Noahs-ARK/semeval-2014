package edu.cmu.cs.ark.semeval2014
import scala.language.implicitConversions

package object common {
    implicit def doubleToMulAssoc(x: Double) = new MulAssoc(x)
    var verbosity = 1
    def logger(n: Int, s: Any) { if(n<=verbosity) System.err.println(s) }
}
