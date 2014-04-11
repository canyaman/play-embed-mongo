name := "play-embed-mongo"

version := "1.0-SNAPSHOT"

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

libraryDependencies ++= Seq(
  "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.44-SNAPSHOT",
  "com.typesafe.play" %% "play" % "2.2.2"
)     
