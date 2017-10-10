lazy val baseName  = "Wr_t_ng-M_ch_n_"
lazy val baseNameL = baseName.filter(_ != '_').toLowerCase

lazy val projectVersion = "0.1.0-SNAPSHOT"

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  homepage           := Some(url(s"https://github.com/Sciss/$baseName")),
  description        := "A sound installation",
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalaVersion       := "2.12.3",
  resolvers          += "Oracle Repository" at "http://download.oracle.com/maven",  // required for sleepycat
  scalacOptions     ++= Seq("-deprecation", "-unchecked", "-feature", "-encoding", "utf8", "-Xfuture", "-Xlint:-stars-align,_"),
  scalacOptions     ++= {
    if (loggingEnabled || isSnapshot.value) Nil else Seq("-Xelide-below", "INFO")
  }
)

lazy val soundProcessesVersion  = "3.14.1"
lazy val fscapeVersion          = "2.9.1-SNAPSHOT"

lazy val loggingEnabled = true

// ---- sub-projects ----

lazy val root = Project(id = baseNameL, base = file("."))
  .settings(commonSettings)
  .settings(
    name := baseName,
    libraryDependencies ++= Seq(
      "de.sciss" %% "soundprocesses"  % soundProcessesVersion,
      "de.sciss" %% "fscape"          % fscapeVersion
    )
  )

