package com.chinhsiang.premiumnotes.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(private val context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val credentialManager: CredentialManager = CredentialManager.create(context)

    // 取得當前已登入的使用者
    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // 執行 Google 登入（使用 GetSignInWithGoogleOption 強制顯示帳號選擇視窗）
    suspend fun signIn(activity: Activity): Result<FirebaseUser> {
        return try {
            val webClientId = context.getString(com.chinhsiang.premiumnotes.R.string.default_web_client_id)

            // 使用 GetSignInWithGoogleOption 而非 GetGoogleIdOption
            // 這會直接顯示「使用 Google 帳號登入」的按鈕視窗，不依賴緩存
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
                .build()

            val request: GetCredentialRequest = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result: GetCredentialResponse = try {
                credentialManager.getCredential(
                    request = request,
                    context = activity
                )
            } catch (e: Exception) {
                return Result.failure(Exception("取得憑證失敗: ${e.message}"))
            }

            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)

                // 使用 Google Token 向 Firebase 認證
                val firebaseCredential =
                    GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(firebaseCredential).await()

                val user = authResult.user
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("登入成功，但無法獲取使用者資料"))
                }
            } else {
                Result.failure(Exception("登入憑證類型不符: ${credential.type}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("系統錯誤: ${e.message}"))
        }
    }

    // 執行登出
    fun signOut() {
        auth.signOut()
    }
}
