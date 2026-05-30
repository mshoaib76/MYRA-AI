package com.myra.assistant.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.myra.assistant.R
import com.myra.assistant.ui.main.MainActivity
import com.myra.assistant.util.AuthManager
import com.myra.assistant.util.PrefsHelper
import com.google.firebase.auth.FirebaseAuth

class AuthActivity : AppCompatActivity() {

    private lateinit var emailInput: TextInputEditText
    private lateinit var passInput: TextInputEditText
    private lateinit var confirmInput: TextInputEditText
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var primaryBtn: Button
    private lateinit var switchBtn: Button
    private lateinit var progress: ProgressBar

    private var isSignUp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (AuthManager.isSignedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_auth)

        emailInput = findViewById(R.id.authEmailInput)
        passInput = findViewById(R.id.authPasswordInput)
        confirmInput = findViewById(R.id.authConfirmPasswordInput)
        titleText = findViewById(R.id.authTitle)
        subtitleText = findViewById(R.id.authSubtitle)
        primaryBtn = findViewById(R.id.authPrimaryBtn)
        switchBtn = findViewById(R.id.authSwitchBtn)
        progress = findViewById(R.id.authProgress)

        render()

        primaryBtn.setOnClickListener { submit() }
        switchBtn.setOnClickListener {
            isSignUp = !isSignUp
            render()
        }
    }

    private fun render() {
        titleText.text = if (isSignUp) getString(R.string.sign_up_title) else getString(R.string.sign_in_title)
        subtitleText.text = if (isSignUp) getString(R.string.sign_up_subtitle) else getString(R.string.sign_in_subtitle)
        primaryBtn.text = if (isSignUp) getString(R.string.sign_up) else getString(R.string.sign_in)
        switchBtn.text = if (isSignUp) getString(R.string.switch_to_sign_in) else getString(R.string.switch_to_sign_up)
        confirmInput.visibility = if (isSignUp) View.VISIBLE else View.GONE
    }

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        primaryBtn.isEnabled = !loading
        switchBtn.isEnabled = !loading
        emailInput.isEnabled = !loading
        passInput.isEnabled = !loading
        confirmInput.isEnabled = !loading
    }

    private fun submit() {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val pass = passInput.text?.toString().orEmpty()
        val confirm = confirmInput.text?.toString().orEmpty()

        if (email.isBlank() || pass.isBlank()) {
            Toast.makeText(this, R.string.auth_fill_fields, Toast.LENGTH_LONG).show()
            return
        }

        if (isSignUp) {
            if (pass.length < 6) {
                Toast.makeText(this, R.string.auth_password_short, Toast.LENGTH_LONG).show()
                return
            }
            if (pass != confirm) {
                Toast.makeText(this, R.string.auth_password_mismatch, Toast.LENGTH_LONG).show()
                return
            }
        }

        setLoading(true)
        val auth = FirebaseAuth.getInstance()
        val task = if (isSignUp) {
            auth.createUserWithEmailAndPassword(email, pass)
        } else {
            auth.signInWithEmailAndPassword(email, pass)
        }

        task.addOnCompleteListener { t ->
            setLoading(false)
            if (t.isSuccessful) {
                PrefsHelper.setLoggedIn(this, true)
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                val msg = t.exception?.localizedMessage ?: getString(R.string.auth_failed)
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}

