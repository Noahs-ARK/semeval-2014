package edu.cmu.cs.ark.semeval2014
import scala.language.implicitConversions

// In Java, this package is:
// public final class edu.cmu.cs.ark.semeval2014.common.package extends java.lang.Object{
//     public static void logger(int, java.lang.Object);    // print to std err
//     public static void verbosity_$eq(int);               // set verbosity
//     public static int verbosity();                       // get current verbosity
// }

package object common {
    implicit def doubleToMulAssoc(x: Double) = new MulAssoc(x)
    var verbosity = 1
    def logger(n: Int, s: Any) { if(n<=verbosity) System.err.println(s) }
}
