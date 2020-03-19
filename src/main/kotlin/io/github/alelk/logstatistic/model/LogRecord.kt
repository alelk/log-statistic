package io.github.alelk.logstatistic.model

import java.time.LocalDateTime

enum class LogLevel(private val signature: String) {
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error");

    fun forSignature(signature: String) = values().find { it.signature == signature }
}

/** Запись лога
 *
 * @param timestamp временная метка
 * @param level уровень
 * @param message текст сообщения
 */
data class LogRecord(val timestamp: LocalDateTime, val level: LogLevel, val message: String)