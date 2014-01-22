package edu.cmu.cs.ark.semeval2014.amr.graph


case class Span(var start: Int, var end: Int, var nodeIds: List[String], var words: String, var amr: Node, var coRef: Boolean) {
    def format() : String = {
        if (start < end) {
            coRef match {
                case false => start.toString+"-"+end.toString+"|"+nodeIds.mkString("+")
                case true => "*"+start.toString+"-"+end.toString+"|"+nodeIds.mkString("+")
            }
        } else {
            ""
        }
    }
}

