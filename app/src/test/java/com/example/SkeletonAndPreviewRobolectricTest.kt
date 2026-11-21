package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.ApkShareHelper
import com.example.model.AutoFetchConfig
import com.example.model.CsvBatchRecord
import com.example.model.PeriodicSyncState
import com.example.model.TeamAccount
import com.example.ui.LeadXApp
import com.example.ui.components.ApkDownloadQrCard
import com.example.ui.components.LeadResultsSkeletonSection
import com.example.ui.components.WorkspaceSkeletonLoadingScreen
import com.example.ui.screens.AuthLoginScreen
import com.example.ui.screens.AutoFetchSchedulerSection
import com.example.ui.screens.ClientCsvHubTabContent
import com.example.ui.screens.DashboardHomeScreen
import com.example.ui.screens.LeadFetchTabsScreen
import com.example.ui.screens.OwnerPointTransferScreen
import com.example.ui.screens.TeamAndRecordsScreen
import com.example.ui.theme.LeadXTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp-xxhdpi")
class SkeletonAndPreviewRobolectricTest {

  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun workspaceSkeletonLoadingScreen_rendersCleanlyInRuntimeAndPreviewModes() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      LeadXTheme {
        WorkspaceSkeletonLoadingScreen(statusLabel = "Hydrating LeadX Workspace...")
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("workspace_skeleton_loading_screen").assertIsDisplayed()
  }

  @Test
  fun leadFetchTabsScreen_whenBusyAndEmpty_showsLeadResultsSkeletonSection() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      LeadXTheme {
        LeadFetchTabsScreen(
          account = TeamAccount(loginId = "OWN1001", fullName = "Aarav Mehta", balance = 25_000L),
          selectedSubTab = 0,
          candidates = emptyList(),
          scopedLeads = emptyList(),
          lastVehicleResult = null,
          autoFetchConfig = AutoFetchConfig(),
          autoFetchLogs = emptyList(),
          flashBanner = null,
          isBusy = true,
          apiConfigVersion = 0,
          onSelectSubTab = {},
          onSearchLinkedInB2B = {},
          onExtractLinkedInUrl = {},
          onExtractWebViewDomJson = {},
          onToggleCandidate = {},
          onSelectAllCandidates = {},
          onClearCandidates = {},
          onSaveSelectedLinkedIn = {},
          onFetchVehicleByPlate = {},
          onRequestConsent = {},
          onSaveAutoFetchConfig = {},
          onRunAutoFetchNow = {},
          onShowMessage = { _, _ -> },
          onOpenApiKeys = {},
          onDismissFlash = {},
          onBackDashboard = {},
        )
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("lead_results_skeleton_section").assertExists()
  }

  @Test
  fun leadResultsSkeletonSection_displaysStandalone() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      LeadXTheme {
        LeadResultsSkeletonSection()
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("lead_results_skeleton_section").assertIsDisplayed()
  }

  @Test
  fun leadXAppAndAuthLogin_renderSafelyUnderLayoutLibInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          LeadXApp()
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("login_submit_button").assertExists()
  }

  @Test
  fun dashboardHomeScreen_rendersSafelyUnderLayoutLibInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          DashboardHomeScreen(
            entryLabel = "Owner Dashboard · Preview",
            account = TeamAccount(loginId = "OWN1001", fullName = "Aarav Mehta", balance = 25_000L),
            allAccounts = emptyList(),
            allLeads = emptyList(),
            allTransactions = emptyList(),
            autoFetchConfig = AutoFetchConfig(),
            csvBatchesCount = 1,
            flashBanner = null,
            isBusy = false,
            isGoogleConnected = false,
            onDismissFlash = {},
            onSwitchTeamId = {},
            onConnectGoogle = {},
            onSignOutGoogle = {},
            onSyncCloud = {},
            onOpenApiKeys = {},
            onTopUp = {},
            onOpenFetchTab = {},
            onNavigateTab = {},
            onOpenTransferScreen = {},
            onExportOwnerCsv = {},
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("wallet_balance_text").assertIsDisplayed()
  }

  @Test
  fun authLoginScreen_rendersSafelyUnderLocalInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          AuthLoginScreen(
            flashBanner = null,
            isBusy = false,
            googleUserEmail = null,
            onSignIn = { _, _ -> },
            onRegisterOwner = { _, _, _ -> },
            onConnectGoogle = {},
            onSignOutGoogle = {},
            onDismissFlash = {},
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("login_id_input").assertExists()
  }

  @Test
  fun teamAndRecordsScreen_rendersSafelyUnderLocalInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          TeamAndRecordsScreen(
            account = TeamAccount(loginId = "OWN1001", fullName = "Aarav Mehta", balance = 25_000L),
            allAccounts = emptyList(),
            allLeads = emptyList(),
            allTransactions = emptyList(),
            flashBanner = null,
            isBusy = false,
            onSharePoints = { _, _ -> },
            onCreateEmployee = { _, _, _, _ -> },
            onCreateOwner = { _, _, _, _ -> },
            onDismissFlash = {},
            onBackDashboard = {},
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("team_back_button").assertIsDisplayed()
  }

  @Test
  fun csvHubAndQrCard_renderSafelyUnderLocalInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          Column(modifier = Modifier.fillMaxSize()) {
            ApkDownloadQrCard(
              url = ApkShareHelper.DIRECT_APK_DOWNLOAD_URL,
              onCopyUrl = {},
            )
            ClientCsvHubTabContent(
              scopedLeads = emptyList(),
              csvBatches =
                listOf(
                  CsvBatchRecord(
                    id = "batch_preview",
                    clientName = "Acme Corp",
                    fileName = "acme.csv",
                    filePath = "",
                    categoryLabel = "24h Auto-Fetch",
                    leadCount = 5,
                    createdAtMillis = 1_730_000_000_000L,
                  )
                ),
              isBusy = false,
              fieldColors = OutlinedTextFieldDefaults.colors(),
              onSplitCsvs = { _, _, _ -> },
              onCreateSingleClientCsv = { _, _ -> },
              onOpenCsvBatch = {},
              onImportClientCsv = { _, _, _ -> },
              onDeleteCsvBatch = {},
              onLocalFeedback = { _, _ -> },
            )
          }
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("apk_download_qr_code").assertExists()
    composeRule.onNodeWithTag("import_client_csv_button").assertExists()
  }

  @Test
  fun leadFetchAutoFetchSubTab_rendersSafelyUnderLocalInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          LeadFetchTabsScreen(
            account = TeamAccount(loginId = "OWN1001", fullName = "Aarav Mehta", balance = 25_000L),
            selectedSubTab = 2,
            candidates = emptyList(),
            scopedLeads = emptyList(),
            lastVehicleResult = null,
            autoFetchConfig = AutoFetchConfig(enabled = true, roleTitle = "Founder"),
            autoFetchLogs = emptyList(),
            flashBanner = null,
            isBusy = false,
            apiConfigVersion = 0,
            periodicSyncState = PeriodicSyncState(enabled = true),
            onSelectSubTab = {},
            onSearchLinkedInB2B = {},
            onExtractLinkedInUrl = {},
            onExtractWebViewDomJson = {},
            onToggleCandidate = {},
            onSelectAllCandidates = {},
            onClearCandidates = {},
            onSaveSelectedLinkedIn = {},
            onFetchVehicleByPlate = {},
            onRequestConsent = {},
            onSaveAutoFetchConfig = {},
            onRunAutoFetchNow = {},
            onShowMessage = { _, _ -> },
            onOpenApiKeys = {},
            onDismissFlash = {},
            onBackDashboard = {},
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("autofetch_enable_switch").assertExists()
  }

  @Test
  fun ownerPointTransferScreen_rendersSafelyUnderLocalInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          OwnerPointTransferScreen(
            ownerAccount = TeamAccount(loginId = "OWN1001", fullName = "Aarav Mehta", balance = 25_000L),
            teamEmployees = emptyList(),
            allTransactions = emptyList(),
            flashBanner = null,
            isBusy = false,
            onTransferPoints = { _, _ -> },
            onDismissFlash = {},
            onBack = {},
          )
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("owner_transfer_submit_button").assertExists()
  }

  @Test
  fun autoFetchSchedulerSection_rendersSafelyUnderLocalInspectionMode() {
    composeRule.mainClock.autoAdvance = false
    composeRule.setContent {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        LeadXTheme {
          Column(modifier = Modifier.fillMaxSize()) {
            AutoFetchSchedulerSection(
              config = AutoFetchConfig(enabled = true, roleTitle = "VP Sales"),
              runLogs = emptyList(),
              scopedLeads = emptyList(),
              stagedCandidates = emptyList(),
              isBusy = false,
              apiConfigVersion = 0,
              fieldColors = OutlinedTextFieldDefaults.colors(),
              onSaveConfig = {},
              onRunNow = {},
              onShowMessage = { _, _ -> },
              onOpenApiKeys = {},
              onToggleCandidate = {},
              onSelectAllCandidates = {},
              onClearCandidates = {},
              onSaveSelectedLinkedIn = {},
            )
          }
        }
      }
    }
    composeRule.mainClock.advanceTimeBy(120)
    composeRule.onNodeWithTag("workmanager_sync_card").assertExists()
  }
}
