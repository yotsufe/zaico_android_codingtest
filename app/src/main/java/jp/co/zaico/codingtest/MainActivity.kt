package jp.co.zaico.codingtest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import dagger.hilt.android.AndroidEntryPoint
import jp.co.zaico.codingtest.databinding.ActivityMainBinding

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = navController()
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onSupportNavigateUp(): Boolean = navController().navigateUp(appBarConfiguration) || super.onSupportNavigateUp()

    /**
     * ナビゲーションのホストから NavController を取り出す。
     *
     * レイアウトが FragmentContainerView なので `Activity.findNavController(viewId)` は使えない。
     * NavHostFragment はビューの生成より後に FragmentManager が組み立てるため、
     * onCreate の時点ではビューにコントローラが結び付いていない。
     */
    private fun navController(): NavController {
        val host = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        return checkNotNull(host as? NavHostFragment).navController
    }
}
