package jp.co.zaico.codingtest

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import jp.co.zaico.codingtest.databinding.FragmentInventoryImageBinding

/** 在庫画像を画面いっぱいに表示するダイアログ。 */
class InventoryImageDialogFragment : DialogFragment() {

    private val args: InventoryImageDialogFragmentArgs by navArgs()
    private var _binding: FragmentInventoryImageBinding? = null
    private val binding get() = checkNotNull(_binding)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val binding = FragmentInventoryImageBinding.inflate(inflater, container, false)
        _binding = binding
        return binding.root
    }

    /**
     * レイアウトを `match_parent` にしてもウィンドウは中身に合わせて縮む。
     * ここで指定しないと、小さな箱の中に画像が出る。
     */
    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            // 既定の白い角丸をやめて、レイアウト側の半透明グレーをそのまま見せる。
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // タイトルが欠けた在庫では空文字が渡る。そのままだと「の画像」になる。
        binding.imageView.contentDescription = if (args.title.isBlank()) {
            getString(R.string.inventory_image_description_untitled)
        } else {
            getString(R.string.inventory_image_description, args.title)
        }
        binding.closeButton.setOnClickListener { dismiss() }
        Glide.with(this)
            .load(args.imageUrl)
            // fitCenter は要求サイズが元画像より大きいと拡大してデコードする。
            // 全画面だと元の 2 倍・メモリ 4 倍になるが、ImageView が描画時に
            // 同じ倍率で拡大するので見た目は変わらない。元寸で止める。
            .downsample(DownsampleStrategy.CENTER_INSIDE)
            .placeholder(R.drawable.no_image)
            .error(R.drawable.no_image)
            .into(binding.imageView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.imageView?.let { Glide.with(this).clear(it) }
        _binding = null
    }
}
