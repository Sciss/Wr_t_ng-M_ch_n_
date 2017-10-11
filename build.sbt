import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName  = "Wr_t_ng-M_ch_n_"
lazy val baseNameL = baseName.filter(_ != '_').toLowerCase

lazy val projectVersion = "0.1.0-SNAPSHOT"

lazy val soundProcessesVersion  = "3.14.1"
lazy val fscapeVersion          = "2.9.1-SNAPSHOT"
lazy val scoptVersion           = "3.7.0"
lazy val fileUtilVersion        = "1.1.3"
//lazy val pi4jVersion            = "1.1"
//lazy val numbersVersion         = "0.1.3"
lazy val kollFlitzVersion       = "0.2.1"
lazy val scalaOSCVersion        = "1.1.5"

lazy val loggingEnabled = true

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

lazy val buildInfoSettings = Seq(
  // ---- build info ----
  buildInfoKeys := Seq(name, organization, version, scalaVersion, description,
    BuildInfoKey.map(homepage) { case (k, opt)           => k -> opt.get },
    BuildInfoKey.map(licenses) { case (_, Seq((lic, _))) => "license" -> lic }
  ),
  buildInfoOptions += BuildInfoOption.BuildTime
)

// ---- sub-projects ----

lazy val root = Project(id = baseNameL, base = file("."))
  .aggregate(common, sound, radio)
  .settings(commonSettings)

lazy val common = Project(id = s"$baseNameL-common", base = file("common"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    name := s"$baseName Common",
    libraryDependencies ++= Seq(
      "de.sciss"          %% "kollflitz"  % kollFlitzVersion,
      "de.sciss"          %% "fileutil"   % fileUtilVersion,
      "de.sciss"          %% "scalaosc"   % scalaOSCVersion,
      "com.github.scopt"  %% "scopt"      % scoptVersion
    )
  )

lazy val sound = Project(id = s"$baseNameL-sound", base = file("sound"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(common)
  .settings(commonSettings)
  .settings(buildInfoSettings)
  .settings(
    name := s"$baseName Sound",
    buildInfoPackage := "de.sciss.wrtng.sound",
    libraryDependencies ++= Seq(
      "de.sciss" %% "soundprocesses-core" % soundProcessesVersion,
      "de.sciss" %% "fscape-lucre"        % fscapeVersion
    )
  )

lazy val radio = Project(id = s"$baseNameL-radio", base = file("radio"))
  .enablePlugins(BuildInfoPlugin)
  .dependsOn(common)
  .settings(commonSettings)
  .settings(buildInfoSettings)
  .settings(
    name := s"$baseName Radio",
    buildInfoPackage := "de.sciss.wrtng.radio"
  )

// ---- debian package ----

enablePlugins(JavaAppPackaging, DebianPlugin)

useNativeZip

executableScriptName /* in Universal */ := baseNameL
// NOTE: doesn't work on Windows, where we have to
// provide manual file `SCALACOLLIDER_config.txt` instead!
// javaOptions in Universal ++= Seq(
//   // -J params will be added as jvm parameters
//   "-J-Xmx1024m"
//   // others will be added as app parameters
//   // "-Dproperty=true",
// )
// Since our class path is very very long,
// we use instead the wild-card, supported
// by Java 6+. In the packaged script this
// results in something like `java -cp "../lib/*" ...`.
// NOTE: `in Universal` does not work. It therefore
// also affects debian package building :-/
// We need this settings for Windows.
scriptClasspath /* in Universal */ := Seq("*")

name        in Debian := baseNameL // baseName
packageName in Debian := baseNameL
name        in Linux  := baseNameL // baseName
packageName in Linux  := baseNameL
mainClass   in Debian := Some("de.sciss.wrtng.Main")
maintainer  in Debian := s"Hanns Holger Rutz <contact@sciss.de>"
debianPackageDependencies in Debian += "java8-runtime"
packageSummary in Debian := description.value
packageDescription in Debian :=
  """Software for a sound installation - Wr_t_ng M_ch_n_.
    |""".stripMargin
// include all files in src/debian in the installed base directory
linuxPackageMappings in Debian ++= {
  val n     = (name            in Debian).value.toLowerCase
  val dir   = (sourceDirectory in Debian).value / "debian"
  val f1    = (dir * "*").filter(_.isFile).get  // direct child files inside `debian` folder
  val f2    = ((dir / "doc") * "*").get
  //
  def readOnly(in: LinuxPackageMapping) =
  in.withUser ("root")
    .withGroup("root")
    .withPerms("0644")  // http://help.unc.edu/help/how-to-use-unix-and-linux-file-permissions/
  //
  val aux   = f1.map { fIn => packageMapping(fIn -> s"/usr/share/$n/${fIn.name}") }
  val doc   = f2.map { fIn => packageMapping(fIn -> s"/usr/share/doc/$n/${fIn.name}") }
  (aux ++ doc).map(readOnly)
}

