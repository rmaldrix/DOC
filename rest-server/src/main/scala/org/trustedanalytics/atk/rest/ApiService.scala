/*
// Copyright (c) 2015 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

package org.trustedanalytics.atk.rest

import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import akka.event.Logging
import org.trustedanalytics.atk.rest.v1.ApiV1Service
import org.trustedanalytics.atk.spray.json.AtkDefaultJsonProtocol
import RestServerConfig.{ version, docLink }

/**
 * We don't implement our route structure directly in the service actor because
 * we want to be able to test it independently, without having to spin up an actor
 *
 * @param apiService the service to delegate to
 */
class ApiServiceActor(val apiService: ApiService) extends Actor with HttpService {

  /**
   * the HttpService trait defines only one abstract member, which
   * connects the services environment to the enclosing actor or test
   */
  override def actorRefFactory = context

  /**
   * Delegates to apiService.
   *
   * This actor only runs our route, but you could add other things here, like
   * request stream processing or timeout handling
   */
  def receive = runRoute(apiService.serviceRoute)
}

/**
 * Defines our service behavior independently from the service actor
 */
class ApiService(val commonDirectives: CommonDirectives, val apiV1Service: ApiV1Service) extends Directives {

  def homepage = {
    respondWithMediaType(`text/html`) {
      complete {
        <html>
          <body>
            <h1>Welcome to the Trusted Analytics Toolkit API Server</h1>
            <h2>Server Version: { version }</h2>
            <h2><a href={ docLink }>Server/Client Documentation</a></h2>
          </body>
        </html>
      }
    }
  }

  lazy val description = {
    new ServiceDescription(name = "Trusted Analytics",
      identifier = RestServerConfig.identifier,
      apiVersions = List("v1"))
  }

  lazy val oauthServer = { new OAuthServer(RestServerConfig.uaaUri, RestServerConfig.uaaClientName, RestServerConfig.uaaClientPassword) }

  import spray.json._
  import spray.httpx.SprayJsonSupport._
  import AtkDefaultJsonProtocol._
  implicit val descFormat = jsonFormat3(ServiceDescription)
  implicit val oauthServerFormat = jsonFormat3(OAuthServer)

  /**
   * Main Route entry point to the API Server
   */
  val serviceRoute: Route = logRequest("api service", Logging.InfoLevel) {
    path("") {
      get { homepage }
    } ~
      pathPrefix("v1") {
        apiV1Service.route
      } ~
      path("info") {
        respondWithMediaType(`application/json`) {
          commonDirectives.respondWithVersion {
            complete(description)
          }
        }
      } ~
      path("oauth_server") {
        respondWithMediaType(`application/json`) {
          commonDirectives.respondWithVersion {
            complete(oauthServer)
          }
        }
      }
  }
}

case class ServiceDescription(name: String, identifier: String, apiVersions: List[String])
case class OAuthServer(uri: String, clientName: String, clientPassword: String)
