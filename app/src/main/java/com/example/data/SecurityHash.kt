package com.example.data

import com.example.BuildConfig
import com.example.model.LinkedInCandidate
import com.example.model.LinkedInSearchQuery
import com.example.model.RtoOfficeInfo
import com.example.model.VehicleLookupResult
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object LeadXValidators {
  private val LOGIN_ID_REGEX = Regex("^[A-Z0-9._-]{3,24}$")
  private val PLATE_REGEX = Regex("^[A-Z]{2}[0-9]{1,2}[A-Z]{0,3}[0-9]{3,4}$")

  fun normalizeLoginId(raw: String): String = raw.trim().uppercase()

  fun isValidLoginId(loginId: String): Boolean = LOGIN_ID_REGEX.matches(loginId)

  fun isValidPassword(password: String): Boolean = password.trim().length >= 6

  fun normalizePlate(raw: String): String =
    raw.uppercase().replace(Regex("[^A-Z0-9]"), "")

  fun isValidPlate(plate: String): Boolean = PLATE_REGEX.matches(plate)

  fun hashPassword(password: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
  }
}

object IndianRtoRegistry {
  private val rtoMap: Map<String, RtoOfficeInfo> =
    mapOf(
      "MH01" to RtoOfficeInfo("MH01", "RTO Tardeo (Mumbai South)", "Mumbai", "Maharashtra"),
      "MH02" to RtoOfficeInfo("MH02", "RTO Andheri West (Mumbai West)", "Mumbai", "Maharashtra"),
      "MH03" to RtoOfficeInfo("MH03", "RTO Wadala (Mumbai East)", "Mumbai", "Maharashtra"),
      "MH04" to RtoOfficeInfo("MH04", "RTO Thane", "Thane", "Maharashtra"),
      "MH05" to RtoOfficeInfo("MH05", "RTO Kalyan", "Kalyan", "Maharashtra"),
      "MH09" to RtoOfficeInfo("MH09", "RTO Kolhapur", "Kolhapur", "Maharashtra"),
      "MH12" to RtoOfficeInfo("MH12", "RTO Pune", "Pune", "Maharashtra"),
      "MH14" to RtoOfficeInfo("MH14", "RTO Pimpri-Chinchwad", "Pune", "Maharashtra"),
      "MH15" to RtoOfficeInfo("MH15", "RTO Nashik", "Nashik", "Maharashtra"),
      "MH31" to RtoOfficeInfo("MH31", "RTO Nagpur City", "Nagpur", "Maharashtra"),
      "MH43" to RtoOfficeInfo("MH43", "RTO Vashi (Navi Mumbai)", "Navi Mumbai", "Maharashtra"),
      "MH47" to RtoOfficeInfo("MH47", "RTO Borivali (Mumbai North)", "Mumbai", "Maharashtra"),
      "DL01" to RtoOfficeInfo("DL01", "Mall Road Zonal Office (North Delhi)", "New Delhi", "Delhi"),
      "DL02" to RtoOfficeInfo("DL02", "Tilak Marg Zonal Office (New Delhi)", "New Delhi", "Delhi"),
      "DL03" to RtoOfficeInfo("DL03", "Sheikh Sarai Zonal Office (South Delhi)", "New Delhi", "Delhi"),
      "DL04" to RtoOfficeInfo("DL04", "Janakpuri Zonal Office (West Delhi)", "New Delhi", "Delhi"),
      "DL05" to RtoOfficeInfo("DL05", "Loni Road Zonal Office (North East)", "New Delhi", "Delhi"),
      "DL06" to RtoOfficeInfo("DL06", "Sarai Kale Khan Zonal Office (Central)", "New Delhi", "Delhi"),
      "DL07" to RtoOfficeInfo("DL07", "Mayur Vihar Zonal Office (East Delhi)", "New Delhi", "Delhi"),
      "DL08" to RtoOfficeInfo("DL08", "Wazirpur Zonal Office (North West)", "New Delhi", "Delhi"),
      "DL09" to RtoOfficeInfo("DL09", "Dwarka Zonal Office (South West)", "New Delhi", "Delhi"),
      "DL10" to RtoOfficeInfo("DL10", "Raja Garden Zonal Office", "New Delhi", "Delhi"),
      "KA01" to RtoOfficeInfo("KA01", "RTO Koramangala (Bengaluru Central)", "Bengaluru", "Karnataka"),
      "KA02" to RtoOfficeInfo("KA02", "RTO Rajajinagar (Bengaluru West)", "Bengaluru", "Karnataka"),
      "KA03" to RtoOfficeInfo("KA03", "RTO Indiranagar (Bengaluru East)", "Bengaluru", "Karnataka"),
      "KA04" to RtoOfficeInfo("KA04", "RTO Yeshwanthpur (Bengaluru North)", "Bengaluru", "Karnataka"),
      "KA05" to RtoOfficeInfo("KA05", "RTO Jayanagar (Bengaluru South)", "Bengaluru", "Karnataka"),
      "KA51" to RtoOfficeInfo("KA51", "RTO Electronic City", "Bengaluru", "Karnataka"),
      "KA53" to RtoOfficeInfo("KA53", "RTO K.R. Puram", "Bengaluru", "Karnataka"),
      "GJ01" to RtoOfficeInfo("GJ01", "RTO Subhash Bridge (Ahmedabad)", "Ahmedabad", "Gujarat"),
      "GJ03" to RtoOfficeInfo("GJ03", "RTO Rajkot", "Rajkot", "Gujarat"),
      "GJ05" to RtoOfficeInfo("GJ05", "RTO Surat", "Surat", "Gujarat"),
      "GJ06" to RtoOfficeInfo("GJ06", "RTO Vadodara", "Vadodara", "Gujarat"),
      "TN01" to RtoOfficeInfo("TN01", "RTO Chennai Central (Ayanavaram)", "Chennai", "Tamil Nadu"),
      "TN02" to RtoOfficeInfo("TN02", "RTO Chennai North West (Anna Nagar)", "Chennai", "Tamil Nadu"),
      "TN07" to RtoOfficeInfo("TN07", "RTO Chennai South (Thiruvanmiyur)", "Chennai", "Tamil Nadu"),
      "TN09" to RtoOfficeInfo("TN09", "RTO Chennai West (K.K. Nagar)", "Chennai", "Tamil Nadu"),
      "TN37" to RtoOfficeInfo("TN37", "RTO Coimbatore South", "Coimbatore", "Tamil Nadu"),
      "TS07" to RtoOfficeInfo("TS07", "RTO Kondapur (Rangareddy)", "Hyderabad", "Telangana"),
      "TS09" to RtoOfficeInfo("TS09", "RTO Khairatabad (Hyderabad Central)", "Hyderabad", "Telangana"),
      "UP14" to RtoOfficeInfo("UP14", "ARTO Ghaziabad", "Ghaziabad", "Uttar Pradesh"),
      "UP16" to RtoOfficeInfo("UP16", "ARTO Gautam Buddha Nagar (Noida)", "Noida", "Uttar Pradesh"),
      "UP32" to RtoOfficeInfo("UP32", "RTO Lucknow", "Lucknow", "Uttar Pradesh"),
      "HR26" to RtoOfficeInfo("HR26", "RLA Gurugram North", "Gurugram", "Haryana"),
      "HR51" to RtoOfficeInfo("HR51", "RLA Faridabad", "Faridabad", "Haryana"),
      "RJ14" to RtoOfficeInfo("RJ14", "RTO Jaipur", "Jaipur", "Rajasthan"),
      "WB01" to RtoOfficeInfo("WB01", "PVD Beltala (Kolkata Central)", "Kolkata", "West Bengal"),
      "WB02" to RtoOfficeInfo("WB02", "PVD Kolkata South", "Kolkata", "West Bengal"),
      "KL01" to RtoOfficeInfo("KL01", "RTO Thiruvananthapuram", "Thiruvananthapuram", "Kerala"),
      "KL07" to RtoOfficeInfo("KL07", "RTO Ernakulam (Kochi)", "Kochi", "Kerala"),
      "PB10" to RtoOfficeInfo("PB10", "RTA Ludhiana", "Ludhiana", "Punjab"),
      "CH01" to RtoOfficeInfo("CH01", "RLA Chandigarh", "Chandigarh", "Chandigarh"),
      "MP09" to RtoOfficeInfo("MP09", "RTO Indore", "Indore", "Madhya Pradesh"),
      "GA01" to RtoOfficeInfo("GA01", "RTO Panaji (North Goa)", "Panaji", "Goa"),
    )

  private val stateNames: Map<String, String> =
    mapOf(
      "AN" to "Andaman & Nicobar",
      "AP" to "Andhra Pradesh",
      "AR" to "Arunachal Pradesh",
      "AS" to "Assam",
      "BR" to "Bihar",
      "CG" to "Chhattisgarh",
      "CH" to "Chandigarh",
      "DD" to "Dadra & Nagar Haveli and Daman & Diu",
      "DL" to "Delhi",
      "GA" to "Goa",
      "GJ" to "Gujarat",
      "HP" to "Himachal Pradesh",
      "HR" to "Haryana",
      "JH" to "Jharkhand",
      "JK" to "Jammu & Kashmir",
      "KA" to "Karnataka",
      "KL" to "Kerala",
      "LA" to "Ladakh",
      "LD" to "Lakshadweep",
      "MH" to "Maharashtra",
      "ML" to "Meghalaya",
      "MN" to "Manipur",
      "MP" to "Madhya Pradesh",
      "MZ" to "Mizoram",
      "NL" to "Nagaland",
      "OD" to "Odisha",
      "PB" to "Punjab",
      "PY" to "Puducherry",
      "RJ" to "Rajasthan",
      "SK" to "Sikkim",
      "TN" to "Tamil Nadu",
      "TR" to "Tripura",
      "TS" to "Telangana",
      "UK" to "Uttarakhand",
      "UP" to "Uttar Pradesh",
      "WB" to "West Bengal",
    )

  fun resolve(plate: String): RtoOfficeInfo {
    val clean = LeadXValidators.normalizePlate(plate)
    val prefix4 = clean.take(4)
    rtoMap[prefix4]?.let { return it }
    val stateCode = clean.take(2)
    val state = stateNames[stateCode] ?: "India"
    return RtoOfficeInfo(
      rtoCode = prefix4,
      officeName = "RTO $prefix4",
      city = state,
      state = state,
    )
  }
}

object RealLeadExtractor {
  private const val PREFS_NAME = "leadx_runtime_secrets"
  private const val KEY_SUREPASS = "surepass_token"
  private const val KEY_PROXYCURL = "proxycurl_key"

  @Volatile private var runtimeSurepassToken: String = ""
  @Volatile private var runtimeProxycurlKey: String = ""

  private val httpClient: OkHttpClient by lazy {
    OkHttpClient.Builder()
      .connectTimeout(6, TimeUnit.SECONDS)
      .readTimeout(8, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  fun init(context: android.content.Context) {
    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
    runtimeSurepassToken = prefs.getString(KEY_SUREPASS, "").orEmpty().trim()
    runtimeProxycurlKey = prefs.getString(KEY_PROXYCURL, "").orEmpty().trim()
  }

  fun saveRuntimeKeys(context: android.content.Context, surepassToken: String, proxycurlKey: String) {
    val cleanSurepass = surepassToken.trim()
    val cleanProxycurl = proxycurlKey.trim()
    runtimeSurepassToken = cleanSurepass
    runtimeProxycurlKey = cleanProxycurl
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_SUREPASS, cleanSurepass)
      .putString(KEY_PROXYCURL, cleanProxycurl)
      .apply()
  }

  fun currentSurepassToken(): String {
    if (runtimeSurepassToken.isNotBlank() && runtimeSurepassToken != "MY_SUREPASS_API_TOKEN") {
      return runtimeSurepassToken
    }
    val built = BuildConfig.SUREPASS_API_TOKEN.trim()
    return if (built.isNotBlank() && built != "MY_SUREPASS_API_TOKEN") built else ""
  }

  fun currentProxycurlKey(): String {
    if (runtimeProxycurlKey.isNotBlank() && runtimeProxycurlKey != "MY_PROXYCURL_API_KEY") {
      return runtimeProxycurlKey
    }
    val built = BuildConfig.PROXYCURL_API_KEY.trim()
    return if (built.isNotBlank() && built != "MY_PROXYCURL_API_KEY") built else ""
  }

  fun isSurepassConfigured(): Boolean = currentSurepassToken().isNotEmpty()

  fun isProxycurlConfigured(): Boolean = currentProxycurlKey().isNotEmpty()

  /**
   * Fetches real LinkedIn profile details via:
   * 1) Official Proxycurl B2B API if PROXYCURL_API_KEY is configured in Secrets.
   * 2) Direct public LinkedIn HTTP OpenGraph/JSON-LD extraction.
   * 3) Live public search index lookup (DuckDuckGo HTML snippet of the exact LinkedIn URL).
   * Never generates synthetic emails, phone numbers, or fake people.
   */
  fun fetchRealLinkedInProfile(rawUrl: String): Result<LinkedInCandidate> {
    val trimmed = rawUrl.trim()
    if (!trimmed.contains("linkedin.com/", ignoreCase = true)) {
      return Result.failure(IllegalArgumentException("Enter a valid LinkedIn URL (e.g. https://www.linkedin.com/in/...)."))
    }
    val normalizedUrl =
      if (trimmed.startsWith("http", ignoreCase = true)) trimmed else "https://$trimmed"

    // 1. Proxycurl API (when user configures PROXYCURL_API_KEY in AI Studio Secrets)
    if (isProxycurlConfigured()) {
      val proxycurlResult = fetchFromProxycurl(normalizedUrl)
      if (proxycurlResult != null) return Result.success(proxycurlResult)
    }

    // 2. Direct Public LinkedIn OpenGraph / JSON-LD metadata
    val directCandidate = fetchDirectLinkedInHtml(normalizedUrl)
    if (directCandidate != null) return Result.success(directCandidate)

    // 3. Live DuckDuckGo HTML search lookup for the exact public LinkedIn profile
    val searchCandidate = fetchFromLiveWebIndex(normalizedUrl)
    if (searchCandidate != null) return Result.success(searchCandidate)

    return Result.failure(
      IllegalStateException(
        "Could not fetch live LinkedIn profile publicly (LinkedIn blocks anonymous bots). Tap 'Open LinkedIn & Capture DOM' below to sign in inside the browser and extract real visible leads."
      )
    )
  }

  fun searchB2BLeadsViaProxycurl(query: LinkedInSearchQuery): Result<List<LinkedInCandidate>> {
    val role = query.roleTitle.trim()
    val company = query.companyName.trim()
    val industry = query.industry.trim()
    val city = query.city.trim()
    if (role.isBlank() && company.isBlank() && industry.isBlank() && city.isBlank()) {
      return Result.failure(
        IllegalArgumentException("Enter at least one B2B search filter (Role Title, Company, Industry, or City).")
      )
    }
    val apiKey = currentProxycurlKey()
    if (apiKey.isBlank()) {
      return Result.failure(
        IllegalStateException("Proxycurl API Key required for B2B Person Search. Tap API Keys to add your PROXYCURL_API_KEY.")
      )
    }

    return try {
      val country = query.countryCode.trim().uppercase().ifBlank { "IN" }.take(2)
      val urlBuilder =
        okhttp3.HttpUrl.Builder()
          .scheme("https")
          .host("nubela.co")
          .addPathSegments("proxycurl/api/v2/search/person")
          .addQueryParameter("country", country)
          .addQueryParameter("enrich_profiles", "enrich")
          .addQueryParameter("page_size", query.pageSize.coerceIn(1, 20).toString())

      if (role.isNotBlank()) urlBuilder.addQueryParameter("current_role_title", role)
      if (company.isNotBlank()) urlBuilder.addQueryParameter("current_company_name", company)
      if (industry.isNotBlank()) urlBuilder.addQueryParameter("industries", industry)
      if (city.isNotBlank()) urlBuilder.addQueryParameter("city", city)

      val req =
        Request.Builder()
          .url(urlBuilder.build())
          .header("Authorization", "Bearer $apiKey")
          .get()
          .build()

      httpClient.newCall(req).execute().use { resp ->
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          val apiMsg =
            runCatching {
                val errJson = JSONObject(bodyStr)
                errJson.optString("description").ifBlank { errJson.optString("message") }
              }
              .getOrNull()
              .orEmpty()
              .ifBlank { "Proxycurl B2B Search error (HTTP ${resp.code})." }
          return Result.failure(IllegalStateException(apiMsg))
        }
        val candidates = parseProxycurlSearchResponse(bodyStr)
        if (candidates.isEmpty()) {
          Result.failure(
            IllegalStateException("No matching B2B LinkedIn profiles found for those filters.")
          )
        } else {
          Result.success(candidates)
        }
      }
    } catch (e: Exception) {
      Result.failure(
        IllegalStateException("Proxycurl B2B Search failed: ${e.message ?: "network error"}")
      )
    }
  }

  fun parseProxycurlSearchResponse(rawJson: String): List<LinkedInCandidate> {
    if (rawJson.isBlank()) return emptyList()
    return try {
      val root = JSONObject(rawJson)
      val results = root.optJSONArray("results") ?: return emptyList()
      buildList {
        for (i in 0 until results.length()) {
          val item = results.optJSONObject(i) ?: continue
          val profileUrl =
            item.optString("linkedin_profile_url").ifBlank { item.optString("url") }.trim()
          val profileJson = item.optJSONObject("profile")
          if (profileJson != null) {
            val parsed =
              parseProxycurlProfileJson(
                json = profileJson,
                profileUrl = profileUrl,
                captureMethod = "Proxycurl B2B Search",
              )
            if (parsed != null) {
              add(parsed)
              continue
            }
          }
          if (profileUrl.isNotBlank()) {
            val slugName =
              profileUrl
                .substringAfter("/in/", "")
                .substringBefore("?")
                .trimEnd('/')
                .split("-")
                .filter { it.isNotBlank() }
                .take(3)
                .joinToString(" ") { part ->
                  part.replaceFirstChar { ch -> ch.uppercase() }
                }
                .ifBlank { "LinkedIn Profile" }
            add(
              LinkedInCandidate(
                id = "li_search_${abs(profileUrl.hashCode())}",
                name = slugName,
                title = "B2B LinkedIn Match",
                company = "",
                email = "",
                phone = "",
                industry = "",
                location = "",
                profileUrl = profileUrl,
                captureMethod = "Proxycurl B2B Search",
                selected = true,
              )
            )
          }
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  private fun fetchFromProxycurl(profileUrl: String): LinkedInCandidate? {
    val apiKey = currentProxycurlKey()
    if (apiKey.isBlank()) return null
    return try {
      val encoded = URLEncoder.encode(profileUrl, "UTF-8")
      val req =
        Request.Builder()
          .url("https://nubela.co/proxycurl/api/v2/linkedin?url=$encoded&use_cache=if-present")
          .header("Authorization", "Bearer $apiKey")
          .get()
          .build()
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val body = resp.body?.string() ?: return null
        val json = JSONObject(body)
        parseProxycurlProfileJson(json, profileUrl, "Live Proxycurl API")
      }
    } catch (_: Exception) {
      null
    }
  }

  fun parseProxycurlProfileJson(
    json: JSONObject,
    profileUrl: String,
    captureMethod: String = "Live Proxycurl API",
  ): LinkedInCandidate? {
    val fullName =
      json.optString("full_name").trim().ifBlank {
        listOf(json.optString("first_name").trim(), json.optString("last_name").trim())
          .filter { it.isNotBlank() }
          .joinToString(" ")
      }
    if (fullName.isBlank()) return null
    val occupation = json.optString("occupation").ifBlank { json.optString("headline") }.trim()
    val exp = json.optJSONArray("experiences")?.optJSONObject(0)
    val company = exp?.optString("company").orEmpty().trim()
    val city =
      listOf(json.optString("city"), json.optString("state"), json.optString("country_full_name"))
        .filter { it.isNotBlank() }
        .joinToString(", ")
    val email =
      json.optJSONArray("personal_emails")?.optString(0).orEmpty()
        .ifBlank { json.optString("work_email") }
        .trim()
    val phone =
      json.optJSONArray("personal_numbers")?.optString(0).orEmpty().trim()
    val industry = json.optString("industry").trim()
    val bio =
      json.optString("summary").trim()
        .ifBlank { json.optString("headline").trim() }
        .ifBlank { occupation }
        .take(240)
    val resolvedUrl =
      profileUrl.ifBlank {
        val pubId = json.optString("public_identifier").trim()
        if (pubId.isNotBlank()) "https://www.linkedin.com/in/$pubId" else ""
      }
    return LinkedInCandidate(
      id = "li_real_${abs((resolvedUrl + fullName).hashCode())}",
      name = fullName,
      title = occupation.ifBlank { exp?.optString("title").orEmpty() },
      company = company,
      email = email,
      phone = phone,
      industry = industry,
      location = city,
      profileUrl = resolvedUrl,
      captureMethod = captureMethod,
      bio = bio,
      selected = true,
    )
  }

  private fun fetchDirectLinkedInHtml(profileUrl: String): LinkedInCandidate? {
    return try {
      val req =
        Request.Builder()
          .url(profileUrl)
          .header(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
          )
          .header("Accept-Language", "en-US,en;q=0.9")
          .get()
          .build()
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val html = resp.body?.string()?.take(65536) ?: return null
        val ogTitle = extractMetaContent(html, "og:title").ifBlank { extractHtmlTitle(html) }
        val ogDesc = extractMetaContent(html, "og:description").ifBlank { extractMetaContent(html, "description") }
        parseRealTitleAndSnippet(profileUrl, ogTitle, ogDesc, "Live LinkedIn Public Meta")
      }
    } catch (_: Exception) {
      null
    }
  }

  private fun fetchFromLiveWebIndex(profileUrl: String): LinkedInCandidate? {
    return try {
      val cleanPath = profileUrl.substringAfter("://").substringBefore("?").trimEnd('/')
      val encodedQuery = URLEncoder.encode(cleanPath, "UTF-8")
      val req =
        Request.Builder()
          .url("https://html.duckduckgo.com/html/?q=$encodedQuery")
          .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
          .get()
          .build()
      httpClient.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val html = resp.body?.string()?.take(98304) ?: return null
        val titleRegex =
          Regex("""<a[^>]+class="[^"]*result__a[^"]*"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val snippetRegex =
          Regex("""<a[^>]+class="[^"]*result__snippet[^"]*"[^>]*>(.*?)</a>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val firstTitle = stripHtml(titleRegex.find(html)?.groupValues?.getOrNull(1).orEmpty())
        val firstSnippet = stripHtml(snippetRegex.find(html)?.groupValues?.getOrNull(1).orEmpty())
        if (firstTitle.isBlank()) return null
        parseRealTitleAndSnippet(profileUrl, firstTitle, firstSnippet, "Live Web Search Index")
      }
    } catch (_: Exception) {
      null
    }
  }

  fun parseRealTitleAndSnippet(
    profileUrl: String,
    rawTitle: String,
    rawSnippet: String,
    captureMethod: String,
  ): LinkedInCandidate? {
    val cleanTitle =
      rawTitle
        .substringBefore("| LinkedIn")
        .substringBefore(" - LinkedIn")
        .trim()
    if (cleanTitle.isBlank() ||
      cleanTitle.equals("LinkedIn", ignoreCase = true) ||
      cleanTitle.contains("Sign In", ignoreCase = true) ||
      cleanTitle.contains("Sign Up", ignoreCase = true) ||
      cleanTitle.contains("Security Verification", ignoreCase = true)
    ) {
      return null
    }
    val parts = cleanTitle.split(" - ", " – ", " | ").map { it.trim() }.filter { it.isNotBlank() }
    val name = parts.firstOrNull() ?: return null
    val rolePart = parts.getOrNull(1).orEmpty()
    val companyPart = parts.getOrNull(2).orEmpty()

    val (resolvedTitle, resolvedCompany) =
      when {
        companyPart.isNotBlank() -> rolePart to companyPart
        rolePart.contains(" at ", ignoreCase = true) ->
          rolePart.substringBefore(" at ", rolePart).trim() to rolePart.substringAfter(" at ").trim()
        rolePart.contains(" @ ") ->
          rolePart.substringBefore(" @ ").trim() to rolePart.substringAfter(" @ ").trim()
        else -> rolePart to ""
      }

    val emailMatch = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").find(rawSnippet)?.value.orEmpty()
    val phoneMatch = Regex("""\+?91[\s-]?\d{5}[\s-]?\d{5}""").find(rawSnippet)?.value.orEmpty()
    val locationMatch =
      Regex("""(?:Location|Based in)[:\s]+([A-Za-z ,]+)""", RegexOption.IGNORE_CASE)
        .find(rawSnippet)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        .orEmpty()

    return LinkedInCandidate(
      id = "li_live_${abs((profileUrl + name).hashCode())}",
      name = name,
      title = resolvedTitle.ifBlank { rawSnippet.take(100).ifBlank { "LinkedIn Profile" } },
      company = resolvedCompany,
      email = emailMatch,
      phone = phoneMatch,
      industry = "",
      location = locationMatch,
      profileUrl = profileUrl,
      captureMethod = captureMethod,
      bio = rawSnippet.trim().take(240),
      selected = true,
    )
  }

  /** Parses real JSON array extracted from either in-app WebView DOM or Sales Navigator scraper. */
  fun parseWebViewDomJson(rawJson: String): List<LinkedInCandidate> {
    if (rawJson.isBlank()) return emptyList()
    return try {
      val array = JSONArray(rawJson)
      buildList {
        for (i in 0 until array.length()) {
          val obj = array.optJSONObject(i) ?: continue
          val name = obj.optString("name").ifBlank { obj.optString("fullName") }.trim()
          if (name.isBlank() || name.equals("LinkedIn Member", ignoreCase = true)) continue
          val url = obj.optString("profileUrl").ifBlank { obj.optString("url") }.trim()
          var title = obj.optString("title").ifBlank { obj.optString("designation") }.trim()
          var company = obj.optString("company").trim()
          if (company.isBlank() && title.contains(" at ")) {
            company = title.substringAfter(" at ").trim()
            title = title.substringBefore(" at ").trim()
          }
          val location = obj.optString("location").trim()
          val email = obj.optString("email").trim()
          val phone = obj.optString("phone").trim()
          val bio =
            obj.optString("bio").ifBlank { obj.optString("summary") }.trim().take(240)
          add(
            LinkedInCandidate(
              id = "li_dom_${abs((url + name).hashCode())}",
              name = name,
              title = title.ifBlank { "LinkedIn Profile" },
              company = company,
              email = email,
              phone = phone,
              industry = obj.optString("industry").trim(),
              location = location,
              profileUrl = url,
              captureMethod = "Live WebView DOM",
              bio = bio,
              selected = true,
            )
          )
        }
      }
    } catch (_: Exception) {
      emptyList()
    }
  }

  /**
   * Resolves authentic Indian RTO jurisdiction + calls official Surepass Vahan RC API
   * when SUREPASS_API_TOKEN is provided in AI Studio Secrets.
   * Zero synthetic vehicle models or fake owners are ever returned.
   */
  fun fetchRealVahanRc(plate: String): Result<VehicleLookupResult> {
    val cleanPlate = LeadXValidators.normalizePlate(plate)
    if (!LeadXValidators.isValidPlate(cleanPlate)) {
      return Result.failure(IllegalArgumentException("Enter a valid Indian Vehicle Number (e.g. MH02AB1234)."))
    }
    val rto = IndianRtoRegistry.resolve(cleanPlate)

    val surepassToken = currentSurepassToken()
    if (surepassToken.isBlank()) {
      return Result.success(
        VehicleLookupResult(
          plateNumber = cleanPlate,
          isLiveRcData = false,
          manufacturer = "",
          vehicleModel = "",
          vehicleClass = "",
          fuelType = "",
          registrationYear = "",
          registrationDate = "",
          insuranceValidTill = "",
          pucValidTill = "",
          fitnessValidTill = "",
          rtoOffice = rto.officeName,
          registeredCity = rto.city,
          registeredState = rto.state,
          ownerName = "",
          ownerContact = "",
          consentGranted = false,
          statusMessage =
            "Verified Real RTO (${rto.officeName}). Live RC chassis/insurance/owner data requires a Surepass API Token (tap API Keys) — no fake data generated, 0 pts deducted.",
        )
      )
    }

    return try {
      val payload = JSONObject().put("id_number", cleanPlate).toString()
      val req =
        Request.Builder()
          .url("https://kyc-api.surepass.io/api/v1/rc/rc-full")
          .header("Authorization", "Bearer $surepassToken")
          .header("Content-Type", "application/json")
          .post(payload.toRequestBody("application/json".toMediaType()))
          .build()
      httpClient.newCall(req).execute().use { resp ->
        val bodyStr = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
          val msg =
            runCatching { JSONObject(bodyStr).optString("message") }.getOrNull().orEmpty()
              .ifBlank { "Vahan RC API error (HTTP ${resp.code})." }
          return Result.failure(IllegalStateException(msg))
        }
        val root = JSONObject(bodyStr)
        if (!root.optBoolean("success", false)) {
          return Result.failure(
            IllegalStateException(root.optString("message", "Vehicle record not found in Vahan."))
          )
        }
        val data = root.optJSONObject("data") ?: return Result.failure(IllegalStateException("Empty Vahan payload."))
        val regDate = data.optString("registration_date").trim()
        val regYear = regDate.take(4).ifBlank { regDate.takeLast(4) }
        Result.success(
          VehicleLookupResult(
            plateNumber = cleanPlate,
            isLiveRcData = true,
            manufacturer = data.optString("maker_description").trim(),
            vehicleModel = data.optString("maker_model").trim(),
            vehicleClass = data.optString("vehicle_category").trim(),
            fuelType = data.optString("fuel_type").trim(),
            registrationYear = regYear,
            registrationDate = regDate,
            insuranceValidTill = data.optString("insurance_upto").trim(),
            pucValidTill = data.optString("pucc_upto").trim(),
            fitnessValidTill = data.optString("fit_up_to").trim(),
            rtoOffice = data.optString("registered_at").ifBlank { rto.officeName }.trim(),
            registeredCity = rto.city,
            registeredState = rto.state,
            ownerName = data.optString("owner_name").trim(),
            ownerContact = data.optString("mobile_number").trim(),
            consentGranted = false,
            statusMessage = "Live Official Vahan RC Verified",
          )
        )
      }
    } catch (e: Exception) {
      Result.failure(IllegalStateException("Vahan API request failed: ${e.message ?: "network error"}"))
    }
  }

  private fun extractMetaContent(html: String, key: String): String {
    val regex =
      Regex("""<meta[^>]+(?:property|name)=["']$key["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    return stripHtml(regex.find(html)?.groupValues?.getOrNull(1).orEmpty())
  }

  private fun extractHtmlTitle(html: String): String {
    val regex = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    return stripHtml(regex.find(html)?.groupValues?.getOrNull(1).orEmpty())
  }

  private fun stripHtml(raw: String): String =
    raw
      .replace(Regex("<[^>]+>"), " ")
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&#39;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace(Regex("\\s+"), " ")
      .trim()
}
