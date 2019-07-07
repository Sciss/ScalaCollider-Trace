/*
 *  TracingUGenGraphBuilder.scala
 *  (ScalaCollider-Trace)
 *
 *  Copyright (c) 2016-2019 Hanns Holger Rutz. All rights reserved.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.synth
package trace

import de.sciss.synth.impl.{BasicUGenGraphBuilder, DefaultUGenGraphBuilderFactory}

import scala.collection.immutable.{IndexedSeq => Vec}

object TracingUGenGraphBuilder {
  /** Expands a synth graph with the default nesting graph builder. */
  def build(graph: SynthGraph): Result = {
    val b = new Impl
    UGenGraph.use(b) {
      val proxies = DefaultUGenGraphBuilderFactory.buildWith(graph, b)
      val ug = b.build(proxies)
      Result(ug, tracesControl = b.tracesControl, tracesAudio = b.tracesAudio)
    }
  }

  final case class Result(graph: UGenGraph, tracesControl: Vec[Trace], tracesAudio: Vec[Trace]) {
    val tracesControlChannels: Int = tracesControl.lastOption.map { t => t.index + t.numChannels } .getOrElse(0)
    val tracesAudioChannels  : Int = tracesAudio  .lastOption.map { t => t.index + t.numChannels } .getOrElse(0)
  }

  private final class Impl extends Basic

  final case class Trace(rate: Rate, index: Int, numChannels: Int, label: String)

  trait Basic extends TracingUGenGraphBuilder with BasicUGenGraphBuilder {
    private[this] var _tracesControl = Vector.empty: Vec[Trace]
    private[this] var _tracesAudio   = Vector.empty: Vec[Trace]
    
    final def tracesControl: Vec[Trace] = _tracesControl
    final def tracesAudio  : Vec[Trace] = _tracesAudio

    def addTrace(rate: Rate, numChannels: Int, label: String): Int = {
      val seq0  = if (rate == control) _tracesControl else if (rate == audio) _tracesAudio else
        throw new IllegalArgumentException(s"Trace must have audio or control rate: $rate")

      val index = seq0.lastOption.map { t => t.index + t.numChannels } .getOrElse(0)
      val t     = Trace(rate = rate, index = index, numChannels = numChannels, label = label)
      val seq1  = seq0 :+ t
      if (rate == control) _tracesControl = seq1 else _tracesAudio = seq1
      index
    }
  }
}
/** A UGen graph builder that supports the registration of
  * UGens via the `Trace` graph element.
  */
trait TracingUGenGraphBuilder extends UGenGraph.Builder {
  def addTrace(rate: Rate, numChannels: Int, label: String): Int
}