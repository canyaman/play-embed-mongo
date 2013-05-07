package info.schleichardt.play.embed.mongo

import play.api.{Logger, Plugin, Application}
import java.util.logging.{Logger => JLogger}
import de.flapdoodle.embed.mongo.{MongodStarter, MongodProcess, MongodExecutable}
import de.flapdoodle.embed.process.distribution.GenericVersion
import de.flapdoodle.embed.mongo.config.MongodConfig
import de.flapdoodle.embed.process.runtime.Network

/** provides a MongoDB instance for development and testing
  * Hast to be loaded before any other MongoDB using plugin.
  */
class EmbedMongoPlugin(app: Application) extends Plugin {
  private var mongoExe: MongodExecutable = _
  private var process: MongodProcess = _

  override def enabled = app.configuration.getBoolean("embed.mongo.enabled").getOrElse(false)

  override def onStart() {
    val runtime = MongodStarter.getDefaultInstance
    val keyMongoDbVersion = "embed.mongo.dbversion"
    val versionNumber = app.configuration.getString(keyMongoDbVersion).getOrElse(throw new RuntimeException(s"$keyMongoDbVersion is missing in your configuration"))
    val version = new GenericVersion(versionNumber)
    val keyPort = "embed.mongo.port"
    val port = app.configuration.getInt(keyPort).getOrElse(throw new RuntimeException(s"$keyPort is missing in your configuration"))
    mongoExe = runtime.prepare(new MongodConfig(version, port, Network.localhostIsIPv6()))
    Logger.info(s"Starting MongoDB on port $port. This might take a while the first time due to the download of MongoDB.")
    process = mongoExe.start()
  }

  override def onStop() {
    Logger.info(s"Stopping MongoDB.")
    try {
      if (mongoExe != null)
        mongoExe.stop()
    } finally {
      if (process != null)
        process.stop()
    }
  }
}

object EmbedMongoPlugin {
  def freePort() = Network.getFreeServerPort
}