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

package org.trustedanalytics.atk.engine.command

import org.trustedanalytics.atk.NotFoundException

import scala.util.{ Success, Failure, Try }
import spray.json.JsObject
import org.trustedanalytics.atk.domain.command.{ CommandTemplate, Command }
import org.trustedanalytics.atk.engine.{ ProgressInfo, CommandStorage }
import org.trustedanalytics.atk.repository.SlickMetaStoreComponent
import org.trustedanalytics.atk.event.{ EventContext, EventLogging }

/**
 * Methods for modifying command records stored in Meta-Store
 */
class CommandStorageImpl(val metaStore: SlickMetaStoreComponent#SlickMetaStore) extends CommandStorage with EventLogging {
  val repo = metaStore.commandRepo

  /**
   * Generally it is better to call expectCommand() since commands don't get deleted,
   * so if you have an id it should be valid
   */
  override def lookup(id: Long): Option[Command] =
    metaStore.withSession("se.command.lookup") {
      implicit session =>
        repo.lookup(id)
    }

  override def create(createReq: CommandTemplate): Command =
    metaStore.withSession("se.command.create") {
      implicit session =>

        val created = repo.insert(createReq)
        repo.lookup(created.get.id).getOrElse(throw new Exception("Command not found immediately after creation"))
    }

  override def scan(offset: Int, count: Int): Seq[Command] = metaStore.withSession("se.command.getCommands") {
    implicit session =>
      repo.scan(offset, count).sortBy(c => c.id) //TODO: Can't seem to get db to produce sorted results.
  }

  /**
   * On complete - mark progress as 100% or failed
   */
  override def complete(id: Long, result: Try[JsObject]): Unit = {
    require(id > 0, "invalid ID")
    require(result != null, "result must not be null")
    updateResult(id, result, markComplete = true)
  }

  override def storeResult(id: Long, result: Try[JsObject]): Unit = {
    require(id > 0, "invalid ID")
    require(result != null, "result must not be null")
    updateResult(id, result, markComplete = false)
  }

  private def updateResult(commandId: Long, result: Try[JsObject], markComplete: Boolean): Unit = {
    metaStore.withSession("se.command.updateResult") {
      implicit session =>
        val command = repo.lookup(commandId).getOrElse(throw new IllegalArgumentException(s"Command $commandId not found"))
        val corId = EventContext.getCurrent.getCorrelationId
        if (command.complete) {
          warn(s"Completion attempt for command $commandId, already completed")
        }
        import org.trustedanalytics.atk.domain.throwableToError
        val changed = result match {
          case Failure(ex) =>
            error(s"command completed with error, id: $commandId, name: ${command.name}, args: ${command.compactArgs} ", exception = ex)
            command.copy(complete = markComplete,
              error = Some(throwableToError(ex)),
              correlationId = corId)
          case Success(r) =>
            // update progress to 100 since the command is complete. This step is necessary
            // because the actually progress notification events are sent to SparkProgressListener.
            // The exact timing of the events arrival can not be determined.
            val progress = if (command.progress.nonEmpty) {
              command.progress.map(info => info.copy(progress = 100f))
            }
            else {
              List(ProgressInfo(100f, None))
            }
            command.copy(complete = markComplete,
              progress = progress,
              result = Some(r),
              error = None,
              correlationId = corId)
        }
        repo.update(changed)
    }
  }

  /**
   * update progress information for the command
   * @param id command id
   * @param progressInfo progress
   */
  override def updateProgress(id: Long, progressInfo: List[ProgressInfo]): Unit = {
    metaStore.withSession("se.command.updateProgress") {
      implicit session =>
        repo.updateProgress(id, progressInfo)
    }
  }

}
