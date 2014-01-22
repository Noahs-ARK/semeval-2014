// Copyright (c) 2014, Sam Thomson
package edu.cmu.cs.ark.semeval2014.utils

import org.scalatest.FlatSpec
import org.scalatest.matchers.ShouldMatchers
import CycleTester._


class CycleTesterTest extends FlatSpec with ShouldMatchers {
  "A CycleTester" should "detect a simple cycle" in {
    val graphA = Map(
      1 -> Set(2),
      2 -> Set(3),
      3 -> Set(1)
    )
    hasCycle(1 to 3, graphA) should equal (true)
  }

  it should "not detect a cycle in a simple DAG" in {
    val graphB = Map(
      1 -> Set(2, 3),
      2 -> Set(3)
    )
    hasCycle(1 to 3, graphB) should equal (false)
  }

  it should "not detect a cycle in a complicated DAG" in {
    val graphC = Map(
      1 -> Set(2),
      2 -> Set(3, 4),
      4 -> Set(5, 6),
      5 -> Set(6),
      6 -> Set(3)
    )
    hasCycle(1 to 6, graphC) should equal (false)
  }

  it should "detect a cycle in a complicated graph" in {
    val graphD = Map(
      1 -> Set(2),
      2 -> Set(3, 4),
      4 -> Set(5),
      5 -> Set(6),
      6 -> Set(3, 4)
    )
    hasCycle(1 to 6, graphD) should equal (true)
  }
}
