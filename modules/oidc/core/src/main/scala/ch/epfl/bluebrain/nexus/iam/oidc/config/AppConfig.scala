package ch.epfl.bluebrain.nexus.iam.oidc.config

import akka.http.scaladsl.model.Uri
import ch.epfl.bluebrain.nexus.commons.iam.acls
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path
import ch.epfl.bluebrain.nexus.commons.iam.acls.Path._
import ch.epfl.bluebrain.nexus.iam.oidc.config.AppConfig._

import scala.concurrent.duration.FiniteDuration

/**
  * Case class which aggregates the configuration parameters
  *
  * @param description The service description namespace
  * @param instance    Service instance specific settings
  * @param http        Http binding settings
  * @param runtime     Service runtime settings
  * @param cluster     Cluster specific settings
  * @param oidc        OpenIdConnect specific settings
  */
final case class AppConfig(description: DescriptionConfig,
                           instance: InstanceConfig,
                           http: HttpConfig,
                           runtime: RuntimeConfig,
                           cluster: ClusterConfig,
                           oidc: OidcConfig)

object AppConfig {

  final case class DescriptionConfig(name: String, environment: String) {
    val version: String = BuildInfo.version
    val ActorSystemName = s"$name-${version.replaceAll("\\.", "-")}-$environment"
  }

  final case class InstanceConfig(interface: String)

  final case class HttpConfig(interface: String, port: Int, prefix: String, publicUri: Uri)

  final case class RuntimeConfig(defaultTimeout: FiniteDuration)

  final case class ClusterConfig(passivationTimeout: FiniteDuration, shards: Int, seeds: Option[String]) {
    lazy val seedAddresses: Set[String] =
      seeds.map(_.split(",").toSet).getOrElse(Set.empty[String])
  }

  final case class OidcConfig(discoveryUri: Uri,
                              clientId: String,
                              clientSecret: String,
                              scopes: List[String],
                              tokenUri: Uri,
                              realm: String) {
    lazy val tokenWithRealm: Uri = tokenUri.copy(path = (tokenUri.path: Path) ++ acls.Path(realm))
  }

}
