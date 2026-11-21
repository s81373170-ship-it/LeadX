package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.LINKEDIN_COST_PER_LEAD
import com.example.model.LinkedInCandidate
import com.example.model.LinkedInSearchQuery
import com.example.ui.components.EditorialDivider
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonoBadge
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.components.StatusPill
import com.example.ui.components.formatPoints
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkedInB2BSearchCard(
  hasProxycurl: Boolean,
  isBusy: Boolean,
  fieldColors: TextFieldColors,
  onSearchB2B: (LinkedInSearchQuery) -> Unit,
  onOpenApiKeys: () -> Unit,
) {
  var roleTitle by rememberSaveable { mutableStateOf("") }
  var companyName by rememberSaveable { mutableStateOf("") }
  var industry by rememberSaveable { mutableStateOf("") }
  var city by rememberSaveable { mutableStateOf("") }
  var countryCode by rememberSaveable { mutableStateOf("IN") }

  val hasAnyFilter =
    roleTitle.isNotBlank() ||
      companyName.isNotBlank() ||
      industry.isNotBlank() ||
      city.isNotBlank()

  val triggerSearch = {
    if (!isBusy && hasAnyFilter) {
      onSearchB2B(
        LinkedInSearchQuery(
          roleTitle = roleTitle,
          companyName = companyName,
          industry = industry,
          city = city,
          countryCode = countryCode,
          pageSize = 10,
        )
      )
    }
  }

  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Rounded.PersonSearch,
          contentDescription = null,
          tint = InkBlack,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        SectionLabel("No-API LinkedIn Lead Search", color = InkBlack)
      }
      StatusPill(
        text = "NO API NEEDED",
        containerColor = SuccessSurface,
        contentColor = SuccessInk,
        dotColor = SuccessDot,
      )
    }

    Spacer(Modifier.height(8.dp))
    Text(
      text =
        "Fetch real B2B LinkedIn profiles, roles, companies, and bio info directly from live web indexes without needing any paid API key.",
      style = MaterialTheme.typography.bodyMedium,
      color = InkGraphite,
    )

    Spacer(Modifier.height(10.dp))
    SectionLabel("Quick Role Filters", color = InkMuted)
    Spacer(Modifier.height(6.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf("VP Sales", "Founder", "CTO", "Head of Marketing").forEach { preset ->
        val selected = roleTitle.equals(preset, ignoreCase = true)
        Surface(
          onClick = { roleTitle = preset },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier = Modifier.minimumInteractiveComponentSize(),
        ) {
          Text(
            text = preset,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureWhite else InkBlack,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = roleTitle,
      onValueChange = { roleTitle = it },
      label = { Text("Job Title / Role (e.g. VP Sales, CEO)") },
      leadingIcon = { Icon(Icons.Rounded.WorkOutline, contentDescription = null) },
      colors = fieldColors,
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth().testTag("b2b_role_input"),
    )

    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = companyName,
      onValueChange = { companyName = it },
      label = { Text("Company Name (optional, e.g. Infosys)") },
      leadingIcon = { Icon(Icons.Rounded.Business, contentDescription = null) },
      colors = fieldColors,
      singleLine = true,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
      modifier = Modifier.fillMaxWidth().testTag("b2b_company_input"),
    )

    Spacer(Modifier.height(10.dp))
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      if (maxWidth >= 520.dp) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          OutlinedTextField(
            value = industry,
            onValueChange = { industry = it },
            label = { Text("Industry (optional)") },
            colors = fieldColors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.weight(1f).testTag("b2b_industry_input"),
          )
          OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City (optional)") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
            colors = fieldColors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { triggerSearch() }),
            modifier = Modifier.weight(1f).testTag("b2b_city_input"),
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(
            value = industry,
            onValueChange = { industry = it },
            label = { Text("Industry (optional, e.g. Software)") },
            colors = fieldColors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("b2b_industry_input"),
          )
          OutlinedTextField(
            value = city,
            onValueChange = { city = it },
            label = { Text("City (optional, e.g. Bengaluru)") },
            leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
            colors = fieldColors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { triggerSearch() }),
            modifier = Modifier.fillMaxWidth().testTag("b2b_city_input"),
          )
        }
      }
    }

    Spacer(Modifier.height(12.dp))
    SectionLabel("Target Country", color = InkMuted)
    Spacer(Modifier.height(6.dp))
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      listOf("IN" to "India", "US" to "USA", "GB" to "UK", "SG" to "Singapore", "AE" to "UAE").forEach {
        (code, label) ->
        val selected = countryCode == code
        Surface(
          onClick = { countryCode = code },
          shape = RoundedCornerShape(8.dp),
          color = if (selected) InkBlack else SoftAlabaster,
          border = BorderStroke(1.dp, if (selected) InkBlack else HairlineBorder),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("country_chip_$code"),
        ) {
          Text(
            text = "$code · $label",
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureWhite else InkBlack,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
          )
        }
      }
    }

    Spacer(Modifier.height(14.dp))
    Button(
      onClick = triggerSearch,
      enabled = !isBusy && hasAnyFilter,
      colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier.fillMaxWidth().height(52.dp).testTag("search_b2b_leads_button"),
    ) {
      if (isBusy) {
        CircularProgressIndicator(
          color = PureWhite,
          strokeWidth = 2.dp,
          modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
      } else {
        Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
      }
      Text("Fetch LinkedIn Leads (No API)", fontWeight = FontWeight.Bold)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StructuredLeadResultsList(
  candidates: List<LinkedInCandidate>,
  isBusy: Boolean,
  onToggleCandidate: (String) -> Unit,
  onSelectAll: (Boolean) -> Unit,
  onClearCandidates: () -> Unit,
  onSaveSelected: (String) -> Unit,
) {
  var clientBatchName by rememberSaveable { mutableStateOf("") }
  val selectedCount = remember(candidates) { candidates.count { it.selected } }
  val allSelected = remember(candidates) { candidates.isNotEmpty() && candidates.all { it.selected } }
  val totalCost = selectedCount * LINKEDIN_COST_PER_LEAD

  LeadXCard(borderColor = StrongBorder) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        SectionLabel("Structured B2B Lead Results", color = InkMuted)
        Spacer(Modifier.height(2.dp))
        Text(
          text =
            if (candidates.isEmpty()) "No search results loaded"
            else "${candidates.size} Prospect${if (candidates.size > 1) "s" else ""} · $selectedCount Selected",
          style = MaterialTheme.typography.titleMedium,
          color = InkBlack,
        )
      }
      if (candidates.isNotEmpty()) {
        MonochromeBadgeTag(
          text = "-${formatPoints(totalCost)} PTS",
          inverted = true,
        )
      }
    }

    if (candidates.isEmpty()) {
      Spacer(Modifier.height(12.dp))
      Surface(
        shape = MaterialTheme.shapes.small,
        color = SoftAlabaster,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth().testTag("b2b_results_empty_state"),
      ) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Icon(
            imageVector = Icons.Rounded.PersonSearch,
            contentDescription = null,
            tint = InkBlack,
            modifier = Modifier.size(32.dp),
          )
          Spacer(Modifier.height(10.dp))
          Text(
            text = "Run a No-API LinkedIn search or capture profiles via URL / DOM",
            style = MaterialTheme.typography.titleMedium,
            color = InkBlack,
          )
          Spacer(Modifier.height(4.dp))
          Text(
            text =
              "Matching leads appear here with role, company, industry, location, and bio data ($LINKEDIN_COST_PER_LEAD pts/lead on DB save + auto-creates a separate Client CSV).",
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
          )
        }
      }
    } else {
      Spacer(Modifier.height(10.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(
          onClick = { onSelectAll(!allSelected) },
          modifier = Modifier.minimumInteractiveComponentSize().testTag("select_all_candidates_button"),
        ) {
          Icon(
            imageVector =
              if (allSelected) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = InkBlack,
            modifier = Modifier.size(18.dp),
          )
          Spacer(Modifier.width(6.dp))
          Text(
            text = if (allSelected) "Deselect All" else "Select All",
            color = InkBlack,
            fontWeight = FontWeight.Bold,
          )
        }
        OutlinedButton(
          onClick = onClearCandidates,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_candidates_button"),
        ) {
          Text("Clear Results", color = InkBlack)
        }
      }

      Spacer(Modifier.height(6.dp))
      Surface(
        shape = MaterialTheme.shapes.small,
        color = PaperBackground,
        border = BorderStroke(1.dp, HairlineBorder),
        modifier = Modifier.fillMaxWidth().testTag("b2b_structured_results_list"),
      ) {
        Column {
          // Structured list header bar
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .background(SoftAlabaster)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            SectionLabel("Prospect · Role & Company", color = InkGraphite)
            SectionLabel("Contact & Source", color = InkGraphite)
          }
          EditorialDivider()

          candidates.forEachIndexed { idx, candidate ->
            if (idx > 0) EditorialDivider()
            StructuredCandidateListRow(
              index = idx + 1,
              candidate = candidate,
              onToggle = { onToggleCandidate(candidate.id) },
            )
          }
        }
      }

      Spacer(Modifier.height(12.dp))
      OutlinedTextField(
        value = clientBatchName,
        onValueChange = { clientBatchName = it },
        label = { Text("Client / Separate CSV Name (optional, e.g. Acme Client)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().testTag("save_client_csv_name_input"),
      )

      Spacer(Modifier.height(10.dp))
      Button(
        onClick = { onSaveSelected(clientBatchName) },
        enabled = !isBusy && selectedCount > 0,
        colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("save_linkedin_leads_button"),
      ) {
        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
          text =
            "Save $selectedCount Lead${if (selectedCount > 1) "s" else ""} & CSV (-${formatPoints(totalCost)} pts)",
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StructuredCandidateListRow(
  index: Int,
  candidate: LinkedInCandidate,
  onToggle: () -> Unit,
) {
  Surface(
    onClick = onToggle,
    color = if (candidate.selected) PureWhite else PaperBackground,
    modifier = Modifier.fillMaxWidth().testTag("lead_result_row_${candidate.id}"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
      verticalAlignment = Alignment.Top,
    ) {
      Checkbox(
        checked = candidate.selected,
        onCheckedChange = { onToggle() },
        colors =
          CheckboxDefaults.colors(
            checkedColor = InkBlack,
            uncheckedColor = InkMuted,
            checkmarkColor = PureWhite,
          ),
        modifier = Modifier.minimumInteractiveComponentSize(),
      )
      Spacer(Modifier.width(4.dp))
      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            MonoBadge("#${index.toString().padStart(2, '0')}", color = InkMuted)
            Text(
              text = candidate.name,
              style = MaterialTheme.typography.titleMedium,
              color = InkBlack,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          Spacer(Modifier.width(8.dp))
          MonochromeBadgeTag(text = candidate.captureMethod)
        }

        val roleLine =
          listOf(candidate.title, candidate.company)
            .filter { it.isNotBlank() }
            .joinToString(" @ ")
        if (roleLine.isNotBlank()) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = roleLine,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = InkBlack,
          )
        }
        if (candidate.bio.isNotBlank()) {
          Spacer(Modifier.height(4.dp))
          Text(
            text = candidate.bio,
            style = MaterialTheme.typography.bodyMedium,
            color = InkGraphite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }

        val hasStructuredMeta =
          candidate.company.isNotBlank() ||
            candidate.industry.isNotBlank() ||
            candidate.location.isNotBlank() ||
            candidate.email.isNotBlank() ||
            candidate.phone.isNotBlank()
        if (hasStructuredMeta) {
          Spacer(Modifier.height(8.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            if (candidate.company.isNotBlank()) {
              StructuredMetaChip(Icons.Rounded.Business, candidate.company)
            }
            if (candidate.industry.isNotBlank()) {
              StructuredMetaChip(Icons.Rounded.Public, candidate.industry)
            }
            if (candidate.location.isNotBlank()) {
              StructuredMetaChip(Icons.Rounded.LocationOn, candidate.location)
            }
            if (candidate.email.isNotBlank()) {
              StructuredMetaChip(Icons.Rounded.Email, candidate.email)
            }
            if (candidate.phone.isNotBlank()) {
              StructuredMetaChip(Icons.Rounded.Phone, candidate.phone)
            }
          }
        }

        if (candidate.profileUrl.isNotBlank()) {
          Spacer(Modifier.height(6.dp))
          Text(
            text = candidate.profileUrl,
            fontFamily = MonoFontFamily,
            style = MaterialTheme.typography.labelSmall,
            color = InkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun StructuredMetaChip(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = SoftAlabaster,
    border = BorderStroke(1.dp, HairlineBorder),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(icon, contentDescription = null, tint = InkGraphite, modifier = Modifier.size(13.dp))
      Spacer(Modifier.width(5.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = InkBlack,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
