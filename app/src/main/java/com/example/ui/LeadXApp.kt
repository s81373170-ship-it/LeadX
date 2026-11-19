package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.GoogleAuthHelper
import com.example.data.CsvExporter
import com.example.data.RealLeadExtractor
import com.example.model.AccountRole
import com.example.model.AppTab
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchRunLog
import com.example.model.TeamAccount
import com.example.model.UiState
import com.example.ui.components.HowToInstallModal
import com.example.ui.components.SectionLabel
import com.example.ui.components.ShareAppApkSheet
import com.example.ui.screens.AuthLoginScreen
import com.example.ui.screens.DashboardHomeScreen
import com.example.ui.screens.LeadFetchTabsScreen
import com.example.ui.screens.OwnerPointTransferScreen
import com.example.ui.screens.TeamAndRecordsScreen
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.viewmodel.LeadXViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

private object LeadXRoutes {
  const val TEAM_LOGIN = "team_login"
  const val OWNER_DASHBOARD = "owner_dashboard"
  const val EMPLOYEE_DASHBOARD = "employee_dashboard"
  const val OWNER_POINT_TRANSFER = "owner_point_transfer"
}

@Composable
fun LeadXApp() {
  val context = LocalContext.current
  val auth = remember(context) {
    val hasFirebase =
      runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false) ||
        runCatching { FirebaseApp.initializeApp(context) != null }.getOrDefault(false)
    if (hasFirebase) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
  }
  var currentUser by remember(auth) { mutableStateOf(auth?.currentUser) }

  DisposableEffect(auth) {
    if (auth == null) {
      onDispose {}
    } else {
      val listener =
        FirebaseAuth.AuthStateListener { firebaseAuth ->
          currentUser = firebaseAuth.currentUser
        }
      auth.addAuthStateListener(listener)
      onDispose { auth.removeAuthStateListener(listener) }
    }
  }

  val firebaseUser = currentUser
  val workspaceKey = firebaseUser?.uid.orEmpty()
  key(workspaceKey) {
    val viewModel: LeadXViewModel =
      viewModel(
        key = "leadx_${workspaceKey.ifBlank { "local_room" }}",
        factory = LeadXViewModel.factory(workspaceKey),
      )
    RoleNavigatedWorkspace(
      viewModel = viewModel,
      googleUserEmail = firebaseUser?.email,
    )
  }
}

private data class NavItem(
  val tab: AppTab,
  val labelRes: Int,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val tag: String,
)

private val navItems =
  listOf(
    NavItem(
      AppTab.DASHBOARD,
      R.string.nav_dashboard,
      Icons.Rounded.AccountBalanceWallet,
      Icons.Outlined.AccountBalanceWallet,
      "nav_dashboard",
    ),
    NavItem(
      AppTab.FETCH,
      R.string.nav_fetch,
      Icons.Rounded.Hub,
      Icons.Outlined.Hub,
      "nav_fetch",
    ),
    NavItem(
      AppTab.TEAM,
      R.string.nav_team,
      Icons.Rounded.Group,
      Icons.Outlined.Group,
      "nav_team",
    ),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleNavigatedWorkspace(
  viewModel: LeadXViewModel,
  googleUserEmail: String?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val navController = rememberNavController()

  var showApiKeysSheet by rememberSaveable { mutableStateOf(false) }
  var showShareApkSheet by rememberSaveable { mutableStateOf(false) }
  var showHowToInstallModal by rememberSaveable { mutableStateOf(false) }
  var apiConfigVersion by rememberSaveable { mutableIntStateOf(0) }
  var googleConnecting by rememberSaveable { mutableStateOf(false) }

  val accountsState by viewModel.accountsState.collectAsStateWithLifecycle()
  val leadsState by viewModel.leadsState.collectAsStateWithLifecycle()
  val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
  val firestoreRole by viewModel.firestoreRoleState.collectAsStateWithLifecycle()
  val loggedInId by viewModel.loggedInId.collectAsStateWithLifecycle()
  val activeAccount by viewModel.activeAccount.collectAsStateWithLifecycle()
  val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
  val fetchSubTab by viewModel.fetchSubTab.collectAsStateWithLifecycle()
  val flashBanner by viewModel.flashBanner.collectAsStateWithLifecycle()
  val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
  val capturedCandidates by viewModel.capturedCandidates.collectAsStateWithLifecycle()
  val lastVehicleResult by viewModel.lastVehicleResult.collectAsStateWithLifecycle()
  val csvPreview by viewModel.csvPreview.collectAsStateWithLifecycle()
  val autoFetchConfig by viewModel.autoFetchConfig.collectAsStateWithLifecycle()
  val autoFetchLogs by viewModel.autoFetchLogs.collectAsStateWithLifecycle()

  val allAccounts = (accountsState as? UiState.Success)?.data.orEmpty()
  val allLeads = (leadsState as? UiState.Success)?.data.orEmpty()
  val allTransactions = (transactionsState as? UiState.Success)?.data.orEmpty()

  val connectGoogleCloud: () -> Unit = {
    if (!googleConnecting) {
      googleConnecting = true
      scope.launch {
        val result = GoogleAuthHelper.signInWithGoogle(context)
        googleConnecting = false
        result.onFailure { err ->
          viewModel.showFlash(
            "${err.message ?: "Google Sign-In unavailable."} Workspace remains active in Local Room DB.",
            isError = true,
          )
        }
      }
    }
  }

  val signOutGoogleCloud: () -> Unit = {
    scope.launch {
      GoogleAuthHelper.signOut(context)
      viewModel.showFlash("Disconnected Google Cloud Sync. Switched to Local Room DB.")
    }
  }

  if (showApiKeysSheet) {
    var surepassInput by rememberSaveable { mutableStateOf(RealLeadExtractor.currentSurepassToken()) }
    var proxycurlInput by rememberSaveable { mutableStateOf(RealLeadExtractor.currentProxycurlKey()) }
    val fieldColors =
      OutlinedTextFieldDefaults.colors(
        focusedBorderColor = InkBlack,
        unfocusedBorderColor = HairlineBorder,
        focusedLabelColor = InkBlack,
        unfocusedLabelColor = InkMuted,
        cursorColor = InkBlack,
        focusedContainerColor = PureWhite,
        unfocusedContainerColor = PaperBackground,
      )
    ModalBottomSheet(
      onDismissRequest = { showApiKeysSheet = false },
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = PureWhite,
      contentColor = InkBlack,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Key, contentDescription = null, tint = InkBlack, modifier = Modifier.size(20.dp))
          Spacer(Modifier.width(8.dp))
          SectionLabel("Live Provider API Keys", color = InkBlack)
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Keys are saved privately on this device. Leave empty to use built-in Secrets.",
          style = MaterialTheme.typography.bodyMedium,
          color = InkGraphite,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
          value = surepassInput,
          onValueChange = { surepassInput = it },
          label = { Text("Surepass Vahan RC Token (-15 pts live RC)") },
          colors = fieldColors,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("surepass_token_input"),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = proxycurlInput,
          onValueChange = { proxycurlInput = it },
          label = { Text("Proxycurl API Key (optional LinkedIn URL)") },
          colors = fieldColors,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("proxycurl_key_input"),
        )
        Spacer(Modifier.height(14.dp))
        Button(
          onClick = {
            RealLeadExtractor.saveRuntimeKeys(context, surepassInput, proxycurlInput)
            apiConfigVersion++
            showApiKeysSheet = false
            viewModel.showFlash("API keys updated on device.")
          },
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("save_api_keys_button"),
        ) {
          Text("Save API Keys", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
      }
    }
  }

  if (showShareApkSheet) {
    ShareAppApkSheet(
      onDismiss = { showShareApkSheet = false },
      onFeedback = viewModel::showFlash,
    )
  }

  if (showHowToInstallModal) {
    HowToInstallModal(
      onDismiss = { showHowToInstallModal = false },
      onFeedback = viewModel::showFlash,
    )
  }

  val activeCsv = csvPreview
  if (activeCsv != null) {
    ModalBottomSheet(
      onDismissRequest = viewModel::dismissCsvPreview,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = PureWhite,
      contentColor = InkBlack,
    ) {
      Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        SectionLabel("LeadX Pro · CSV Export Ready", color = InkBlack)
        Spacer(Modifier.height(4.dp))
        Text(
          text = "${activeCsv.lines().size - 1} lead row(s) formatted for Excel/Sheets.",
          style = MaterialTheme.typography.bodyMedium,
          color = InkGraphite,
        )
        Spacer(Modifier.height(12.dp))
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = SoftAlabaster,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
        ) {
          Text(
            text = activeCsv,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = InkBlack,
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(12.dp),
          )
        }
        Spacer(Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedButton(
            onClick = {
              CsvExporter.copyCsvToClipboard(context, activeCsv)
              viewModel.showFlash("CSV copied to clipboard.")
              viewModel.dismissCsvPreview()
            },
            border = BorderStroke(1.dp, InkBlack),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("copy_csv_button"),
          ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Copy CSV", color = InkBlack, fontWeight = FontWeight.Bold)
          }
          Button(
            onClick = {
              val opened = CsvExporter.shareCsvFile(context, activeCsv)
              if (!opened) {
                CsvExporter.copyCsvToClipboard(context, activeCsv)
                viewModel.showFlash("No external share app on device — CSV copied to clipboard.")
              }
              viewModel.dismissCsvPreview()
            },
            colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.weight(1f).height(48.dp).testTag("share_csv_file_button"),
          ) {
            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Share File", fontWeight = FontWeight.Bold)
          }
        }
        Spacer(Modifier.height(18.dp))
      }
    }
  }

  if (accountsState is UiState.Loading) {
    Box(
      modifier = Modifier.fillMaxSize().background(PaperBackground),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(color = InkBlack)
    }
    return
  }

  if (accountsState is UiState.Error) {
    val errMsg = (accountsState as UiState.Error).message
    Box(
      modifier = Modifier.fillMaxSize().background(PaperBackground).padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Workspace Initialization Error",
          style = MaterialTheme.typography.titleLarge,
          color = InkBlack,
        )
        Spacer(Modifier.height(8.dp))
        Text(
          text = errMsg,
          style = MaterialTheme.typography.bodyMedium,
          color = InkGraphite,
        )
        Spacer(Modifier.height(16.dp))
        Button(
          onClick = viewModel::retryWorkspaceInit,
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        ) {
          Text("Retry")
        }
      }
    }
    return
  }

  // Separate role-based entry points driven by the role persisted in Firestore / active account
  val resolvedRole =
    when {
      activeAccount == null || loggedInId == null -> null
      firestoreRole != null && firestoreRole?.activeLoginId == activeAccount?.loginId ->
        AccountRole.fromWire(firestoreRole?.role)
      else -> activeAccount?.roleEnum()
    }

  val targetRoute =
    when (resolvedRole) {
      AccountRole.OWNER -> LeadXRoutes.OWNER_DASHBOARD
      AccountRole.EMPLOYEE -> LeadXRoutes.EMPLOYEE_DASHBOARD
      null -> LeadXRoutes.TEAM_LOGIN
    }

  LaunchedEffect(targetRoute) {
    val graphId = runCatching { navController.graph.id }.getOrNull() ?: return@LaunchedEffect
    val currentDest = navController.currentBackStackEntry?.destination?.route
    if (currentDest != null && currentDest != targetRoute) {
      runCatching {
        navController.navigate(targetRoute) {
          popUpTo(graphId) { inclusive = true }
          launchSingleTop = true
        }
      }
    }
  }

  NavHost(
    navController = navController,
    startDestination = LeadXRoutes.TEAM_LOGIN,
  ) {
    composable(LeadXRoutes.TEAM_LOGIN) {
      Scaffold(
        containerColor = PaperBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
      ) { innerPadding ->
        AuthLoginScreen(
          flashBanner = flashBanner,
          isBusy = isBusy || googleConnecting,
          googleUserEmail = googleUserEmail,
          onSignIn = viewModel::signIn,
          onRegisterOwner = viewModel::registerOwnerAndSignIn,
          onConnectGoogle = connectGoogleCloud,
          onSignOutGoogle = signOutGoogleCloud,
          onOpenShareApk = { showShareApkSheet = true },
          onOpenHowToInstall = { showHowToInstallModal = true },
          onDismissFlash = viewModel::clearFlash,
          modifier = Modifier.padding(innerPadding),
        )
      }
    }

    composable(LeadXRoutes.OWNER_DASHBOARD) {
      val ownerAccount = activeAccount
      if (ownerAccount != null) {
        RoleDashboardShell(
          entryLabel =
            if (googleUserEmail.isNullOrBlank()) "Owner Dashboard · Local Room DB"
            else "Owner Dashboard · Firestore Sync",
          account = ownerAccount,
          allAccounts = allAccounts,
          allLeads = allLeads,
          allTransactions = allTransactions,
          currentTab = currentTab,
          fetchSubTab = fetchSubTab,
          flashBanner = flashBanner,
          isBusy = isBusy || googleConnecting,
          isGoogleConnected = !googleUserEmail.isNullOrBlank(),
          apiConfigVersion = apiConfigVersion,
          capturedCandidates = capturedCandidates,
          lastVehicleResult = lastVehicleResult,
          autoFetchConfig = autoFetchConfig,
          autoFetchLogs = autoFetchLogs,
          viewModel = viewModel,
          onOpenTransferScreen = { navController.navigate(LeadXRoutes.OWNER_POINT_TRANSFER) },
          onExportOwnerCsv = {
            viewModel.exportOwnerLeadsFromFirestoreToCsv { csv ->
              CsvExporter.copyCsvToClipboard(context, csv)
              viewModel.openCsvPreview(csv)
            }
          },
          onConnectGoogle = connectGoogleCloud,
          onSignOutGoogle = signOutGoogleCloud,
          onOpenApiKeys = { showApiKeysSheet = true },
          onOpenShareApk = { showShareApkSheet = true },
          onOpenHowToInstall = { showHowToInstallModal = true },
        )
      } else {
        Box(Modifier.fillMaxSize().background(PaperBackground), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = InkBlack)
        }
      }
    }

    composable(LeadXRoutes.OWNER_POINT_TRANSFER) {
      val ownerAccount = activeAccount
      if (ownerAccount != null) {
        val employees =
          allAccounts.filter { it.ownerAccountId == ownerAccount.loginId && !it.isOwnerRole() }
        Scaffold(
          containerColor = PaperBackground,
          contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
          OwnerPointTransferScreen(
            ownerAccount = ownerAccount,
            teamEmployees = employees,
            allTransactions = allTransactions,
            flashBanner = flashBanner,
            isBusy = isBusy,
            onTransferPoints = viewModel::sharePoints,
            onDismissFlash = viewModel::clearFlash,
            onBack = { navController.popBackStack() },
            modifier = Modifier.padding(innerPadding),
          )
        }
      } else {
        Box(Modifier.fillMaxSize().background(PaperBackground), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = InkBlack)
        }
      }
    }

    composable(LeadXRoutes.EMPLOYEE_DASHBOARD) {
      val empAccount = activeAccount
      if (empAccount != null) {
        RoleDashboardShell(
          entryLabel =
            if (googleUserEmail.isNullOrBlank()) "Employee Dashboard · Local Room DB"
            else "Employee Dashboard · Firestore Sync",
          account = empAccount,
          allAccounts = allAccounts,
          allLeads = allLeads,
          allTransactions = allTransactions,
          currentTab = currentTab,
          fetchSubTab = fetchSubTab,
          flashBanner = flashBanner,
          isBusy = isBusy || googleConnecting,
          isGoogleConnected = !googleUserEmail.isNullOrBlank(),
          apiConfigVersion = apiConfigVersion,
          capturedCandidates = capturedCandidates,
          lastVehicleResult = lastVehicleResult,
          autoFetchConfig = autoFetchConfig,
          autoFetchLogs = autoFetchLogs,
          viewModel = viewModel,
          onOpenTransferScreen = {},
          onExportOwnerCsv = {},
          onConnectGoogle = connectGoogleCloud,
          onSignOutGoogle = signOutGoogleCloud,
          onOpenApiKeys = { showApiKeysSheet = true },
          onOpenShareApk = { showShareApkSheet = true },
          onOpenHowToInstall = { showHowToInstallModal = true },
        )
      } else {
        Box(Modifier.fillMaxSize().background(PaperBackground), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = InkBlack)
        }
      }
    }
  }
}

@Composable
private fun RoleDashboardShell(
  entryLabel: String,
  account: TeamAccount,
  allAccounts: List<TeamAccount>,
  allLeads: List<com.example.model.LeadRecord>,
  allTransactions: List<com.example.model.PointTransaction>,
  currentTab: AppTab,
  fetchSubTab: Int,
  flashBanner: com.example.model.FlashBanner?,
  isBusy: Boolean,
  isGoogleConnected: Boolean,
  apiConfigVersion: Int,
  capturedCandidates: List<com.example.model.LinkedInCandidate>,
  lastVehicleResult: com.example.model.VehicleLookupResult?,
  autoFetchConfig: AutoFetchConfig,
  autoFetchLogs: List<AutoFetchRunLog>,
  viewModel: LeadXViewModel,
  onOpenTransferScreen: () -> Unit,
  onExportOwnerCsv: () -> Unit,
  onConnectGoogle: () -> Unit,
  onSignOutGoogle: () -> Unit,
  onOpenApiKeys: () -> Unit,
  onOpenShareApk: () -> Unit,
  onOpenHowToInstall: () -> Unit,
) {
  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val useRail = maxWidth >= 700.dp

    Scaffold(
      containerColor = PaperBackground,
      contentWindowInsets = WindowInsets.safeDrawing,
      bottomBar = {
        if (!useRail) {
          Column {
            HorizontalDivider(thickness = 1.dp, color = HairlineBorder)
            NavigationBar(containerColor = PureWhite, tonalElevation = 0.dp) {
              navItems.forEach { item ->
                val selected = currentTab == item.tab
                NavigationBarItem(
                  selected = selected,
                  onClick = { viewModel.selectTab(item.tab) },
                  icon = {
                    Icon(
                      imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                      contentDescription = stringResource(item.labelRes),
                    )
                  },
                  label = {
                    Text(
                      stringResource(item.labelRes),
                      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                  },
                  colors =
                    NavigationBarItemDefaults.colors(
                      selectedIconColor = InkBlack,
                      selectedTextColor = InkBlack,
                      indicatorColor = SoftAlabaster,
                      unselectedIconColor = InkMuted,
                      unselectedTextColor = InkMuted,
                    ),
                  modifier = Modifier.testTag(item.tag),
                )
              }
            }
          }
        }
      },
    ) { innerPadding ->
      Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        if (useRail) {
          NavigationRail(containerColor = PureWhite) {
            navItems.forEach { item ->
              val selected = currentTab == item.tab
              NavigationRailItem(
                selected = selected,
                onClick = { viewModel.selectTab(item.tab) },
                icon = {
                  Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = stringResource(item.labelRes),
                  )
                },
                label = { Text(stringResource(item.labelRes)) },
                colors =
                  NavigationRailItemDefaults.colors(
                    selectedIconColor = InkBlack,
                    selectedTextColor = InkBlack,
                    indicatorColor = SoftAlabaster,
                    unselectedIconColor = InkMuted,
                    unselectedTextColor = InkMuted,
                  ),
                modifier = Modifier.testTag(item.tag),
              )
            }
          }
        }

        when (currentTab) {
          AppTab.DASHBOARD ->
            DashboardHomeScreen(
              entryLabel = entryLabel,
              account = account,
              allAccounts = allAccounts,
              allLeads = allLeads,
              allTransactions = allTransactions,
              autoFetchConfig = autoFetchConfig,
              flashBanner = flashBanner,
              isBusy = isBusy,
              isGoogleConnected = isGoogleConnected,
              onDismissFlash = viewModel::clearFlash,
              onSwitchTeamId = viewModel::signOutTeamRole,
              onConnectGoogle = onConnectGoogle,
              onSignOutGoogle = onSignOutGoogle,
              onSyncCloud = viewModel::syncWorkspace,
              onOpenApiKeys = onOpenApiKeys,
              onTopUp = viewModel::topUp,
              onOpenFetchTab = viewModel::openFetchTab,
              onNavigateTab = viewModel::selectTab,
              onOpenTransferScreen = onOpenTransferScreen,
              onExportOwnerCsv = onExportOwnerCsv,
              onOpenShareApk = onOpenShareApk,
              onOpenHowToInstall = onOpenHowToInstall,
            )
          AppTab.FETCH -> {
            val scopedLeads =
              remember(account, allLeads) {
                if (account.isOwnerRole()) {
                  allLeads.filter { it.ownerAccountId == account.loginId }
                } else {
                  allLeads.filter { it.fetchedByLoginId == account.loginId }
                }
              }
            LeadFetchTabsScreen(
              account = account,
              selectedSubTab = fetchSubTab,
              candidates = capturedCandidates,
              scopedLeads = scopedLeads,
              lastVehicleResult = lastVehicleResult,
              autoFetchConfig = autoFetchConfig,
              autoFetchLogs = autoFetchLogs,
              flashBanner = flashBanner,
              isBusy = isBusy,
              apiConfigVersion = apiConfigVersion,
              onSelectSubTab = viewModel::selectFetchSubTab,
              onSearchLinkedInB2B = viewModel::searchLinkedInB2BLeads,
              onExtractLinkedInUrl = viewModel::extractLinkedInUrl,
              onExtractWebViewDomJson = viewModel::extractWebViewDomLeadsJson,
              onToggleCandidate = viewModel::toggleCandidateSelection,
              onSelectAllCandidates = viewModel::selectAllCandidates,
              onClearCandidates = viewModel::clearCapturedCandidates,
              onSaveSelectedLinkedIn = viewModel::saveSelectedLinkedInLeads,
              onFetchVehicleByPlate = viewModel::fetchVehicleByPlate,
              onRequestConsent = viewModel::requestVehicleOwnerConsent,
              onSaveAutoFetchConfig = viewModel::saveAutoFetchConfig,
              onRunAutoFetchNow = viewModel::runAutoFetchNow,
              onShowMessage = { msg, isErr -> viewModel.showFlash(msg, isErr) },
              onOpenApiKeys = onOpenApiKeys,
              onDismissFlash = viewModel::clearFlash,
              onBackDashboard = { viewModel.selectTab(AppTab.DASHBOARD) },
            )
          }
          AppTab.TEAM ->
            TeamAndRecordsScreen(
              account = account,
              allAccounts = allAccounts,
              allLeads = allLeads,
              allTransactions = allTransactions,
              flashBanner = flashBanner,
              isBusy = isBusy,
              onSharePoints = viewModel::sharePoints,
              onCreateEmployee = viewModel::createEmployeeAccount,
              onCreateOwner = viewModel::createOwnerAccount,
              onPreviewCsv = viewModel::openCsvPreview,
              onOpenShareApk = onOpenShareApk,
              onDismissFlash = viewModel::clearFlash,
              onBackDashboard = { viewModel.selectTab(AppTab.DASHBOARD) },
            )
        }
      }
    }
  }
}
