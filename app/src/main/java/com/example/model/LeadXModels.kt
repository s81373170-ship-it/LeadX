package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val MIN_WALLET_BALANCE = 5000L
const val MIN_SINGLE_TOPUP = 100L
const val LINKEDIN_COST_PER_LEAD = 10L
const val VEHICLE_CHECK_COST = 15L

enum class AccountRole(val wireValue: String) {
  OWNER("owner"),
  EMPLOYEE("employee");

  companion object {
    fun fromWire(raw: String?): AccountRole =
      entries.firstOrNull { it.wireValue == raw } ?: EMPLOYEE
  }
}

enum class LeadSource(val wireValue: String, val label: String) {
  LINKEDIN("linkedin", "LINKEDIN"),
  NUMBER_PLATE("numberplate", "NUMBER PLATE");

  companion object {
    fun fromWire(raw: String?): LeadSource =
      entries.firstOrNull { it.wireValue == raw } ?: LINKEDIN
  }
}

enum class TxType(val wireValue: String) {
  TOPUP("topup"),
  TRANSFER_IN("transfer_in"),
  TRANSFER_OUT("transfer_out"),
  LEAD_LINKEDIN("lead_linkedin"),
  LEAD_VEHICLE("lead_vehicle");

  companion object {
    fun fromWire(raw: String?): TxType =
      entries.firstOrNull { it.wireValue == raw } ?: TOPUP
  }
}

@Keep
@IgnoreExtraProperties
data class FirestoreUserRoleProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val role: String = AccountRole.OWNER.wireValue,
  val activeLoginId: String = "",
  val fullName: String = "",
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class TeamAccount(
  @DocumentId val id: String = "",
  val userId: String = "",
  val loginId: String = "",
  val fullName: String = "",
  val role: String = AccountRole.EMPLOYEE.wireValue,
  val ownerAccountId: String = "",
  val balance: Long = MIN_WALLET_BALANCE,
  val createdAtMillis: Long = System.currentTimeMillis(),
) {
  fun roleEnum(): AccountRole = AccountRole.fromWire(role)
  fun isOwnerRole(): Boolean = roleEnum() == AccountRole.OWNER
  fun meetsMinBalance(): Boolean = balance >= MIN_WALLET_BALANCE
  fun usablePoints(): Long = (balance - MIN_WALLET_BALANCE).coerceAtLeast(0L)
}

@Keep
@IgnoreExtraProperties
data class LeadRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val source: String = LeadSource.LINKEDIN.wireValue,
  val ownerAccountId: String = "",
  val fetchedByLoginId: String = "",
  val name: String = "",
  val title: String = "",
  val company: String = "",
  val email: String = "",
  val phone: String = "",
  val industry: String = "",
  val location: String = "",
  val plateNumber: String = "",
  val vehicleDetails: String = "",
  val profileUrl: String = "",
  val consentGranted: Boolean = false,
  val pointsCost: Long = 0L,
  val createdAtMillis: Long = System.currentTimeMillis(),
) {
  fun sourceEnum(): LeadSource = LeadSource.fromWire(source)
}

@Keep
@IgnoreExtraProperties
data class PointTransaction(
  @DocumentId val id: String = "",
  val userId: String = "",
  val type: String = TxType.TOPUP.wireValue,
  val ownerAccountId: String = "",
  val loginId: String = "",
  val counterpartyId: String = "",
  val amount: Long = 0L,
  val balanceAfter: Long = 0L,
  val note: String = "",
  val createdAtMillis: Long = System.currentTimeMillis(),
) {
  fun typeEnum(): TxType = TxType.fromWire(type)
}

data class LinkedInCandidate(
  val id: String,
  val name: String,
  val title: String,
  val company: String,
  val email: String,
  val phone: String,
  val industry: String,
  val location: String,
  val profileUrl: String,
  val captureMethod: String,
  val bio: String = "",
  val selected: Boolean = true,
)

data class LinkedInSearchQuery(
  val roleTitle: String = "",
  val companyName: String = "",
  val industry: String = "",
  val city: String = "",
  val countryCode: String = "IN",
  val pageSize: Int = 10,
)

enum class AutoFetchInterval(val hours: Long, val label: String) {
  DAILY(24L, "Daily (24h)"),
  TWICE_DAILY(12L, "Every 12h"),
  EVERY_6_HOURS(6L, "Every 6h");

  companion object {
    fun fromHours(hours: Long): AutoFetchInterval =
      entries.firstOrNull { it.hours == hours } ?: DAILY
  }
}

data class AutoFetchConfig(
  val enabled: Boolean = false,
  val intervalHours: Long = AutoFetchInterval.DAILY.hours,
  val maxLeadsPerRun: Int = 5,
  val autoSaveToWallet: Boolean = true,
  val roleTitle: String = "",
  val companyName: String = "",
  val industry: String = "",
  val city: String = "",
  val countryCode: String = "IN",
  val profileUrlsText: String = "",
  val activeLoginId: String = "",
  val ownerAccountId: String = "",
  val lastRunAtMillis: Long = 0L,
  val lastRunStatus: String = "Not run yet",
  val lastFetchedCount: Int = 0,
  val totalAutoFetchedCount: Int = 0,
) {
  fun parsedProfileUrls(): List<String> =
    profileUrlsText
      .split('\n', ',', ';')
      .map { it.trim() }
      .filter { it.contains("linkedin.com/", ignoreCase = true) }
      .distinct()

  fun hasSearchFilters(): Boolean =
    roleTitle.isNotBlank() ||
      companyName.isNotBlank() ||
      industry.isNotBlank() ||
      city.isNotBlank()

  fun hasValidTarget(): Boolean = hasSearchFilters() || parsedProfileUrls().isNotEmpty()
}

data class AutoFetchRunLog(
  val id: String,
  val timestampMillis: Long,
  val fetchedCount: Int,
  val savedCount: Int,
  val statusMessage: String,
  val isError: Boolean = false,
)

data class RtoOfficeInfo(
  val rtoCode: String,
  val officeName: String,
  val city: String,
  val state: String,
)

data class VehicleLookupResult(
  val savedLeadId: String = "",
  val plateNumber: String,
  val isLiveRcData: Boolean,
  val manufacturer: String,
  val vehicleModel: String,
  val vehicleClass: String,
  val fuelType: String,
  val registrationYear: String,
  val registrationDate: String,
  val insuranceValidTill: String,
  val pucValidTill: String,
  val fitnessValidTill: String,
  val rtoOffice: String,
  val registeredCity: String,
  val registeredState: String,
  val ownerName: String,
  val ownerContact: String,
  val consentGranted: Boolean = false,
  val statusMessage: String = "",
)

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

data class FlashBanner(
  val message: String,
  val isError: Boolean = false,
)

enum class AppTab {
  DASHBOARD,
  FETCH,
  TEAM,
}
