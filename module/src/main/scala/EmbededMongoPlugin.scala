package play.embed.mongo

import play.api.{Logger, Plugin, Application}
import java.util.logging.{Logger => JLogger}
import de.flapdoodle.embed.mongo._
import de.flapdoodle.embed.mongo.config._
import de.flapdoodle.embed.process.runtime.Network
import java.io.{File, IOException}
import de.flapdoodle.embed.mongo.distribution.{Feature, Versions, Version}
import de.flapdoodle.embed.process.distribution.GenericVersion
import de.flapdoodle.embed.mongo.config.processlistener.ProcessListenerBuilder
import scala.runtime
import de.flapdoodle.embed.mongo.runtime.MongoImport


/**
 * Provides a MongoDB instance for development and testing.
 * Hast to be loaded before any other plugin that connects with MongoDB.
 */
class EmbedMongoPlugin(app: Application) extends Plugin {
  private var mongoExe: MongodExecutable = _
  private var process: MongodProcess = _

  override def enabled = app.configuration.getBoolean("embed.mongo.enabled").getOrElse(false)
  def mongoImportEnabled = app.configuration.getBoolean("embed.mongo.import.enabled").getOrElse(false)

  override def onStart() {
    val runtimeConfig = new RuntimeConfigBuilder()
      .defaultsWithLogger(Command.MongoD, JLogger.getLogger(getClass().getName()))
      .build()
    val runtime = MongodStarter.getInstance(runtimeConfig)
    val keyPort = "embed.mongo.port"
    val keyMongoDbVersion = "embed.mongo.dbversion"
    val keyPersistancePath = "embed.mongo.store"

    val versionNumber = app.configuration.getString(keyMongoDbVersion).getOrElse(throw new RuntimeException(s"$keyMongoDbVersion is missing in your configuration"))
    val version = Versions.withFeatures(new GenericVersion(versionNumber),Feature.SYNC_DELAY)
    val port = app.configuration.getInt(keyPort).getOrElse(throw new RuntimeException(s"$keyPort is missing in your configuration"))
    val configBuilder= new MongodConfigBuilder()
      .version(version)
      .net(new Net(port,Network.localhostIsIPv6()))
    app.configuration.getString(keyPersistancePath).map {
      path =>
        Logger.info(s"MongoDB persistent path $path")
        val destFile = new File(path)
        destFile.mkdirs
        Logger.info("MongoDB persistent absolute path "+destFile.getAbsolutePath)
        val processListenerBuilder= new ProcessListenerBuilder()
        processListenerBuilder.copyDbFilesBeforeStopInto(destFile)
        if (destFile.exists){
          processListenerBuilder.copyFilesIntoDbDirBeforeStarFrom(destFile)
        }
        configBuilder.processListener(processListenerBuilder.build())
        configBuilder.cmdOptions(new MongoCmdOptionsBuilder()
          .defaultSyncDelay()
          .build())
        Logger.info(s"MongoDB configuration "+configBuilder.toString)
    }

    mongoExe = runtime.prepare(configBuilder.build)
    Logger.info(s"Starting MongoDB on port $port. This might take a while the first time due to the download of MongoDB.")
    try {
      process = mongoExe.start()
    } catch {
      case e: IOException => {
        val message = s"""Maybe the port $port is used by another application. If it was a MongoDB, it might be down now."""
        throw new IOException(message, e)
      }
    }
    /*if(mongoImportEnabled){
      new MongoImportPlugin(app).onStart()
    }*/
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

private[mongo] object EmbedMongoPlugin {
  def freePort() = Network.getFreeServerPort
}