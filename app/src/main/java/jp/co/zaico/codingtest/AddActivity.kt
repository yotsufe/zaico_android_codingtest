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
import jp.co.zaico.codingtest.databinding.ActivityAddBinding
import kotlinx.coroutines.launch

/**
 * 在庫データを作成する画面。
 *
 * 判断も通信も AddViewModel が持ち、この画面は入力を渡して状態を描画するだけに留めている。
 */
@AndroidEntryPoint
class AddActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddBinding
    private val viewModel: AddViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_add)
        binding.viewModel = viewModel

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    private fun render(state: AddUiState) {
        val saving = state is AddUiState.Saving
        binding.progressBar.isVisible = saving
        binding.saveButton.isEnabled = !saving
        binding.titleInputText.isEnabled = !saving

        binding.titleInputLayout.error = if (state is AddUiState.TitleRequired) {
            getString(R.string.error_title_required)
        } else {
            null
        }

        when (state) {
            is AddUiState.Completed -> showSuccessAndFinish()
            is AddUiState.Failed -> showError(state.error)
            else -> Unit
        }
    }

    private fun showSuccessAndFinish() {
        Toast.makeText(
            this,
            getString(R.string.message_create_inventory_success),
            Toast.LENGTH_SHORT
        ).show()
        viewModel.onResultHandled()
        finish()
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            this,
            getString(R.string.error_create_inventory, messageOf(error)),
            Toast.LENGTH_LONG
        ).show()
        viewModel.onResultHandled()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        fun createIntent(context: Context) = Intent(context, AddActivity::class.java)
    }

}
