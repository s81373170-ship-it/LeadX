package com.example.data

import com.example.data.local.LeadEntity
import com.example.data.local.LeadXDao
import com.example.data.local.TeamAccountEntity
import com.example.data.local.TransactionEntity
import com.example.model.AccountRole
import com.example.model.FirestoreUserRoleProfile
import com.example.model.LINKEDIN_COST_PER_LEAD
import com.example.model.LeadRecord
import com.example.model.LeadSource
import com.example.model.LinkedInCandidate
import com.example.model.LinkedInSearchQuery
import com.example.model.MIN_SINGLE_TOPUP
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.PointTransaction
import com.example.model.TeamAccount
import com.example.model.TxType
import com.example.model.VEHICLE_CHECK_COST
import com.example.model.VehicleLookupResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class PeriodicFirestoreSyncResult(
  val syncedUserId: String,
  val firestoreUpdated: Boolean,
  val fetchedRecordCount: Int,
  val simulatedBatchLabel: String,
  val statusMessage: String,
  val timestampMillis: Long,
)

class LeadXRepository(
  private val dao: LeadXDao,
  private val firestore: FirebaseFirestore?,
  private val auth: FirebaseAuth?,
) {

  private fun currentUid(): String? = auth?.currentUser?.uid

  fun observeTeamAccounts(): Flow<List<TeamAccount>> =
    dao.observeAccounts().map { entities -> entities.map { it.toDomain(currentUid().orEmpty()) } }

  fun observeLeads(): Flow<List<LeadRecord>> =
    dao.observeLeads().map { entities -> entities.map { it.toDomain(currentUid().orEmpty()) } }

  fun observeTransactions(): Flow<List<PointTransaction>> =
    dao.observeTransactions().map { entities -> entities.map { it.toDomain(currentUid().orEmpty()) } }

  fun observeFirestoreUserRole(userId: String): Flow<FirestoreUserRoleProfile?> = callbackFlow {
    val db = firestore
    if (userId.isBlank() || db == null) {
      trySend(null)
      awaitClose {}
      return@callbackFlow
    }
    val docRef = db.collection("users").document(userId)
    val registration =
      docRef.addSnapshotListener { snapshot, error ->
        if (error != null) {
          handleFirestoreError(error, OperationType.GET, "users/$userId")
          return@addSnapshotListener
        }
        val profile =
          if (snapshot != null && snapshot.exists()) {
            snapshot.toObject(FirestoreUserRoleProfile::class.java)
          } else {
            null
          }
        trySend(profile)
      }
    awaitClose { registration.remove() }
  }

  suspend fun initializeAndSyncWorkspace(userId: String): Result<Unit> =
    try {
      dao.seedInitialAccountsIfEmpty()
      if (userId.isNotBlank()) {
        syncFromFirestore(userId)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun syncFromFirestore(userId: String): Result<Unit> {
    val db = firestore
    if (userId.isBlank() || db == null) return Result.success(Unit)
    return try {
      val accountsSnap =
        db
          .collection("users")
          .document(userId)
          .collection("team_accounts")
          .get()
          .await()

      val localAccounts = dao.observeAccounts().first()
      val remoteAccountIds = accountsSnap.documents.map { it.id }.toSet()
      if (accountsSnap.isEmpty) {
        localAccounts.forEach { acc ->
          upsertTeamAccountToFirestore(userId, acc, isNew = true)
        }
      } else {
        val remoteAccounts =
          accountsSnap.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val loginId = (data["loginId"] as? String)?.ifBlank { doc.id } ?: return@mapNotNull null
            TeamAccountEntity(
              loginId = loginId,
              fullName = (data["fullName"] as? String).orEmpty().ifBlank { loginId },
              role = (data["role"] as? String).orEmpty().ifBlank { AccountRole.EMPLOYEE.wireValue },
              ownerAccountId = (data["ownerAccountId"] as? String).orEmpty().ifBlank { "OWN1001" },
              passwordHash = (data["passwordHash"] as? String).orEmpty(),
              balance = (data["balance"] as? Number)?.toLong() ?: MIN_WALLET_BALANCE,
              createdAtMillis =
                doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
            )
          }
        if (remoteAccounts.isNotEmpty()) {
          dao.upsertAccounts(remoteAccounts)
        }
        localAccounts
          .filter { it.loginId !in remoteAccountIds }
          .forEach { localOnly -> upsertTeamAccountToFirestore(userId, localOnly, isNew = true) }
      }

      val leadsSnap =
        db
          .collection("users")
          .document(userId)
          .collection("leads")
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .get()
          .await()

      val remoteLeadIds = leadsSnap.documents.map { it.id }.toSet()
      val remoteLeads =
        leadsSnap.documents.mapNotNull { doc ->
          val data = doc.data ?: return@mapNotNull null
          LeadEntity(
            id = doc.id,
            source = (data["source"] as? String).orEmpty().ifBlank { LeadSource.LINKEDIN.wireValue },
            ownerAccountId = (data["ownerAccountId"] as? String).orEmpty(),
            fetchedByLoginId = (data["fetchedByLoginId"] as? String).orEmpty(),
            name = (data["name"] as? String).orEmpty(),
            title = (data["title"] as? String).orEmpty(),
            company = (data["company"] as? String).orEmpty(),
            email = (data["email"] as? String).orEmpty(),
            phone = (data["phone"] as? String).orEmpty(),
            industry = (data["industry"] as? String).orEmpty(),
            location = (data["location"] as? String).orEmpty(),
            plateNumber = (data["plateNumber"] as? String).orEmpty(),
            vehicleDetails = (data["vehicleDetails"] as? String).orEmpty(),
            profileUrl = (data["profileUrl"] as? String).orEmpty(),
            consentGranted = (data["consentGranted"] as? Boolean) ?: false,
            pointsCost = (data["pointsCost"] as? Number)?.toLong() ?: 0L,
            createdAtMillis =
              doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
          )
        }
      if (remoteLeads.isNotEmpty()) {
        dao.insertLeads(remoteLeads)
      }
      dao.observeLeads().first()
        .filter { it.id !in remoteLeadIds }
        .forEach { localLead -> writeLeadToFirestore(userId, localLead) }

      val txSnap =
        db
          .collection("users")
          .document(userId)
          .collection("transactions")
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .get()
          .await()

      val remoteTxIds = txSnap.documents.map { it.id }.toSet()
      val remoteTxs =
        txSnap.documents.mapNotNull { doc ->
          val data = doc.data ?: return@mapNotNull null
          TransactionEntity(
            id = doc.id,
            type = (data["type"] as? String).orEmpty().ifBlank { TxType.TOPUP.wireValue },
            ownerAccountId = (data["ownerAccountId"] as? String).orEmpty(),
            loginId = (data["loginId"] as? String).orEmpty(),
            counterpartyId = (data["counterpartyId"] as? String).orEmpty(),
            amount = (data["amount"] as? Number)?.toLong() ?: 0L,
            balanceAfter = (data["balanceAfter"] as? Number)?.toLong() ?: 0L,
            note = (data["note"] as? String).orEmpty(),
            createdAtMillis =
              doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
          )
        }
      if (remoteTxs.isNotEmpty()) {
        dao.insertTransactions(remoteTxs)
      }
      dao.observeTransactions().first()
        .filter { it.id !in remoteTxIds && it.amount != 0L }
        .forEach { localTx -> writeTransactionToFirestore(userId, localTx) }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users/$userId")
      Result.failure(e)
    }
  }

  suspend fun exportCollectedLeadsFromFirestore(ownerAccountId: String): Result<List<LeadRecord>> {
    val uid = currentUid().orEmpty()
    val db = firestore
    if (uid.isBlank() || db == null) {
      return try {
        val localLeads =
          dao.observeLeads().first()
            .map { it.toDomain("") }
            .filter { it.ownerAccountId == ownerAccountId || ownerAccountId.isBlank() }
        Result.success(localLeads)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Result.failure(e)
      }
    }
    return try {
      val leadsSnap =
        db
          .collection("users")
          .document(uid)
          .collection("leads")
          .orderBy("createdAt", Query.Direction.DESCENDING)
          .get()
          .await()

      val fetched =
        leadsSnap.documents.mapNotNull { doc ->
          val data = doc.data ?: return@mapNotNull null
          LeadEntity(
            id = doc.id,
            source = (data["source"] as? String).orEmpty().ifBlank { LeadSource.LINKEDIN.wireValue },
            ownerAccountId = (data["ownerAccountId"] as? String).orEmpty(),
            fetchedByLoginId = (data["fetchedByLoginId"] as? String).orEmpty(),
            name = (data["name"] as? String).orEmpty(),
            title = (data["title"] as? String).orEmpty(),
            company = (data["company"] as? String).orEmpty(),
            email = (data["email"] as? String).orEmpty(),
            phone = (data["phone"] as? String).orEmpty(),
            industry = (data["industry"] as? String).orEmpty(),
            location = (data["location"] as? String).orEmpty(),
            plateNumber = (data["plateNumber"] as? String).orEmpty(),
            vehicleDetails = (data["vehicleDetails"] as? String).orEmpty(),
            profileUrl = (data["profileUrl"] as? String).orEmpty(),
            consentGranted = (data["consentGranted"] as? Boolean) ?: false,
            pointsCost = (data["pointsCost"] as? Number)?.toLong() ?: 0L,
            createdAtMillis =
              doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis(),
          )
        }
      if (fetched.isNotEmpty()) {
        dao.insertLeads(fetched)
      }
      val ownerLeads =
        if (fetched.isNotEmpty()) {
          fetched
            .map { it.toDomain(uid) }
            .filter { it.ownerAccountId == ownerAccountId || ownerAccountId.isBlank() }
        } else {
          dao.observeLeads().first()
            .map { it.toDomain(uid) }
            .filter { it.ownerAccountId == ownerAccountId || ownerAccountId.isBlank() }
        }
      Result.success(ownerLeads)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.LIST, "users/$uid/leads")
      Result.failure(e)
    }
  }

  suspend fun saveActiveRoleProfileToFirestore(userId: String, account: TeamAccount) {
    val db = firestore ?: return
    if (userId.isBlank()) return
    val payload =
      hashMapOf(
        "userId" to userId,
        "role" to account.role,
        "activeLoginId" to account.loginId,
        "fullName" to account.fullName.take(60),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      db.collection("users").document(userId).set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$userId")
    }
  }

  suspend fun authenticateWithIdAndPassword(
    rawLoginId: String,
    password: String,
  ): Result<TeamAccount> {
    val loginId = LeadXValidators.normalizeLoginId(rawLoginId)
    return try {
      require(LeadXValidators.isValidLoginId(loginId)) {
        "Enter a valid Login ID (3-24 chars: A-Z, 0-9)."
      }
      require(password.isNotBlank()) { "Enter your password." }
      if (dao.accountCount() == 0) {
        dao.seedInitialAccountsIfEmpty()
      }
      val entity = dao.getAccount(loginId) ?: error("Account ID $loginId not found.")
      val expectedHash = LeadXValidators.hashPassword(password)
      require(entity.passwordHash == expectedHash) { "Incorrect Login ID or Password." }
      val uid = currentUid()
      val domain = entity.toDomain(uid.orEmpty())
      if (!uid.isNullOrBlank()) {
        saveActiveRoleProfileToFirestore(uid, domain)
      }
      Result.success(domain)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun createEmployeeAccount(
    ownerAccountId: String,
    rawEmployeeId: String,
    fullName: String,
    password: String,
  ): Result<Unit> {
    val employeeId = LeadXValidators.normalizeLoginId(rawEmployeeId)
    return try {
      require(LeadXValidators.isValidLoginId(employeeId)) {
        "Employee ID must be 3-24 chars (A-Z, 0-9, ., _, -)."
      }
      require(LeadXValidators.isValidPassword(password)) {
        "Employee password must be at least 6 characters."
      }
      require(dao.getAccount(employeeId) == null) { "ID $employeeId already exists." }
      val cleanName = fullName.trim().ifEmpty { employeeId }.take(60)
      val now = System.currentTimeMillis()
      val accountEntity =
        TeamAccountEntity(
          loginId = employeeId,
          fullName = cleanName,
          role = AccountRole.EMPLOYEE.wireValue,
          ownerAccountId = ownerAccountId,
          passwordHash = LeadXValidators.hashPassword(password),
          balance = MIN_WALLET_BALANCE,
          createdAtMillis = now,
        )
      val txEntity =
        TransactionEntity(
          id = "tx_${UUID.randomUUID()}",
          type = TxType.TOPUP.wireValue,
          ownerAccountId = ownerAccountId,
          loginId = employeeId,
          counterpartyId = "",
          amount = MIN_WALLET_BALANCE,
          balanceAfter = MIN_WALLET_BALANCE,
          note = "Employee Minimum Reserve Initialized",
          createdAtMillis = now,
        )
      dao.createAccountWithStartingReserve(account = accountEntity, initTx = txEntity)
      currentUid()?.let { uid ->
        upsertTeamAccountToFirestore(uid, accountEntity, isNew = true)
        writeTransactionToFirestore(uid, txEntity)
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun createOwnerAccount(
    rawOwnerId: String,
    fullName: String,
    password: String,
    switchRoleInFirestore: Boolean = false,
  ): Result<TeamAccount> {
    val ownerId = LeadXValidators.normalizeLoginId(rawOwnerId)
    return try {
      require(LeadXValidators.isValidLoginId(ownerId)) {
        "Owner ID must be 3-24 chars (A-Z, 0-9, ., _, -)."
      }
      require(LeadXValidators.isValidPassword(password)) {
        "Owner password must be at least 6 characters."
      }
      require(dao.getAccount(ownerId) == null) { "ID $ownerId already exists." }
      val cleanName = fullName.trim().ifEmpty { ownerId }.take(60)
      val now = System.currentTimeMillis()
      val entity =
        TeamAccountEntity(
          loginId = ownerId,
          fullName = cleanName,
          role = AccountRole.OWNER.wireValue,
          ownerAccountId = ownerId,
          passwordHash = LeadXValidators.hashPassword(password),
          balance = MIN_WALLET_BALANCE,
          createdAtMillis = now,
        )
      val txEntity =
        TransactionEntity(
          id = "tx_${UUID.randomUUID()}",
          type = TxType.TOPUP.wireValue,
          ownerAccountId = ownerId,
          loginId = ownerId,
          counterpartyId = "",
          amount = MIN_WALLET_BALANCE,
          balanceAfter = MIN_WALLET_BALANCE,
          note = "Owner Minimum Reserve Initialized",
          createdAtMillis = now,
        )
      dao.createAccountWithStartingReserve(account = entity, initTx = txEntity)
      val uid = currentUid()
      val domain = entity.toDomain(uid.orEmpty())
      if (!uid.isNullOrBlank()) {
        upsertTeamAccountToFirestore(uid, entity, isNew = true)
        writeTransactionToFirestore(uid, txEntity)
        if (switchRoleInFirestore) {
          saveActiveRoleProfileToFirestore(uid, domain)
        }
      }
      Result.success(domain)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun topUpWallet(accountId: String, amount: Long): Result<Long> =
    try {
      require(amount >= MIN_SINGLE_TOPUP) {
        "Minimum single top-up is ₹$MIN_SINGLE_TOPUP ($MIN_SINGLE_TOPUP pts)."
      }
      val txId = "tx_${UUID.randomUUID()}"
      val updated = dao.executeTopUp(accountId, amount, txId)
      val uid = currentUid()
      if (!uid.isNullOrBlank()) {
        val account = dao.getAccount(accountId)
        if (account != null) {
          updateAccountBalanceInFirestore(uid, accountId, updated)
          writeTransactionToFirestore(
            uid,
            TransactionEntity(
              id = txId,
              type = TxType.TOPUP.wireValue,
              ownerAccountId = account.ownerAccountId,
              loginId = accountId,
              counterpartyId = "",
              amount = amount,
              balanceAfter = updated,
              note = "Razorpay Top-Up (₹$amount · ${account.role})",
              createdAtMillis = System.currentTimeMillis(),
            ),
          )
        }
      }
      Result.success(updated)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun sharePoints(
    senderLoginId: String,
    rawRecipientId: String,
    amount: Long,
  ): Result<Unit> {
    val recipientId = LeadXValidators.normalizeLoginId(rawRecipientId)
    return try {
      require(LeadXValidators.isValidLoginId(recipientId)) {
        "Enter a valid Recipient ID."
      }
      require(recipientId != senderLoginId) { "Cannot share points with yourself." }
      require(amount > 0L) { "Share amount must be greater than zero." }
      val outTxId = "tx_${UUID.randomUUID()}"
      val inTxId = "tx_${UUID.randomUUID()}"
      dao.executeSharePoints(
        senderId = senderLoginId,
        recipientId = recipientId,
        amount = amount,
        outTxId = outTxId,
        inTxId = inTxId,
      )
      val uid = currentUid()
      if (!uid.isNullOrBlank()) {
        val sender = dao.getAccount(senderLoginId)
        val recipient = dao.getAccount(recipientId)
        if (sender != null && recipient != null) {
          updateAccountBalanceInFirestore(uid, senderLoginId, sender.balance)
          updateAccountBalanceInFirestore(uid, recipientId, recipient.balance)
          val now = System.currentTimeMillis()
          writeTransactionToFirestore(
            uid,
            TransactionEntity(
              id = outTxId,
              type = TxType.TRANSFER_OUT.wireValue,
              ownerAccountId = sender.ownerAccountId,
              loginId = senderLoginId,
              counterpartyId = recipientId,
              amount = -amount,
              balanceAfter = sender.balance,
              note = "ID Share to $recipientId",
              createdAtMillis = now,
            ),
          )
          writeTransactionToFirestore(
            uid,
            TransactionEntity(
              id = inTxId,
              type = TxType.TRANSFER_IN.wireValue,
              ownerAccountId = recipient.ownerAccountId,
              loginId = recipientId,
              counterpartyId = senderLoginId,
              amount = amount,
              balanceAfter = recipient.balance,
              note = "ID Share from $senderLoginId",
              createdAtMillis = now + 1L,
            ),
          )
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun resolveLinkedInUrl(rawUrl: String): Result<LinkedInCandidate> =
    withContext(Dispatchers.IO) {
      RealLeadExtractor.fetchRealLinkedInProfile(rawUrl)
    }

  suspend fun searchLinkedInB2BLeads(query: LinkedInSearchQuery): Result<List<LinkedInCandidate>> =
    withContext(Dispatchers.IO) {
      RealLeadExtractor.searchLinkedInNoApi(query)
    }

  /**
   * Simulates a periodic 24-hour remote B2B dataset fetch and updates both Room and Cloud Firestore
   * (when authenticated). All fields obey firestore.rules parity and zero points are deducted.
   */
  suspend fun perform24hSimulatedFetchAndFirestoreSync(): Result<PeriodicFirestoreSyncResult> =
    try {
      dao.seedInitialAccountsIfEmpty()
      val now = System.currentTimeMillis()
      val accounts = dao.observeAccounts().first()
      val ownerAccount =
        accounts.firstOrNull { it.role == AccountRole.OWNER.wireValue }
          ?: accounts.firstOrNull()
      val ownerLoginId = ownerAccount?.loginId ?: "OWN1001"
      val existingLeadsCount = dao.observeLeads().first().size

      val stamp = SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(now))
      val batchTag = "24H_SIM_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(now))}"
      val simulatedSyncEntity =
        LeadEntity(
          id = "lead_sync24h_${UUID.randomUUID()}",
          source = LeadSource.LINKEDIN.wireValue,
          ownerAccountId = ownerAccount?.ownerAccountId?.ifBlank { ownerLoginId } ?: ownerLoginId,
          fetchedByLoginId = ownerLoginId,
          name = "24h Periodic Fetch Snapshot ($stamp)".take(100),
          title = "Simulated 24h Background Data Sync".take(140),
          company = "LeadX Cloud Sync [$batchTag]".take(120),
          email = "",
          phone = "",
          industry = "Automated 24h WorkManager Refresh",
          location = "India · Periodic Sync",
          plateNumber = "",
          vehicleDetails =
            "Simulated 24h fetch ($batchTag) · ${accounts.size} accounts · $existingLeadsCount prior leads synced"
              .take(240),
          profileUrl = "",
          consentGranted = true,
          pointsCost = 0L,
          createdAtMillis = now,
        )

      dao.insertLead(simulatedSyncEntity)

      val uid = currentUid().orEmpty()
      val db = firestore
      val firestoreUpdated =
        if (uid.isNotBlank() && db != null) {
          val syncOutcome = syncFromFirestore(uid)
          writeLeadToFirestore(uid, simulatedSyncEntity)
          if (ownerAccount != null) {
            saveActiveRoleProfileToFirestore(uid, ownerAccount.toDomain(uid))
          }
          syncOutcome.isSuccess
        } else {
          false
        }

      val statusMsg =
        if (firestoreUpdated) {
          "24h WorkManager simulated fetch completed ($batchTag) & updated Firestore."
        } else {
          "24h WorkManager simulated fetch stored in Room ($batchTag) — sign in with Google to push to Firestore."
        }

      Result.success(
        PeriodicFirestoreSyncResult(
          syncedUserId = uid,
          firestoreUpdated = firestoreUpdated,
          fetchedRecordCount = 1,
          simulatedBatchLabel = batchTag,
          statusMessage = statusMsg,
          timestampMillis = now,
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun importCsvLeadsToDb(
    accountId: String,
    ownerAccountId: String,
    leads: List<LeadRecord>,
  ): Result<Int> =
    try {
      require(leads.isNotEmpty()) { "No valid leads in CSV to import." }
      val existing = dao.observeLeads().first()
      val existingUrls =
        existing.map { it.profileUrl.trim().substringBefore("?").trimEnd('/').lowercase() }.filter { it.isNotBlank() }.toHashSet()
      val existingKeys =
        existing.map { "${it.name.trim().lowercase()}|${it.company.trim().lowercase()}" }.toHashSet()

      val now = System.currentTimeMillis()
      val freshEntities =
        leads.mapIndexedNotNull { idx, r ->
          val urlNorm = r.profileUrl.trim().substringBefore("?").trimEnd('/').lowercase()
          val idKey = "${r.name.trim().lowercase()}|${r.company.trim().lowercase()}"
          if ((urlNorm.isNotBlank() && !existingUrls.add(urlNorm)) || (r.name.isNotBlank() && !existingKeys.add(idKey))) {
            return@mapIndexedNotNull null
          }
          LeadEntity(
            id = "lead_csv_${UUID.randomUUID()}",
            source = r.source.ifBlank { LeadSource.LINKEDIN.wireValue },
            ownerAccountId = ownerAccountId,
            fetchedByLoginId = accountId,
            name = r.name.take(100),
            title = r.title.take(140),
            company = r.company.take(120),
            email = r.email.take(120),
            phone = r.phone.take(40),
            industry = r.industry.take(140),
            location = r.location.take(120),
            plateNumber = r.plateNumber.take(20),
            vehicleDetails = r.vehicleDetails.take(240),
            profileUrl = r.profileUrl.take(300),
            consentGranted = true,
            pointsCost = 0L,
            createdAtMillis = now + idx,
          )
        }
      if (freshEntities.isNotEmpty()) {
        dao.insertLeads(freshEntities)
        val uid = currentUid()
        if (!uid.isNullOrBlank()) {
          freshEntities.forEach { writeLeadToFirestore(uid, it) }
        }
      }
      Result.success(freshEntities.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun captureLinkedInLeads(
    accountId: String,
    ownerAccountId: String,
    candidates: List<LinkedInCandidate>,
  ): Result<Int> =
    try {
      require(candidates.isNotEmpty()) { "Select at least one LinkedIn lead to capture." }
      val now = System.currentTimeMillis()
      val totalCost = candidates.size * LINKEDIN_COST_PER_LEAD
      val txId = "tx_${UUID.randomUUID()}"
      val entities =
        candidates.mapIndexed { idx, candidate ->
          val bioAndMethod =
            listOfNotNull(
              candidate.bio.takeIf { it.isNotBlank() }?.let { "Bio: $it" },
              candidate.captureMethod.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
          LeadEntity(
            id = "lead_${UUID.randomUUID()}",
            source = LeadSource.LINKEDIN.wireValue,
            ownerAccountId = ownerAccountId,
            fetchedByLoginId = accountId,
            name = candidate.name.take(100),
            title = candidate.title.take(140),
            company = candidate.company.take(120),
            email = candidate.email.take(120),
            phone = candidate.phone.take(40),
            industry = candidate.industry.take(140),
            location = candidate.location.take(120),
            plateNumber = "",
            vehicleDetails = bioAndMethod.take(240),
            profileUrl = candidate.profileUrl.take(300),
            consentGranted = true,
            pointsCost = LINKEDIN_COST_PER_LEAD,
            createdAtMillis = now + idx,
          )
        }
      dao.executeLinkedInCapture(accountId, totalCost, entities, txId)
      val uid = currentUid()
      if (!uid.isNullOrBlank()) {
        val updatedAccount = dao.getAccount(accountId)
        if (updatedAccount != null) {
          updateAccountBalanceInFirestore(uid, accountId, updatedAccount.balance)
          entities.forEach { writeLeadToFirestore(uid, it) }
          writeTransactionToFirestore(
            uid,
            TransactionEntity(
              id = txId,
              type = TxType.LEAD_LINKEDIN.wireValue,
              ownerAccountId = ownerAccountId,
              loginId = accountId,
              counterpartyId = "",
              amount = -totalCost,
              balanceAfter = updatedAccount.balance,
              note = "LinkedIn Capture (${entities.size} lead${if (entities.size > 1) "s" else ""})",
              createdAtMillis = now,
            ),
          )
        }
      }
      Result.success(candidates.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun lookupRealVehicleByPlate(rawPlate: String): Result<VehicleLookupResult> =
    withContext(Dispatchers.IO) {
      RealLeadExtractor.fetchRealVahanRc(rawPlate)
    }

  suspend fun fetchNumberPlateLead(
    accountId: String,
    ownerAccountId: String,
    vehicle: VehicleLookupResult,
  ): Result<String> =
    try {
      require(vehicle.isLiveRcData) {
        "Live official Vahan RC token is required before deducting points."
      }
      val leadId = "lead_${UUID.randomUUID()}"
      val txId = "tx_${UUID.randomUUID()}"
      val now = System.currentTimeMillis()
      val entity =
        LeadEntity(
          id = leadId,
          source = LeadSource.NUMBER_PLATE.wireValue,
          ownerAccountId = ownerAccountId,
          fetchedByLoginId = accountId,
          name = "${vehicle.manufacturer} ${vehicle.vehicleModel}".trim().ifBlank { vehicle.plateNumber }.take(100),
          title = "${vehicle.vehicleClass} · ${vehicle.fuelType} · Reg ${vehicle.registrationYear}".take(140),
          company = vehicle.rtoOffice.take(120),
          email = "",
          phone = "",
          industry = "Insurance Valid Till ${vehicle.insuranceValidTill} · PUC ${vehicle.pucValidTill}".take(140),
          location = "${vehicle.registeredCity}, ${vehicle.registeredState}".trim(',', ' ').take(120),
          plateNumber = vehicle.plateNumber.take(20),
          vehicleDetails =
            "Reg Date: ${vehicle.registrationDate} · Fitness: ${vehicle.fitnessValidTill} · Consent Pending".take(240),
          profileUrl = "",
          consentGranted = false,
          pointsCost = VEHICLE_CHECK_COST,
          createdAtMillis = now,
        )
      dao.executeVehicleLookup(accountId, VEHICLE_CHECK_COST, entity, txId)
      val uid = currentUid()
      if (!uid.isNullOrBlank()) {
        val updatedAccount = dao.getAccount(accountId)
        if (updatedAccount != null) {
          updateAccountBalanceInFirestore(uid, accountId, updatedAccount.balance)
          writeLeadToFirestore(uid, entity)
          writeTransactionToFirestore(
            uid,
            TransactionEntity(
              id = txId,
              type = TxType.LEAD_VEHICLE.wireValue,
              ownerAccountId = ownerAccountId,
              loginId = accountId,
              counterpartyId = "",
              amount = -VEHICLE_CHECK_COST,
              balanceAfter = updatedAccount.balance,
              note = "Vehicle RC Check (${vehicle.plateNumber})",
              createdAtMillis = now,
            ),
          )
        }
      }
      Result.success(leadId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  suspend fun grantVehicleOwnerConsent(vehicle: VehicleLookupResult): Result<Unit> =
    try {
      if (vehicle.savedLeadId.isNotBlank()) {
        val displayName =
          "${vehicle.ownerName} (${vehicle.manufacturer} ${vehicle.vehicleModel})".trim().take(100)
        val details =
          "Reg Date: ${vehicle.registrationDate} · Ins: ${vehicle.insuranceValidTill} · Consent Verified".take(240)
        val phone = vehicle.ownerContact.take(40)
        dao.updateLeadConsentDetails(
          leadId = vehicle.savedLeadId,
          ownerDisplayName = displayName,
          phone = phone,
          email = "",
          details = details,
        )
        val uid = currentUid()
        val db = firestore
        if (!uid.isNullOrBlank() && db != null) {
          val updates =
            hashMapOf<String, Any>(
              "name" to displayName,
              "phone" to phone,
              "email" to "",
              "vehicleDetails" to details,
              "consentGranted" to true,
            )
          try {
            db
              .collection("users")
              .document(uid)
              .collection("leads")
              .document(vehicle.savedLeadId)
              .update(updates)
              .await()
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            handleFirestoreError(
              e,
              OperationType.UPDATE,
              "users/$uid/leads/${vehicle.savedLeadId}",
            )
          }
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Result.failure(e)
    }

  private suspend fun upsertTeamAccountToFirestore(
    userId: String,
    entity: TeamAccountEntity,
    isNew: Boolean,
  ) {
    val db = firestore ?: return
    val docRef =
      db
        .collection("users")
        .document(userId)
        .collection("team_accounts")
        .document(entity.loginId)
    val payload =
      hashMapOf(
        "userId" to userId,
        "loginId" to entity.loginId,
        "passwordHash" to entity.passwordHash,
        "fullName" to entity.fullName.take(60),
        "role" to entity.role,
        "ownerAccountId" to entity.ownerAccountId,
        "balance" to entity.balance,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    try {
      if (isNew) {
        val existing = docRef.get().await()
        if (!existing.exists()) {
          docRef.set(payload).await()
        }
      } else {
        docRef.set(payload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, "users/$userId/team_accounts/${entity.loginId}")
    }
  }

  private suspend fun updateAccountBalanceInFirestore(
    userId: String,
    loginId: String,
    newBalance: Long,
  ) {
    val db = firestore ?: return
    val docRef =
      db
        .collection("users")
        .document(userId)
        .collection("team_accounts")
        .document(loginId)
    try {
      val snapshot = docRef.get().await()
      if (!snapshot.exists()) {
        val local = dao.getAccount(loginId) ?: return
        upsertTeamAccountToFirestore(userId, local, isNew = true)
      } else {
        docRef
          .update(
            mapOf(
              "balance" to newBalance,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          )
          .await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "users/$userId/team_accounts/$loginId")
    }
  }

  private suspend fun writeLeadToFirestore(userId: String, entity: LeadEntity) {
    val db = firestore ?: return
    val docRef =
      db
        .collection("users")
        .document(userId)
        .collection("leads")
        .document(entity.id)
    val payload =
      hashMapOf(
        "userId" to userId,
        "source" to entity.source,
        "ownerAccountId" to entity.ownerAccountId,
        "fetchedByLoginId" to entity.fetchedByLoginId,
        "name" to entity.name.take(100),
        "title" to entity.title.take(140),
        "company" to entity.company.take(120),
        "email" to entity.email.take(120),
        "phone" to entity.phone.take(40),
        "industry" to entity.industry.take(140),
        "location" to entity.location.take(120),
        "plateNumber" to entity.plateNumber.take(20),
        "vehicleDetails" to entity.vehicleDetails.take(240),
        "profileUrl" to entity.profileUrl.take(300),
        "consentGranted" to entity.consentGranted,
        "pointsCost" to entity.pointsCost,
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "users/$userId/leads/${entity.id}")
    }
  }

  private suspend fun writeTransactionToFirestore(userId: String, entity: TransactionEntity) {
    val db = firestore ?: return
    if (entity.amount == 0L) return
    val docRef =
      db
        .collection("users")
        .document(userId)
        .collection("transactions")
        .document(entity.id)
    val payload =
      hashMapOf(
        "userId" to userId,
        "type" to entity.type,
        "ownerAccountId" to entity.ownerAccountId,
        "loginId" to entity.loginId,
        "counterpartyId" to entity.counterpartyId.take(24),
        "amount" to entity.amount,
        "balanceAfter" to entity.balanceAfter,
        "note" to entity.note.ifBlank { "Transaction" }.take(140),
        "createdAt" to FieldValue.serverTimestamp(),
      )
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, "users/$userId/transactions/${entity.id}")
    }
  }
}
