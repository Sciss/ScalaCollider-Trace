/*
 *  Trace.scala
 *  (ScalaCollider-Trace)
 *
 *  Copyright (c) 2016 Institute of Electronic Music and Acoustics, Graz.
 *  Written by Hanns Holger Rutz.
 *
 *	This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.synth
package trace
package ugen

import de.sciss.synth
import de.sciss.synth.UGenSource._
import de.sciss.synth.ugen.Out

import scala.Predef.{any2stringadd => _}

object Trace {
  /** Control name used to receive control-bus index for the aggregated control-rate traces. */
  final val controlNameKr = "$trace_c"
  /** Control name used to receive audio-bus index for the aggregated audio-rate traces. */
  final val controlNameAr = "$trace_a"

  def controlName(rate: Rate): String =
    if      (rate == control) controlNameKr
    else if (rate == audio  ) controlNameAr
    else throw new IllegalArgumentException(s"Trace rate must be control or audio :$rate")
}

/** A graph element that adds tracing to its input argument.
  *
  * Example:
  * {{{
  * val g = traceGraph {
  *   val n = WhiteNoise.ar
  *   Trace(n, "noise")
  *   Pan2.ar(n)
  * }
  *
  * val x   = g.play()
  * val fut = x.traceFor(numFrames = 10) // Future
  * fut.foreach { traces => traces.foreach(_.print()) }
  * }}}
  *
  * @param in     the signal to monitor; can be multi-channel.
  * @param label  the label to associate with the element, as it shows up in a GUI
  */
final case class Trace(in: GE, label: String = "debug") extends UGenSource.ZeroOut {
  import Trace._

  def rate: MaybeRate = in.rate

  protected def makeUGens: Unit = unwrap(this, in.expand.outputs)

  def makeUGen(args: Vec[UGenIn]): Unit = UGenGraph.builder match {
    case tb: TracingUGenGraphBuilder =>
      import synth._
      import Ops.stringToControl
      val aRate       = args.map(_.rate).max.max(control)
      val args1       = matchRateFrom(args, 0, aRate)
      val numChannels = args.size
      val off         = tb.addTrace(aRate, numChannels = numChannels, label = label)
      val ctlName     = if (aRate == control) controlNameKr else controlNameAr
      val ctl         = ctlName.kr
      val index       = ctl + off
      Out(control, index, args1)

    case _ => // just emit a warning and do nothing
      Console.err.println(s"Warning: `Trace` inside a non-tracing UGen graph builder.")
  }
}
