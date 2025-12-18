// app/src/main/java/com/mathsnew/mathsnew/HomeFragment.kt
// 首页Fragment - 九宫格模块选择页面

package com.mathsnew.mathsnew

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mathsnew.mathsnew.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
    }

    /**
     * 设置所有按钮的点击监听
     */
    private fun setupClickListeners() {
        // 微积分模块
        binding.btnModuleCalculus.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_calculus)
        }

        // 其他模块暂时无功能
        binding.btnModule1.setOnClickListener { showModuleComingSoon() }
        binding.btnModule2.setOnClickListener { showModuleComingSoon() }
        binding.btnModule3.setOnClickListener { showModuleComingSoon() }
        binding.btnModule4.setOnClickListener { showModuleComingSoon() }
        binding.btnModule5.setOnClickListener { showModuleComingSoon() }
        binding.btnModule6.setOnClickListener { showModuleComingSoon() }
        binding.btnModule7.setOnClickListener { showModuleComingSoon() }
        binding.btnModule8.setOnClickListener { showModuleComingSoon() }

        // 设置按钮
        binding.btnSettings.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_settings)
        }

        // 退出按钮
        binding.btnExit.setOnClickListener {
            showExitDialog()
        }
    }

    /**
     * 显示模块开发中提示
     */
    private fun showModuleComingSoon() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.app_name))
            .setMessage("此模块正在开发中...")
            .setPositiveButton(getString(R.string.confirm)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * 显示退出确认对话框
     */
    private fun showExitDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.btn_exit))
            .setMessage(getString(R.string.exit_confirm))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                requireActivity().finish()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}