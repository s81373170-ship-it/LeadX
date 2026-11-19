package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object GoogleAuthHelper {
  private const val TAG = "GoogleAuthHelper"

  suspend fun signInWithGoogle(
    activityContext: Context,
    auth: FirebaseAuth? = null,
  ): Result<FirebaseUser> {
    return try {
      val resId =
        activityContext.resources.getIdentifier(
          "default_web_client_id",
          "string",
          activityContext.packageName,
        )
      if (resId == 0) {
        return Result.failure(
          IllegalStateException("Google Cloud Sync is not configured in this build (missing google-services.json). Local Room DB stays active.")
        )
      }
      val firebaseAuth =
        auth ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
          ?: return Result.failure(
            IllegalStateException("Google Cloud Sync is not configured in this build (missing google-services.json). Local Room DB stays active.")
          )
      val webClientId = activityContext.getString(resId)
      val googleOption =
        GetSignInWithGoogleOption.Builder(webClientId)
          .build()
      val request =
        GetCredentialRequest.Builder()
          .addCredentialOption(googleOption)
          .build()

      val credentialManager = CredentialManager.create(activityContext)
      val response = credentialManager.getCredential(activityContext, request)
      val credential = response.credential

      if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val googleIdCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCred = GoogleAuthProvider.getCredential(googleIdCredential.idToken, null)
        val authResult = firebaseAuth.signInWithCredential(firebaseCred).await()
        val user = authResult.user
          ?: return Result.failure(IllegalStateException("Google Sign-In completed without a Firebase user."))
        Result.success(user)
      } else {
        Result.failure(IllegalStateException("Unexpected credential type returned by Google Sign-In."))
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Interactive Google Sign-In cancelled (check SHA-1/SHA-256 registration if persistent).", e)
      Result.failure(IllegalStateException("Sign-in was cancelled. Please select a Google account to continue."))
    } catch (e: NoCredentialException) {
      Log.w(TAG, "No Google credentials available on device.", e)
      Result.failure(IllegalStateException("No Google account found on this device. Add a Google account in device settings and retry."))
    } catch (e: GetCredentialException) {
      Log.w(TAG, "Credential Manager error during Google Sign-In.", e)
      Result.failure(IllegalStateException(e.message ?: "Google Sign-In failed."))
    } catch (e: Exception) {
      Log.e(TAG, "Unexpected error during Google Sign-In.", e)
      Result.failure(IllegalStateException(e.message ?: "Google Sign-In failed."))
    }
  }

  suspend fun signOut(
    context: Context,
    auth: FirebaseAuth? = null,
  ) {
    withContext(NonCancellable) {
      runCatching {
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
      }.onFailure { err ->
        Log.w(TAG, "Unable to clear Credential Manager state on sign-out.", err)
      }
      val firebaseAuth = auth ?: runCatching { FirebaseAuth.getInstance() }.getOrNull()
      firebaseAuth?.signOut()
    }
  }
}
