package com.myra.assistant.ui.profile

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.myra.assistant.R
import com.myra.assistant.personalization.PersonalizationStore
import com.myra.assistant.util.ProfileHelper
import com.myra.assistant.util.PrefsHelper

class ProfileFragment : Fragment() {

    private lateinit var profileImage: ImageView
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailText: android.widget.TextView
    private lateinit var removePhotoBtn: MaterialButton

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@registerForActivityResult
        val ok = ProfileHelper.savePhotoFromUri(requireContext(), uri)
        if (ok) {
            loadPhotoIntoView()
            removePhotoBtn.visibility = View.VISIBLE
            Toast.makeText(requireContext(), R.string.photo_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.photo_save_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        profileImage = view.findViewById(R.id.profileImage)
        nameInput = view.findViewById(R.id.profileNameInput)
        emailText = view.findViewById(R.id.profileEmail)
        removePhotoBtn = view.findViewById(R.id.btnRemovePhoto)

        nameInput.setText(displayName())
        emailText.text = getString(R.string.profile_email_label, currentEmail())

        view.findViewById<MaterialButton>(R.id.btnChangePhoto).setOnClickListener {
            pickImage.launch("image/*")
        }

        removePhotoBtn.setOnClickListener {
            ProfileHelper.clearPhoto(requireContext())
            profileImage.setImageResource(R.drawable.ic_nav_profile)
            removePhotoBtn.visibility = View.GONE
        }

        view.findViewById<MaterialButton>(R.id.btnSaveProfile).setOnClickListener {
            val name = nameInput.text?.toString()?.trim().orEmpty()
            if (name.isBlank()) {
                Toast.makeText(requireContext(), R.string.profile_name_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            PrefsHelper.setUserName(requireContext(), name)
            Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show()
        }

        loadPhotoIntoView()
        removePhotoBtn.visibility =
            if (ProfileHelper.hasPhoto(requireContext())) View.VISIBLE else View.GONE

        return view
    }

    private fun displayName(): String {
        val nick = PersonalizationStore.getNickname(requireContext())
        if (nick.isNotBlank()) return nick
        return PrefsHelper.getUserName(requireContext())
    }

    private fun currentEmail(): String {
        return FirebaseAuth.getInstance().currentUser?.email ?: "—"
    }

    private fun loadPhotoIntoView() {
        val bitmap = ProfileHelper.loadPhoto(requireContext()) ?: return
        profileImage.setImageBitmap(toCircleBitmap(bitmap))
    }

    private fun toCircleBitmap(source: Bitmap): Bitmap {
        val size = minOf(source.width, source.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val left = (source.width - size) / 2
        val top = (source.height - size) / 2
        canvas.drawBitmap(source, Rect(left, top, left + size, top + size), rect, paint)
        return output
    }
}
