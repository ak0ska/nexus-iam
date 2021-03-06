package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.implicits._
import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport.OrderedKeys
import ch.epfl.bluebrain.nexus.commons.iam.auth.{AuthenticatedUser, User}
import ch.epfl.bluebrain.nexus.commons.iam.auth.UserInfo.userInfoEncoder
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity
import ch.epfl.bluebrain.nexus.commons.iam.identity.Identity.GroupRef
import ch.epfl.bluebrain.nexus.commons.iam.io.serialization.JsonLdSerialization.identityEncoder
import ch.epfl.bluebrain.nexus.iam.core.groups.UsedGroups
import ch.epfl.bluebrain.nexus.iam.service.auth.ClaimExtractor.{JsonSyntax, OAuth2BearerTokenSyntax}
import ch.epfl.bluebrain.nexus.iam.service.auth.{ClaimExtractor, DownstreamAuthClient}
import ch.epfl.bluebrain.nexus.iam.service.config.AppConfig.OidcConfig
import ch.epfl.bluebrain.nexus.iam.service.directives.CredentialsDirectives._
import ch.epfl.bluebrain.nexus.iam.service.io.CirceSupport._
import ch.epfl.bluebrain.nexus.iam.service.types.ApiUri
import io.circe.Encoder
import kamon.akka.http.KamonTraceDirectives.traceName

import scala.concurrent.{ExecutionContext, Future}

/**
  * HTTP routes for OAuth2 specific functionality
  *
  * @param clients OIDC provider clients
  */
class AuthRoutes(clients: List[DownstreamAuthClient[Future]], usedGroups: UsedGroups[Future])(implicit oidc: OidcConfig,
                                                                                              api: ApiUri,
                                                                                              ce: ClaimExtractor,
                                                                                              ec: ExecutionContext,
                                                                                              orderedKeys: OrderedKeys)
    extends DefaultRoutes("oauth2") {

  private implicit val enc: Encoder[Identity] = identityEncoder(api.base)

  def apiRoutes: Route =
    (get & path("authorize") & parameter('redirect.?) & parameter('realm ? oidc.defaultRealm)) { (redirectUri, realm) =>
      traceName("authorize") {
        clients
          .findByRealm(realm)
          .fold(complete(StatusCodes.NotFound))(cl => complete(cl.authorize(redirectUri)))
      }
    } ~
      (get & pathPrefix("token") & pathPrefix(Segment) & parameters(('code, 'state))) { (realm, code, state) =>
        traceName("token") {
          clients
            .findByRealm(realm)
            .fold(complete(StatusCodes.NotFound))(cl => complete(cl.token(code, state)))
        }
      } ~
      (get & path("userinfo") & extractBearerToken) { credentials =>
        traceName("userinfo") {
          complete {
            credentials.extractClaim.flatMap {
              case (client, json) =>
                json.extractUserInfo.recoverWith {
                  case _ => client.userInfo(credentials)
                }
            }
          }
        }
      } ~
      (get & path("user") & extractBearerToken & parameter('filterGroups.as[Boolean] ? false)) {
        import ch.epfl.bluebrain.nexus.commons.http.JsonLdCirceSupport._
        (credentials, filterGroups) =>
          traceName("user") {
            complete {
              credentials.extractClaim.flatMap {
                case (client, json) =>
                  val user = json
                    .extractUser(client.config)
                    .recoverWith {
                      case _ => client.getUser(credentials)
                    }
                  if (filterGroups) {
                    val realmUsedGroups = usedGroups
                      .fetch(client.config.realm)
                    user product realmUsedGroups map { case (u, groups) => filterUserGroups(u, groups) }
                  } else user

              }
            }
          }
      }

  private def filterUserGroups(user: User, usedGroups: Set[GroupRef]): User = {
    user match {
      case au @ AuthenticatedUser(identities) =>
        val filteredIdentities = identities filter {
          case group: GroupRef => usedGroups(group)
          case _               => true
        }
        au.copy(identities = filteredIdentities)
      case other => other
    }
  }
}

object AuthRoutes {
  // $COVERAGE-OFF$
  /**
    * Factory method for oauth2 related routes.
    *
    * @param clients OIDC provider clients
    * @return new instance of AuthRoutes
    */
  def apply(clients: List[DownstreamAuthClient[Future]], usedGroups: UsedGroups[Future])(
      implicit oidc: OidcConfig,
      api: ApiUri,
      ce: ClaimExtractor,
      ec: ExecutionContext,
      orderedKeys: OrderedKeys): AuthRoutes =
    new AuthRoutes(clients, usedGroups: UsedGroups[Future])

  // $COVERAGE-ON$
}
