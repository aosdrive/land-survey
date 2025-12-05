package pk.gop.pulse.katchiAbadi.ui.activities

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import pk.gop.pulse.katchiAbadi.adapter.ViewPagerAdapter
import pk.gop.pulse.katchiAbadi.databinding.ActivityViewpagerBinding
import pk.gop.pulse.katchiAbadi.common.ImageDetails
import dagger.hilt.android.AndroidEntryPoint
import pk.gop.pulse.katchiAbadi.presentation.util.ToastUtil
import java.util.*

@AndroidEntryPoint
class OfflineViewpagerActivity : AppCompatActivity(), ViewPager.OnPageChangeListener {

    private lateinit var binding: ActivityViewpagerBinding

    private lateinit var adapter: PagerAdapter
    private lateinit var context: Context
    private lateinit var imagePathList: ArrayList<ImageDetails>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewpagerBinding.inflate(layoutInflater)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(binding.root)
        context = this
        // Set ActionBar title to uppercase
        supportActionBar?.title = supportActionBar?.title?.toString()?.uppercase()

        try {
            val bundle = intent.getBundleExtra("bundle_data")
            if (bundle != null) {
                imagePathList =
                    (bundle.getSerializable("imagePathList") as ArrayList<ImageDetails>?)!!
                adapter = ViewPagerAdapter(this, imagePathList)
                binding.viewPager.adapter = adapter
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Exception2:$e", Toast.LENGTH_SHORT).show()
        }

        binding.viewPager.addOnPageChangeListener(this)

        binding.tvPageNumber.text = "${binding.viewPager.currentItem + 1}/${imagePathList.size}"

        checkFabVisibility(binding.viewPager.currentItem)

        binding.fabLeft.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current == 0) {
                ToastUtil.showShort(
                    applicationContext,
                    "Already Viewing First Image"
                )
            } else {
                binding.viewPager.currentItem = current - 1
            }
        }

        binding.fabRight.setOnClickListener {
            val current = binding.viewPager.currentItem
            if (current == imagePathList.size - 1) {
                ToastUtil.showShort(
                    applicationContext,
                    "Already Viewing Last Image"
                )
            } else {
                binding.viewPager.currentItem = current + 1
            }
        }
    }

    private fun checkFabVisibility(position: Int) {
        when (position) {
            0 -> {
                if (imagePathList.size == 1) {
                    binding.fabLeft.hide()
                    binding.fabRight.hide()
                } else {
                    binding.fabLeft.hide()
                    binding.fabRight.show()
                }
            }

            imagePathList.size - 1 -> {
                binding.fabLeft.show()
                binding.fabRight.hide()
            }

            else -> {
                binding.fabLeft.show()
                binding.fabRight.show()
            }
        }
    }

    override fun onPageSelected(position: Int) {
        binding.tvPageNumber.text = "${binding.viewPager.currentItem + 1}/${imagePathList.size}"
        checkFabVisibility(position)
    }

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
}
