import sbt.Keys._
import sbt._

enablePlugins(GitVersioning)
git.useGitDescribe := true
git.baseVersion := "0.1.2"
name := "LunesNode"
mainClass in Compile := Some("io.lunes.LunesNode")

inThisBuild(
  Seq(scalaVersion := "2.12.4", organization := "io.lunes", crossPaths := false)
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-language:higherKinds",
  "-language:implicitConversions",
  "-Ywarn-unused:-implicits",
  "-Xlint"
)

resolvers += Resolver.bintrayRepo("ethereum", "maven")

val network = SettingKey[Network]("network")
network := { Network(sys.props.get("network")) }
normalizedName := network.value.name

fork in run := true

lazy val node = project
  .in(file("."))
  .settings(
    libraryDependencies ++=
      Dependencies.network ++
        Dependencies.db ++
        Dependencies.http ++
        Dependencies.akka ++
        Dependencies.serialization ++
        Dependencies.testKit.map(_ % "test") ++
        Dependencies.logging ++
        Dependencies.matcher ++
        Dependencies.metrics ++
        Dependencies.fp ++
        Dependencies.ficus ++
        Dependencies.scorex ++
        Dependencies.commons_net ++
        Dependencies.monix.value
  )

//assembly settings
assemblyJarName in assembly := s"LunesNode-all-${version.value}.jar"
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "io.netty.versions.properties") =>
    MergeStrategy.concat
  case other => (assemblyMergeStrategy in assembly).value(other)
}
test in assembly := {}

javaOptions in Universal ++= Seq(
  // -J prefix is required by the bash script
  "-J-server",
  // JVM memory tuning for 2g ram
  "-J-Xms128m",
  "-J-Xmx2g",
  "-J-XX:+ExitOnOutOfMemoryError",
  // Java 9 support
  "-J-XX:+IgnoreUnrecognizedVMOptions",
  "-J--add-modules=java.xml.bind",
  // from https://groups.google.com/d/msg/akka-user/9s4Yl7aEz3E/zfxmdc0cGQAJ
  "-J-XX:+UseG1GC",
  "-J-XX:+UseNUMA",
  "-J-XX:+AlwaysPreTouch",
  // probably can't use these with jstack and others tools
  "-J-XX:+PerfDisableSharedMem",
  "-J-XX:+ParallelRefProcEnabled",
  "-J-XX:+UseStringDeduplication"
)
