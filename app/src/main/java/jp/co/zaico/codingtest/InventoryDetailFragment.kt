package jp.co.zaico.codingtest

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.databinding.FragmentInventoryDetailBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InventoryDetailFragment : Fragment() {

    private val viewModel: InventoryDetailViewModel by viewModels()
    private var _binding: FragmentInventoryDetailBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentInventoryDetailBinding.inflate(inflater, container, false)
        _binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inventoryId = requireArguments().getString("inventoryId")!!.toInt()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        viewModel.fetchIfNeeded(inventoryId)
    }

    private fun render(state: InventoryDetailUiState) {
        binding.progressBar.isVisible = state is InventoryDetailUiState.Loading

        when (state) {
            is InventoryDetailUiState.Loading -> Unit
            is InventoryDetailUiState.Loaded -> showInventory(state.inventory)
            is InventoryDetailUiState.Failed -> state.error?.let {
                showError(it)
                // 表示済みにしないと、購読し直すたびに同じ Toast が出る
                viewModel.onErrorShown()
            }
        }
    }

    private fun showInventory(inventory: Inventory) {
        binding.idText.text = inventory.id.toString()
        binding.titleText.text = inventory.title
        binding.quantityText.text = inventory.quantity
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.error_load_inventory, requireContext().displayMessageOf(error)),
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
