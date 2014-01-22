package edu.cmu.cs.ark.semeval2014.common

case class Dependency(head: Int, dependent: Int, relation: String)

object Dependency {
    def fromSemEval8(string: String) : Dependency = {
        // The SemEval8 companion data is in the format:
        // POS\tHEAD\tREL
        // which is stupid.  It should be preprocessed into:
        // TOKEN_NUM\tPOS\tHEAD\tREL
        // This is what the below code assumes.
        val fields = string.split("\t")
        return Dependency(fields(2).toInt-1, fields(0).toInt-1, fields(3))
    }
    def fromConll(string: String) : Dependency = {
        val fields = string.split("\t")
        return Dependency(fields(6).toInt-1, fields(0).toInt-1, fields(7))
    }
    val Stanford = """([^(]+[^-]+([0-9]+), *[^-]+([0-9]+)) *""".r
    def fromStanford(string: String) : Dependency = {
        val Stanford(relation, head, dependent) = string
        return Dependency(head.toInt-1, dependent.toInt-1, relation)
    }
}

