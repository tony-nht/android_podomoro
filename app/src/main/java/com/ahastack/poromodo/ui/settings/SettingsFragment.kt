package com.ahastack.poromodo.ui.settings

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.ahastack.poromodo.databinding.FragmentSettingsBinding
import com.ahastack.poromodo.preferences.DataStoreManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ahastack.poromodo.preferences.Settings

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private var selectedRingtoneTitle: String = ""
    private lateinit var selectedRingtoneUri: Uri

    // Register Activity Result Launcher for Ringtone Picker
    private var ringtonePickerLauncher: ActivityResultLauncher<Intent>? = null

    private fun getSoundTrackTitleFromUri(uri: Uri): String {
        val ringtone = RingtoneManager.getRingtone(requireContext(), uri)
        return ringtone.getTitle(requireContext())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ringtonePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri: Uri? =
                        result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    if (uri != null) {
                        selectedRingtoneUri = uri
                        binding.notificationSoundInput.text = getSoundTrackTitleFromUri(uri)
                    } else {
                        selectedRingtoneTitle = ""
                        binding.notificationSoundInput.text = "Select a ringtone"
                    }
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Load preferences
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(state = Lifecycle.State.STARTED) {
                launch {
                    DataStoreManager.getSettings(requireContext()).collectLatest {
                        binding.pomodoroDurationInput.setText(it.podomoroDuration.toString())
                        binding.breakTimeInput.setText(it.breakTime.toString())
                        binding.breakLongTimeInput.setText(it.longBreakTime.toString())

                        selectedRingtoneUri = it.notiSoundTrack.toUri()
                        val title = getSoundTrackTitleFromUri(selectedRingtoneUri)
                        binding.notificationSoundInput.text = title
                    }
                }
            }
        }

        binding.notificationSoundChangeBtn.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Notification Sound")
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI,
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                )
                // If there’s a previously selected URI, pass it to pre-select in the picker
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, selectedRingtoneUri)
            }
            ringtonePickerLauncher?.launch(intent)
        }
        binding.saveButton.setOnClickListener {
            val pomodoroDuration = binding.pomodoroDurationInput.text.toString().toInt()
            val breakDuration = binding.breakTimeInput.text.toString().toInt()
            val longBreakDuration = binding.breakLongTimeInput.text.toString().toInt()
            when {
                pomodoroDuration == 0 -> {
                    binding.pomodoroDurationLayout.error = "Enter valid duration"
                    return@setOnClickListener
                }

                breakDuration == 0 -> {
                    binding.breakTimeLayout.error = "Enter valid duration"
                    return@setOnClickListener
                }

                longBreakDuration == 0 -> {
                    binding.breakLongTimeLayout.error = "Enter valid duration"
                    return@setOnClickListener
                }

                else -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        launch {
                            DataStoreManager.saveSettings(
                                requireContext(),
                                Settings(
                                    podomoroDuration = pomodoroDuration,
                                    breakTime = breakDuration,
                                    longBreakTime = longBreakDuration,
                                    notiSoundTrack = selectedRingtoneUri.toString()
                                )
                            )
                        }

                        withContext(Dispatchers.Main) {
                            Snackbar.make(view, "Setting Updated!", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            // Save

        }
    }
}