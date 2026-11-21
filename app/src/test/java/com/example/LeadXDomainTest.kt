package com.example

import com.example.data.IndianRtoRegistry
import com.example.data.LeadXValidators
import com.example.data.RealLeadExtractor
import com.example.model.AccountRole
import com.example.model.MIN_WALLET_BALANCE
import com.example.model.TeamAccount
import com.example.model.VEHICLE_CHECK_COST
import kotlinx.coroutines.flow.first
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

    assertTrue(com.example.data.ApkShareHelper.DIRECT_APK_DOWNLOAD_URL.contains("s81373170-ship-it/LeadX/releases/latest/download/LeadX-Pro.apk"))
    assertTrue(com.example.data.ApkShareHelper.WEB_PORTAL_DOWNLOAD_URL.contains("s81373170-ship-it.github.io/LeadX/"))

    val msg = com.example.data.ApkShareHelper.buildDownloadMessage("1.0.25")
    assertTrue(msg.contains(com.example.data.ApkShareHelper.DIRECT_APK_DOWNLOAD_URL))
    assertTrue(msg.contains(com.example.data.ApkShareHelper.WEB_PORTAL_DOWNLOAD_URL))
    assertTrue(msg.contains("LeadX-Pro.apk"))

    val installGuide = com.example.data.ApkShareHelper.buildInstallInstructionsText("1.0.25")
    assertTrue(installGuide.contains("Allow from this source"))
    assertTrue(installGuide.contains("Install unknown apps"))
    assertTrue(installGuide.contains(com.example.data.ApkShareHelper.DIRECT_APK_DOWNLOAD_URL))

    assertTrue(com.example.data.ApkShareHelper.copyDownloadLink(context))
    assertTrue(com.example.data.ApkShareHelper.copyInstallGuide(context))

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

  @Test
  fun qrCodeMatrix_roundTripsDirectApkDownloadUrlViaZxingDecoder() {
    val url = com.example.data.ApkShareHelper.DIRECT_APK_DOWNLOAD_URL
    val qr = com.example.ui.components.encodeQrCodeMatrix(url)
    assertNotNull(qr)
    val matrix = qr!!
    assertTrue(matrix.dimension >= 21)

    val scale = 4
    val margin = 4 * scale
    val side = matrix.dimension * scale + margin * 2
    val pixels = IntArray(side * side) { -1 } // white
    for (r in 0 until matrix.dimension) {
      for (c in 0 until matrix.dimension) {
        if (matrix.isDark(r, c)) {
          for (dy in 0 until scale) {
            for (dx in 0 until scale) {
              val py = margin + r * scale + dy
              val px = margin + c * scale + dx
              pixels[py * side + px] = -16777216 // black
            }
          }
        }
      }
    }
    val source = com.google.zxing.RGBLuminanceSource(side, side, pixels)
    val bitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
    val decoded = com.google.zxing.qrcode.QRCodeReader().decode(bitmap)
    assertEquals(url, decoded.text)
  }

  @Test
  fun noApiLinkedInSearch_buildsWebQueryAndParsesDuckDuckGoAndBingResults() {
    val query =
      com.example.model.LinkedInSearchQuery(
        roleTitle = "Founder",
        companyName = "FinGrid",
        industry = "Fintech",
        city = "Bengaluru",
        countryCode = "IN",
      )
    val webQuery = RealLeadExtractor.buildLinkedInWebQuery(query)
    assertTrue(webQuery.contains("site:linkedin.com/in"))
    assertTrue(webQuery.contains("\"Founder\""))
    assertTrue(webQuery.contains("\"FinGrid\""))
    assertTrue(webQuery.contains("Bengaluru"))
    assertTrue(webQuery.contains("India"))

    val ddgHtml =
      """
      <div class="result results_links">
        <a class="result__a" href="https://duckduckgo.com/l/?uddg=https%3A%2F%2Fwww.linkedin.com%2Fin%2Fkavita-rao">Kavita Rao - Founder - FinGrid | LinkedIn</a>
        <div class="result__snippet">Location: Bengaluru, India. Contact: kavita@fingrid.in · +91 98111 22334</div>
      </div>
      """.trimIndent()
    val ddgParsed = RealLeadExtractor.parseDuckDuckGoLinkedInResults(ddgHtml, query)
    assertEquals(1, ddgParsed.size)
    assertEquals("Kavita Rao", ddgParsed[0].name)
    assertEquals("Founder", ddgParsed[0].title)
    assertEquals("FinGrid", ddgParsed[0].company)
    assertEquals("kavita@fingrid.in", ddgParsed[0].email)
    assertEquals("+91 98111 22334", ddgParsed[0].phone)
    assertEquals("https://www.linkedin.com/in/kavita-rao", ddgParsed[0].profileUrl)

    val bingHtml =
      """
      <li class="b_algo">
        <h2><a href="https://www.linkedin.com/in/arjun-kapoor?trk=public">Arjun Kapoor - CTO at PayStack India | LinkedIn</a></h2>
        <p>Based in Mumbai. Email: arjun@paystack.in</p>
      </li>
      """.trimIndent()
    val bingParsed = RealLeadExtractor.parseBingLinkedInResults(bingHtml, query)
    assertEquals(1, bingParsed.size)
    assertEquals("Arjun Kapoor", bingParsed[0].name)
    assertEquals("CTO", bingParsed[0].title)
    assertEquals("PayStack India", bingParsed[0].company)
    assertEquals("arjun@paystack.in", bingParsed[0].email)
    assertEquals("https://www.linkedin.com/in/arjun-kapoor", bingParsed[0].profileUrl)

    // Blank filter fails cleanly without requiring any API key
    val emptySearch = RealLeadExtractor.searchLinkedInNoApi(com.example.model.LinkedInSearchQuery())
    assertTrue(emptySearch.isFailure)
  }

  @Test
  fun csvExporter_parsesRfc4180CsvWithCommasQuotesAndCustomHeaders() {
    val lead =
      com.example.model.LeadRecord(
        id = "lead_roundtrip",
        ownerAccountId = "OWN1001",
        fetchedByLoginId = "EMP001",
        source = "linkedin",
        name = "Arundhati Bhattacharya",
        title = "Chairperson & \"CEO\"",
        company = "Salesforce, India",
        email = "arundhati@example.com",
        phone = "+919820012345",
        industry = "Enterprise SaaS",
        location = "Mumbai, India",
        vehicleDetails = "Bio: Cloud leader, enterprise transformation",
        profileUrl = "https://www.linkedin.com/in/arundhati",
        consentGranted = true,
        pointsCost = 10L,
      )
    val csv = com.example.data.CsvExporter.buildLeadsCsv(listOf(lead))
    val parsed = com.example.data.CsvExporter.parseCsvLeads(csv)
    assertEquals(1, parsed.size)
    assertEquals("Arundhati Bhattacharya", parsed[0].name)
    assertEquals("Chairperson & \"CEO\"", parsed[0].title)
    assertEquals("Salesforce, India", parsed[0].company)
    assertEquals("arundhati@example.com", parsed[0].email)
    assertEquals("Bio: Cloud leader, enterprise transformation", parsed[0].vehicleDetails)

    // Custom header external client CSV
    val externalCsv =
      """
      Full Name,Role,Organization,Work Email,Mobile,City,LinkedIn
      "Meera Iyer","VP Marketing","Zoho Corp","meera@zoho.example","+91 98400 55667","Chennai","https://www.linkedin.com/in/meera-iyer"
      """.trimIndent()
    val customParsed = com.example.data.CsvExporter.parseCsvLeads(externalCsv)
    assertEquals(1, customParsed.size)
    assertEquals("Meera Iyer", customParsed[0].name)
    assertEquals("VP Marketing", customParsed[0].title)
    assertEquals("Zoho Corp", customParsed[0].company)
    assertEquals("meera@zoho.example", customParsed[0].email)
    assertEquals("Chennai", customParsed[0].location)
  }

  @Test
  fun csvBatchManager_createsSeparateClientCsvsSplitsAndReadsDirectClientLeads() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.example.data.CsvBatchManager.init(context)

    val sampleLeads =
      listOf(
        com.example.model.LeadRecord(
          id = "l1",
          ownerAccountId = "OWN1001",
          fetchedByLoginId = "OWN1001",
          source = "linkedin",
          name = "Priya Verma",
          title = "Head of Growth",
          company = "FinGrid",
          email = "priya@fingrid.in",
          phone = "+919800011111",
          location = "Bengaluru, India",
          profileUrl = "https://www.linkedin.com/in/priya-verma",
        ),
        com.example.model.LeadRecord(
          id = "l2",
          ownerAccountId = "OWN1001",
          fetchedByLoginId = "OWN1001",
          source = "linkedin",
          name = "Rohan Gupta",
          title = "Director Sales",
          company = "CloudBridge",
          email = "rohan@cloudbridge.in",
          phone = "+919800022222",
          location = "Mumbai, India",
          profileUrl = "https://www.linkedin.com/in/rohan-gupta",
        ),
      )

    val splitByCompany =
      com.example.data.CsvBatchManager.splitLeadsIntoSeparateCsvs(
        context = context,
        leads = sampleLeads,
        groupBy = "COMPANY",
        customClientPrefix = "ClientA",
      )
    assertEquals(2, splitByCompany.size)
    assertTrue(splitByCompany.all { java.io.File(it.filePath).exists() && it.fileName.endsWith(".csv") })

    val finGridBatch = splitByCompany.first { it.clientName.contains("FinGrid") }
    val leadsFromCsv = com.example.data.CsvBatchManager.readLeadsFromBatch(finGridBatch)
    assertEquals(1, leadsFromCsv.size)
    assertEquals("Priya Verma", leadsFromCsv[0].name)

    val deliveryText = com.example.data.CsvBatchManager.buildClientDeliveryTextFromCsv(finGridBatch, leadsFromCsv)
    assertTrue(deliveryText.contains("Priya Verma"))
    assertTrue(deliveryText.contains("priya@fingrid.in"))
    assertTrue(deliveryText.contains(finGridBatch.fileName))

    val externalRaw =
      "Name,Title,Company,Email,Phone,Location,ProfileUrl\n" +
        "\"Dev Shah\",\"CEO\",\"NextPay\",\"dev@nextpay.in\",\"+919988776655\",\"Pune\",\"https://www.linkedin.com/in/dev-shah\""
    val (importedBatch, importedLeads) =
      com.example.data.CsvBatchManager.importExternalCsv(
        context = context,
        csvContent = externalRaw,
        originalFileName = "nextpay_leads.csv",
        clientName = "NextPay Client",
      )
    assertEquals("NextPay Client", importedBatch.clientName)
    assertEquals(1, importedLeads.size)
    assertEquals("Dev Shah", importedLeads[0].name)
  }

  @Test
  fun autoFetch24h_supports15MinIntervalAndClientCsvSettingsPersistence() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    com.example.data.AutoFetchPreferences.init(context)

    val cfg =
      com.example.model.AutoFetchConfig(
        enabled = true,
        intervalHours = 1L,
        intervalMinutes = com.example.model.AutoFetchInterval.EVERY_15_MIN.minutes,
        maxLeadsPerRun = 8,
        autoSaveToWallet = true,
        autoExportSeparateCsv = true,
        continuous24hLoop = true,
        targetClientName = "Acme B2B Client",
        roleTitle = "VP Sales",
        city = "Mumbai",
      )
    assertEquals(15L, cfg.resolvedIntervalMinutes())
    com.example.data.AutoFetchPreferences.saveConfig(context, cfg)

    val loaded = com.example.data.AutoFetchPreferences.getConfig(context)
    assertEquals(15L, loaded.resolvedIntervalMinutes())
    assertTrue(loaded.autoExportSeparateCsv)
    assertTrue(loaded.continuous24hLoop)
    assertEquals("Acme B2B Client", loaded.targetClientName)
  }

  @Test
  fun periodicFirestoreSyncScheduler_schedules24hWorkRequestWithNetworkConstraintAndTogglesCleanly() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
    com.example.data.AutoFetchPreferences.init(context)

    val spec = com.example.worker.PeriodicFirestoreSyncScheduler.buildPeriodicWorkRequest().workSpec
    assertEquals(java.util.concurrent.TimeUnit.HOURS.toMillis(24L), spec.intervalDuration)
    assertEquals(androidx.work.NetworkType.CONNECTED, spec.constraints.requiredNetworkType)

    com.example.worker.PeriodicFirestoreSyncScheduler.setPeriodicSyncEnabled(context, true)
    com.example.worker.PeriodicFirestoreSyncScheduler.ensureScheduled24h(context)

    val wm = androidx.work.WorkManager.getInstance(context)
    val infos = wm.getWorkInfosForUniqueWork(com.example.worker.PeriodicFirestoreSyncScheduler.UNIQUE_PERIODIC_SYNC_WORK).get()
    assertEquals(1, infos.size)
    assertEquals(androidx.work.WorkInfo.State.ENQUEUED, infos[0].state)
    assertTrue(infos[0].tags.contains(com.example.worker.PeriodicFirestoreSyncScheduler.UNIQUE_PERIODIC_SYNC_WORK))

    com.example.worker.PeriodicFirestoreSyncScheduler.setPeriodicSyncEnabled(context, false)
    val afterDisable = wm.getWorkInfosForUniqueWork(com.example.worker.PeriodicFirestoreSyncScheduler.UNIQUE_PERIODIC_SYNC_WORK).get()
    assertTrue(afterDisable.isEmpty() || afterDisable.all { it.state == androidx.work.WorkInfo.State.CANCELLED })

    // Re-enable cleanly
    com.example.worker.PeriodicFirestoreSyncScheduler.setPeriodicSyncEnabled(context, true)
  }

  @Test
  fun periodicFirestoreSyncWorker_doWorkSimulatesFetchUpdatesDatabaseAndIncrementsRunTelemetry() {
    val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
    androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(context)
    com.example.data.AutoFetchPreferences.init(context)

    val beforeRuns = com.example.data.AutoFetchPreferences.getPeriodicSyncState(context).totalRuns
    val worker =
      androidx.work.testing.TestListenableWorkerBuilder<com.example.worker.PeriodicFirestoreSyncWorker>(context)
        .build()
    val result = kotlinx.coroutines.runBlocking { worker.doWork() }

    assertTrue(result is androidx.work.ListenableWorker.Result.Success)
    val success = result as androidx.work.ListenableWorker.Result.Success
    val batch = success.outputData.getString(com.example.worker.PeriodicFirestoreSyncWorker.KEY_OUTPUT_BATCH).orEmpty()
    assertTrue(batch.startsWith("24H_SIM_"))
    assertFalse(success.outputData.getString(com.example.worker.PeriodicFirestoreSyncWorker.KEY_OUTPUT_STATUS).isNullOrBlank())

    val leads = kotlinx.coroutines.runBlocking {
      com.example.data.local.LeadXDatabase.getInstance(context).leadXDao().observeLeads().first()
    }
    val syncLead = leads.firstOrNull { it.title == "Simulated 24h Background Data Sync" }
    assertNotNull(syncLead)
    assertEquals(0L, syncLead?.pointsCost)

    val afterState = com.example.data.AutoFetchPreferences.getPeriodicSyncState(context)
    assertEquals(beforeRuns + 1, afterState.totalRuns)
    assertTrue(afterState.lastRunAtMillis > 0L)
  }
}
