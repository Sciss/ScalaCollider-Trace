/*
 *  TraceGraphFunction.scala
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

import de.sciss.osc
import de.sciss.osc.Bundle
import de.sciss.synth.trace.TraceSynth.Link
import de.sciss.synth.trace.ugen.Trace

import scala.collection.immutable.{IndexedSeq => Vec}

// XXX TODO --- ugly, we have to duplicate all of `GraphFunction`
object TraceGraphFunction {
  private[this] final var uniqueIDCnt = 0
  private[this] final val uniqueSync  = new AnyRef

  private def uniqueID(): Int = uniqueSync.synchronized {
    uniqueIDCnt += 1
    val result = uniqueIDCnt
    result
  }

  private[synth] def mkSynthDefName(): String = s"$$trace_${uniqueID()}"
}
// XXX TODO --- ugly, we have to duplicate all of `GraphFunction`
final class TraceGraphFunction[A](val peer: () => A)(implicit val result: GraphFunction.Result[A]) {
  import TraceGraphFunction.mkSynthDefName

  def play(target: Node = Server.default.defaultGroup, outBus: Int = 0,
           fadeTime: Double = 0.02, addAction: AddAction = addToHead,
           toBundleSync : Vec[osc.Packet] = Vector.empty,
           toBundleAsync: Vec[osc.Packet] = Vector.empty): TraceSynth = {

    val server      = target.server
    val defName     = mkSynthDefName()
    val sg = SynthGraph {
      result.close(peer(), fadeTime)
    }
    val res         = TracingUGenGraphBuilder.build(sg)
    val synthDef    = SynthDef(defName, res.graph)
    val syn         = Synth(server)
    val busControl  = Bus.control(server, res.tracesControlChannels)
    val busAudio    = Bus.audio  (server, res.tracesAudioChannels  )
    val dataControl = Link(busControl, res.tracesControl)
    val dataAudio   = Link(busAudio  , res.tracesAudio  )
    var synArgs     = List[ControlSet]("out" -> outBus)
    if (busControl.numChannels != 0) synArgs ::= Trace.controlNameKr -> busControl.index
    if (busAudio  .numChannels != 0) synArgs ::= Trace.controlNameAr -> busAudio  .index
    val synthMsg    = syn.newMsg(synthDef.name, args = synArgs, target = target, addAction = addAction)
    val defFreeMsg  = synthDef.freeMsg
    val completion  = Bundle.now(synthMsg +: defFreeMsg +: toBundleSync: _*)
    val msgRecv     = synthDef.recvMsg(completion)
    val syncPacket  = if (toBundleAsync.isEmpty) msgRecv else osc.Bundle.now(toBundleAsync :+ msgRecv: _*)
    server ! syncPacket
    TraceSynth(peer = syn, controlLink = dataControl, audioLink = dataAudio)
  }
}