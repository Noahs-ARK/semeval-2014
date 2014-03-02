// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.lr.fe

import edu.cmu.cs.ark.semeval2014.lr.fe.FE.FeatureAdder
import scala.collection.mutable
import util.Vocabulary

/** Stores features in a map as they are added */
class InMemoryFeatureAdder extends FeatureAdder {
  val featureValues: mutable.Map[String, Double] = mutable.Map().withDefaultValue(0.0)

  def add(feature: String, value: Double) = featureValues(feature) += value
}

/** Stores numberized features in a map as they are added */
class InMemoryNumberizedFeatureAdder(val vocab: Vocabulary) extends FeatureAdder {
  val featureValues: mutable.Map[Int, Double] = mutable.Map().withDefaultValue(0.0)

  def add(feature: String, value: Double) = featureValues(vocab.num(feature)) += value

  def features = featureValues.keySet.toArray
}
