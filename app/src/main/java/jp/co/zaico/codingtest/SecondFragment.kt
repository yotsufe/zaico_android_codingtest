package jp.co.zaico.codingtest

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.databinding.FragmentSecondBinding

@AndroidEntryPoint
class SecondFragment : Fragment() {

    private val viewModel: SecondViewModel by viewModels()
    private var _binding: FragmentSecondBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return _binding!!.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val inventoryId = requireArguments().getString("inventoryId")!!.toInt()

        viewModel.getInventory(inventoryId)
            .onSuccess { initView(it) }
            .onFailure { showError(it) }

    }

    private fun initView(inventory: Inventory) {
        _binding!!.textViewId.text = inventory.id.toString()
        _binding!!.textViewTitle.text = inventory.title
        _binding!!.textViewQuantity.text = inventory.quantity
    }

    private fun showError(error: Throwable) {
        Toast.makeText(
            requireContext(),
            getString(R.string.error_load_inventory, requireContext().messageOf(error)),
            Toast.LENGTH_LONG
        ).show()
    }

}
