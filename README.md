# ScalaCollider-Trace

[![Build Status](https://travis-ci.org/iem-projects/ScalaCollider-Trace.svg?branch=master)](https://travis-ci.org/iem-projects/ScalaCollider-Trace)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/at.iem/scalacollider-trace_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/at.iem/scalacollider-trace_2.11)

A library for [ScalaCollider](https://github.com/Sciss/ScalaCollider) to aids in debugging synth graphs by providing abstractions
that monitor the values of UGens graph.
This project is (C)opyright 2016 by the Institute of Electronic Music and Acoustics (IEM), Graz. Written by Hanns Holger Rutz. This software is published under the GNU Lesser General Public License v2.1+.

## linking

The following artifact is available from Maven Central:

    "at.iem" %% "scalacollider-trace" % v

The current stable version `v` is `"0.1.0"`.

## building

This project builds with sbt 0.13 and Scala 2.11, 2.10. To compile `sbt test:compile`.
To print the test output, `sbt test:run`.

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

## documentation

We introduce a new graph element `Trace` that explicitly enables the tracing
of an input signal, associated with a label:

```scala
Trace(in, label = "foo")
```
    
For this to work, we require a specific UGen graph builder that extends
`TracingUGenGraphBuilder`. All `Trace` instances participating are
receiving offsets to a debugging bus (or two debugging buses, one for
control rate signals and one for audio rate signals). The builder's
companion object has a specific build function that not only expands
a UGen graph but also returns a debugging object that encapsulates the
knowledge of the tracing instances. This object can then be used to
issue recordings of the values. A separate GUI display component is
available for then browsing the recorded data.

Example:

```scala
import de.sciss.synth._
import ugen._
import trace.ugen._
import trace.TraceOps._

Server.run() { s =>
  import s.clientConfig.executionContext
  
  val x = tracePlay {
    val n = WhiteNoise.ar
    Trace(n, "noise")
    Pan2.ar(n)
  }
  
  val fut = x.traceFor(numFrames = 8)
  fut.foreach { traces =>
    traces.foreach(_.print())
  }
}

```