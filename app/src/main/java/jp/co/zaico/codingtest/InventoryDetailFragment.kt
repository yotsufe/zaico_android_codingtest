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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.databinding.FragmentInventoryDetailBinding
import kotlinx.coroutines.launch
import java.util.Locale

@AndroidEntryPoint
class InventoryDetailFragment : Fragment() {

    private val args: InventoryDetailFragmentArgs by navArgs()
    private val viewModel: InventoryDetailViewModel by viewModels()
    private var _binding: FragmentInventoryDetailBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentInventoryDetailBinding.inflate(inflater, container, false)
        _binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }

        viewModel.fetchIfNeeded(args.inventoryId)
    }

    private fun render(state: InventoryDetailUiState) {
        binding.progressBar.isVisible = state is InventoryDetailUiState.Loading

        when (state) {
            is InventoryDetailUiState.Loading -> Unit
            is InventoryDetailUiState.Loaded -> showInventory(state)
            is InventoryDetailUiState.Failed -> state.error?.let {
                showError(it)
                // 表示済みにしないと、購読し直すたびに同じ Toast が出る
                viewModel.onErrorShown()
            }
        }
    }

    private fun showInventory(state: InventoryDetailUiState.Loaded) {
        val inventory = state.inventory
        binding.idText.text = String.format(Locale.ROOT, "%d", inventory.id)
        binding.titleText.text = inventory.title
        binding.quantityText.text = inventory.quantity
        showImage(state)
    }

    private fun showImage(state: InventoryDetailUiState.Loaded) {
        val inventory = state.inventory
        binding.imageView.contentDescription = if (state.canShowImage) {
            imageDescriptionOf(inventory.title)
        } else {
            getString(R.string.inventory_image_none)
        }

        Glide.with(this)
            .load(inventory.imageUrl)
            .placeholder(R.drawable.no_image)
            .error(R.drawable.no_image)
            .into(binding.imageView)

        binding.imageView.setOnClickListener(
            if (state.canShowImage) {
                View.OnClickListener {
                    findNavController().navigate(
                        InventoryDetailFragmentDirections.actionInventoryDetailToInventoryImage(
                            imageUrl = checkNotNull(inventory.imageUrl),
                            title = inventory.title,
                        ),
                    )
                }
            } else {
                null
            },
        )
        // setOnClickListener は null を渡しても内部で setClickable(true) を呼ぶ。
        // 先に isClickable を落とすと打ち消されるので、必ずこの順で書く。
        binding.imageView.isClickable = state.canShowImage
    }

    /** API のタイトルは欠けていると空文字になる。そのままだと「の画像」とだけ読み上げられる。 */
    private fun imageDescriptionOf(title: String): String = if (title.isBlank()) {
        getString(R.string.inventory_image_description_untitled)
    } else {
        getString(R.string.inventory_image_description, title)
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.error_load_inventory, requireContext().displayMessageOf(error)),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Glide.with(this) は view ではなく Fragment のライフサイクルに紐づくので、
        // view の破棄では自動解除されない。ここで外さないと、読み込み完了時に
        // 破棄済みの binding を触る。
        _binding?.imageView?.let { Glide.with(this).clear(it) }
        _binding = null
    }
}
