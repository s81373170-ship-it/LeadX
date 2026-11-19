package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.AutoFetchNotifier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class LeadXUiRobolectricTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun fullStartup_ownerEmployeeAndAllFeatureFlowsWorkWithoutCrashing() {
    // 1. Login screen renders, How to Install & Share App APK sheets open cleanly, & quick fill owner works
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("quick_fill_owner").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("login_how_to_install_button").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("how_to_install_modal").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("apk_download_qr_card").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("apk_download_qr_code").assertIsDisplayed()
    composeRule.onNodeWithTag("qr_copy_link_button").assertIsDisplayed()
    composeRule.onNodeWithTag("modal_download_latest_apk_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("open_unknown_sources_settings_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("copy_install_guide_button").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("how_to_install_modal").fetchSemanticsNodes().isEmpty()
    }

    composeRule.onNodeWithTag("login_share_apk_button").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("copy_apk_link_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("share_apk_file_button").assertIsDisplayed()
    composeRule.onNodeWithTag("save_apk_downloads_button").assertIsDisplayed()
    composeRule.onNodeWithTag("how_to_install_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("open_apk_link_browser_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("copy_apk_link_button").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("copy_apk_link_button").fetchSemanticsNodes().isEmpty()
    }

    composeRule.onNodeWithTag("quick_fill_owner").performScrollTo().assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("login_submit_button").performScrollTo().assertIsDisplayed().performClick()

    // 2. Owner Dashboard renders, Download Latest APK, How to Install QR & Share APK sheets work from Dashboard, & Top-Up Modal updates balance
    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("wallet_balance_text").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("wallet_balance_text").assertIsDisplayed()
    composeRule.onNodeWithTag("dashboard_direct_apk_url_text").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("dashboard_copy_apk_url_button").assertIsDisplayed().performClick()
    composeRule.onNodeWithTag("dashboard_download_latest_apk_button").performScrollTo().assertIsDisplayed()
    composeRule.onNodeWithTag("dashboard_how_to_install_button").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("how_to_install_modal").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("apk_download_qr_code").assertIsDisplayed()
    composeRule.onNodeWithTag("close_how_to_install_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("how_to_install_modal").fetchSemanticsNodes().isEmpty()
    }

    composeRule.onNodeWithTag("dashboard_share_apk_button").performScrollTo().assertIsDisplayed().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("share_apk_link_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("copy_apk_link_button").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("copy_apk_link_button").fetchSemanticsNodes().isEmpty()
    }

    composeRule.onNodeWithTag("open_topup_sheet_button").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("topup_submit_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("topup_preset_1000").performClick()
    composeRule.onNodeWithTag("topup_submit_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("topup_submit_button").fetchSemanticsNodes().isEmpty()
    }

    // 3. API Keys Modal opens & saves cleanly
    composeRule.onNodeWithTag("api_keys_button").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("save_api_keys_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("save_api_keys_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("save_api_keys_button").fetchSemanticsNodes().isEmpty()
    }

    // 4. Owner Point Transfer screen pre-fills EMP001 & transfers points
    composeRule.onNodeWithTag("open_owner_transfer_button").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("owner_transfer_submit_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("owner_transfer_employee_id_input").assertTextContains("EMP001")
    composeRule.onNodeWithTag("owner_transfer_amount_input").performScrollTo().performTextClearance()
    composeRule.onNodeWithTag("owner_transfer_amount_input").performTextInput("500")
    composeRule.onNodeWithTag("owner_transfer_submit_button").performScrollTo().performClick()
    composeRule.onNodeWithTag("transfer_back_button").performClick()

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("wallet_balance_text").fetchSemanticsNodes().isNotEmpty()
    }

    // 5. Fetch tab: B2B Search -> Profile URL -> DOM & JSON (parse + save) -> Number Plate -> Daily Auto
    composeRule.onNodeWithTag("nav_fetch").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("search_b2b_leads_button").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("linkedin_mode_tab_1").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("extract_linkedin_url_button").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("linkedin_mode_tab_2").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("toggle_dom_json_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("toggle_dom_json_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("dom_json_input").fetchSemanticsNodes().isNotEmpty()
    }
    val sampleDomJson =
      """[{"name":"Asha Nair","profileUrl":"https://www.linkedin.com/in/asha-nair","title":"VP Sales","company":"FinGrid","location":"Bengaluru","email":"asha@fingrid.io","phone":"+919800011223"}]"""
    composeRule.onNodeWithTag("dom_json_input").performTextInput(sampleDomJson)
    composeRule.onNodeWithTag("parse_dom_json_button").performScrollTo().performClick()

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("save_linkedin_leads_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("save_linkedin_leads_button").performScrollTo().performClick()

    // Number Plate tab
    composeRule.onNodeWithTag("segment_tab_1").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("fetch_vehicle_lead_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("plate_number_input").performTextInput("MH02AB1234")
    composeRule.onNodeWithTag("fetch_vehicle_lead_button").performScrollTo().performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("rto_only_notice").fetchSemanticsNodes().isNotEmpty()
    }

    // Daily Auto-Fetch tab
    composeRule.onNodeWithTag("segment_tab_2").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("autofetch_enable_switch").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("autofetch_role_input").performScrollTo().performTextInput("CTO")
    composeRule.onNodeWithTag("save_autofetch_config_button").performScrollTo().performClick()

    // 6. Team & Ledger tab: check form retains values on validation error, CSV preview modal, Leads & Ledger
    composeRule.onNodeWithTag("nav_team").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("share_submit_button").fetchSemanticsNodes().isNotEmpty()
    }

    // Invalid employee creation (short password) should NOT clear the ID/Name fields
    composeRule.onNodeWithTag("new_employee_id_input").performScrollTo().performTextInput("EMP009")
    composeRule.onNodeWithTag("new_employee_name_input").performScrollTo().performTextInput("Kiran Rao")
    composeRule.onNodeWithTag("new_employee_password_input").performScrollTo().performTextInput("12")
    composeRule.onNodeWithTag("create_employee_button").performScrollTo().performClick()
    composeRule.onNodeWithTag("new_employee_id_input").assertTextContains("EMP009")

    // Leads sub-tab & CSV preview sheet
    composeRule.onNodeWithTag("segment_tab_1").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("dashboard_search_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("export_csv_button").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("copy_csv_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("copy_csv_button").performClick()

    // Ledger sub-tab
    composeRule.onNodeWithTag("segment_tab_2").performClick()

    // 7. Switch to Employee role and verify Employee Dashboard renders cleanly
    composeRule.onNodeWithTag("nav_dashboard").performClick()
    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("switch_team_id_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("switch_team_id_button").performClick()

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule.onAllNodesWithTag("quick_fill_employee").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("quick_fill_employee").performScrollTo().performClick()
    composeRule.onNodeWithTag("login_submit_button").performScrollTo().performClick()

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithTag("wallet_balance_text").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithTag("wallet_balance_text").assertIsDisplayed()
  }

  @Test
  fun autoFetchNotifier_postsNotificationWithMonochromeDrawableWithoutCrashing() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    AutoFetchNotifier.notifyRunResult(
      context = context,
      title = "Auto-Fetch Complete",
      message = "Fetched 2 leads safely.",
    )
  }
}
