/*
 *  TraceGraphFunction.scala
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

import de.sciss.synth.trace.TraceSynth.{Data, Link}
import de.sciss.synth.trace.ugen.Trace

import scala.concurrent.Future

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

  def traceFor(target: Node = Server.default.defaultGroup, outBus: Int = 0,
               fadeTime: Double = 0.02, addAction: AddAction = addToHead,
               duration: Double = 0.0, numFrames: Int = 0, numBlocks: Int = 0,
               bundle: BundleBuilder = new BundleBuilder): Future[List[Data]] = {
    val s         = target.server
    val group     = Group(s)
    val groupMsg  = group.newMsg(target, addAction)
    bundle.addSync(groupMsg)
    val res       = playToBundle(target = group, outBus = outBus, fadeTime = fadeTime, addAction = addToHead,
                                 bundle = bundle)
    val fut       = res.traceForToBundle(duration = duration, numFrames = numFrames, numBlocks = numBlocks,
                                         doneAction = freeGroup, bundle = bundle)

    s ! bundle.result()
    fut
  }

  def play(target: Node = Server.default.defaultGroup, outBus: Int = 0,
           fadeTime: Double = 0.02, addAction: AddAction = addToHead,
           bundle: BundleBuilder = new BundleBuilder): TraceSynth = {
    val s   = target.server
    val res = playToBundle(target = target, outBus = outBus, fadeTime = fadeTime, addAction = addAction,
      bundle = bundle)
    s ! bundle.result()
    res
  }

  def playToBundle(target: Node = Server.default.defaultGroup, outBus: Int = 0,
                   fadeTime: Double = 0.02, addAction: AddAction = addToHead,
                   bundle: BundleBuilder = new BundleBuilder): TraceSynth = {

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
    val msgRecv     = synthDef.recvMsg
    bundle.addSync (synthMsg  )
    bundle.addSync (defFreeMsg)
    bundle.addAsync(msgRecv   )

    TraceSynth(peer = syn, controlLink = dataControl, audioLink = dataAudio)
  }
}