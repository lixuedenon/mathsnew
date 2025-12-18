// app/src/main/java/com/mathsnew/mathsnew/SettingsFragment.kt
// 设置Fragment - 语言选择页面

package com.mathsnew.mathsnew

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.mathsnew.mathsnew.databinding.FragmentSettingsBinding
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val PREFS_NAME = "MathGeniusSettings"
    private val LANGUAGE_KEY = "selected_language"

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
        loadSavedLanguage()
        setupListeners()
    }

    /**
     * 加载已保存的语言设置
     */
    private fun loadSavedLanguage() {
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString(LANGUAGE_KEY, "zh") ?: "zh"

        when (savedLanguage) {
            "zh" -> binding.radioLanguageChinese.isChecked = true
            "en" -> binding.radioLanguageEnglish.isChecked = true
            "ja" -> binding.radioLanguageJapanese.isChecked = true
        }
    }

    /**
     * 设置按钮监听
     */
    private fun setupListeners() {
        binding.btnSave.setOnClickListener {
            saveLanguageSettings()
        }
    }

    /**
     * 保存语言设置
     */
    private fun saveLanguageSettings() {
        val selectedLanguageCode = when (binding.radioGroupLanguage.checkedRadioButtonId) {
            R.id.radio_language_chinese -> "zh"
            R.id.radio_language_english -> "en"
            R.id.radio_language_japanese -> "ja"
            else -> "zh"
        }

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(LANGUAGE_KEY, selectedLanguageCode).apply()

        setLocale(selectedLanguageCode)

        Toast.makeText(
            requireContext(),
            "语言设置已保存，重启应用生效",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * 设置应用语言
     */
    private fun setLocale(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        requireContext().resources.updateConfiguration(
            config,
            requireContext().resources.displayMetrics
        )

        requireActivity().recreate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}