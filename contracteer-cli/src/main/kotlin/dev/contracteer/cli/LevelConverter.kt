package dev.contracteer.cli

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Level.INFO
import ch.qos.logback.classic.Level.toLevel
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.joran.spi.ConsoleTarget
import org.slf4j.LoggerFactory
import picocli.CommandLine.ITypeConverter

class LevelConverter: ITypeConverter<Level> {
  override fun convert(value: String): Level = toLevel(value, INFO)

  companion object {
    fun configureLogging(level: Level, target: ConsoleTarget) {
      val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
      rootLogger.level = level
      // Absent when the user supplies their own logback configuration, which then decides where logs go.
      (rootLogger.getAppender(CONSOLE_APPENDER) as? ConsoleAppender<ILoggingEvent>)?.writeTo(target)
    }

    private fun ConsoleAppender<ILoggingEvent>.writeTo(consoleTarget: ConsoleTarget) {
      stop()
      target = consoleTarget.getName()
      start()
    }

    /** The name of the console appender in `logback.xml`. */
    private const val CONSOLE_APPENDER = "CONSOLE"

    fun enableHttpTrafficLogging() {
      val httpLogger = LoggerFactory.getLogger("dev.contracteer.http") as Logger
      httpLogger.level = Level.DEBUG
    }
  }
}
