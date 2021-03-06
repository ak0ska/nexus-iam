package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server._
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection
import ch.epfl.bluebrain.nexus.commons.types.HttpRejection._
import ch.epfl.bluebrain.nexus.iam.service.routes.CommonRejection._
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.auto._

/**
  * A rejection encapsulates a specific reason why a route was not able to handle a request.
  * Rejections are gathered up over the course of a Route evaluation and finally
  * converted to CommonRejections case classes if there was no way for the request to be completed.
  */
object RejectionHandling {

  /**
    * Defines the custom handling of rejections. When multiple rejections are generated
    * in the routes evaluation process, the priority order to handle them is defined
    * by the order of appearance in this method.
    */
  final def rejectionHandler: RejectionHandler =
    RejectionHandler
      .newBuilder()
      .handle {
        case AuthenticationFailedRejection(CredentialsRejected, _) =>
          complete(Unauthorized)
        case AuthorizationFailedRejection =>
          complete(Forbidden)
        case ValidationRejection(_, Some(e: IllegalPermissionString)) =>
          complete(BadRequest -> (e: CommonRejection))
        case MalformedQueryParamRejection(_, _, Some(e: CommonRejection)) =>
          complete(BadRequest -> (e: CommonRejection))
        case MalformedRequestContentRejection(_, e: CommonRejection) =>
          complete(BadRequest -> (e: CommonRejection))
      }
      .handleAll[MalformedRequestContentRejection] { rejection =>
        val aggregate = rejection.map(_.message).mkString(", ")
        complete(BadRequest -> (WrongOrInvalidJson(Some(aggregate)): HttpRejection))
      }
      .handleAll[MethodRejection] { methodRejections =>
        val names = methodRejections.map(_.supported.name)
        complete(MethodNotAllowed -> (MethodNotSupported(names): HttpRejection))
      }
      .handleAll[MissingQueryParamRejection] { rejections =>
        val missingParameters = rejections.map(_.parameterName)
        complete(BadRequest -> (MissingParameters(missingParameters): HttpRejection))
      }
      .handle {
        case MissingHeaderRejection(Authorization.name) =>
          complete(Unauthorized)
      }
      .result()

  /**
    * The discriminator is enough to give us a Json representation (the name of the class)
    */
  private implicit val rejectionConfig: Configuration = Configuration.default.withDiscriminator("code")
}
