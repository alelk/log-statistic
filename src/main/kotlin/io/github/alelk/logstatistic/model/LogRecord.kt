package io.github.alelk.logstatistic.model

import java.time.LocalDateTime

/** Уровень логировани
 * @param signature обозначение уровня в лог-файле
 */
enum class LogLevel(val signature: String) {
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARNING"),
    ERROR("ERROR");

    companion object {
        fun forSignature(signature: String) = values().find { it.signature == signature }
    }
}

/** Запись лога
 *
 * @param timestamp временная метка
 * @param level уровень
 * @param message текст сообщения
 */
data class LogRecord(val timestamp: LocalDateTime, val level: LogLevel, val message: String)