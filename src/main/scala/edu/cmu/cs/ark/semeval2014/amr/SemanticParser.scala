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
import edu.cmu.cs.ark.semeval2014.amr.GraphDecoder._
import edu.cmu.cs.ark.semeval2014.amr.graph._
import edu.cmu.cs.ark.semeval2014.common._
import edu.cmu.cs.ark.semeval2014.utils._
import edu.cmu.cs.ark.semeval2014.prune.Prune
import edu.cmu.cs.ark.semeval2014.topness.TopClassifier

object SemanticParser {

    val usage = """Usage:
scala -classpath . edu.cmu.lti.nlp.amr.AMRParser --stage1-decode --stage1-weights weights --concept-table concepts --ner namedEntities --tok tokenized.txt < inputFile
scala -classpath . edu.cmu.lti.nlp.amr.AMRParser --stage2-train -l labelset < trainfile > output_weights
scala -classpath . edu.cmu.lti.nlp.amr.AMRParser --stage2-decode -w weights -l labelset < input > output""" // TODO: update this

    type OptionMap = Map[Symbol, String]

    def parseOptions(map : OptionMap, list: List[String]) : OptionMap = {
        def isSwitch(s : String) = (s(0) == '-')
        list match {
            case Nil => map
            case "-decoder" :: value :: l =>             parseOptions(map + ('decoder -> value), l)
            case "-formalism" :: value :: l =>           parseOptions(map + ('formalism -> value), l)
            case "-labelset" :: value :: l =>            parseOptions(map + ('labelset -> value), l)
            case "-mode" :: value :: l =>                parseOptions(map + ('mode -> value), l)
            case "-model" :: value :: l =>               parseOptions(map + ('trainingWeightsFile -> value), l)
            case "-sdpOutput" :: value :: tail =>        parseOptions(map + ('sdpOutput -> value), tail)
            case "-sdpInput" :: value :: tail =>         parseOptions(map + ('sdpInput -> value), tail)
            case "-depInput" :: value :: tail =>         parseOptions(map + ('depInput -> value), tail)
            case "-goldSingletons" :: tail =>            parseOptions(map + ('goldSingletons -> "true"), tail)
            case "-numIters" :: value :: l =>            parseOptions(map + ('trainingPasses -> value), l)
            case "-saveEvery" :: value :: l =>           parseOptions(map + ('trainingSaveInterval -> value), l)
            case "-learningRate" :: value :: l =>        parseOptions(map + ('trainingStepsize -> value), l)
            case "-trainingLoss" :: value :: l =>        parseOptions(map + ('trainingLoss -> value), l)
            case "-trainingOptimizer" :: value :: l =>   parseOptions(map + ('trainingOptimizer -> value), l)
            case "-trainingCostScale" :: value :: l =>   parseOptions(map + ('trainingCostScale -> value), l)
            case "-l2reg" :: value :: l =>               parseOptions(map + ('trainingRegularizerStrength -> value), l)
            case "-v" :: value :: tail =>                parseOptions(map ++ Map('verbosity -> value), tail)
            case string :: opt2 :: tail if isSwitch(opt2) => parseOptions(map ++ Map('infile -> string), list.tail)
            case string :: Nil =>  parseOptions(map ++ Map('infile -> string), list.tail)
            case option :: tail => println("Warning: Unknown option "+option); parseOptions(map, tail)
      }
    }

    def time[A](a: => A) = {
       val now = System.nanoTime
       val result = a
       val micros = (System.nanoTime - now) / 1000
       System.err.println("Decoded in %,d microseconds".format(micros))
       result
    }

    def main(args: Array[String]) {

        if (args.length == 0) { println(usage); sys.exit(1) }
        val options = parseOptions(Map(),args.toList)

        verbosity = options.getOrElse('verbosity, "0").toInt

        if (!options.contains('mode)) {
                System.err.println("Error: please specify either -mode train or -mode test")
                sys.exit(1)
        }

        if (options('mode) == "train") {

            ////////////////// Training  ////////////////

            val trainObj = new GraphDecoder.TrainObj(options)
            trainObj.train

            val topClassifier = new TopClassifier()
            topClassifier.train(options('depInput), options('model) + ".topmodel")

            //val singletonPredictor = new Prune(inputAnnotatedSentences, options('model))
            // TODO: train singletonPredictor

        } else {

            /////////////////// Decoding /////////////////

            val decoder = GraphDecoder.Decoder(options)

            if (!options.contains('model)) {
                System.err.println("Error: No model file specified")
                sys.exit(1)
            }
            logger(0, "Reading model file")
            decoder.features.weights.read(Source.fromFile(options('model)).getLines())
            logger(0, "done")

            val inputAnnotatedSentences = Input.loadInputAnnotatedSentences(options)
            val inputGraphs = if (options.contains('goldSingletons)) {
                Input.loadSDPGraphs(options, oracle = false)
            } else {
                val singletonPredictor = new Prune(inputAnnotatedSentences, options('model))
                singletonPredictor.loadModels
                singletonPredictor.predictIntoInputs
                inputAnnotatedSentences.map(x => SDPGraph.fromInputAnnotatedSentence(x))
            }
            assert(inputAnnotatedSentences.size == inputGraphs.size, "Input sdp and dependency files not the same line count")

            val topClassifier = new TopClassifier()
            topClassifier.loadModel(options('model)+".topmodel")

            for (i <- 0 until inputGraphs.size) {
                val graph = decoder.decode(Input(inputAnnotatedSentences(i), inputGraphs(i))).graph
                if (!options.contains('goldTops)) {
                    decideTops(topClassifier, graph.asInstanceOf[SDPGraph], inputAnnotatedSentences(i))
                }
                println(graph.toConll(inputAnnotatedSentences(i))+"\n")
            }
        }
    }

    // TODO: this should probably go somewhere else
    def decideTops(topClassifier: TopClassifier, graph: SDPGraph, inputAnnotatedSentence: InputAnnotatedSentence) {
        val topness : Array[Double] = Array.fill(inputAnnotatedSentence.size)(-1e6)
        val preds : Set[Int] = graph.preds
        for (i <- 0 until topness.size) {
            if (preds.contains(i)) {
                topness(i) = topClassifier.topness(inputAnnotatedSentence, i)
            }
        }
        inputAnnotatedSentence.isTop = topness.map(x => false)
        inputAnnotatedSentence.isTop(topness.view.zipWithIndex.maxBy(_._1)._2) = true   // .view.zipWithIndex.maxBy(_._1)._2 is argmax
    }
}

