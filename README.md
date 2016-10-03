# ScalaCollider-Trace

[![Build Status](https://travis-ci.org/iem-projects/ScalaCollider-Trace.svg?branch=master)](https://travis-ci.org/iem-projects/ScalaCollider-Trace)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/at.iem/scalacollider-trace_2.11/badge.svg)](https://maven-badges.herokuapp.com/maven-central/at.iem/scalacollider-trace_2.11)

A library for [ScalaCollider](https://github.com/Sciss/ScalaCollider) to aids in debugging 
synth graphs by providing abstractions that monitor the values of UGens graph.
This project is (C)opyright 2016 by the Institute of Electronic Music and Acoustics (IEM), Graz. 
Written by Hanns Holger Rutz. This software is published under the GNU Lesser General Public License v2.1+.

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

Here, `tracePlay` returns an enhanced `Synth`, more precisely `TraceSynth`
that encapsulates the running runs as well as the debug traces and buses.
`traceFor` returns a future to the data objects containing the trace.
The type is `List[Data]`, containing one `Data` instance for control-rate
and one for audio-rate traces (if any of these rates are used). The
`Trace` graph element automatically uses the rate of its input argument.

The `print` method on `Data` is a convenience method for creating a
nicely formatted text, a short cut for `println(mkString)`. A possible
output from the above example is:

    -------------------------
    audio-rate data: 8 frames
    -------------------------
    noise    :      -0.00180101     -0.266668      0.0157881      0.00469923     -0.880886     -0.242413      0.812972     -0.205941

### Synchronous trace start

In order to start playing and tracing synchronously, one can use
`traceGraph` to build the enhanced graph-function (`TraceGraphFunction`),
and then use `traceFor` in a similar way:

```scala
val x = traceGraph {
  RandSeed.ir
  val n = WhiteNoise.ar
  Trace(n, "noise")
  Pan2.ar(n)
}

val fut = x.traceFor(numFrames = 8)
fut.foreach { traces =>
  traces.foreach(_.print())
}
```

This variant frees the synth automatically after the trace has been
recorded. The correct output here would be:

    -------------------------
    audio-rate data: 8 frames
    -------------------------
    noise    :      -0.0793402      0.608976     -0.491325      0.387161     -0.563370      0.307421      0.290958      0.928846


### Control rate traces

Here is an example for control rate debugging:

```scala
val y = tracePlay {
  val tr    = "trig".tr
  val count = PulseCount.kr(tr)
  Trace(count, "count")
  val sweep = Sweep.kr(tr, 1)
  Trace(sweep, "sweep")
}

y.set("trig" -> 1)

y.set("trig" -> 1)

y.traceFor(numBlocks = 2).foreach { traces =>
  traces.foreach(_.print())
}
```

With an example output:

    ---------------------------
    control-rate data: 2 frames
    ---------------------------
    count    :       2.00000        2.00000  
    sweep    :       3.25224        3.25370  

### Auxiliary infra-structure with bundle-builder

If you need to feed external synchronized input to the synth,
one way to do so is using the `bundle` argument of type `BundleBuilder`. 
It allows to add asynchronous preparation messages as well as
the add synchronous messages (such as `synth.newMsg` or `node.setMsg`)
to the start or to the end of the overall bundle that is generated
by the tracing commands.

The following more intricate example shows how a timed
control signal can be fed into the synth that is being traced,
using an auxiliary group, an auxiliary control generator synth,
and a bus mapping:


```scala
val x = traceGraph {
  val in  = "in".kr
  val sig = Integrator.kr(in)
  Trace(sig, "integ")
}

val c = Bus.control(s)
val g = Group.head(s)

val genDef = SynthDef("gen") {
  val sig = Phasor.kr(lo = 0, hi = 5)
  Out.kr(c.index, sig)
}

val gen = Synth(s)

val b = new BundleBuilder
b.addAsync(genDef.recvMsg)
b.addSyncToStart(gen.newMsg(genDef.name, target = g, addAction = addToHead))
b.addSyncToEnd(g.mapMsg("in" -> c.index))

val fut = x.traceFor(target = g, addAction = addToTail, numBlocks = 5, bundle = b)
fut.foreach { traces =>
  traces.foreach(_.print())
  c.free()
}
```

The output from this:

    ---------------------------
    control-rate data: 5 frames
    ---------------------------
    integ    :     0.00000      1.00000      3.00000      6.00000     10.00000  
