lazy val commonSettings = Seq(
  name               := "ScalaCollider-Trace",
  version            := "0.4.0",
  organization       := "de.sciss",
  description        := "A library for debugging ScalaCollider UGen graphs by tracing their values",
  homepage           := Some(url(s"https://git.iem.at/sciss/${name.value}")),
  licenses           := Seq("lgpl" -> url("https://www.gnu.org/licenses/lgpl-2.1.txt")),
  scalaVersion       := "2.12.8",
  crossScalaVersions := Seq("2.13.0", "2.12.8", "2.11.12"),
  scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xsource:2.13", "-Xlint")
)

lazy val deps = new {
  val main = new {
    val scalaCollider      = "1.28.4"
    val scalaColliderUGens = "1.19.4"
  }
}

lazy val root = project.in(file("."))
  .settings(commonSettings)
  .settings(publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      "de.sciss" %% "scalacollider"           % deps.main.scalaCollider,
      "de.sciss" %  "scalacolliderugens-spec" % deps.main.scalaColliderUGens
    ),
    initialCommands in console :=
      """import de.sciss.synth._
        |import Ops._
        |import ugen._
        |import trace.ugen._
        |import trace.BundleBuilder
        |import trace.TraceOps._
        |def s = Server.default
        |""".stripMargin
  )

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishTo := {
    Some(if (isSnapshot.value)
      "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
    else
      "Sonatype Releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"
    )
  },
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }, 
  pomExtra := { val n = name.value
<scm>
  <url>git@git.iem.at:sciss/{n}.git</url>
  <connection>scm:git:git@git.iem.at:sciss/{n}.git</connection>
</scm>
<developers>
  <developer>
    <id>sciss</id>
    <name>Hanns Holger Rutz</name>
    <url>http://www.sciss.de</url>
  </developer>
</developers>
  }
)