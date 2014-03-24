package edu.cmu.cs.ark.semeval2014.utils

import scala.collection.mutable

/**
* Caches computed values of f
* @param f the function to memoize
*/
class Memoized[-T, +R](f: T => R) extends (T => R) {
  private[this] val cache = mutable.Map.empty[T, R]

  def apply(x: T): R = cache.getOrElseUpdate(x, f(x))
}

object Memoized {
  def apply[T, R](f: T => R) = new Memoized(f)

  /**
   * Y combinator
   * f needs to written so as to take a second parameter, which will be the
   * memoized version of f
   * (see http://michid.wordpress.com/2009/02/23/function_mem/)
   */
  def recursively[T, R](f: (T, T => R) => R): T => R = {
    lazy val memoized: T => R = Memoized(f(_, memoized))
    memoized
  }
}
