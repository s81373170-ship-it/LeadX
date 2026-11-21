package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.model.FlashBanner
import com.example.ui.components.FlashBannerView
import com.example.ui.components.LeadXCard
import com.example.ui.components.MonolithWalletCard
import com.example.ui.components.MonochromeBadgeTag
import com.example.ui.components.SectionLabel
import com.example.ui.components.SegmentedMonochromeTabs
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InkGraphite
import com.example.ui.theme.InkMuted
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianMuted
import com.example.ui.theme.PaperBackground
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SoftAlabaster
import com.example.ui.theme.StrongBorder

@Composable
fun AuthLoginScreen(
  flashBanner: FlashBanner?,
  isBusy: Boolean,
  googleUserEmail: String?,
  onSignIn: (String, String) -> Unit,
  onRegisterOwner: (String, String, String) -> Unit,
  onConnectGoogle: () -> Unit,
  onSignOutGoogle: () -> Unit,
  onOpenShareApk: () -> Unit = {},
  onOpenHowToInstall: () -> Unit = {},
  onDismissFlash: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var authModeTab by rememberSaveable { mutableIntStateOf(0) }
  var loginId by rememberSaveable { mutableStateOf("") }
  var fullName by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }

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
    contentAlignment = Alignment.Center,
  ) {
    Column(
      modifier =
        Modifier.widthIn(max = 520.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
      MonolithWalletCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.weight(1f)) {
            SectionLabel("LeadX Pro · B2B Intelligence", color = ObsidianMuted)
          }
          Spacer(Modifier.width(8.dp))
          com.example.ui.components.AppVersionBadge(
            inverted = true,
            testTag = "login_app_version_badge",
          )
        }
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Owner & Employee Access",
          style = MaterialTheme.typography.headlineMedium,
          color = PureWhite,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          text = "Separate Owner & Employee IDs · ₹1 = 1 Pt · 5,000 Pts Minimum Reserve",
          style = MaterialTheme.typography.bodyMedium,
          color = ObsidianMuted,
        )
      }

      Spacer(Modifier.height(14.dp))
      FlashBannerView(banner = flashBanner, onDismiss = onDismissFlash)

      LeadXCard(borderColor = StrongBorder) {
        SegmentedMonochromeTabs(
          tabs = listOf("Sign In (ID & Pass)", "Create Owner ID"),
          selectedIndex = authModeTab,
          onSelect = {
            authModeTab = it
            onDismissFlash()
          },
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
          value = loginId,
          onValueChange = { loginId = it.uppercase() },
          label = { Text(if (authModeTab == 0) "Login ID (e.g. OWN1001 or EMP001)" else "New Owner ID (e.g. OWN2001)") },
          leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
          colors = fieldColors,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("login_id_input"),
        )

        if (authModeTab == 1) {
          Spacer(Modifier.height(10.dp))
          OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text("Owner Full Name") },
            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
            colors = fieldColors,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("register_owner_name_input"),
          )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("Password") },
          leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(
              onClick = { passwordVisible = !passwordVisible },
              modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
              Icon(
                imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password",
              )
            }
          },
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          colors = fieldColors,
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("login_password_input"),
        )

        Spacer(Modifier.height(16.dp))

        Button(
          onClick = {
            if (authModeTab == 0) {
              onSignIn(loginId, password)
            } else {
              onRegisterOwner(loginId, fullName, password)
            }
          },
          enabled = !isBusy && loginId.isNotBlank() && password.isNotBlank(),
          colors = ButtonDefaults.buttonColors(containerColor = InkBlack, contentColor = PureWhite),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().height(52.dp).testTag("login_submit_button"),
        ) {
          Text(
            text = if (authModeTab == 0) "Sign In with ID & Password" else "Create Owner ID (5,000 Pts Min)",
            fontWeight = FontWeight.Bold,
          )
        }

        if (authModeTab == 0) {
          Spacer(Modifier.height(16.dp))
          SectionLabel("Default Workspace IDs", color = InkMuted)
          Spacer(Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            DemoCredentialChip(
              title = "Owner ID",
              id = "OWN1001",
              pass = "Owner@123",
              testTag = "quick_fill_owner",
              onClick = {
                loginId = "OWN1001"
                password = "Owner@123"
              },
              modifier = Modifier.weight(1f),
            )
            DemoCredentialChip(
              title = "Employee ID",
              id = "EMP001",
              pass = "Emp@123",
              testTag = "quick_fill_employee",
              onClick = {
                loginId = "EMP001"
                password = "Emp@123"
              },
              modifier = Modifier.weight(1f),
            )
          }
        }

        Spacer(Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = SoftAlabaster,
          border = BorderStroke(1.dp, HairlineBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = if (googleUserEmail.isNullOrBlank()) "STORAGE: LOCAL ROOM DB" else "SYNCED: $googleUserEmail",
                fontFamily = MonoFontFamily,
                style = MaterialTheme.typography.labelSmall,
                color = InkBlack,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
              )
              MonochromeBadgeTag(
                text = if (googleUserEmail.isNullOrBlank()) "OFFLINE READY" else "FIRESTORE ON",
                inverted = !googleUserEmail.isNullOrBlank(),
              )
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
              onClick = if (googleUserEmail.isNullOrBlank()) onConnectGoogle else onSignOutGoogle,
              enabled = !isBusy,
              border = BorderStroke(1.dp, InkBlack),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("google_cloud_sync_button"),
            ) {
              Text(
                text = if (googleUserEmail.isNullOrBlank()) "Connect Google Cloud Sync (Optional)" else "Disconnect Google Account",
                color = InkBlack,
                fontWeight = FontWeight.SemiBold,
              )
            }
          }
        }
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.OutlinedButton(
          onClick = onOpenShareApk,
          enabled = !isBusy,
          border = BorderStroke(1.dp, HairlineBorder),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("login_share_apk_button"),
        ) {
          Text(
            text = "Share Installable App (.apk) / Download Link",
            color = InkBlack,
            fontWeight = FontWeight.SemiBold,
          )
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
          onClick = onOpenHowToInstall,
          enabled = !isBusy,
          modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("login_how_to_install_button"),
        ) {
          Icon(Icons.Rounded.Info, contentDescription = null, tint = InkGraphite, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            text = "How to Install (.apk) · Unknown Sources Guide",
            color = InkGraphite,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }
    }
  }
}

@Composable
private fun DemoCredentialChip(
  title: String,
  id: String,
  pass: String,
  testTag: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(10.dp),
    color = SoftAlabaster,
    border = BorderStroke(1.dp, HairlineBorder),
    modifier = modifier.minimumInteractiveComponentSize().testTag(testTag),
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = InkGraphite, fontWeight = FontWeight.Bold)
        MonochromeBadgeTag(text = id, inverted = true)
      }
      Spacer(Modifier.height(4.dp))
      Text(
        text = "Pass: $pass",
        fontFamily = MonoFontFamily,
        style = MaterialTheme.typography.labelSmall,
        color = InkBlack,
      )
    }
  }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true, name = "Auth Login Screen")
@Composable
private fun AuthLoginScreenPreview() {
  com.example.ui.theme.LeadXTheme {
    AuthLoginScreen(
      flashBanner = null,
      isBusy = false,
      googleUserEmail = null,
      onSignIn = { _, _ -> },
      onRegisterOwner = { _, _, _ -> },
      onConnectGoogle = {},
      onSignOutGoogle = {},
      onDismissFlash = {},
    )
  }
}
