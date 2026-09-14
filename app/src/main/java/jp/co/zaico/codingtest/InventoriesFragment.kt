package jp.co.zaico.codingtest

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.databinding.FragmentInventoriesBinding
import jp.co.zaico.codingtest.databinding.ItemInventoryBinding
import kotlinx.coroutines.launch

@AndroidEntryPoint
class InventoriesFragment : Fragment() {

    private val viewModel: InventoriesViewModel by viewModels()
    private var _binding: FragmentInventoriesBinding? = null
    private val binding get() = checkNotNull(_binding)
    private var adapter: InventoryAdapter? = null

    /** 一覧の先頭にある在庫の ID。差し替え時にスクロール位置を戻すかの判定に使う。 */
    private var topInventoryId: Int? = null

    /**
     * 在庫データ作成画面の結果を受け取る。
     *
     * 作成に成功したときだけ読み込み直す。画面復帰のたびに読み込む方式だと、
     * 画面回転や通知を閉じただけでも API を叩いてしまう。
     */
    private val addInventoryLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.fetch()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val binding = FragmentInventoriesBinding.inflate(inflater, container, false)
        binding.fragment = this
        _binding = binding
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val layoutManager = LinearLayoutManager(requireContext())
        adapter = InventoryAdapter(object : InventoryAdapter.OnItemClickListener {
            override fun onItemClick(item: Inventory) {
                findNavController().navigate(
                    InventoriesFragmentDirections.actionInventoriesToInventoryDetail(item.id),
                )
            }
        })

        binding.recyclerView.also {
            it.layoutManager = layoutManager
            it.addItemDecoration(DividerItemDecoration(requireContext(), layoutManager.orientation))
            it.adapter = adapter
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { render(it) }
            }
        }
    }

    /**
     * まだ読み込めていなければ読み込む。
     *
     * 初回表示に加えて、読み込みに失敗したままアプリを離れて戻ってきたときの
     * 再試行を兼ねる。読み込み済みなら通信は起きない。
     */
    override fun onResume() {
        super.onResume()
        viewModel.fetchIfNeeded()
    }

    private fun render(state: InventoriesUiState) {
        binding.progressBar.isVisible = state is InventoriesUiState.Loading
        // 可視・不可視は毎回ここで決める。Loaded の分岐の中だけで切り替えると、
        // 0 件のあと Loading や Failed になったときに「在庫データがありません」が残る。
        binding.emptyText.isVisible = state is InventoriesUiState.Loaded && state.isEmpty

        when (state) {
            is InventoriesUiState.Loading -> Unit
            is InventoriesUiState.Loaded -> showInventories(state)
            is InventoriesUiState.Failed -> state.error?.let {
                showError(it)
                // 表示済みにしないと、購読し直すたびに同じ Toast が出る
                viewModel.onErrorShown()
            }
        }
    }

    private fun showInventories(state: InventoriesUiState.Loaded) {
        if (state.hasUnshownSkipped) {
            // 黙って件数を減らすと在庫が欠けたことに気づけないため、読み飛ばしたことを伝える。
            Toast.makeText(
                requireContext(),
                getString(R.string.inventories_skipped, state.skipped),
                Toast.LENGTH_LONG,
            ).show()
            // 表示済みにしないと、購読し直すたびに同じ Toast が出る
            viewModel.onSkippedShown()
        }
        submitInventories(state.inventories)
    }

    /**
     * 一覧を差し替える。先頭の在庫が入れ替わったときだけスクロール位置を先頭へ戻す。
     *
     * 新しく作成した在庫は一覧の先頭に来るが、スクロール位置はそのままなので
     * 画面外にあると気づけない。一方で無条件に戻すと、StateFlow は購読し直すたびに
     * 最後の値を再配信するので、アプリに復帰しただけで位置を失う。
     */
    private fun submitInventories(inventories: List<Inventory>) {
        val newTopId = inventories.firstOrNull()?.id
        val topChanged = newTopId != null && newTopId != topInventoryId
        topInventoryId = newTopId

        adapter?.submitList(inventories) {
            // 差分の適用後に呼ばれる。ここで初めて先頭の項目が存在する。
            // このコールバックは view の破棄後に届きうるので binding は安全参照で取る。
            if (topChanged) {
                _binding?.recyclerView?.scrollToPosition(0)
            }
        }
    }

    /** 在庫データ作成画面を開く。レイアウトの android:onClick から呼ばれる。 */
    fun openAddInventory() {
        addInventoryLauncher.launch(CreateInventoryActivity.createIntent(requireContext()))
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.error_load_inventories, requireContext().displayMessageOf(error)),
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ここで解放しないと Fragment がビューと一覧データを参照し続ける
        binding.recyclerView.adapter = null
        adapter = null
        _binding = null
    }
}

private val inventoryDiffCallback = object : DiffUtil.ItemCallback<Inventory>() {
    override fun areItemsTheSame(oldItem: Inventory, newItem: Inventory): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Inventory, newItem: Inventory): Boolean = oldItem == newItem
}

class InventoryAdapter(
    private val itemClickListener: OnItemClickListener,
) : ListAdapter<Inventory, InventoryAdapter.ViewHolder>(inventoryDiffCallback) {

    class ViewHolder(val binding: ItemInventoryBinding) : RecyclerView.ViewHolder(binding.root)

    interface OnItemClickListener {
        fun onItemClick(item: Inventory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemInventoryBinding.inflate(LayoutInflater.from(parent.context), parent, false),
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.inventory = getItem(position)
        holder.binding.clickListener = itemClickListener
        // RecyclerView の再利用で描画が 1 フレーム遅れるのを防ぐ
        holder.binding.executePendingBindings()
    }
}
