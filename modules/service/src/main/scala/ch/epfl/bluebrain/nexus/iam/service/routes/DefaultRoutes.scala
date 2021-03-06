package ch.epfl.bluebrain.nexus.iam.service.routes

import akka.http.scaladsl.server.Directives.{handleExceptions, handleRejections, pathPrefix}
import akka.http.scaladsl.server.Route

/**
  * Abstract class for prefixed HTTP routes implementing a specific functionality.
  * Wraps API calls in custom rejection and exception handling.
  *
  * @param prefix the initial top-level prefix to be consumed
  */
abstract class DefaultRoutes(prefix: String) {

  /**
    * Placeholder method that needs to be implemented by a concrete type.
    */
  protected def apiRoutes: Route

  /**
    * @return ''apiRoutes'' wrapped in exception and rejection handlers
    */
  def routes: Route = handleExceptions(ExceptionHandling.exceptionHandler) {
    handleRejections(RejectionHandling.rejectionHandler) {
      pathPrefix(prefix)(apiRoutes)
    }
  }
}
