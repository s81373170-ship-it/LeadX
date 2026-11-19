package com.example

import com.example.data.IndianRtoRegistry
import com.example.data.LeadXValidators
import com.example.data.RealLeadExtractor
import com.example.model.AccountRole
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.TeamAccount
import com.example.model.VEHICLE_CHECK_COST
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LeadXDomainTest {

  @Test
  fun minimumBalanceReserve_enforcedAt5000Points() {
    val owner =
      TeamAccount(
        loginId = "OWN1001",
        role = AccountRole.OWNER.wireValue,
        balance = 12500L,
      )
    assertTrue(owner.meetsMinBalance())
    assertEquals(7500L, owner.usablePoints())

    val belowMin =
      TeamAccount(
        loginId = "EMP002",
        role = AccountRole.EMPLOYEE.wireValue,
        balance = 4999L,
      )
    assertFalse(belowMin.meetsMinBalance())
    assertEquals(0L, belowMin.usablePoints())
    assertEquals(5000L, MIN_WALLET_BALANCE)
  }

  @Test
  fun validators_acceptValidLoginIdsPasswordsAndPlates() {
    assertTrue(LeadXValidators.isValidLoginId(LeadXValidators.normalizeLoginId(" own1001 ")))
    assertTrue(LeadXValidators.isValidLoginId("EMP_01"))
    assertFalse(LeadXValidators.isValidLoginId("AB"))

    assertTrue(LeadXValidators.isValidPassword("Owner@123"))
    assertFalse(LeadXValidators.isValidPassword("123"))
    assertEquals(
      LeadXValidators.hashPassword("Owner@123"),
      LeadXValidators.hashPassword("Owner@123"),
    )
    assertNotEquals(
      LeadXValidators.hashPassword("Owner@123"),
      LeadXValidators.hashPassword("Emp@123"),
    )

    val normalizedPlate = LeadXValidators.normalizePlate("mh-02 ab 1234")
    assertEquals("MH02AB1234", normalizedPlate)
    assertTrue(LeadXValidators.isValidPlate(normalizedPlate))
    assertFalse(LeadXValidators.isValidPlate("INVALID"))
  }

  @Test
  fun realLeadExtractor_parsesRealTitleSnippetAndWebViewDomWithoutFakeData() {
    val parsed =
      RealLeadExtractor.parseRealTitleAndSnippet(
        profileUrl = "https://www.linkedin.com/in/satya-nadella",
        rawTitle = "Satya Nadella - Chairman and CEO - Microsoft | LinkedIn",
        rawSnippet = "Location: Redmond, Washington. Contact: press@microsoft.com",
        captureMethod = "Live LinkedIn Public Meta",
      )
    assertNotNull(parsed)
    assertEquals("Satya Nadella", parsed?.name)
    assertEquals("Chairman and CEO", parsed?.title)
    assertEquals("Microsoft", parsed?.company)
    assertEquals("press@microsoft.com", parsed?.email)
    assertEquals("", parsed?.phone) // Never fabricates phone numbers

    // Rejects login/generic LinkedIn pages
    assertNull(
      RealLeadExtractor.parseRealTitleAndSnippet(
        profileUrl = "https://www.linkedin.com/login",
        rawTitle = "LinkedIn Login, Sign In | LinkedIn",
        rawSnippet = "",
        captureMethod = "Live",
      )
    )

    // Parses multiple visible DOM cards captured from in-app LinkedIn WebView or Sales Navigator
    val domJson =
      """
      [
        {
          "name": "Nandan Nilekani",
          "profileUrl": "https://www.linkedin.com/in/nandan-nilekani",
          "title": "Co-founder & Chairman",
          "company": "Infosys",
          "location": "Bengaluru, India",
          "email": "",
          "phone": ""
        },
        {
          "fullName": "Arundhati Bhattacharya",
          "url": "https://www.linkedin.com/sales/lead/ACwAAA123",
          "designation": "Chairperson & CEO at Salesforce India",
          "location": "Mumbai, India"
        }
      ]
      """.trimIndent()
    val domCandidates = RealLeadExtractor.parseWebViewDomJson(domJson)
    assertEquals(2, domCandidates.size)
    assertEquals("Nandan Nilekani", domCandidates[0].name)
    assertEquals("Live WebView DOM", domCandidates[0].captureMethod)
    assertEquals("", domCandidates[0].phone)
    assertEquals("Arundhati Bhattacharya", domCandidates[1].name)
    assertEquals("Chairperson & CEO", domCandidates[1].title)
    assertEquals("Salesforce India", domCandidates[1].company)
    assertEquals("https://www.linkedin.com/sales/lead/ACwAAA123", domCandidates[1].profileUrl)
  }

  @Test
  fun indianRtoRegistry_andVahanLookup_returnRealRtoWithoutSyntheticModels() {
    val rto = IndianRtoRegistry.resolve("MH02AB1234")
    assertEquals("MH02", rto.rtoCode)
    assertEquals("RTO Andheri West (Mumbai West)", rto.officeName)
    assertEquals("Mumbai", rto.city)
    assertEquals("Maharashtra", rto.state)

    val stateFallback = IndianRtoRegistry.resolve("GJ99XY1111")
    assertEquals("Gujarat", stateFallback.state)

    val result = RealLeadExtractor.fetchRealVahanRc("MH02AB1234").getOrThrow()
    assertEquals("MH02AB1234", result.plateNumber)
    assertEquals(15L, VEHICLE_CHECK_COST)
    assertEquals("RTO Andheri West (Mumbai West)", result.rtoOffice)
    assertEquals("Mumbai", result.registeredCity)
    // Without SUREPASS_API_TOKEN, does not invent synthetic vehicle model or fake owner
    if (!RealLeadExtractor.isSurepassConfigured()) {
      assertFalse(result.isLiveRcData)
      assertEquals("", result.manufacturer)
      assertEquals("", result.ownerName)
      assertEquals("", result.ownerContact)
    }
  }

  @Test
  fun csvExporter_formatsRealLeadsSafely() {
    val lead =
      com.example.model.LeadRecord(
        id = "lead_1",
        ownerAccountId = "OWN1001",
        fetchedByLoginId = "EMP001",
        source = "LINKEDIN",
        name = "Arundhati Bhattacharya",
        title = "Chairperson & \"CEO\"",
        company = "Salesforce, India",
        location = "Mumbai, India",
        profileUrl = "https://www.linkedin.com/in/example",
        pointsCost = 10L,
      )
    val csv = com.example.data.CsvExporter.buildLeadsCsv(listOf(lead))
    assertTrue(csv.startsWith("Source,Name,Title,Company,Email,Phone,Industry,Location"))
    assertTrue(csv.contains("\"Salesforce, India\""))
    assertTrue(csv.contains("\"Chairperson & \"\"CEO\"\"\""))
    assertTrue(csv.contains("\"Arundhati Bhattacharya\""))
  }

  @Test
  fun proxycurlB2BSearch_parsesStructuredResultsAndValidatesFilters() {
    val searchPayload =
      """
      {
        "results": [
          {
            "linkedin_profile_url": "https://www.linkedin.com/in/priya-sharma-sales",
            "profile": {
              "full_name": "Priya Sharma",
              "headline": "VP of Enterprise Sales",
              "industry": "Information Technology and Services",
              "city": "Bengaluru",
              "state": "Karnataka",
              "country_full_name": "India",
              "work_email": "priya.sharma@acmecloud.in",
              "personal_numbers": ["+91 98765 43210"],
              "experiences": [
                { "company": "Acme Cloud India", "title": "VP Sales" }
              ]
            }
          },
          {
            "linkedin_profile_url": "https://www.linkedin.com/in/rohan-mehta"
          }
        ]
      }
      """.trimIndent()

    val parsed = RealLeadExtractor.parseProxycurlSearchResponse(searchPayload)
    assertEquals(2, parsed.size)

    val first = parsed[0]
    assertEquals("Priya Sharma", first.name)
    assertEquals("VP of Enterprise Sales", first.title)
    assertEquals("Acme Cloud India", first.company)
    assertEquals("Information Technology and Services", first.industry)
    assertEquals("Bengaluru, Karnataka, India", first.location)
    assertEquals("priya.sharma@acmecloud.in", first.email)
    assertEquals("+91 98765 43210", first.phone)
    assertEquals("https://www.linkedin.com/in/priya-sharma-sales", first.profileUrl)
    assertEquals("Proxycurl B2B Search", first.captureMethod)
    assertTrue(first.selected)

    val second = parsed[1]
    assertEquals("Rohan Mehta", second.name)
    assertEquals("https://www.linkedin.com/in/rohan-mehta", second.profileUrl)

    assertTrue(RealLeadExtractor.parseProxycurlSearchResponse("not-json").isEmpty())

    val blankRes =
      RealLeadExtractor.searchB2BLeadsViaProxycurl(com.example.model.LinkedInSearchQuery())
    assertTrue(blankRes.isFailure)
  }

  @Test
  fun autoFetchPreferences_andEngineDeduplication_preserveBioAndAvoidDuplicateCharges() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.example.data.AutoFetchPreferences.init(context)

    val cfg =
      com.example.model.AutoFetchConfig(
        enabled = true,
        intervalHours = com.example.model.AutoFetchInterval.DAILY.hours,
        maxLeadsPerRun = 5,
        autoSaveToWallet = true,
        roleTitle = "Founder",
        city = "Bengaluru",
        countryCode = "IN",
        profileUrlsText = "https://www.linkedin.com/in/satya-nadella\nhttps://www.linkedin.com/in/nandan-nilekani",
      )
    assertTrue(cfg.hasValidTarget())
    assertEquals(2, cfg.parsedProfileUrls().size)

    com.example.data.AutoFetchPreferences.saveConfig(context, cfg)
    val loaded = com.example.data.AutoFetchPreferences.getConfig(context)
    assertTrue(loaded.enabled)
    assertEquals("Founder", loaded.roleTitle)
    assertEquals("Bengaluru", loaded.city)

    val bioJson =
      org.json.JSONObject(
        """
        {
          "full_name": "Ananya Rao",
          "headline": "Chief Revenue Officer",
          "summary": "Scaling enterprise SaaS across APAC and India.",
          "industry": "Enterprise Software",
          "city": "Mumbai",
          "work_email": "ananya@cloudflow.in",
          "personal_numbers": ["+91 98200 11223"],
          "experiences": [{ "company": "CloudFlow India", "title": "CRO" }]
        }
        """.trimIndent()
      )
    val bioCandidate =
      RealLeadExtractor.parseProxycurlProfileJson(
        bioJson,
        "https://www.linkedin.com/in/ananya-rao",
        "Proxycurl B2B Search",
      )
    assertNotNull(bioCandidate)
    assertEquals("Scaling enterprise SaaS across APAC and India.", bioCandidate?.bio)

    val existingLead =
      com.example.model.LeadRecord(
        id = "lead_existing",
        ownerAccountId = "OWN1001",
        fetchedByLoginId = "OWN1001",
        name = "Ananya Rao",
        company = "CloudFlow India",
        profileUrl = "https://www.linkedin.com/in/ananya-rao/",
      )
    val freshCandidate =
      com.example.model.LinkedInCandidate(
        id = "li_new_1",
        name = "Vikram Nair",
        title = "VP Sales",
        company = "FinStack",
        email = "vikram@finstack.io",
        phone = "+91 99001 22334",
        industry = "Fintech",
        location = "Bengaluru",
        profileUrl = "https://www.linkedin.com/in/vikram-nair",
        captureMethod = "Proxycurl B2B Search",
        bio = "12 years leading B2B payments across South Asia.",
      )

    val deduped =
      com.example.data.AutoFetchEngine.deduplicateNewCandidates(
        fetched = listOf(bioCandidate!!, freshCandidate, freshCandidate),
        existingLeads = listOf(existingLead),
      )
    assertEquals(1, deduped.size)
    assertEquals("Vikram Nair", deduped[0].name)

    val digest =
      com.example.data.AutoFetchPreferences.buildAutoFetchDigestText(
        leads = listOf(existingLead),
        stagedCandidates = deduped,
      )
    assertTrue(digest.contains("Vikram Nair"))
    assertTrue(digest.contains("vikram@finstack.io"))
    assertTrue(digest.contains("12 years leading B2B payments"))
  }

  @Test
  fun apkShareHelper_sanitizesFilename_buildsDownloadMessage_andGuardsInvalidApk() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    assertEquals("LeadX-Pro-v1.0.25.apk", com.example.data.ApkShareHelper.sanitizeApkFileName("1.0.25"))
    assertEquals("LeadX-Pro-v1.0_beta.apk", com.example.data.ApkShareHelper.sanitizeApkFileName("1.0 beta"))

    val msg = com.example.data.ApkShareHelper.buildDownloadMessage("1.0.25")
    assertTrue(msg.contains(com.example.data.ApkShareHelper.DIRECT_APK_DOWNLOAD_URL))
    assertTrue(msg.contains(com.example.data.ApkShareHelper.WEB_PORTAL_DOWNLOAD_URL))
    assertTrue(msg.contains("LeadX-Pro.apk"))

    assertTrue(com.example.data.ApkShareHelper.copyDownloadLink(context))

    // Rejects tiny / non-PK fake file
    val fakeApk = java.io.File(context.cacheDir, "fake_base.apk").apply {
      writeText("not an apk archive")
    }
    assertFalse(com.example.data.ApkShareHelper.isValidApkArchive(context, fakeApk))
    fakeApk.delete()

    // Rejects ZIP archive missing required Android APK entries (AndroidManifest.xml, resources.arsc, classes.dex)
    val incompleteZip = java.io.File(context.cacheDir, "incomplete.apk")
    java.util.zip.ZipOutputStream(incompleteZip.outputStream()).use { zip ->
      zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"))
      zip.write(ByteArray(128))
      zip.closeEntry()
    }
    assertFalse(com.example.data.ApkShareHelper.hasRequiredZipEntries(incompleteZip))
    assertFalse(com.example.data.ApkShareHelper.isValidApkArchive(context, incompleteZip))
    incompleteZip.delete()

    // Accepts ZIP archive only when all core APK entries exist
    val structuralZip = java.io.File(context.cacheDir, "structural.apk")
    java.util.zip.ZipOutputStream(structuralZip.outputStream()).use { zip ->
      listOf("AndroidManifest.xml", "resources.arsc", "classes.dex").forEach { entry ->
        zip.putNextEntry(java.util.zip.ZipEntry(entry))
        zip.write(ByteArray(64))
        zip.closeEntry()
      }
    }
    assertTrue(com.example.data.ApkShareHelper.hasRequiredZipEntries(structuralZip))
    // Still rejects unsigned / invalid-manifest fake ZIP
    assertFalse(com.example.data.ApkShareHelper.isValidApkArchive(context, structuralZip))
    structuralZip.delete()

    // Detects Split AAB installs safely when splitSourceDirs or splits.required metadata is populated
    val baseAppInfo = android.content.pm.ApplicationInfo(context.applicationInfo)
    baseAppInfo.splitSourceDirs = arrayOf("/data/app/split_config.arm64_v8a.apk")
    val wrapped =
      object : android.content.ContextWrapper(context) {
        override fun getApplicationInfo(): android.content.pm.ApplicationInfo = baseAppInfo
      }
    val status = com.example.data.ApkShareHelper.inspectInstalledApk(wrapped)
    assertFalse(status.isUniversalApk)
    assertEquals(1, status.splitCount)

    val metaSplitInfo = android.content.pm.ApplicationInfo(context.applicationInfo)
    metaSplitInfo.splitSourceDirs = null
    metaSplitInfo.metaData = android.os.Bundle().apply {
      putBoolean("com.android.vending.splits.required", true)
    }
    val wrappedMeta =
      object : android.content.ContextWrapper(context) {
        override fun getApplicationInfo(): android.content.pm.ApplicationInfo = metaSplitInfo
      }
    val metaStatus = com.example.data.ApkShareHelper.inspectInstalledApk(wrappedMeta)
    assertFalse(metaStatus.isUniversalApk)
    assertEquals(1, metaStatus.splitCount)
  }
}
