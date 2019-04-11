import com.typesafe.sbt.packager.linux.LinuxPackageMapping

lazy val baseName   = "Wr_t_ng-M_ch_n_"
lazy val baseNameL  = baseName.filter(_ != '_').toLowerCase

lazy val soundName  = s"$baseName-Sound"
lazy val soundNameL = s"$baseNameL-sound"

lazy val radioName  = s"$baseName-Radio"
lazy val radioNameL = s"$baseNameL-radio"

lazy val projectVersion = "0.3.0-SNAPSHOT"

lazy val deps = new {
  val main = new {
    val akka            = "2.5.19" // N.B. should match with FScape's
    val audioFile       = "1.5.2"
    val desktop         = "0.10.2"
    val equal           = "0.1.3"
    val fileUtil        = "1.1.3"
    val fscape          = "2.24.0"
    val kollFlitz       = "0.2.3"
    val model           = "0.3.4"
    val numbers         = "0.2.0"
    val scalaOSC        = "1.2.0"
    val scalaSTM        = "0.9"
    val scopt           = "3.7.1"
    val soundProcesses  = "3.27.1"
    val swingPlus       = "0.4.1"
  }
}

lazy val loggingEnabled = true

lazy val commonSettings = Seq(
  version            := projectVersion,
  organization       := "de.sciss",
  homepage           := Some(url(s"https://github.com/Sciss/$baseName")),
  description        := "A sound installation",
  licenses           := Seq("GPL v3+" -> url("http://www.gnu.org/licenses/gpl-3.0.txt")),
  scalaVersion       := "2.12.8",
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
  .aggregate(common, sound, radio, control)
  .settings(commonSettings)

lazy val common = Project(id = s"$baseNameL-common", base = file("common"))
  .enablePlugins(BuildInfoPlugin)
  .settings(commonSettings)
  .settings(
    name := s"$baseName Common",
    libraryDependencies ++= Seq(
      "de.sciss"          %% "kollflitz"  % deps.main.kollFlitz,
      "de.sciss"          %% "fileutil"   % deps.main.fileUtil,
      "de.sciss"          %% "scalaosc"   % deps.main.scalaOSC,
      "com.github.scopt"  %% "scopt"      % deps.main.scopt,
      "de.sciss"          %% "equal"      % deps.main.equal,
      "de.sciss"          %% "numbers"    % deps.main.numbers,
      "org.scala-stm"     %% "scala-stm"  % deps.main.scalaSTM
    )
  )

lazy val sound = Project(id = soundNameL, base = file("sound"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .dependsOn(common)
  .settings(commonSettings)
  .settings(buildInfoSettings)
  .settings(
    name := soundName,
    buildInfoPackage := "de.sciss.wrtng.sound",
    mainClass in Compile := Some("de.sciss.wrtng.sound.Main"),
    libraryDependencies ++= Seq(
      "de.sciss" %% "audiofile"           % deps.main.audioFile,
      "de.sciss" %% "soundprocesses-core" % deps.main.soundProcesses,
      "de.sciss" %% "fscape-lucre"        % deps.main.fscape
    )
  )
  .settings(soundDebianSettings)

lazy val radio = Project(id = radioNameL, base = file("radio"))
  .enablePlugins(BuildInfoPlugin)
  .enablePlugins(JavaAppPackaging, DebianPlugin)
  .dependsOn(common)
  .settings(commonSettings)
  .settings(buildInfoSettings)
  .settings(
    name := radioName,
    buildInfoPackage := "de.sciss.wrtng.radio",
    mainClass in Compile := Some("de.sciss.wrtng.radio.Main"),
    libraryDependencies ++= Seq(
      "de.sciss"          %% "audiofile"      % deps.main.audioFile,
      "com.typesafe.akka" %% "akka-actor"     % deps.main.akka
    )
  )
  .settings(radioDebianSettings)

lazy val control = Project(id = s"$baseNameL-control", base = file("control"))
  .dependsOn(common)
  .settings(commonSettings)
  .settings(
    name := s"$baseName-Control",
    libraryDependencies ++= Seq(
      "de.sciss" %% "swingplus"      % deps.main.swingPlus,
      "de.sciss" %% "desktop"        % deps.main.desktop,
      "de.sciss" %% "model"          % deps.main.model
//      "de.sciss" %% "soundprocesses" % deps.main.soundProcesses
    )
  )

// ---- debian package ----

lazy val maintainerHH = "Hanns Holger Rutz <contact@sciss.de>"

lazy val soundDebianSettings = useNativeZip ++ Seq[Def.Setting[_]](
  executableScriptName /* in Universal */ := soundNameL,
  scriptClasspath /* in Universal */ := Seq("*"),
  name        in Debian := soundNameL, // soundName,
  packageName in Debian := soundNameL,
  name        in Linux  := soundNameL, // soundName,
  packageName in Linux  := soundNameL,
  mainClass   in Debian := Some("de.sciss.wrtng.sound.Main"),
  maintainer  in Debian := maintainerHH,
  debianPackageDependencies in Debian += "java8-runtime",
  packageSummary in Debian := description.value,
  packageDescription in Debian :=
    s"""Software for a sound installation - $soundName.
      |""".stripMargin
) ++ commonDebianSettings

lazy val radioDebianSettings = useNativeZip ++ Seq[Def.Setting[_]](
  executableScriptName /* in Universal */ := radioNameL,
  scriptClasspath /* in Universal */ := Seq("*"),
  name        in Debian := radioNameL, // radioName,
  packageName in Debian := radioNameL,
  name        in Linux  := radioNameL, // radioName,
  packageName in Linux  := radioNameL,
  mainClass   in Debian := Some("de.sciss.wrtng.radio.Main"),
  maintainer  in Debian := maintainerHH,
  debianPackageDependencies in Debian += "java8-runtime",
  packageSummary in Debian := description.value,
  packageDescription in Debian :=
    s"""Software for a sound installation - $radioName.
      |""".stripMargin
) ++ commonDebianSettings

lazy val commonDebianSettings = Seq(
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
)
