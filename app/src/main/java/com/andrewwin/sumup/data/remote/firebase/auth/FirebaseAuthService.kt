package com.andrewwin.sumup.data.remote.firebase.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class AuthSession(
    val isSignedIn: Boolean = false,
    val displayName: String = "",
    val email: String = ""
)

@Singleton
class FirebaseSettingsAuthService @Inject constructor() {
    private val firebaseAuth: FirebaseAuth by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { FirebaseAuth.getInstance() }

    fun currentSession(): AuthSession {
        val user = firebaseAuth.currentUser ?: return AuthSession()
        return AuthSession(
            isSignedIn = true,
            displayName = user.displayName.orEmpty(),
            email = user.email.orEmpty()
        )
    }

    suspend fun signInWithEmail(email: String, password: String, register: Boolean): AuthSession {
        if (register) {
            firebaseAuth.createUserWithEmailAndPassword(email.trim(), password).await()
        } else {
            firebaseAuth.signInWithEmailAndPassword(email.trim(), password).await()
        }
        return currentSession()
    }

    suspend fun signInWithGoogleIdToken(idToken: String): AuthSession {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        firebaseAuth.signInWithCredential(credential).await()
        return currentSession()
    }

    fun signOut() {
        firebaseAuth.signOut()
    }

    fun currentUserId(): String? = firebaseAuth.currentUser?.uid
}
