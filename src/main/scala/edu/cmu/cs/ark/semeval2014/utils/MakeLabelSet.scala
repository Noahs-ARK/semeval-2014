package edu.cmu.cs.ark.semeval2014.utils

import scala.io.Source
import java.lang.Math.max
import scala.collection.mutable.Map
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common._

object MakeLabelSet {
    val usage = """Usage: scala -classpath . edu.cmu.cs.ark.semeval2014.utils.MakeLabelSet < corpus.sdp > labels
Creates the labelset, and figures out which labels should be deterministic"""
    type OptionMap = Map[Symbol, Any]

    def parseOptions(map : OptionMap, list: List[String]) : OptionMap = {
        def isSwitch(s : String) = s(0) == '-'
        list match {
            case Nil => map
            case "-v" :: value :: tail =>
                      parseOptions(map ++ Map('verbosity -> value.toInt), tail)
            case option :: tail => println("Error: Unknown option "+option)
                               sys.exit(1)
      }
    }

    def main(args: Array[String]) {
        val options = parseOptions(Map(),args.toList)
        if (options.contains('verbosity)) {
            verbosity = options('verbosity).asInstanceOf[Int]
        }

        val graphs = Input.loadSDPGraphs(Map('sdpInput -> "/dev/stdin"), oracle = true)
        val labelset : Map[String, (Int, Graph)] = Map()
        for { graph <- graphs
              node <- graph.nodes } {
            val labelcount : List[(String, Int)] = node.relations.map(_._1).distinct.map(x => (x, node.relations.count(_._1 == x)))
            for ((label, count) <- labelcount) {
                //labelset(label) = (max(count, labelset.getOrElse(label, (0,SDPGraph.empty))._1), graph)
                labelset(label) = if (count > labelset.getOrElse(label, (0,SDPGraph.empty))._1) {
                    (count, graph)
                } else {
                    labelset(label)
                }
            }
        }
        for ((label, (count, graph)) <- labelset) {
            println(label+" "+count.toString)
            logger(1, "Graph: "+graph.toString)
            for (node <- graph.nodes) {
                logger(2, "node.relations = " +node.relations.map(x => (x._1,x._2.concept)).toString)
                val labelcount : List[(String, Int)] = node.relations.map(_._1).distinct.map(x => (x, node.relations.count(_._1 == x)))
                logger(2, "labelcount = "+labelcount.toString)
            }
            println()
        }
    }
}
