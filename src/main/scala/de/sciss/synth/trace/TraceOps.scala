/*
 *  TraceOps.scala
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

import de.sciss.synth.Ops.NodeOps
import de.sciss.synth.trace.TraceSynth.BundleBuilder

import scala.language.implicitConversions

object TraceOps {
  implicit def TraceNodeOps(ts: TraceSynth): NodeOps = new NodeOps(ts.peer)

  def tracePlay[A: GraphFunction.Result](thunk: => A): TraceSynth = tracePlay()(thunk)

  /** Wraps the body of the thunk argument in a `SynthGraph`, adds an output UGen, and plays the graph
    * in a synth attached to a given target.
    *
    * @param  target        the target with respect to which to place the synth
    * @param  addAction     the relation between the new synth and the target
    * @param  outBus        audio bus index which is used for the synthetically generated `Out` UGen.
    * @param  fadeTime      if `&gt;= 0`, specifies the fade-in time for a synthetically added amplitude envelope.
    *                       if negative, avoids building an envelope.
    * @param  thunk         the thunk which produces the UGens to play
    * @return               a reference to the spawned Synth and its trace data
    */
  def tracePlay[A](target: Node = Server.default, outBus: Int = 0,
                   fadeTime: Double = 0.02,
                   addAction: AddAction = addToHead, bundle: BundleBuilder = new BundleBuilder)
                  (thunk: => A)
                  (implicit result: GraphFunction.Result[A]): TraceSynth = {
    val fun = new TraceGraphFunction(() => thunk)(result)
    fun.play(target = target, outBus = outBus, fadeTime = fadeTime, addAction = addAction, bundle = bundle)
  }

  /** Constructs a `TraceGraphFunction`, on which then for example `play` can be called. */
  def traceGraph[A](thunk: => A)(implicit result: GraphFunction.Result[A]): TraceGraphFunction[A] =
    new TraceGraphFunction(() => thunk)(result)
}