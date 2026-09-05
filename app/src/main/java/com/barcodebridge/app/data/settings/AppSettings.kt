package com.barcodebridge.app.data.settings

import kotlinx.serialization.Serializable

enum class AppLanguage { SYSTEM, GERMAN, ENGLISH }

enum class ScanMode { SINGLE, CONTINUOUS }

enum class TransferMethod { NONE, BLUETOOTH_HID, WIFI_TCP, HTTP_WEBHOOK }

/**
 * One entry per JSON file under `assets/keymaps/`. The enum name lower-cased
 * is the asset file's base name (see [assetFileName]), so adding a layout
 * only requires a new JSON file plus one enum constant - never a `when` over
 * key data.
 */
enum class HidKeyboardLayout(val assetFileName: String) {
    DE("de.json"),
    EN_US("en_us.json"),
    EN_UK("en_uk.json"),
    CH("ch.json"),
    AT("at.json"),
    FR_AZERTY("fr_azerty.json"),
}

enum class HidSuffix { NONE, ENTER, TAB, CRLF }

enum class UnmappableCharAction { SKIP, REPLACE_WITH_QUESTION_MARK, ABORT_AND_WARN }

enum class CsvDelimiter(val char: Char) { COMMA(','), SEMICOLON(';'), TAB('\t') }

enum class CsvColumn { CONTENT, FORMAT, DATE, TIME, NOTE, SESSION, INDEX }

enum class LineEnding(val value: String) { LF("\n"), CRLF("\r\n") }

enum class WebhookPayloadFormat { JSON, PLAIN_TEXT }

@Serializable
data class FeedbackSettings(
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val flashEnabled: Boolean = true,
)

@Serializable
data class CsvExportSettings(
    val columns: List<CsvColumn> = listOf(CsvColumn.CONTENT, CsvColumn.FORMAT, CsvColumn.DATE, CsvColumn.TIME),
    val delimiter: CsvDelimiter = CsvDelimiter.SEMICOLON,
    val includeHeader: Boolean = true,
    val utf8Bom: Boolean = false,
)

@Serializable
data class TxtExportSettings(
    val lineTemplate: String = "{content}\t{format}\t{timestamp}",
    val lineEnding: LineEnding = LineEnding.LF,
)

@Serializable
data class HidSettings(
    val layout: HidKeyboardLayout = HidKeyboardLayout.DE,
    val prefix: String = "",
    val suffix: HidSuffix = HidSuffix.ENTER,
    val typingDelayMs: Int = 12,
    val autoReconnect: Boolean = true,
    val unmappableAction: UnmappableCharAction = UnmappableCharAction.ABORT_AND_WARN,
)

@Serializable
data class TcpSettings(
    val host: String = "",
    val port: Int = 9999,
    val token: String = "",
)

@Serializable
data class HttpSettings(
    val url: String = "",
    val payloadFormat: WebhookPayloadFormat = WebhookPayloadFormat.JSON,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val defaultScanMode: ScanMode = ScanMode.SINGLE,
    val duplicateWindowMillis: Long = 2000,
    val feedback: FeedbackSettings = FeedbackSettings(),
    val csv: CsvExportSettings = CsvExportSettings(),
    val txt: TxtExportSettings = TxtExportSettings(),
    val exportFilenameTemplate: String = "barcodebridge_{timestamp}",
    val shareAfterExport: Boolean = false,
    val transferMethod: TransferMethod = TransferMethod.NONE,
    val hid: HidSettings = HidSettings(),
    val tcp: TcpSettings = TcpSettings(),
    val http: HttpSettings = HttpSettings(),
)
