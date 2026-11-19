package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AutoFetchEngine
import com.example.data.AutoFetchPreferences
import com.example.data.LeadXRepository
import com.example.data.LeadXValidators
import com.example.data.RealLeadExtractor
import com.example.data.local.LeadXDao
import com.example.data.local.LeadXDatabase
import com.example.model.AppTab
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchInterval
import com.example.model.AutoFetchRunLog
import com.example.model.FirestoreUserRoleProfile
import com.example.model.FlashBanner
import com.example.model.LeadRecord
import com.example.model.LinkedInCandidate
import com.example.model.LinkedInSearchQuery
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.PointTransaction
import com.example.model.TeamAccount
import com.example.model.UiState
import com.example.model.VehicleLookupResult
import com.example.worker.AutoFetchScheduler
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeadXViewModel(
  private val repository: LeadXRepository,
  private val currentUserId: String,
  private val appContext: Context,
  private val dao: LeadXDao,
) : ViewModel() {

  val accountsState: StateFlow<UiState<List<TeamAccount>>> =
    repository
      .observeTeamAccounts()
      .map<List<TeamAccount>, UiState<List<TeamAccount>>> { UiState.Success(it) }
      .catch { error ->
        if (error is CancellationException) throw error
        emit(UiState.Error(error.message ?: "Failed to load accounts"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val leadsState: StateFlow<UiState<List<LeadRecord>>> =
    repository
      .observeLeads()
      .map<List<LeadRecord>, UiState<List<LeadRecord>>> { UiState.Success(it) }
      .catch { error ->
        if (error is CancellationException) throw error
        emit(UiState.Error(error.message ?: "Failed to load leads"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val transactionsState: StateFlow<UiState<List<PointTransaction>>> =
    repository
      .observeTransactions()
      .map<List<PointTransaction>, UiState<List<PointTransaction>>> { UiState.Success(it) }
      .catch { error ->
        if (error is CancellationException) throw error
        emit(UiState.Error(error.message ?: "Failed to load transactions"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = UiState.Loading,
      )

  val firestoreRoleState: StateFlow<FirestoreUserRoleProfile?> =
    repository
      .observeFirestoreUserRole(currentUserId)
      .catch { error ->
        if (error is CancellationException) throw error
        emit(null)
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  private val _loggedInId = MutableStateFlow<String?>(null)
  val loggedInId: StateFlow<String?> = _loggedInId.asStateFlow()

  private var suppressAutoTeamRestore = false

  val activeAccount: StateFlow<TeamAccount?> =
    combine(accountsState, _loggedInId) { state, selectedId ->
        val id = selectedId ?: return@combine null
        val list = (state as? UiState.Success)?.data ?: return@combine null
        list.firstOrNull { it.loginId == id }
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = null,
      )

  private val _currentTab = MutableStateFlow(AppTab.DASHBOARD)
  val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

  private val _fetchSubTab = MutableStateFlow(0)
  val fetchSubTab: StateFlow<Int> = _fetchSubTab.asStateFlow()

  private val _flashBanner = MutableStateFlow<FlashBanner?>(null)
  val flashBanner: StateFlow<FlashBanner?> = _flashBanner.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _capturedCandidates = MutableStateFlow<List<LinkedInCandidate>>(emptyList())
  val capturedCandidates: StateFlow<List<LinkedInCandidate>> = _capturedCandidates.asStateFlow()

  private val _lastVehicleResult = MutableStateFlow<VehicleLookupResult?>(null)
  val lastVehicleResult: StateFlow<VehicleLookupResult?> = _lastVehicleResult.asStateFlow()

  private val _csvPreview = MutableStateFlow<String?>(null)
  val csvPreview: StateFlow<String?> = _csvPreview.asStateFlow()

  val autoFetchConfig: StateFlow<AutoFetchConfig> = AutoFetchPreferences.configFlow
  val autoFetchLogs: StateFlow<List<AutoFetchRunLog>> = AutoFetchPreferences.logsFlow

  init {
    AutoFetchPreferences.init(appContext)
    val stagedFromWorker = AutoFetchPreferences.consumeStagedCandidates(appContext)
    if (stagedFromWorker.isNotEmpty()) {
      _capturedCandidates.value = stagedFromWorker
    }
    val initialCfg = AutoFetchPreferences.getConfig(appContext)
    if (initialCfg.enabled && initialCfg.hasValidTarget()) {
      AutoFetchScheduler.schedulePeriodic(appContext, initialCfg.intervalHours)
    }

    viewModelScope.launch {
      val result = repository.initializeAndSyncWorkspace(currentUserId)
      if (result.isFailure) {
        _flashBanner.value =
          FlashBanner(
            result.exceptionOrNull()?.message ?: "Could not sync workspace",
            isError = true,
          )
      }
    }

    // Auto-route back into role stored in Firestore when returning to an already-signed-in workspace
    viewModelScope.launch {
      combine(accountsState, firestoreRoleState) { accountsUi, roleProfile ->
          accountsUi to roleProfile
        }
        .collect { (accountsUi, roleProfile) ->
          if (!suppressAutoTeamRestore && _loggedInId.value == null && roleProfile != null) {
            val list = (accountsUi as? UiState.Success)?.data ?: return@collect
            val matchingAccount =
              list.firstOrNull { it.loginId == roleProfile.activeLoginId }
                ?: list.firstOrNull { it.role == roleProfile.role }
            if (matchingAccount != null) {
              _loggedInId.value = matchingAccount.loginId
            }
          }
        }
    }
  }

  fun retryWorkspaceInit() {
    viewModelScope.launch {
      _isBusy.value = true
      val result = repository.initializeAndSyncWorkspace(currentUserId)
      _isBusy.value = false
      if (result.isFailure) {
        _flashBanner.value =
          FlashBanner(
            result.exceptionOrNull()?.message ?: "Could not sync workspace",
            isError = true,
          )
      }
    }
  }

  fun clearFlash() {
    _flashBanner.value = null
  }

  fun showFlash(message: String, isError: Boolean = false) {
    _flashBanner.value = FlashBanner(message, isError)
  }

  fun openCsvPreview(csv: String) {
    _csvPreview.value = csv
  }

  fun dismissCsvPreview() {
    _csvPreview.value = null
  }

  fun selectTab(tab: AppTab) {
    _flashBanner.value = null
    _currentTab.value = tab
  }

  fun selectFetchSubTab(index: Int) {
    _fetchSubTab.value = index.coerceIn(0, 2)
  }

  fun openFetchTab(subTabIndex: Int) {
    _flashBanner.value = null
    _fetchSubTab.value = subTabIndex.coerceIn(0, 2)
    _currentTab.value = AppTab.FETCH
  }

  fun syncWorkspace() {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.syncFromFirestore(currentUserId)
      _isBusy.value = false
      res.fold(
        onSuccess = {
          _flashBanner.value = FlashBanner("Cloud workspace synced with Firestore.", isError = false)
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Firestore sync failed", isError = true)
        },
      )
    }
  }

  fun signIn(rawLoginId: String, password: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.authenticateWithIdAndPassword(rawLoginId, password)
      _isBusy.value = false
      res.fold(
        onSuccess = { account ->
          suppressAutoTeamRestore = false
          _loggedInId.value = account.loginId
          _currentTab.value = AppTab.DASHBOARD
          _capturedCandidates.value = AutoFetchPreferences.consumeStagedCandidates(appContext)
          _lastVehicleResult.value = null
          syncAutoFetchAccountBinding(account)
          _flashBanner.value =
            FlashBanner(
              "Signed in as ${account.loginId} (${if (account.isOwnerRole()) "Owner" else "Employee"}).",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Sign in failed", isError = true)
        },
      )
    }
  }

  fun registerOwnerAndSignIn(rawOwnerId: String, fullName: String, password: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        repository.createOwnerAccount(
          rawOwnerId = rawOwnerId,
          fullName = fullName,
          password = password,
          switchRoleInFirestore = true,
        )
      _isBusy.value = false
      res.fold(
        onSuccess = { owner ->
          suppressAutoTeamRestore = false
          _loggedInId.value = owner.loginId
          _currentTab.value = AppTab.DASHBOARD
          syncAutoFetchAccountBinding(owner)
          _flashBanner.value =
            FlashBanner(
              "Owner ID ${owner.loginId} created with 5,000 pts reserve.",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Owner registration failed", isError = true)
        },
      )
    }
  }

  fun signOutTeamRole() {
    suppressAutoTeamRestore = true
    _loggedInId.value = null
    _currentTab.value = AppTab.DASHBOARD
    _capturedCandidates.value = emptyList()
    _lastVehicleResult.value = null
    _flashBanner.value = null
  }

  fun createOwnerAccount(
    rawOwnerId: String,
    fullName: String,
    password: String,
    onSuccess: () -> Unit = {},
  ) {
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        repository.createOwnerAccount(
          rawOwnerId = rawOwnerId,
          fullName = fullName,
          password = password,
          switchRoleInFirestore = false,
        )
      _isBusy.value = false
      res.fold(
        onSuccess = { owner ->
          onSuccess()
          _flashBanner.value =
            FlashBanner(
              "Owner ID ${owner.loginId} initialized with 5,000 pts reserve.",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Failed to create Owner ID", isError = true)
        },
      )
    }
  }

  fun createEmployeeAccount(
    rawEmployeeId: String,
    fullName: String,
    password: String,
    onSuccess: () -> Unit = {},
  ) {
    val owner = activeAccount.value ?: return
    if (!owner.isOwnerRole()) {
      _flashBanner.value = FlashBanner("Only Owner IDs can add Employee IDs.", isError = true)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res =
        repository.createEmployeeAccount(
          ownerAccountId = owner.loginId,
          rawEmployeeId = rawEmployeeId,
          fullName = fullName,
          password = password,
        )
      _isBusy.value = false
      res.fold(
        onSuccess = {
          onSuccess()
          val id = LeadXValidators.normalizeLoginId(rawEmployeeId)
          _flashBanner.value =
            FlashBanner(
              "Employee $id created with password & 5,000 pts reserve.",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Failed to create employee", isError = true)
        },
      )
    }
  }

  fun topUp(amountText: String) {
    val account = activeAccount.value ?: return
    val amount = amountText.trim().toLongOrNull()
    if (amount == null || amount < 100L) {
      _flashBanner.value = FlashBanner("Minimum single top-up is ₹100 (100 pts).", isError = true)
      return
    }
    if (account.balance + amount < MIN_WALLET_BALANCE) {
      _flashBanner.value =
        FlashBanner(
          "Minimum 5000 balance required, please recharge at least ${MIN_WALLET_BALANCE - account.balance} pts.",
          isError = true,
        )
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.topUpWallet(account.loginId, amount)
      _isBusy.value = false
      res.fold(
        onSuccess = { newBal ->
          _flashBanner.value =
            FlashBanner(
              "Razorpay Top-Up complete: +$amount pts (₹$amount) added to ${account.loginId} · Bal: $newBal pts.",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Top-up failed", isError = true)
        },
      )
    }
  }

  fun sharePoints(recipientIdRaw: String, amountText: String) {
    val sender = activeAccount.value ?: return
    val amount = amountText.trim().toLongOrNull()
    if (recipientIdRaw.isBlank() || amount == null || amount <= 0L) {
      _flashBanner.value = FlashBanner("Enter recipient ID and positive points.", isError = true)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.sharePoints(sender.loginId, recipientIdRaw, amount)
      _isBusy.value = false
      res.fold(
        onSuccess = {
          val target = LeadXValidators.normalizeLoginId(recipientIdRaw)
          _flashBanner.value = FlashBanner("Transferred $amount pts to $target.", isError = false)
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Point transfer failed", isError = true)
        },
      )
    }
  }

  fun extractLinkedInUrl(rawUrl: String) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.resolveLinkedInUrl(rawUrl)
      _isBusy.value = false
      res.fold(
        onSuccess = { parsed ->
          val current = _capturedCandidates.value.toMutableList()
          current.removeAll { it.profileUrl.equals(parsed.profileUrl, ignoreCase = true) }
          current.add(0, parsed)
          _capturedCandidates.value = current
          _flashBanner.value =
            FlashBanner(
              "Live LinkedIn profile extracted: ${parsed.name}${if (parsed.company.isNotBlank()) " · ${parsed.company}" else ""}.",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value =
            FlashBanner(err.message ?: "Could not extract LinkedIn profile.", isError = true)
        },
      )
    }
  }

  fun extractWebViewDomLeadsJson(rawJson: String) {
    val parsedList = RealLeadExtractor.parseWebViewDomJson(rawJson)
    if (parsedList.isEmpty()) {
      _flashBanner.value =
        FlashBanner(
          "No visible LinkedIn profile/search leads detected on this page yet. Sign in or open a People Search / Profile page first.",
          isError = true,
        )
      return
    }
    val current = _capturedCandidates.value.toMutableList()
    parsedList.reversed().forEach { candidate ->
      current.removeAll {
        it.profileUrl.isNotBlank() && it.profileUrl.equals(candidate.profileUrl, ignoreCase = true)
      }
      current.add(0, candidate)
    }
    _capturedCandidates.value = current
    _flashBanner.value =
      FlashBanner(
        "Captured ${parsedList.size} real LinkedIn lead${if (parsedList.size > 1) "s" else ""} from visible DOM.",
        isError = false,
      )
  }

  fun searchLinkedInB2BLeads(query: LinkedInSearchQuery) {
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.searchLinkedInB2BLeads(query)
      _isBusy.value = false
      res.fold(
        onSuccess = { results ->
          val current = _capturedCandidates.value.toMutableList()
          results.reversed().forEach { candidate ->
            if (candidate.profileUrl.isNotBlank()) {
              current.removeAll {
                it.profileUrl.equals(candidate.profileUrl, ignoreCase = true)
              }
            } else {
              current.removeAll { it.id == candidate.id }
            }
            current.add(0, candidate)
          }
          _capturedCandidates.value = current
          _flashBanner.value =
            FlashBanner(
              "Found ${results.size} real B2B LinkedIn lead${if (results.size > 1) "s" else ""} via Proxycurl.",
              isError = false,
            )
        },
        onFailure = { err ->
          _flashBanner.value =
            FlashBanner(err.message ?: "Proxycurl B2B lead search failed.", isError = true)
        },
      )
    }
  }

  fun toggleCandidateSelection(candidateId: String) {
    _capturedCandidates.value =
      _capturedCandidates.value.map { c ->
        if (c.id == candidateId) c.copy(selected = !c.selected) else c
      }
  }

  fun selectAllCandidates(selected: Boolean) {
    _capturedCandidates.value = _capturedCandidates.value.map { it.copy(selected = selected) }
  }

  fun clearCapturedCandidates() {
    _capturedCandidates.value = emptyList()
  }

  fun saveSelectedLinkedInLeads() {
    val account = activeAccount.value ?: return
    val selected = _capturedCandidates.value.filter { it.selected }
    if (selected.isEmpty()) {
      _flashBanner.value = FlashBanner("Select at least 1 LinkedIn lead to save.", isError = true)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.captureLinkedInLeads(account.loginId, account.ownerAccountId, selected)
      _isBusy.value = false
      res.fold(
        onSuccess = { count ->
          _capturedCandidates.value = emptyList()
          _flashBanner.value =
            FlashBanner("Saved $count real LinkedIn lead${if (count > 1) "s" else ""} (-${count * 10} pts).", isError = false)
        },
        onFailure = { err ->
          _flashBanner.value =
            FlashBanner(err.message ?: "Minimum 5000 balance required, please recharge", isError = true)
        },
      )
    }
  }

  fun fetchVehicleByPlate(rawPlate: String) {
    val account = activeAccount.value ?: return
    val plate = LeadXValidators.normalizePlate(rawPlate)
    if (!LeadXValidators.isValidPlate(plate)) {
      _flashBanner.value =
        FlashBanner("Enter a valid Indian Vehicle Number (e.g. MH02AB1234).", isError = true)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val rcLookupRes = repository.lookupRealVehicleByPlate(plate)
      rcLookupRes.fold(
        onSuccess = { lookup ->
          if (!lookup.isLiveRcData) {
            _isBusy.value = false
            _lastVehicleResult.value = lookup
            _flashBanner.value = FlashBanner(lookup.statusMessage, isError = false)
          } else {
            val saveRes = repository.fetchNumberPlateLead(account.loginId, account.ownerAccountId, lookup)
            _isBusy.value = false
            saveRes.fold(
              onSuccess = { savedLeadId ->
                _lastVehicleResult.value = lookup.copy(savedLeadId = savedLeadId)
                _flashBanner.value =
                  FlashBanner(
                    "Live Vahan RC saved for $plate (-15 pts). Tap Request Consent to reveal owner details.",
                    isError = false,
                  )
              },
              onFailure = { err ->
                _flashBanner.value =
                  FlashBanner(err.message ?: "Minimum 5000 balance required, please recharge", isError = true)
              },
            )
          }
        },
        onFailure = { err ->
          _isBusy.value = false
          _flashBanner.value = FlashBanner(err.message ?: "Vehicle lookup failed.", isError = true)
        },
      )
    }
  }

  fun requestVehicleOwnerConsent() {
    val current = _lastVehicleResult.value ?: return
    if (!current.isLiveRcData) return
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.grantVehicleOwnerConsent(current)
      _isBusy.value = false
      res.fold(
        onSuccess = {
          _lastVehicleResult.value = current.copy(consentGranted = true)
          _flashBanner.value =
            FlashBanner("Owner consent verified for ${current.plateNumber}. Full RC owner details saved.", isError = false)
        },
        onFailure = { err ->
          _flashBanner.value = FlashBanner(err.message ?: "Consent update failed.", isError = true)
        },
      )
    }
  }

  fun exportOwnerLeadsFromFirestoreToCsv(onCsvReady: (String) -> Unit) {
    val owner = activeAccount.value ?: return
    if (!owner.isOwnerRole()) {
      _flashBanner.value = FlashBanner("Only Owner accounts can export all team leads.", isError = true)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      val res = repository.exportCollectedLeadsFromFirestore(owner.loginId)
      _isBusy.value = false
      res.fold(
        onSuccess = { leads ->
          if (leads.isEmpty()) {
            _flashBanner.value = FlashBanner("No collected leads found in Firestore yet.", isError = true)
          } else {
            val csv = com.example.data.CsvExporter.buildLeadsCsv(leads)
            onCsvReady(csv)
            _flashBanner.value =
              FlashBanner("Exported ${leads.size} collected lead${if (leads.size > 1) "s" else ""} from Firestore to CSV.", isError = false)
          }
        },
        onFailure = { err ->
          val fallbackLeads =
            (leadsState.value as? UiState.Success)?.data?.filter { it.ownerAccountId == owner.loginId }.orEmpty()
          if (fallbackLeads.isNotEmpty()) {
            onCsvReady(com.example.data.CsvExporter.buildLeadsCsv(fallbackLeads))
            _flashBanner.value =
              FlashBanner("Exported ${fallbackLeads.size} cached leads to CSV (Firestore sync unavailable).", isError = false)
          } else {
            _flashBanner.value =
              FlashBanner(err.message ?: "Failed to export leads from Firestore.", isError = true)
          }
        },
      )
    }
  }

  private fun syncAutoFetchAccountBinding(account: TeamAccount) {
    val existing = AutoFetchPreferences.getConfig(appContext)
    if (existing.activeLoginId != account.loginId || existing.ownerAccountId != account.ownerAccountId) {
      AutoFetchPreferences.saveConfig(
        appContext,
        existing.copy(
          activeLoginId = account.loginId,
          ownerAccountId = account.ownerAccountId,
        ),
      )
    }
  }

  fun saveAutoFetchConfig(updated: AutoFetchConfig, showFlashMessage: Boolean = true) {
    if (updated.enabled && !updated.hasValidTarget()) {
      _flashBanner.value =
        FlashBanner(
          "Enter at least one B2B search filter or LinkedIn URL before enabling Daily Auto-Fetch.",
          isError = true,
        )
      return
    }
    val account = activeAccount.value
    val persisted =
      updated.copy(
        activeLoginId = account?.loginId ?: updated.activeLoginId,
        ownerAccountId = account?.ownerAccountId ?: updated.ownerAccountId,
      )
    AutoFetchPreferences.saveConfig(appContext, persisted)
    AutoFetchScheduler.syncSchedule(appContext, persisted.enabled, persisted.intervalHours)
    if (showFlashMessage) {
      val freqLabel = AutoFetchInterval.fromHours(persisted.intervalHours).label
      _flashBanner.value =
        FlashBanner(
          if (persisted.enabled) {
            "Daily Auto-Fetch active ($freqLabel · max ${persisted.maxLeadsPerRun} leads/run)."
          } else {
            "Auto-Fetch settings saved (schedule paused)."
          },
          isError = false,
        )
    }
  }

  fun runAutoFetchNow(draftConfig: AutoFetchConfig? = null) {
    val account = activeAccount.value
    val baseConfig = draftConfig ?: AutoFetchPreferences.getConfig(appContext)
    val effectiveConfig =
      baseConfig.copy(
        activeLoginId = account?.loginId ?: baseConfig.activeLoginId,
        ownerAccountId = account?.ownerAccountId ?: baseConfig.ownerAccountId,
      )
    if (!effectiveConfig.hasValidTarget()) {
      _flashBanner.value =
        FlashBanner(
          "Enter at least one B2B filter (Role, Company, Industry, City) or LinkedIn profile URL first.",
          isError = true,
        )
      return
    }
    AutoFetchPreferences.saveConfig(appContext, effectiveConfig)
    if (effectiveConfig.enabled) {
      AutoFetchScheduler.schedulePeriodic(appContext, effectiveConfig.intervalHours)
    }

    viewModelScope.launch {
      _isBusy.value = true
      val outcome =
        withContext(Dispatchers.IO) {
          AutoFetchEngine.executeAutoFetch(
            context = appContext,
            dao = dao,
            repository = repository,
            config = effectiveConfig,
            notifyUser = true,
          )
        }
      _isBusy.value = false

      if (outcome.stagedCandidates.isNotEmpty()) {
        AutoFetchPreferences.consumeStagedCandidates(appContext)
        val current = _capturedCandidates.value.toMutableList()
        outcome.stagedCandidates.reversed().forEach { candidate ->
          if (candidate.profileUrl.isNotBlank()) {
            current.removeAll { it.profileUrl.equals(candidate.profileUrl, ignoreCase = true) }
          } else {
            current.removeAll { it.id == candidate.id }
          }
          current.add(0, candidate)
        }
        _capturedCandidates.value = current
      }

      _flashBanner.value = FlashBanner(outcome.statusMessage, isError = outcome.isError)
    }
  }

  companion object {
    private const val STOP_TIMEOUT_MILLIS = 5000L

    fun factory(userId: String): ViewModelProvider.Factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) { "APPLICATION_KEY missing" }
        RealLeadExtractor.init(app)
        AutoFetchPreferences.init(app)
        val dao = LeadXDatabase.getInstance(app).leadXDao()
        val hasFirebase =
          runCatching { FirebaseApp.getApps(app).isNotEmpty() }.getOrDefault(false) ||
            runCatching { FirebaseApp.initializeApp(app) != null }.getOrDefault(false)
        val databaseId = app.getString(R.string.firestore_database_id)
        val firestore = if (hasFirebase) runCatching { FirebaseFirestore.getInstance(databaseId) }.getOrNull() else null
        val auth = if (hasFirebase) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null
        LeadXViewModel(
          repository = LeadXRepository(dao, firestore, auth),
          currentUserId = userId,
          appContext = app.applicationContext,
          dao = dao,
        )
      }
    }
  }
}
