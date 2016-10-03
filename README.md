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
