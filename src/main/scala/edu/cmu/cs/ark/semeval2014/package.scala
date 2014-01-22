package edu.cmu.cs

package object ark {
    var verbosity = 1
    def logger(n: Int, s: Any) { if(n<=verbosity) System.err.println(s) }
}

