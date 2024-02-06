package de.post.ident.internal_core.util.ui

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Simple fragment view pager which can be used for displaying custom fragments inside ViewPager2.
 */
class PiFragmentAdapter(val viewList: List<Item>, val parentFragment: Fragment) {

    data class Item(val title: String, val contentDescription: String? = null, val fragmentCreator: () -> Fragment)

    fun attach(viewPager: ViewPager2, tabLayout: TabLayout) {
        viewPager.adapter = object : FragmentStateAdapter(parentFragment) {
            override fun createFragment(position: Int): Fragment = viewList[position].fragmentCreator()
            override fun getItemCount(): Int = viewList.size
            override fun getItemId(position: Int) = viewList[position].hashCode().toLong()
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = viewList[position].title
            tab.contentDescription = viewList[position].contentDescription
        }.attach()
    }
}