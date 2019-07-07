/*
 *  TraceSynth.scala
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

import java.util.Locale

import de.sciss.synth
import de.sciss.synth.trace.TraceSynth.{Data, Link}
import de.sciss.synth.trace.ugen.Trace
import de.sciss.synth.trace.{TracingUGenGraphBuilder => UGB}

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions

object TraceSynth {
  /** Returns the peer `Synth` of the trace-synth.
    * Probably not very useful as `SynthOps` are another implicit extension.
    */
  implicit def peer(t: TraceSynth): Synth = t.peer

  final case class Link(bus: Bus, traces: Vec[UGB.Trace]) {
    def numChannels: Int  = bus.numChannels
    def rate       : Rate = bus.rate

    def isEmpty : Boolean = numChannels == 0
    def nonEmpty: Boolean = !isEmpty
  }

  final case class Data(bus: Bus, numFrames: Int, traceMap: Map[String, Vec[Vec[Float]]]) {
    def rate: Rate = bus.rate

    /** Utility that simply returns the first trace found in the map.
      * Useful when only one trace is generated.
      */
    def firstTrace: Vec[Vec[Float]] = traceMap.headOption.map(_._2).getOrElse(Vector.empty)

    def print(): Unit = println(mkString)

    /** Creates a string representation of the traces, formatted
      * as a table with alphabetically ordered labels.
      */
    def mkString: String = {
      val labels    = traceMap.keysIterator.toSeq.sorted
      val labelLen  = if (labels.isEmpty) 0 else labels                 .map(_.length).max
      val maxChans  = if (labels.isEmpty) 0 else traceMap.valuesIterator.map(_.length).max
      val chansL    = maxChans.toString.length
      val chansFmt  = s"[%${chansL}d]"
      val sb        = new StringBuilder
      val header    = s"${rate.name}-rate data: $numFrames frames"
      var hi = 0
      while (hi < header.length) {
        sb.append('-')
        hi += 1
      }
      sb.append('\n')
      sb.append(header)
      sb.append('\n')
      hi = 0
      while (hi < header.length) {
        sb.append('-')
        hi += 1
      }
      sb.append('\n')
      labels.foreach { lb =>
        val data        = traceMap(lb)
        val numChannels = data.size
        if (numChannels > 0) {
          data.iterator.zipWithIndex.foreach { case (vec, ch) =>
            sb.append(lb)
            var lbi = lb.length
            while (lbi <= labelLen) {
              sb.append(' ')
              lbi += 1
            }
            if (numChannels == 1) {
              var chi = -1
              while (chi <= chansL) {
                sb.append(' ')
                chi += 1
              }
            } else {
              val s = java.lang.String.format(Locale.US, chansFmt, ch.asInstanceOf[AnyRef])
              sb.append(s)
            }
            sb.append(": ")
            vec.iterator.zipWithIndex.foreach { case (x, _ /* i */) =>
              val s       = java.lang.String.format(Locale.US, "%g", x.asInstanceOf[AnyRef])
              val dec0    = s.indexOf('.')
              val dec     = if (dec0 >= 0) dec0 else s.length
              val numPre  = 7 - dec
              var i = 0
              while (i < numPre) {
                sb.append(' ')
                i += 1
              }
              val s1 = if (s.length + i <= 15) s else s.substring(0, 15 - i)
              sb.append(s1)
              i += s1.length
              // assert(i <= 14, s"'$s'")
              while (i < 15) {
                sb.append(' ')
                i += 1
              }
            }
            sb.append('\n')
          }
        }
      }
      sb.result()
    }
  }
}
final case class TraceSynth(peer: Synth, controlLink: Link, audioLink: Link) {
  def server: Server  = peer.server
  def id    : Int     = peer.id

  def numTraceChannels: Int = controlLink.bus.numChannels + audioLink.bus.numChannels

  def link(rate: Rate): Link =
    if      (rate == control) controlLink
    else if (rate == audio  ) audioLink
    else throw new IllegalArgumentException(s"Trace rate must be control or audio :$rate")

  // ---- constructor ----
  peer.onEnd {
    controlLink.bus.free()
    audioLink  .bus.free()
  }

  /** Generates a data trace for the peer synth over a given
    * duration or number of sample frames. If none of
    * `duration`, `numFrames`, or `numBlocks` are given, the trace is
    * generated over one sample frame (if an audio link exists) or
    * one control block (if only a control link exists).
    *
    * @param duration   duration of the trace in seconds. Alternative to `numFrames.`
    * @param numFrames  duration of the trace in sample frames. Alternative to `duration`.
    * @param numBlocks  duration of the trace in control blocks. Alternative to `duration` and `numFrames`.
    */
  def traceFor(duration: Double = 0.0, numFrames: Int = 0, numBlocks: Int = 0,
               bundle: BundleBuilder = new BundleBuilder): Future[List[Data]] = {
    val res = traceForToBundle(duration = duration, numFrames = numFrames, numBlocks = numBlocks, bundle = bundle)
    server ! bundle.result()
    res
  }

  def traceForToBundle(duration: Double = 0.0, numFrames: Int = 0, numBlocks: Int = 0,
                       doneAction: DoneAction = freeSelf,
                       bundle: BundleBuilder = new BundleBuilder): Future[List[Data]] = {
    val s           = server
    val blockSize   = s.config.blockSize
    val numFrames1  = if (duration > 0) {
      val n = (s.sampleRate * duration).toLong
      if (n > Int.MaxValue) throw new IllegalArgumentException(s"Duration too long: $duration")
      n.toInt
    } else if (numFrames > 0)
      numFrames
    else {
      val n = (numBlocks - 1).toLong * blockSize + 1
      if (n > Int.MaxValue) throw new IllegalArgumentException(s"numBlocks too large: $numBlocks")
      n.toInt
    }

    val numFramesOk = math.max(1, numFrames1)
    val numBlocksOk = (numFramesOk + blockSize - 1) / blockSize

    var futures = List.empty[Future[Data]]

    import s.clientConfig.executionContext

    def mkData(link: Link): Unit = if (link.nonEmpty) {
      val numChannels = link.numChannels
      val rate        = link.rate
      val busCtlName  = Trace.controlName(rate)
      val bufCtlName  = "$trace_buf"
      val doneCtlName = "$trace_done"
      val synthDef    = SynthDef(TraceGraphFunction.mkSynthDefName()) {
        import Ops.stringToControl
        import synth.ugen._
        val busIdx      = busCtlName.ir
        val sig         = In(rate, busIdx, numChannels = numChannels)
        val bufId       = bufCtlName.ir
        val doneId      = doneCtlName.ir(freeSelf.id.toFloat)
        RecordBuf(rate, in = sig, buf = bufId, loop = 0, doneAction = doneId)
      }
      val bufSize   = if (link.rate == control) numBlocksOk else numFramesOk
      val buf       = Buffer(s)
      val syn       = Synth (s)
      val synArgs   = List[ControlSet](busCtlName -> link.bus.index, bufCtlName -> buf.id, doneCtlName -> doneAction.id)
      val newMsg    = syn.newMsg(synthDef.name, args = synArgs, target = peer, addAction = addAfter)
      bundle.addSync(newMsg)
      bundle.addSync(synthDef.freeMsg)
      val recvMsg   = synthDef.recvMsg // (completion = osc.Bundle.now(newMsg, synthDef.freeMsg))
      val allocMsg  = buf.allocMsg(numFrames = bufSize, numChannels = numChannels) // , completion = recvMsg
      bundle.addAsync(allocMsg)
      bundle.addAsync(recvMsg)
      val p         = Promise[Data]()

      futures ::= p.future

      syn.onEnd {
        import Ops._
        val fut  = buf.getData()
        val futI = fut.map { flat =>
          // val xs = flat.grouped(bufSize).toVector.transpose
          // is this faster than intermediate vectors and transpose?
          val xs = Vector.tabulate(numChannels)(ch => Vector.tabulate(bufSize)(i => flat(i * numChannels + ch)))
          val traceMap: Map[String, Vec[Vec[Float]]] = link.traces.iterator.map { t =>
            val sub = xs.slice(t.index, t.index + t.numChannels)
            t.label -> sub
          } .toMap

          Data(bus = link.bus, numFrames = bufSize, traceMap = traceMap)
        }
        p.completeWith(futI)
      }
    }

    mkData(controlLink)
    mkData(audioLink  )

    Future.sequence(futures.reverse)
  }
}