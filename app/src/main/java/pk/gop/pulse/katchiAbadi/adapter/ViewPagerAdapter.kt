package pk.gop.pulse.katchiAbadi.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.common.ImageDetails
import java.util.ArrayList

class ViewPagerAdapter(var context: Context, var imagePathList: ArrayList<ImageDetails>) :
    PagerAdapter() {
    var inflater: LayoutInflater? = null
    lateinit var ivFlower: ImageView
    lateinit var tvPictureType: TextView
    override fun getCount(): Int {
        return imagePathList.size
    }

    override fun isViewFromObject(view: View, `object`: Any): Boolean {
        return view === `object`
    }

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val itemView = inflater!!.inflate(R.layout.viewpager_item, container, false)
        ivFlower = itemView.findViewById<View>(R.id.ivFlower) as ImageView
        tvPictureType = itemView.findViewById<View>(R.id.tv_picture_type) as TextView
        ivFlower.setImageBitmap(BitmapFactory.decodeFile(imagePathList[position].path))
        tvPictureType.text = imagePathList[position].type
        container.addView(itemView)
        return itemView
    }

    override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
        (container as ViewPager).removeView(`object` as RelativeLayout)
    }
}