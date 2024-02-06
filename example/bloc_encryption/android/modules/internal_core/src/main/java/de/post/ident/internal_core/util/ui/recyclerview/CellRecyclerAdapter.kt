package de.post.ident.internal_core.util.ui.recyclerview

import android.util.SparseArray
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import de.post.ident.internal_core.databinding.PiProcessDescriptionPageBinding
import de.post.ident.internal_core.process_description.ProcessDescriptionPageCell
import de.post.ident.internal_core.util.ui.recyclerview.CellRecyclerAdapter.CellAdapterViewHolder
import kotlin.reflect.KClass


interface BaseCell<VH: ViewBinding> {
    val viewFactory: ViewFactory<VH>
    fun bindView(viewHolder: VH)
}

interface AdvancedCell<VH: ViewBinding> {
    fun attachViewHolder(viewHolder: VH)
    fun detachViewHolder()
}

interface ViewFactory<VH: ViewBinding> {
    fun createViewHolder(parent: ViewGroup): VH
}

abstract class ViewBindingCell<T: ViewBinding> : BaseCell<T> {
    override val viewFactory = object : ViewFactory<T> {
        override fun createViewHolder(parent: ViewGroup): T {
            val inflater = LayoutInflater.from(parent.context)
            return inflate(inflater, parent, false)
        }
    }

    abstract fun inflate(inflater: LayoutInflater, parent: ViewGroup, attachToParent: Boolean): T
}

class CellRecyclerAdapter(private val mCellList: List<BaseCell<*>>) : RecyclerView.Adapter<CellAdapterViewHolder>() {

    private val viewTypes = SparseArray<ViewFactory<*>>()
    private val factoryMap = mutableMapOf<ViewFactory<*>, Int>()

    class CellAdapterViewHolder(val viewHolder: ViewBinding) : RecyclerView.ViewHolder(viewHolder.root)

    override fun getItemViewType(position: Int): Int {
        val viewType = mCellList[position].viewFactory
        val typeId = factoryMap[viewType]
        return if (typeId == null) {
            val id = factoryMap.size
            factoryMap[viewType] = id
            viewTypes.put(id, viewType)
            id
        } else {
            typeId
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellAdapterViewHolder {
        val vt = checkNotNull(viewTypes[viewType])
        val viewHolder = vt.createViewHolder(parent)
        return CellAdapterViewHolder(viewHolder)
    }

    override fun onBindViewHolder(holder: CellAdapterViewHolder, position: Int) {
        val cell = mCellList[position] as BaseCell<ViewBinding>
        cell.bindView(holder.viewHolder)

        //for testautomation
        if (holder.viewHolder is PiProcessDescriptionPageBinding) {
            holder.viewHolder.title.contentDescription = "process_descr_txt_img" + (position+1)
        }
    }

    override fun onViewAttachedToWindow(holder: CellAdapterViewHolder) {
        val cell = mCellList[holder.adapterPosition]
        (cell as? AdvancedCell<*>)?.let {
            (it as AdvancedCell<ViewBinding>).attachViewHolder(holder.viewHolder)
        }
    }

    override fun onViewDetachedFromWindow(holder: CellAdapterViewHolder) {
        val position = holder.adapterPosition
        if (position >= 0) {
            val cell = mCellList[position]
            if (cell is AdvancedCell<*>) {
                (cell as AdvancedCell<*>).detachViewHolder()
            }
        }
    }

    override fun getItemCount(): Int {
        return mCellList.size
    }
}

