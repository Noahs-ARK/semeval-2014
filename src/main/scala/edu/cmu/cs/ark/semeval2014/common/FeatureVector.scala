package edu.cmu.cs.ark.semeval2014.common

import scala.collection.mutable.Map
import scala.io.Source

// In Java, the important methods for this class are:
// public class FeatureVector {
//     public FeatureVector();                      // constructor (use this one for Java)
//     public double get(java.lang.String);         // look up the value of a feature
//     public double set(java.lang.String, double); // set the value of a feature
//     public double dot(FeatureVector);            // dot two feature vectors
//     public void $plus$eq(FeatureVector);         // += feature vector
//     public void fromFile(java.lang.String);      // for reading from file
//     public java.lang.String toString();          // converts to String (can be written to file)
// }

case class mul(scale: Double, v: FeatureVector);
// Trickyness below: see p.452 Programming Scala 2nd Edition (21.5 Implicit conversions)
case class MulAssoc(x: Double) { def * (v: FeatureVector) = mul(x, v) }
// in package.scala:
// implicit def doubleToMulAssoc(x: Double) = new MulAssoc(x)

case class FeatureVector(fmap : Map[String, Double] = Map[String, Double]()) {
    //def this() = this(Map[String, Double]())
    def dot(v: FeatureVector) : Double = {
        if (fmap.size <= v.fmap.size) {
            (fmap :\ 0.0)((f, sum) => f._2 * v.fmap.getOrElse(f._1, 0.0) + sum)
        } else {
            (v.fmap :\ 0.0)((f, sum) => fmap.getOrElse(f._1, 0.0) * f._2 + sum)
        }
    }
    def += (v: FeatureVector) : Unit = {
        for ((feat, value) <- v.fmap) {
            fmap(feat) = fmap.getOrElse(feat,0.0) + value
        }
    }
    def += (m: mul) : Unit = {
        val mul(scale, v) = m
        for ((feat, value) <- v.fmap) {
            fmap(feat) = fmap.getOrElse(feat,0.0) + scale * value
        }
    }
    def -= (v: FeatureVector) : Unit = this += -1.0 * v
    def -= (m: mul) : Unit = this += mul(-m.scale, m.v)
    def * (scale: Double) = mul(scale, this)
    def nonzero : Boolean = {
        var result = false
        for ((feat, value) <- fmap) {
            result = result || (value != 0.0)
        }
        return result
    }
    def slice(v: FeatureVector) : FeatureVector = {
        val f = new FeatureVector()
        for ((feat, _) <- v.fmap) {
            f.fmap(feat) = fmap.getOrElse(feat,0.0)
        }
        return f
    }
    def slice(func: String => Boolean) : FeatureVector = {
        val f = new FeatureVector()
        for ((feat, value) <- fmap if func(feat)) {
            f.fmap(feat) = value
        }
        return f
    }
    def read(iterator: Iterator[String]) {
        val regex = """(.*)[ \t]([^ \t]*)""".r
        fmap.clear()
        fmap ++= iterator.map((s : String) => { val regex(f,v) = s; (f,v.toDouble) })
    }
    def fromFile(filename: String) {
        val iterator = Source.fromFile(filename).getLines()
        read(iterator)
    }
    override def toString() : String = {
        val string = new StringBuilder
        for (key <- fmap.keys.toList.sorted) {
            if (fmap(key) != 0.0) {
                string.append(key + " " + fmap(key).toString + "\n")
            }
        }
        return string.toString
    }
    def unsorted() : String = {
        val string = new StringBuilder
        for ((key, value) <- fmap) {
            if (fmap(key) != 0.0) {
                string.append(key + " " + value.toString + "\n")
            }
        }
        return string.toString
    }
    def apply(feature_name: String) : Double = {
        fmap.getOrElse(feature_name, 0.0)
    }
    def get(feature_name: String) : Double = apply(feature_name)
    def set(feature_name: String, value: Double) {
        fmap(feature_name) = value
    }
}

