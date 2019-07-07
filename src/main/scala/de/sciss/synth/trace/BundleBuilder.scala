/*
 *  BundleBuilder.scala
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

import de.sciss.osc
import de.sciss.synth.message.HasCompletion

import scala.collection.immutable.{IndexedSeq => Vec}

final class BundleBuilder {
  def addSyncToStart(p: osc.Packet): Unit = _syncStart :+= p
  def addSync       (p: osc.Packet): Unit = _syncMid   :+= p
  def addSyncToEnd  (p: osc.Packet): Unit = _syncEnd   :+= p

  def addAsync(p: osc.Packet with HasCompletion): Unit = _async = _async :+ p

  private[this] var _syncStart  = Vector.empty: Vec[osc.Packet]
  private[this] var _syncMid    = Vector.empty: Vec[osc.Packet]
  private[this] var _syncEnd    = Vector.empty: Vec[osc.Packet]
  private[this] var _async      = Vector.empty: Vec[osc.Packet with HasCompletion]

  //    def sync : Vec[osc.Packet] = _sync
  //    def async: Vec[osc.Packet with HasCompletion] = _async

  def result(): osc.Packet = {
    val _sync = _syncStart ++ _syncMid ++ _syncEnd
    val syncP = if (_sync.size == 1) _sync.head
    else osc.Bundle.now(_sync: _*)

    if (_async.isEmpty) syncP else {
      val init :+ last = _async
      val syncU = last.completion.fold(syncP) { comp =>
        syncP match {
          case b: osc.Bundle => osc.Bundle.now(comp +: b.packets: _*)
          case _ => osc.Bundle.now(comp, syncP)
        }
      }
      val lastU = last.updateCompletion(Some(syncU))
      if (init.isEmpty) lastU else osc.Bundle.now(init :+ lastU: _*)
    }
  }
}