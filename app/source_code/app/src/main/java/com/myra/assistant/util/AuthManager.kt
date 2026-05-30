package com.myra.assistant.util

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

object AuthManager {
    fun isSignedIn(context: Context): Boolean = 
        PrefsHelper.isLoggedIn(context) || FirebaseAuth.getInstance().currentUser != null

    fun signOut(context: Context) {
        PrefsHelper.setLoggedIn(context, false)
        FirebaseAuth.getInstance().signOut()
    }
}

