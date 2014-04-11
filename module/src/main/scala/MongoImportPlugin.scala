package play.embed.mongo

import de.flapdoodle.embed.mongo.config._
import java.util.logging.{Logger => JLogger}
import de.flapdoodle.embed.mongo.distribution.{IFeatureAwareVersion, Feature, Versions, Version}
import de.flapdoodle.embed.mongo._
import de.flapdoodle.embed.process.runtime.Network
import play.api.{Logger, Plugin, Application}
import scala.runtime
import de.flapdoodle.embed.process.distribution.GenericVersion
import java.io.{IOException, File}
import de.flapdoodle.embed.mongo.config.processlistener.ProcessListenerBuilder

/**
 * Created by canyaman on 11/04/14.
 */
class MongoImportPlugin(app: Application) extends Plugin {

  override def enabled = app.configuration.getBoolean("embed.mongo.import.enabled").getOrElse(false)

  var processes:List[MongoImportProcess]=List.empty[MongoImportProcess]
  var executables:List[MongoImportExecutable]=List.empty[MongoImportExecutable]

  val keyMongoImport = "embed.mongo.import.enabled"
  val keyMongoImportDump = "embed.mongo.import.dump"
  val keyMongoImportUpsert = "embed.mongo.import.upsert"
  val keyMongoImportPath = "embed.mongo.import.path"
  val keyPort = "embed.mongo.port"
  val keyMongoDbVersion = "embed.mongo.dbversion"
  val keyMongoDbName = "mongodb.db"
  var mongoImportRuntime:MongoImportStarter =null

  override def onStart() {
    val runtimeConfig = new RuntimeConfigBuilder()
      .defaultsWithLogger(Command.MongoImport, JLogger.getLogger(getClass().getName()))
      .build()
    mongoImportRuntime = MongoImportStarter.getInstance(runtimeConfig)

    val mongoDbName = app.configuration.getString(keyMongoDbName).getOrElse(throw new RuntimeException(s"$keyMongoDbName is missing in your configuration"))
    val versionNumber = app.configuration.getString(keyMongoDbVersion).getOrElse(throw new RuntimeException(s"$keyMongoDbVersion is missing in your configuration"))
    val version = Versions.withFeatures(new GenericVersion(versionNumber),Feature.SYNC_DELAY)
    val port = app.configuration.getInt(keyPort).getOrElse(throw new RuntimeException(s"$keyPort is missing in your configuration"))
    val dump= app.configuration.getBoolean(keyMongoImportDump).getOrElse(true)
    val upsert = app.configuration.getBoolean(keyMongoImportUpsert).getOrElse(true)
    val evolutionDirectory:File=app.configuration.getString(keyMongoImportPath).map(new File(_))
      .getOrElse(new File(Thread.currentThread.getContextClassLoader.getResource("evolution").getFile))

    Logger.info("MongoImport import path:"+evolutionDirectory.getAbsolutePath)
    startMongoImport(version,port,mongoDbName,evolutionDirectory.listFiles(),upsert,dump)
  }

  def startMongoImport(version:IFeatureAwareVersion,
                       port: Int,
                       dbName: String,
                       jsonFiles: Array[File],
                       upsert: Boolean,
                       drop: Boolean) = {
    //collection names are derived from file name
    jsonFiles.filter(_.getName.endsWith(".json")).map{ jsonFile =>
      val mongoImportConfig: IMongoImportConfig = new MongoImportConfigBuilder().version(version).net(new Net(port, Network.localhostIsIPv6)).db(dbName).collection(jsonFile.getName.replaceFirst(".json","")).upsert(upsert).dropCollection(drop).jsonArray(true).importFile(jsonFile.getAbsolutePath).build
      var mongoImport: MongoImportProcess=null
      try{
        var mongoImportExecutable :MongoImportExecutable= mongoImportRuntime.prepare(mongoImportConfig)
        executables :+ mongoImportExecutable
        processes :+ mongoImportExecutable.start
        Logger.info("MongoImport file successfuly imported"+jsonFile.getAbsolutePath)
      }catch{
        case e:Exception =>  Logger.info("MongoImport Error file not imported"+jsonFile.getAbsolutePath)
      }
    }
  }

  override def onStop() {
    Logger.info(s"Stopping MongoImport.")
    try {
      executables.map(_.stop())
    } finally {
      processes.map(_.stop())
    }
  }
}
