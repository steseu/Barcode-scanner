package com.barcodebridge.app.ui.scan

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.barcodebridge.app.R
import com.barcodebridge.app.domain.model.BarcodeFormat
import com.barcodebridge.app.ui.theme.BarcodeBridgeTheme
import org.junit.Rule
import org.junit.Test

/**
 * UI half of the scan flow's manual-entry fallback (Funktion 1: "Manuelle
 * Eingabe als Fallback"): typing content and confirming must hand the exact
 * content/format back to the caller, which is what wires it into
 * [ScanViewModel.submitManualEntry] in the real screen.
 */
class ManualEntryDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Resolved from resources (not hardcoded English) so the test passes
    // regardless of the test device's system locale.
    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val hintText get() = targetContext.getString(R.string.scan_manual_entry_hint)
    private val doneText get() = targetContext.getString(R.string.done)

    @Test
    fun submittingManualEntryReturnsTypedContentAndDefaultFormat() {
        var submittedContent: String? = null
        var submittedFormat: BarcodeFormat? = null

        composeRule.setContent {
            BarcodeBridgeTheme {
                ManualEntryDialog(
                    onDismiss = {},
                    onSubmit = { content, format ->
                        submittedContent = content
                        submittedFormat = format
                    },
                )
            }
        }

        composeRule.onNodeWithText(hintText).performTextInput("4006381333931")
        composeRule.onNodeWithText(doneText).performClick()

        composeRule.runOnIdle {
            assert(submittedContent == "4006381333931") { "expected submitted content to match typed text" }
            assert(submittedFormat == BarcodeFormat.QR_CODE) { "expected default format QR_CODE" }
        }
    }

    @Test
    fun confirmButtonIsDisabledUntilContentIsEntered() {
        composeRule.setContent {
            BarcodeBridgeTheme {
                ManualEntryDialog(onDismiss = {}, onSubmit = { _, _ -> })
            }
        }

        composeRule.onNodeWithText(doneText).assertIsNotEnabled()
    }
}
