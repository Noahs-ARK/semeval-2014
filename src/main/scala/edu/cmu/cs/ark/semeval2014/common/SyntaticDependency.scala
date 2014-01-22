package edu.cmu.cs.ark.semeval2014.common

// In Java this class is:
// public class edu.cmu.cs.ark.semeval2014.common.SyntacticDependency {
//    public SyntacticDependency(int, int, java.lang.String);
//    public static SyntacticDependency fromStanford(java.lang.String);
//    public static SyntacticDependency fromConll(java.lang.String);
//    public static SyntacticDependency fromSemEval8(java.lang.String);
//    public int head();
//    public int dependent();
//    public java.lang.String relation();
// }

case class SyntacticDependency(head: Int, dependent: Int, relation: String)

object SyntacticDependency {
    def fromSemEval8(string: String) : SyntacticDependency = {
        // The SemEval8 companion data is in the format:
        // POS\tHEAD\tREL
        // which is stupid.  It should be preprocessed into:
        // TOKEN_NUM\tPOS\tHEAD\tREL
        // This is what the below code assumes.
        // See /home/jmflanig/sdp/companion/sb.bn.cpn.better on cab for the data in this format.
        val fields = string.split("\t")
        return SyntacticDependency(fields(2).toInt-1, fields(0).toInt-1, fields(3))
    }
    def fromConll(string: String) : SyntacticDependency = {
        val fields = string.split("\t")
        return SyntacticDependency(fields(6).toInt-1, fields(0).toInt-1, fields(7))
    }
    val Stanford = """([^(]+[^-]+([0-9]+), *[^-]+([0-9]+)) *""".r
    def fromStanford(string: String) : SyntacticDependency = {
        val Stanford(relation, head, dependent) = string
        return SyntacticDependency(head.toInt-1, dependent.toInt-1, relation)
    }
}

