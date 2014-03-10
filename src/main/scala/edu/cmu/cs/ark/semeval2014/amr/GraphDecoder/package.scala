package edu.cmu.cs.ark.semeval2014.amr

import java.lang.Math.abs
import java.lang.Math.log
import java.lang.Math.exp
import java.lang.Math.random
import java.lang.Math.floor
import java.lang.Math.min
import java.lang.Math.max
import scala.io.Source
import scala.io.Source.stdin
import scala.io.Source.fromFile
import scala.util.matching.Regex
import scala.collection.mutable.Map
import scala.collection.mutable.Set
import scala.collection.mutable.ArrayBuffer
import edu.cmu.cs.ark.semeval2014.amr._
import edu.cmu.cs.ark.semeval2014.common._

package object GraphDecoder {
    type OptionMap = Map[Symbol, String]

    def getFeatures(options: OptionMap) : List[String] = {
        options.getOrElse('stage2Features, "conceptBigram,rootConcept").split(",").toList.filter(x => x != "edgeId" && x != "labelWithId")
    }

    def Decoder(options: OptionMap) : GraphDecoder.Decoder = {
        if (!options.contains('stage2Labelset)) {
            System.err.println("Error: No labelset file specified"); sys.exit(1)
        }

        val labelset: Array[(String, Int)] = {
            Source.fromFile(options('stage2Labelset)).getLines().toArray.map(x => {
                val split = x.split(" +")
                (split(0), if (split.size > 1) { split(1).toInt } else { 1000 })
            })
        }

        val features = getFeatures(options)
        logger(0, "features = " + features)

        val connected = !options.contains('stage2NotConnected)
        logger(0, "connected = " + connected)

        if (!options.contains('stage2Decoder)) {
            System.err.println("Error: No stage2 decoder specified"); sys.exit(1)
        }

        val decoder: Decoder = options('stage2Decoder) match {
            //case "Alg1" => new Alg1(features, labelset)
            //case "Alg1a" => new Alg1(features, labelset, connectedConstraint = "and")
            case "Alg2" => new Alg2(features, labelset, connected)
            case "LR" => new LagrangianRelaxation(features, labelset, 1, 500)
            case x => { System.err.println("Error: unknown stage2 decoder " + x); sys.exit(1) }
        }

        val outputFormat = options.getOrElse('outputFormat,"triples").split(",").toList
        if (outputFormat.contains("AMR") && !connected) {
            println("Cannot have both -stage2NotConnected flag and --outputFormat \"AMR\""); sys.exit(1)
        }

        if (options('stage2Decoder) == "Alg1" && outputFormat.contains("AMR")) {
            println("Cannot have --outputFormat \"AMR\" for stage2 Alg1 (graph may not be connected!)")
            sys.exit(1)
        }

        return decoder
    }
}

