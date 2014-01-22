package edu.cmu.lti.nlp.amr

case class Input(tokenized: Array[String],
                 dependencies: Array[Dependency],
                 pos: Array[String])

