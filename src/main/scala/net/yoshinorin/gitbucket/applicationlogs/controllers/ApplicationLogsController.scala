package net.yoshinorin.gitbucket.applicationlogs.controllers

import java.io.{File, FileInputStream}
import java.nio.charset.Charset
import scala.util.{Failure, Success, Try}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.compress.utils.IOUtils
import org.slf4j.LoggerFactory
import org.scalatra.FlashMapSupport
import gitbucket.core.controller.ControllerBase
import gitbucket.core.util.AdminAuthenticator
import net.yoshinorin.gitbucket.applicationlogs.models.LogBack
import net.yoshinorin.gitbucket.applicationlogs.services.ApplicationLogService
import net.yoshinorin.gitbucket.applicationlogs.utils.{Error, SortType}
import net.yoshinorin.gitbucket.applicationlogs.utils.Converter.stringConverter

class ApplicationLogsController extends ControllerBase with AdminAuthenticator with ApplicationLogService with FlashMapSupport {

  private val logger = LoggerFactory.getLogger(getClass)

  get("/admin/application-logs")(adminOnly {
    redirect(s"/admin/application-logs/configuration")
  })

  get("/admin/application-logs/configuration")(adminOnly {

    net.yoshinorin.gitbucket.applicationlogs.html.configuration(
      LogBack.isEnable,
      LogBack.getConfigurationFilePath,
      LogBack.readConfigurationFile,
      flash.iterator.map(f => f._1 -> f._2.toString).toMap
    )
  })

  post("/admin/application-logs/configuration/reload")(adminOnly {

    if (LogBack.isEnable) {
      LogBack.reload() match {
        case Success(s) => {
          logger.info(s)
          flash.update("flashMessageSuccess", s)
        }
        case Failure(f) => {
          logger.error(f.getMessage, f)
          flash.update("flashMessageError", "Reload failed.")
        }
      }
      redirect(s"/admin/application-logs/configuration")
    } else {
      NotFound()
    }

  })

  get("/admin/application-logs/list")(adminOnly {

    if (LogBack.isEnable) {
      net.yoshinorin.gitbucket.applicationlogs.html.list(
        LogBack.isEnable,
        LogBack.getLogFiles
      )
    } else {
      NotFound()
    }

  })

  get("/admin/application-logs/:id/view")(adminOnly {

    val logId = params("id").toInt

    LogBack.findById(logId) match {
      case Some(logFile) => {
        var n = defaultDisplayLines
        val lineNum = request.getParameter("lines")
        if (Try(lineNum.toInt).toOption.isDefined) {
          n = lineNum.toInt
        }

        val sortBy = params.getOrElse("sortBy", "asc").toString.toSortType
        val logs = readLog(logFile, n) match {
          case Success(s) =>
            s match {
              case Some(s) if sortBy == SortType.ASC => Right(s)
              case Some(s) if sortBy == SortType.DESC => Right(s.reverse)
              case None => Left(Error.FILE_NOT_FOUND)
            }
          case Failure(f) => {
            logger.error(f.toString)
            Left(Error.FAILURE)
          }
        }

        net.yoshinorin.gitbucket.applicationlogs.html.logviewer(
          LogBack.isEnable,
          logFile,
          defaultDisplayLines,
          displayLimitLines,
          logs,
          n,
          sortBy
        )
      }
      case None => NotFound()
    }

  })

  get("/admin/application-logs/:id/download")(adminOnly {

    val logId = params("id").toInt

    LogBack.findById(logId) match {
      case Some(v) => {
        val file = new File(v.path)
        if (file.exists()) {
          response.setHeader(
            "Content-Disposition",
            s"attachment; filename=${file.getName}.zip"
          )
          contentType = "application/zip"
          response.setBufferSize(1024 * 1024)

          val zipArchiveOutStream = new ZipArchiveOutputStream(response.getOutputStream)
          try {
            zipArchiveOutStream.setEncoding(Charset.defaultCharset().toString) //TODO: Set charset from logback configuration file.
            val zipArchive = new ZipArchiveEntry(file.getName)
            zipArchiveOutStream.putArchiveEntry(zipArchive)
            IOUtils.copy(new FileInputStream(file), zipArchiveOutStream)
            zipArchiveOutStream.closeArchiveEntry()
          } finally {
            zipArchiveOutStream.close()
          }
        } else {
          NotFound()
        }
      }
      case _ => NotFound()
    }

  })

}
