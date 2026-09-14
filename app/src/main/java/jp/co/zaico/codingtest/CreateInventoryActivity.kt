package jp.co.zaico.codingtest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.databinding.ActivityCreateInventoryBinding
import kotlinx.coroutines.launch

/**
 * 在庫データを作成する画面。
 *
 * 判断も通信も CreateInventoryViewModel が持ち、この画面は入力を渡して状態を描画するだけに留めている。
 */
@AndroidEntryPoint
class CreateInventoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateInventoryBinding
    private val viewModel: CreateInventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_create_inventory)
        binding.viewModel = viewModel

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: CreateInventoryUiState) {
        val saving = state is CreateInventoryUiState.Saving
        binding.progressBar.isVisible = saving
        binding.saveButton.isEnabled = !saving
        binding.titleInputText.isEnabled = !saving

        binding.titleInputLayout.error = if (state is CreateInventoryUiState.TitleRequired) {
            getString(R.string.error_title_required)
        } else {
            null
        }

        when (state) {
            is CreateInventoryUiState.Completed -> showSuccessAndFinish()
            is CreateInventoryUiState.Failed -> showError(state.error)
            else -> Unit
        }
    }

    private fun showSuccessAndFinish() {
        Toast.makeText(
            this,
            getString(R.string.message_create_inventory_success),
            Toast.LENGTH_SHORT,
        ).show()
        viewModel.onResultHandled()
        // 一覧画面がこれを受けて読み込み直す
        setResult(RESULT_OK)
        finish()
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            this,
            getString(R.string.error_create_inventory, displayMessageOf(error)),
            Toast.LENGTH_LONG,
        ).show()
        viewModel.onResultHandled()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, CreateInventoryActivity::class.java)
    }
}
