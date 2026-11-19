package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.example.data.LeadXValidators
import com.example.model.AccountRole
import com.example.model.LeadRecord
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.PointTransaction
import com.example.model.TeamAccount
import com.example.model.TxType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "team_accounts")
data class TeamAccountEntity(
  @PrimaryKey val loginId: String,
  val fullName: String,
  val role: String,
  val ownerAccountId: String,
  val passwordHash: String,
  val balance: Long,
  val createdAtMillis: Long,
) {
  fun toDomain(userId: String = ""): TeamAccount =
    TeamAccount(
      id = loginId,
      userId = userId,
      loginId = loginId,
      fullName = fullName,
      role = role,
      ownerAccountId = ownerAccountId,
      balance = balance,
      createdAtMillis = createdAtMillis,
    )
}

@Entity(tableName = "leads")
data class LeadEntity(
  @PrimaryKey val id: String,
  val source: String,
  val ownerAccountId: String,
  val fetchedByLoginId: String,
  val name: String,
  val title: String,
  val company: String,
  val email: String,
  val phone: String,
  val industry: String,
  val location: String,
  val plateNumber: String,
  val vehicleDetails: String,
  val profileUrl: String,
  val consentGranted: Boolean,
  val pointsCost: Long,
  val createdAtMillis: Long,
) {
  fun toDomain(userId: String = ""): LeadRecord =
    LeadRecord(
      id = id,
      userId = userId,
      source = source,
      ownerAccountId = ownerAccountId,
      fetchedByLoginId = fetchedByLoginId,
      name = name,
      title = title,
      company = company,
      email = email,
      phone = phone,
      industry = industry,
      location = location,
      plateNumber = plateNumber,
      vehicleDetails = vehicleDetails,
      profileUrl = profileUrl,
      consentGranted = consentGranted,
      pointsCost = pointsCost,
      createdAtMillis = createdAtMillis,
    )
}

@Entity(tableName = "transactions")
data class TransactionEntity(
  @PrimaryKey val id: String,
  val type: String,
  val ownerAccountId: String,
  val loginId: String,
  val counterpartyId: String,
  val amount: Long,
  val balanceAfter: Long,
  val note: String,
  val createdAtMillis: Long,
) {
  fun toDomain(userId: String = ""): PointTransaction =
    PointTransaction(
      id = id,
      userId = userId,
      type = type,
      ownerAccountId = ownerAccountId,
      loginId = loginId,
      counterpartyId = counterpartyId,
      amount = amount,
      balanceAfter = balanceAfter,
      note = note,
      createdAtMillis = createdAtMillis,
    )
}

@Dao
interface LeadXDao {
  @Query("SELECT * FROM team_accounts ORDER BY CASE WHEN role = 'owner' THEN 0 ELSE 1 END, loginId ASC")
  fun observeAccounts(): Flow<List<TeamAccountEntity>>

  @Query("SELECT * FROM leads ORDER BY createdAtMillis DESC")
  fun observeLeads(): Flow<List<LeadEntity>>

  @Query("SELECT * FROM transactions ORDER BY createdAtMillis DESC")
  fun observeTransactions(): Flow<List<TransactionEntity>>

  @Query("SELECT COUNT(*) FROM team_accounts")
  suspend fun accountCount(): Int

  @Query("SELECT * FROM team_accounts WHERE loginId = :loginId LIMIT 1")
  suspend fun getAccount(loginId: String): TeamAccountEntity?

  @Query("SELECT balance FROM team_accounts WHERE loginId = :loginId LIMIT 1")
  suspend fun getPointBalance(loginId: String): Long?

  @Insert(onConflict = OnConflictStrategy.ABORT)
  suspend fun insertAccount(account: TeamAccountEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAccounts(accounts: List<TeamAccountEntity>)

  @Query("UPDATE team_accounts SET balance = :balance WHERE loginId = :loginId")
  suspend fun updateBalance(loginId: String, balance: Long)

  @Query(
    """
    UPDATE leads 
    SET name = :ownerDisplayName, phone = :phone, email = :email, consentGranted = 1, vehicleDetails = :details
    WHERE id = :leadId
    """
  )
  suspend fun updateLeadConsentDetails(
    leadId: String,
    ownerDisplayName: String,
    phone: String,
    email: String,
    details: String,
  )

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLead(lead: LeadEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertLeads(leads: List<LeadEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTransaction(tx: TransactionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertTransactions(txs: List<TransactionEntity>)

  @Query("DELETE FROM team_accounts")
  suspend fun clearAccounts()

  @Query("DELETE FROM leads")
  suspend fun clearLeads()

  @Query("DELETE FROM transactions")
  suspend fun clearTransactions()

  /**
   * Initializes role-based accounts (Owner + Employee with minimum 5,000 pts reserve).
   * Intentionally seeds ZERO fake leads—only real captured leads are ever stored.
   */
  @Transaction
  suspend fun seedInitialAccountsIfEmpty() {
    if (accountCount() > 0) return
    val now = System.currentTimeMillis()
    upsertAccounts(
      listOf(
        TeamAccountEntity(
          loginId = "OWN1001",
          fullName = "Workspace Owner",
          role = AccountRole.OWNER.wireValue,
          ownerAccountId = "OWN1001",
          passwordHash = LeadXValidators.hashPassword("Owner@123"),
          balance = 12500L,
          createdAtMillis = now - 20000L,
        ),
        TeamAccountEntity(
          loginId = "EMP001",
          fullName = "Field Executive",
          role = AccountRole.EMPLOYEE.wireValue,
          ownerAccountId = "OWN1001",
          passwordHash = LeadXValidators.hashPassword("Emp@123"),
          balance = 5000L,
          createdAtMillis = now - 10000L,
        ),
      )
    )
    insertTransactions(
      listOf(
        TransactionEntity(
          id = "tx_init_owner",
          type = TxType.TOPUP.wireValue,
          ownerAccountId = "OWN1001",
          loginId = "OWN1001",
          counterpartyId = "",
          amount = 12500L,
          balanceAfter = 12500L,
          note = "Owner Opening Reserve (₹12,500)",
          createdAtMillis = now - 20000L,
        ),
        TransactionEntity(
          id = "tx_init_emp",
          type = TxType.TOPUP.wireValue,
          ownerAccountId = "OWN1001",
          loginId = "EMP001",
          counterpartyId = "",
          amount = 5000L,
          balanceAfter = 5000L,
          note = "Employee Minimum Reserve (₹5,000)",
          createdAtMillis = now - 10000L,
        ),
      )
    )
  }

  /**
   * Enforces the strict 5,000-point minimum reserve rule for lead generation actions.
   */
  suspend fun assertMinBalanceForLeadSpend(accountId: String, cost: Long): TeamAccountEntity {
    val account = getAccount(accountId) ?: error("Active account $accountId not found.")
    check(account.balance >= MIN_WALLET_BALANCE && account.balance - cost >= MIN_WALLET_BALANCE) {
      "Minimum 5000 balance required, please recharge"
    }
    return account
  }

  @Transaction
  suspend fun createAccountWithStartingReserve(
    account: TeamAccountEntity,
    initTx: TransactionEntity,
  ) {
    insertAccount(account)
    insertTransaction(initTx)
  }

  @Transaction
  suspend fun resetWorkspace() {
    clearAccounts()
    clearLeads()
    clearTransactions()
    seedInitialAccountsIfEmpty()
  }

  @Transaction
  suspend fun executeTopUp(accountId: String, amount: Long, txId: String): Long {
    val account = getAccount(accountId) ?: error("Account $accountId not found.")
    val updated = account.balance + amount
    check(updated >= MIN_WALLET_BALANCE) {
      "Minimum 5000 balance required, please recharge at least ${MIN_WALLET_BALANCE - account.balance} pts."
    }
    updateBalance(accountId, updated)
    insertTransaction(
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
      )
    )
    return updated
  }

  @Transaction
  suspend fun executeSharePoints(
    senderId: String,
    recipientId: String,
    amount: Long,
    outTxId: String,
    inTxId: String,
  ) {
    val sender = getAccount(senderId) ?: error("Sender account not found.")
    val recipient = getAccount(recipientId) ?: error("Target ID $recipientId not found.")
    check(sender.ownerAccountId == recipient.ownerAccountId) {
      "Points can only be shared within the same Owner team (${sender.ownerAccountId})."
    }
    val senderAfter = sender.balance - amount
    check(senderAfter >= MIN_WALLET_BALANCE) {
      "Minimum 5000 balance required, please recharge. Max shareable: ${(sender.balance - MIN_WALLET_BALANCE).coerceAtLeast(0L)} pts."
    }
    val recipientAfter = recipient.balance + amount
    val now = System.currentTimeMillis()
    updateBalance(senderId, senderAfter)
    updateBalance(recipientId, recipientAfter)
    insertTransactions(
      listOf(
        TransactionEntity(
          id = outTxId,
          type = TxType.TRANSFER_OUT.wireValue,
          ownerAccountId = sender.ownerAccountId,
          loginId = senderId,
          counterpartyId = recipientId,
          amount = -amount,
          balanceAfter = senderAfter,
          note = "ID Share to $recipientId",
          createdAtMillis = now,
        ),
        TransactionEntity(
          id = inTxId,
          type = TxType.TRANSFER_IN.wireValue,
          ownerAccountId = recipient.ownerAccountId,
          loginId = recipientId,
          counterpartyId = senderId,
          amount = amount,
          balanceAfter = recipientAfter,
          note = "ID Share from $senderId",
          createdAtMillis = now + 1L,
        ),
      )
    )
  }

  @Transaction
  suspend fun executeLinkedInCapture(
    accountId: String,
    totalCost: Long,
    leads: List<LeadEntity>,
    txId: String,
  ) {
    val account = assertMinBalanceForLeadSpend(accountId, totalCost)
    val balanceAfter = account.balance - totalCost
    updateBalance(accountId, balanceAfter)
    insertLeads(leads)
    insertTransaction(
      TransactionEntity(
        id = txId,
        type = TxType.LEAD_LINKEDIN.wireValue,
        ownerAccountId = account.ownerAccountId,
        loginId = accountId,
        counterpartyId = "",
        amount = -totalCost,
        balanceAfter = balanceAfter,
        note = "LinkedIn Capture (${leads.size} lead${if (leads.size > 1) "s" else ""})",
        createdAtMillis = System.currentTimeMillis(),
      )
    )
  }

  @Transaction
  suspend fun executeVehicleLookup(
    accountId: String,
    cost: Long,
    lead: LeadEntity,
    txId: String,
  ) {
    val account = assertMinBalanceForLeadSpend(accountId, cost)
    val balanceAfter = account.balance - cost
    updateBalance(accountId, balanceAfter)
    insertLead(lead)
    insertTransaction(
      TransactionEntity(
        id = txId,
        type = TxType.LEAD_VEHICLE.wireValue,
        ownerAccountId = account.ownerAccountId,
        loginId = accountId,
        counterpartyId = "",
        amount = -cost,
        balanceAfter = balanceAfter,
        note = "Vehicle RC Check (${lead.plateNumber})",
        createdAtMillis = System.currentTimeMillis(),
      )
    )
  }
}

@Database(
  entities = [TeamAccountEntity::class, LeadEntity::class, TransactionEntity::class],
  version = 3,
  exportSchema = false,
)
abstract class LeadXDatabase : RoomDatabase() {
  abstract fun leadXDao(): LeadXDao

  companion object {
    @Volatile private var INSTANCE: LeadXDatabase? = null

    fun getInstance(context: Context): LeadXDatabase {
      return INSTANCE
        ?: synchronized(this) {
          INSTANCE
            ?: Room.databaseBuilder(
                context.applicationContext,
                LeadXDatabase::class.java,
                "leadx_pro.db",
              )
              .fallbackToDestructiveMigration(dropAllTables = true)
              .build()
              .also { INSTANCE = it }
        }
    }
  }
}
