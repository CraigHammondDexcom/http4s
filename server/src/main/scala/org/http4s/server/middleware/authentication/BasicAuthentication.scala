package org.http4s
package server
package middleware
package authentication

import org.http4s.headers.Authorization
import scalaz._
import scalaz.concurrent.Task

/**
 * Provides Basic Authentication from RFC 2617.
 * @param realm The realm used for authentication purposes.
 * @param store A partial function mapping (realm, user) to the
 *              appropriate password.
 */
class BasicAuthentication(realm: String, store: AuthenticationStore) extends Authentication {
  protected def getChallenge(req: Request) = checkAuth(req).map {
    case OK(user, realm) => \/-(addUserRealmAttributes(req, user, realm))
    case _ => -\/(Challenge("Basic", realm, Nil.toMap))
  }

  private def checkAuth(req: Request): Task[AuthReply] = {
    req.headers.get(Authorization) match {
      case Some(Authorization(BasicCredentials(user, client_pass))) =>
        store(realm, user).map {
          case None => UserUnknown
          case Some(server_pass) =>
            if (server_pass == client_pass) OK(user, realm)
            else WrongPassword
        }

      case Some(Authorization(_)) => Task.now(NoCredentials)

      case None => Task.now(NoAuthorizationHeader)
    }
  }
}
