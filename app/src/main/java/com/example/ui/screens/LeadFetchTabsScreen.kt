package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.RealLeadExtractor
import com.example.model.AutoFetchConfig
import com.example.model.AutoFetchRunLog
import com.example.model.FlashBanner
import com.example.model.LINKEDIN_COST_PER_LEAD
import com.example.model.LeadRecord
import com.example.model.LinkedInCandidate
import com.example.model.LinkedInSearchQuery
import com.example.model.PeriodicSyncState
import com.example.model.TeamAccount
import com.example.model.VEHICLE_CHECK_COST
import com.example.model.VehicleLookupResult
import com.example.ui.components.EditorialDivider
import com.example.ui.components.FlashBannerView
import com.example.ui.components.LeadResultsSkeletonSection
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.components.SegmentedMonochromeTabs
import com.example.ui.components.StatusPill
import com.example.ui.components.formatPoints
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.LeadXTheme
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.StrongBorder
import com.example.ui.theme.SuccessDot
import com.example.ui.theme.SuccessInk
import com.example.ui.theme.SuccessSurface
import com.example.ui.theme.WarningInk
import com.example.ui.theme.WarningSurface
import org.json.JSONArray

@Composable
fun LeadFetchTabsScreen(
  account: TeamAccount,
  selectedSubTab: Int,
  candidates: List<LinkedInCandidate>,
  scopedLeads: List<LeadRecord>,
  lastVehicleResult: VehicleLookupResult?,
  autoFetchConfig: AutoFetchConfig,
  autoFetchLogs: List<AutoFetchRunLog>,
  flashBanner: FlashBanner?,
  isBusy: Boolean,
  apiConfigVersion: Int,
  csvBatchesCount: Int = 0,
  continuousLoopActive: Boolean = false,
  periodicSyncState: PeriodicSyncState = PeriodicSyncState(),
  onSelectSubTab: (Int) -> Unit,
  onSearchLinkedInB2B: (LinkedInSearchQuery) -> Unit,
  onExtractLinkedInUrl: (String) -> Unit,
  onExtractWebViewDomJson: (String) -> Unit,
  onToggleCandidate: (String) -> Unit,
  onSelectAllCandidates: (Boolean) -> Unit,
  onClearCandidates: () -> Unit,
  onSaveSelectedLinkedIn: (String) -> Unit,
  onFetchVehicleByPlate: (String) -> Unit,
  onRequestConsent: () -> Unit,
  onSaveAutoFetchConfig: (AutoFetchConfig) -> Unit,
  onRunAutoFetchNow: (AutoFetchConfig) -> Unit,
  onTogglePeriodicFirestoreSync: (Boolean) -> Unit = {},
  onRunPeriodicFirestoreSyncNow: () -> Unit = {},
  onShowMessage: (String, Boolean) -> Unit,
  onOpenApiKeys: () -> Unit,
  onDismissFlash: () -> Unit,
  onBackDashboard: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (!LocalInspectionMode.current && LocalOnBackPressedDispatcherOwner.current != null) {
    BackHandler { onBackDashboard() }
  }

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

  Box(
    modifier = modifier.fillMaxSize().background(PaperBackground),
    contentAlignment = Alignment.TopCenter,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 860.dp)
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            onClick = onBackDashboard,
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.size(44.dp).minimumInteractiveComponentSize().testTag("fetch_back_button"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Dashboard", tint = InkBlack)
            }
          }
          Spacer(Modifier.width(12.dp))
          Column {
            Text("Real Lead Intelligence", style = MaterialTheme.typography.headlineMedium, color = InkBlack)
            Text(
              "Usable: ${formatPoints(account.usablePoints())} pts · Bal ${formatPoints(account.balance)}",
              style = MaterialTheme.typography.bodyMedium,
              color = InkGraphite,
            )
          }
        }
        MonochromeBadgeTag(
          text =
            when (selectedSubTab) {
              0 -> "$LINKEDIN_COST_PER_LEAD PTS/LEAD"
              1 -> "$VEHICLE_CHECK_COST PTS/CHECK"
              else -> "24H AUTO"
            },
          inverted = true,
        )
      }

      Spacer(Modifier.height(14.dp))

      SegmentedMonochromeTabs(
        tabs = listOf("LinkedIn B2B", "Number Plate", "24h Auto"),
        selectedIndex = selectedSubTab,
        onSelect = onSelectSubTab,
      )

      Spacer(Modifier.height(14.dp))
      FlashBannerView(banner = flashBanner, onDismiss = onDismissFlash)

      if (!account.meetsMinBalance()) {
        Surface(
          shape = MaterialTheme.shapes.small,
          color = WarningSurface,
          border = BorderStroke(1.dp, WarningInk.copy(alpha = 0.3f)),
          modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).testTag("fetch_min_balance_alert"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = WarningInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              text = "Minimum 5000 balance required, please recharge",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = WarningInk,
            )
          }
        }
      }

      when (selectedSubTab) {
        0 -> {
          LinkedInInputTabPane(
            candidates = candidates,
            isBusy = isBusy,
            apiConfigVersion = apiConfigVersion,
            fieldColors = fieldColors,
            onSearchB2B = onSearchLinkedInB2B,
            onExtractUrl = onExtractLinkedInUrl,
            onExtractWebViewDomJson = onExtractWebViewDomJson,
            onToggleCandidate = onToggleCandidate,
            onSelectAll = onSelectAllCandidates,
            onClearCandidates = onClearCandidates,
            onSaveSelected = onSaveSelectedLinkedIn,
            onOpenApiKeys = onOpenApiKeys,
          )
        }
        1 -> {
          NumberPlateInputTabPane(
            lastVehicleResult = lastVehicleResult,
            isBusy = isBusy,
            apiConfigVersion = apiConfigVersion,
            fieldColors = fieldColors,
            onFetchVehicleByPlate = onFetchVehicleByPlate,
            onRequestConsent = onRequestConsent,
            onOpenApiKeys = onOpenApiKeys,
          )
        }
        else -> {
          AutoFetchSchedulerSection(
            config = autoFetchConfig,
            runLogs = autoFetchLogs,
            scopedLeads = scopedLeads,
            stagedCandidates = candidates,
            isBusy = isBusy,
            apiConfigVersion = apiConfigVersion,
            csvBatchesCount = csvBatchesCount,
            continuousLoopActive = continuousLoopActive,
            periodicSyncState = periodicSyncState,
            fieldColors = fieldColors,
            onSaveConfig = onSaveAutoFetchConfig,
            onRunNow = onRunAutoFetchNow,
            onTogglePeriodicFirestoreSync = onTogglePeriodicFirestoreSync,
            onRunPeriodicFirestoreSyncNow = onRunPeriodicFirestoreSyncNow,
            onShowMessage = onShowMessage,
            onOpenApiKeys = onOpenApiKeys,
            onToggleCandidate = onToggleCandidate,
            onSelectAllCandidates = onSelectAllCandidates,
            onClearCandidates = onClearCandidates,
            onSaveSelectedLinkedIn = onSaveSelectedLinkedIn,
          )
        }
      }

      Spacer(Modifier.height(24.dp))
    }
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LinkedInInputTabPane(
  candidates: List<LinkedInCandidate>,
  isBusy: Boolean,
  apiConfigVersion: Int,
  fieldColors: TextFieldColors,
  onSearchB2B: (LinkedInSearchQuery) -> Unit,
  onExtractUrl: (String) -> Unit,
  onExtractWebViewDomJson: (String) -> Unit,
  onToggleCandidate: (String) -> Unit,
  onSelectAll: (Boolean) -> Unit,
  onClearCandidates: () -> Unit,
  onSaveSelected: (String) -> Unit,
  onOpenApiKeys: () -> Unit,
) {
  var linkedInMode by rememberSaveable { mutableStateOf(0) }
  var profileUrlInput by rememberSaveable { mutableStateOf("") }
  var showBrowser by rememberSaveable { mutableStateOf(false) }
  var showJsonImporter by rememberSaveable { mutableStateOf(false) }
  var domJsonInput by rememberSaveable { mutableStateOf("") }
  var currentBrowserUrl by rememberSaveable { mutableStateOf("https://www.linkedin.com/login") }
  var webViewRef by remember { mutableStateOf<WebView?>(null) }
  var webViewError by remember { mutableStateOf<String?>(null) }

  val isPreview = LocalInspectionMode.current
  val hasProxycurl =
    remember(apiConfigVersion, isPreview) {
      !isPreview && runCatching { RealLeadExtractor.isProxycurlConfigured() }.getOrDefault(false)
    }

  SegmentedMonochromeTabs(
    tabs = listOf("B2B Search", "Profile URL", "DOM & JSON"),
    selectedIndex = linkedInMode,
    onSelect = { linkedInMode = it },
    tagPrefix = "linkedin_mode_tab",
  )
  Spacer(Modifier.height(12.dp))

  when (linkedInMode) {
    0 -> {
      LinkedInB2BSearchCard(
        hasProxycurl = hasProxycurl,
        isBusy = isBusy,
        fieldColors = fieldColors,
        onSearchB2B = onSearchB2B,
        onOpenApiKeys = onOpenApiKeys,
      )
    }
    1 -> {
      LeadXCard(borderColor = StrongBorder) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AddLink, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel("Direct LinkedIn Profile URL", color = InkBlack)
          }
          MonochromeBadgeTag(
            text = "PUBLIC · NO API",
          )
        }
        Spacer(Modifier.height(8.dp))
        Text(
          text = "Extracts LinkedIn profile bio, role, company, and location via public JSON-LD, OpenGraph, and live web index without requiring any API key.",
          style = MaterialTheme.typography.bodyMedium,
          color = InkGraphite,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = profileUrlInput,
          onValueChange = { profileUrlInput = it },
          label = { Text("Paste real linkedin.com/in/username URL") },
          colors = fieldColors,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("linkedin_url_input"),
        )
        Spacer(Modifier.height(10.dp))
        Button(
          onClick = { onExtractUrl(profileUrlInput) },
          enabled = !isBusy && profileUrlInput.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("extract_linkedin_url_button"),
        ) {
          if (isBusy) {
            CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
          } else {
            Icon(Icons.Rounded.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
          }
          Text("Extract LinkedIn Lead (No API)", fontWeight = FontWeight.Bold)
        }
      }
    }
    else -> {

  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Language, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        SectionLabel("Sales Navigator WebView & DOM JSON", color = InkBlack)
      }
      MonochromeBadgeTag(text = "WEBVIEW + DOM")
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Sign in to LinkedIn Sales Navigator inside the built-in browser to capture visible DOM cards or paste extracted JSON.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )
    Spacer(Modifier.height(10.dp))
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      OutlinedButton(
        onClick = { showBrowser = !showBrowser },
        border = BorderStroke(1.dp, InkBlack),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_linkedin_webview_button"),
      ) {
        Icon(Icons.Rounded.Language, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(
          text = if (showBrowser) "Hide Browser" else "LinkedIn DOM",
          color = InkBlack,
          fontWeight = FontWeight.Bold,
        )
      }
      OutlinedButton(
        onClick = { showJsonImporter = !showJsonImporter },
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_dom_json_button"),
      ) {
        Text(
          text = if (showJsonImporter) "Hide JSON Box" else "Paste DOM JSON",
          color = InkBlack,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    if (showJsonImporter) {
      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = domJsonInput,
        onValueChange = { domJsonInput = it },
        label = { Text("Paste Sales Navigator / Extension JSON Array") },
        colors = fieldColors,
        minLines = 3,
        maxLines = 5,
        modifier = Modifier.fillMaxWidth().testTag("dom_json_input"),
      )
      Spacer(Modifier.height(8.dp))
      Button(
        onClick = {
          onExtractWebViewDomJson(domJsonInput)
          domJsonInput = ""
        },
        enabled = !isBusy && domJsonInput.isNotBlank(),
        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("parse_dom_json_button"),
      ) {
        Text("Parse Extracted Sales Navigator Leads", fontWeight = FontWeight.Bold)
      }
    }

    if (showBrowser) {
      Spacer(Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() },
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(40.dp),
        ) {
          Text("Back", color = InkBlack, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
          onClick = { webViewRef?.reload() },
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(40.dp),
        ) {
          Text("Reload", color = InkBlack, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
          onClick = { webViewRef?.loadUrl("https://www.linkedin.com/search/results/people/") },
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(40.dp),
        ) {
          Text("People", color = InkBlack, style = MaterialTheme.typography.labelMedium)
        }
        OutlinedButton(
          onClick = { webViewRef?.loadUrl("https://www.linkedin.com/sales/search/people") },
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.height(40.dp),
        ) {
          Text("Sales Nav", color = InkBlack, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.weight(1f))
        Button(
          onClick = {
            val view = webViewRef
            if (view != null) {
              val js =
                """
                (function() {
                  var results = [];
                  var cards = document.querySelectorAll('ol.artdeco-list > li.artdeco-list__item, div[data-x--lead-search-result], li.reusable-search__result-container, div.entity-result, ol.artdeco-list > li');
                  cards.forEach(function(card) {
                    var nameEl = card.querySelector('span[data-anonymize="person-name"], .artdeco-entity-lockup__title a span, .entity-result__title-text a span[aria-hidden="true"], a[href*="/sales/lead/"], a[href*="/in/"] span');
                    var linkEl = card.querySelector('a[href*="/sales/lead/"], .artdeco-entity-lockup__title a, a[href*="/in/"]');
                    var titleEl = card.querySelector('span[data-anonymize="title"], .artdeco-entity-lockup__subtitle span[data-anonymize="title"], .entity-result__primary-subtitle, .artdeco-entity-lockup__subtitle');
                    var companyEl = card.querySelector('a[data-anonymize="company-name"], span[data-anonymize="company-name"], .artdeco-entity-lockup__subtitle a');
                    var locEl = card.querySelector('span[data-anonymize="location"], .artdeco-entity-lockup__caption span, .entity-result__secondary-subtitle');
                    var summaryEl = card.querySelector('.entity-result__summary, .artdeco-entity-lockup__metadata, p');
                    var cardText = card.innerText || '';
                    var emailMatch = cardText.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/);
                    var phoneMatch = cardText.match(/(?:\+?\d{1,3}[\s-]?)?\d{5}[\s-]?\d{5}/);
                    var name = nameEl ? nameEl.innerText.trim() : '';
                    var url = linkEl && linkEl.href ? linkEl.href.split('?')[0] : '';
                    var title = titleEl ? titleEl.innerText.trim() : '';
                    var company = companyEl ? companyEl.innerText.trim() : '';
                    if (!company && title.indexOf(' at ') !== -1) {
                      var parts = title.split(' at ');
                      title = parts[0].trim();
                      company = parts.slice(1).join(' at ').trim();
                    }
                    if (name && name.toLowerCase() !== 'linkedin member' && url) {
                      results.push({
                        name: name,
                        profileUrl: url,
                        title: title,
                        company: company,
                        location: locEl ? locEl.innerText.trim() : '',
                        bio: summaryEl ? summaryEl.innerText.trim().slice(0, 220) : '',
                        email: emailMatch ? emailMatch[0] : '',
                        phone: phoneMatch ? phoneMatch[0] : ''
                      });
                    }
                  });
                  if (results.length === 0) {
                    var h1 = document.querySelector('h1');
                    var nameSingle = h1 ? h1.innerText.trim() : '';
                    if (!nameSingle) {
                      var ogTitle = document.querySelector('meta[property="og:title"]')?.content || document.title || '';
                      nameSingle = ogTitle.split('|')[0].split('-')[0].trim();
                    }
                    var headline = document.querySelector('.text-body-medium.break-words')?.innerText?.trim() || '';
                    var locSingle = document.querySelector('.text-body-small.inline.t-black--light.break-words')?.innerText?.trim() || '';
                    var aboutSingle = document.querySelector('#about')?.parentElement?.innerText?.replace(/^About\s*/i, '').trim() || '';
                    var bodyText = document.body.innerText || '';
                    var mailSingle = (document.querySelector('a[href^="mailto:"]')?.href || '').replace('mailto:', '').trim() ||
                      (bodyText.match(/[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/)?.[0] || '');
                    var phoneSingle = bodyText.match(/(?:\+?\d{1,3}[\s-]?)?\d{5}[\s-]?\d{5}/)?.[0] || '';
                    if (nameSingle && nameSingle.toLowerCase() !== 'linkedin') {
                      results.push({
                        name: nameSingle,
                        profileUrl: window.location.href.split('?')[0],
                        title: headline,
                        company: '',
                        location: locSingle,
                        bio: aboutSingle.slice(0, 220),
                        email: mailSingle,
                        phone: phoneSingle
                      });
                    }
                  }
                  return JSON.stringify(results);
                })();
                """.trimIndent()
              view.evaluateJavascript(js) { rawResult ->
                val clean = decodeEvaluateJavascriptJson(rawResult)
                onExtractWebViewDomJson(clean)
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          modifier = Modifier.testTag("capture_visible_linkedin_button"),
        ) {
          Text("Capture DOM", fontWeight = FontWeight.Bold)
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = currentBrowserUrl,
        style = MaterialTheme.typography.labelSmall,
        color = InkGraphite,
        maxLines = 1,
      )
      Spacer(Modifier.height(8.dp))
      DisposableEffect(Unit) {
        onDispose {
          runCatching {
            webViewRef?.stopLoading()
            webViewRef?.removeAllViews()
            webViewRef?.destroy()
          }
          webViewRef = null
        }
      }
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(340.dp)
            .clip(MaterialTheme.shapes.small)
            .background(SoftAlabaster),
        contentAlignment = Alignment.Center,
      ) {
        if (LocalInspectionMode.current) {
          Text(
            text = "In-app LinkedIn WebView Preview",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
            modifier = Modifier.padding(16.dp),
          )
        } else if (webViewError != null) {
          Text(
            text = webViewError ?: "In-app browser unavailable on this device. Use Paste DOM JSON instead.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
            modifier = Modifier.padding(16.dp),
          )
        } else {
          AndroidView(
            factory = { ctx ->
              runCatching {
                WebView(ctx).apply {
                  settings.javaScriptEnabled = true
                  settings.domStorageEnabled = true
                  runCatching {
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                  }
                  setOnTouchListener { v, _ ->
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                  }
                  webViewClient =
                    object : WebViewClient() {
                      override fun onPageFinished(view: WebView?, url: String?) {
                        if (!url.isNullOrBlank()) {
                          currentBrowserUrl = url
                        }
                      }
                    }
                  loadUrl("https://www.linkedin.com")
                  webViewRef = this
                }
              }.getOrElse { err ->
                webViewError = "In-app browser unavailable (${err.message ?: "no WebView"}). Use Paste DOM JSON above."
                android.view.View(ctx)
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
  }
    }
  }

  Spacer(Modifier.height(14.dp))
  if (isBusy && candidates.isEmpty()) {
    LeadResultsSkeletonSection()
  } else {
    StructuredLeadResultsList(
      candidates = candidates,
      isBusy = isBusy,
      onToggleCandidate = onToggleCandidate,
      onSelectAll = onSelectAll,
      onClearCandidates = onClearCandidates,
      onSaveSelected = onSaveSelected,
    )
  }
}

private fun decodeEvaluateJavascriptJson(rawResult: String?): String {
  if (rawResult.isNullOrBlank() || rawResult == "null") return ""
  return runCatching {
    val wrapped = JSONArray("[$rawResult]")
    wrapped.getString(0)
  }.getOrElse { rawResult.trim('"') }
}

@Composable
private fun NumberPlateInputTabPane(
  lastVehicleResult: VehicleLookupResult?,
  isBusy: Boolean,
  apiConfigVersion: Int,
  fieldColors: TextFieldColors,
  onFetchVehicleByPlate: (String) -> Unit,
  onRequestConsent: () -> Unit,
  onOpenApiKeys: () -> Unit,
) {
  var plateInput by rememberSaveable { mutableStateOf("") }
  val isPreview = LocalInspectionMode.current
  val hasSurepass =
    remember(apiConfigVersion, isPreview) {
      !isPreview && runCatching { RealLeadExtractor.isSurepassConfigured() }.getOrDefault(false)
    }

  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.DirectionsCar, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        SectionLabel("Vehicle Number Plate Lookup", color = InkBlack)
      }
      MonochromeBadgeTag(text = if (hasSurepass) "SUREPASS LIVE" else "RTO REGISTRY")
    }
    Spacer(Modifier.height(12.dp))

    Surface(
      shape = MaterialTheme.shapes.small,
      color = PureWhite,
      border = BorderStroke(2.dp, InkBlack),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Surface(
          color = InkBlack,
          shape = RoundedCornerShape(6.dp),
        ) {
          Text(
            text = "IND",
            color = PureWhite,
            fontFamily = MonoFontFamily,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
          )
        }
        Spacer(Modifier.width(14.dp))
        Text(
          text = plateInput.ifBlank { "MH02AB1234" },
          fontFamily = MonoFontFamily,
          fontSize = 22.sp,
          fontWeight = FontWeight.Bold,
          color = if (plateInput.isBlank()) InkMuted else InkBlack,
          letterSpacing = 2.5.sp,
        )
      }
    }

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
      value = plateInput,
      onValueChange = { raw ->
        plateInput = raw.uppercase().filter { it.isLetterOrDigit() }.take(12)
      },
      label = { Text("Enter Vehicle Number (e.g. MH02AB1234)") },
      colors = fieldColors,
      textStyle = TextStyle(fontFamily = MonoFontFamily, fontWeight = FontWeight.Bold),
      keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
      singleLine = true,
      modifier = Modifier.fillMaxWidth().testTag("plate_number_input"),
    )

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      listOf("MH02AB1234", "KA05MJ4321", "DL01CA9999").forEach { sample ->
        val selected = plateInput == sample
        Surface(
          onClick = { plateInput = sample },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(
            text = sample,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureWhite else InkBlack,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(14.dp))

    Button(
      onClick = { onFetchVehicleByPlate(plateInput) },
      enabled = !isBusy && plateInput.isNotBlank(),
      colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("fetch_vehicle_lead_button"),
    ) {
      if (isBusy) {
        CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text(
        text = if (hasSurepass) "Verify Live Vahan RC (-$VEHICLE_CHECK_COST pts)" else "Verify RTO Jurisdiction (0 pts)",
        fontWeight = FontWeight.Bold,
      )
    }

    if (!hasSurepass) {
      Spacer(Modifier.height(8.dp))
      OutlinedButton(
        onClick = onOpenApiKeys,
        border = BorderStroke(1.dp, HairlineBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().height(44.dp).testTag("open_surepass_key_button"),
      ) {
        Text("Set Surepass Vahan API Token (-15 pts Live RC)", color = InkBlack, fontWeight = FontWeight.SemiBold)
      }
    }
  }

  if (lastVehicleResult != null) {
    Spacer(Modifier.height(14.dp))
    LeadXCard(borderColor = StrongBorder) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        SectionLabel("Vahan Record · ${lastVehicleResult.plateNumber}", color = InkBlack)
        StatusPill(
          text = if (lastVehicleResult.isLiveRcData) "LIVE RC (-15 PTS)" else "RTO VERIFIED (0 PTS)",
          containerColor = if (lastVehicleResult.isLiveRcData) SuccessSurface else SoftAlabaster,
          contentColor = if (lastVehicleResult.isLiveRcData) SuccessInk else InkBlack,
          dotColor = if (lastVehicleResult.isLiveRcData) SuccessDot else InkBlack,
        )
      }
      Spacer(Modifier.height(12.dp))

      RcDetailRow("RTO Office", lastVehicleResult.rtoOffice, highlight = true)
      EditorialDivider()
      RcDetailRow("Registered City", lastVehicleResult.registeredCity)
      EditorialDivider()
      RcDetailRow("State / UT", lastVehicleResult.registeredState)

      if (!lastVehicleResult.isLiveRcData) {
        Spacer(Modifier.height(12.dp))
        Surface(
          shape = MaterialTheme.shapes.small,
          color = SoftAlabaster,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth().testTag("rto_only_notice"),
        ) {
          Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
              text = lastVehicleResult.statusMessage,
              style = MaterialTheme.typography.bodyMedium,
              color = InkGraphite,
            )
          }
        }
      } else {
        EditorialDivider()
        RcDetailRow("Manufacturer", lastVehicleResult.manufacturer, highlight = true)
        EditorialDivider()
        RcDetailRow("Vehicle Model", lastVehicleResult.vehicleModel)
        EditorialDivider()
        RcDetailRow("Vehicle Class", lastVehicleResult.vehicleClass)
        EditorialDivider()
        RcDetailRow("Fuel Type", lastVehicleResult.fuelType)
        EditorialDivider()
        RcDetailRow("Registration Date", lastVehicleResult.registrationDate)
        EditorialDivider()
        RcDetailRow("Insurance Valid Till", lastVehicleResult.insuranceValidTill)
        EditorialDivider()
        RcDetailRow("PUC Valid Till", lastVehicleResult.pucValidTill)
        EditorialDivider()
        RcDetailRow("Fitness Valid Till", lastVehicleResult.fitnessValidTill)

        Spacer(Modifier.height(14.dp))

        if (!lastVehicleResult.consentGranted) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = SoftAlabaster,
            border = BorderStroke(1.dp, HairlineBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                  text = "Owner personal details require verified Vahan consent.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = InkGraphite,
                )
              }
              Spacer(Modifier.height(10.dp))
              OutlinedButton(
                onClick = onRequestConsent,
                enabled = !isBusy,
                border = BorderStroke(1.dp, InkBlack),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp).testTag("request_consent_button"),
              ) {
                Icon(Icons.Rounded.VerifiedUser, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Request Consent & Unlock Owner Details", color = InkBlack, fontWeight = FontWeight.Bold)
              }
            }
          }
        } else {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = SuccessSurface,
            border = BorderStroke(1.dp, SuccessInk.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth().testTag("consent_verified_owner_panel"),
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = SuccessInk, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                  text = "OWNER CONSENT VERIFIED",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.Bold,
                  color = SuccessInk,
                )
              }
              Spacer(Modifier.height(6.dp))
              RcDetailRow("Registered Owner", lastVehicleResult.ownerName, highlight = true)
              if (lastVehicleResult.ownerContact.isNotBlank()) {
                EditorialDivider()
                RcDetailRow("Registered Contact", lastVehicleResult.ownerContact)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RcDetailRow(label: String, value: String, highlight: Boolean = false) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = InkGraphite)
    Spacer(Modifier.width(12.dp))
    Text(
      text = value.ifBlank { "—" },
      style = MaterialTheme.typography.bodyLarge,
      fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
      color = InkBlack,
    )
  }
}

@Preview(showBackground = true, showSystemUi = true, name = "Lead Fetch Tabs Preview")
@Composable
private fun LeadFetchTabsScreenPreview() {
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
      isBusy = false,
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

@Preview(showBackground = true, showSystemUi = true, name = "Lead Fetch Skeleton Loading")
@Composable
private fun LeadFetchTabsSkeletonPreview() {
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
