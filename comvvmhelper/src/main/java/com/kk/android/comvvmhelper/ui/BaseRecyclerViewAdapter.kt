@file:Suppress("MemberVisibilityCanBePrivate")

package com.kk.android.comvvmhelper.ui

import android.util.SparseArray
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.kk.android.comvvmhelper.extension.otherwise
import com.kk.android.comvvmhelper.extension.setOnDebounceClickListener
import com.kk.android.comvvmhelper.extension.yes
import com.kk.android.comvvmhelper.helper.KLogger
import com.kk.android.comvvmhelper.listener.OnRecyclerItemClickListener
import com.kk.android.comvvmhelper.listener.OnRecyclerItemLongClickListener

/**
 * @author kuky.
 * @description
 * @param openDebounce whether item click is debounced click, default is true
 */
abstract class BaseRecyclerViewAdapter<T : Any>(
    dataList: MutableList<T>? = null,
    private val openDebounce: Boolean = true,
    private val debounceDuration: Long = 300
) : RecyclerView.Adapter<BaseViewHolder>(), KLogger {

    companion object {
        private const val HEADER = 100_000

        private const val FOOTER = 200_000
    }

    protected var mDataList = dataList
    private val mHeaderViewList = SparseArray<ViewDataBinding>()
    private val mFooterViewList = SparseArray<ViewDataBinding>()

    var onItemClickListener: OnRecyclerItemClickListener? = null
    var onItemLongClickListener: OnRecyclerItemLongClickListener? = null

    open fun updateAdapterDataListWithoutAnim(dataList: MutableList<T>?) {
        mDataList = dataList
        notifyDataSetChanged()
    }

    /**
     * refresh data by diffutil
     */
    open fun updateAdapterDataListWithAnim(helper: BaseDiffCallback<T>) {
        helper.oldList = getAdapterDataList()
        val result = DiffUtil.calculateDiff(helper, true)
        result.dispatchUpdatesTo(BaseListUpdateCallback(this))
        mDataList = helper.getNewItems()
    }

    /**
     * append data at head
     */
    fun appendDataAtHeadWithAnim(dataList: MutableList<T>) {
        mDataList = (mDataList ?: mutableListOf()).apply { addAll(0, dataList) }
        notifyItemRangeInserted(getHeaderSize(), dataList.size)
    }

    fun appendDataAtHeadWithoutAnim(dataList: MutableList<T>) {
        mDataList = (mDataList ?: mutableListOf()).apply { addAll(0, dataList) }
        notifyDataSetChanged()
    }

    /**
     * append data at tail
     */
    fun appendDataAtTailWithAnim(dataList: MutableList<T>) {
        val rangeStar = getDataSize()
        mDataList = (mDataList ?: mutableListOf()).apply { addAll(dataList) }
        notifyItemRangeInserted(getHeaderSize() + rangeStar, dataList.size)
    }

    fun appendDataAtTailWithoutAnim(dataList: MutableList<T>) {
        mDataList = (mDataList ?: mutableListOf()).apply { addAll(dataList) }
        notifyDataSetChanged()
    }

    /**
     * remove an item
     */
    fun removeDataAtPosition(position: Int) {
        mDataList?.let {
            it.removeAt(position)
            val realPosition = position + getHeaderSize()
            notifyItemRemoved(realPosition)

            if (realPosition != getDataSize() + getHeaderSize())
                notifyItemRangeChanged(realPosition, getDataSize() + getHeaderSize() - realPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder =
        if (haveHeader() && mHeaderViewList.get(viewType) != null) {
            BaseViewHolder(mHeaderViewList.get(viewType))
        } else if (haveFooter() && mFooterViewList.get(viewType) != null) {
            BaseViewHolder(mFooterViewList.get(viewType))
        } else {
            BaseViewHolder.createHolder(parent, layoutId(viewType))
        }

    abstract fun layoutId(viewType: Int): Int

    override fun getItemCount() = getHeaderSize() + getDataSize() + getFooterSize()

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        if (!isHeader(position) && !isFooter(position)) {
            val dataPosition = position - getHeaderSize()
            val data = mDataList?.get(dataPosition) ?: return

            setVariable(data, holder, dataPosition, position)
            holder.binding.executePendingBindings()

            holder.binding.root.let {
                openDebounce.yes {
                    it.setOnDebounceClickListener(duration = debounceDuration) { v ->
                        onItemClickListener?.onRecyclerItemClick(dataPosition, v)
                    }
                }.otherwise {
                    it.setOnClickListener { v ->
                        onItemClickListener?.onRecyclerItemClick(dataPosition, v)
                    }
                }

                it.setOnLongClickListener { v ->
                    onItemLongClickListener?.onRecyclerItemLongClick(dataPosition, v) ?: false
                }
            }
        }
    }

    abstract fun setVariable(data: T, holder: BaseViewHolder, dataPosition: Int, layoutPosition: Int)

    override fun getItemViewType(position: Int) = when {
        isHeader(position) -> mHeaderViewList.keyAt(position)
        isFooter(position) -> mFooterViewList.keyAt(position - getDataSize() - getHeaderSize())
        else -> getAdapterItemViewType(position)
    }

    open fun getAdapterItemViewType(position: Int): Int = 0

    fun addHeaderView(header: ViewDataBinding) {
        val headKey = HEADER + getHeaderSize()
        mHeaderViewList.put(headKey, header)
        notifyItemInserted(getHeaderSize())
    }

    fun removeHeaderView(header: ViewDataBinding) {
        val index = mHeaderViewList.indexOfValue(header)
        mHeaderViewList.removeAt(index)
        notifyItemRemoved(index)
    }

    fun addFooterView(footer: ViewDataBinding) {
        val footKey = FOOTER + getFooterSize()
        mFooterViewList.put(footKey, footer)
        notifyItemInserted(getHeaderSize() + getDataSize() + getFooterSize())
    }

    fun removeFooterView(footer: ViewDataBinding) {
        val index = mFooterViewList.indexOfValue(footer)
        mFooterViewList.removeAt(index)
        notifyItemRemoved(getHeaderSize() + getDataSize() + index)
    }

    fun getAdapterDataList(): MutableList<T>? = mDataList

    fun getItemData(position: Int): T? = mDataList?.get(position)

    fun getHeaderSize(): Int = mHeaderViewList.size()

    fun getDataSize(): Int = mDataList?.size ?: 0

    fun getFooterSize(): Int = mFooterViewList.size()

    private fun haveHeader() = mHeaderViewList.size() > 0

    private fun haveFooter() = mFooterViewList.size() > 0

    private fun isHeader(pos: Int) = haveHeader() && pos < getHeaderSize()

    private fun isFooter(pos: Int) = haveFooter() && pos >= getHeaderSize() + getDataSize()

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.layoutManager.let {
            if (it is GridLayoutManager)
                it.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int) =
                        if (isHeader(position) || isFooter(position)) it.spanCount
                        else 1
                }
        }
    }

    override fun onViewAttachedToWindow(holder: BaseViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.itemView.layoutParams.let {
            if (it is StaggeredGridLayoutManager.LayoutParams)
                it.isFullSpan = isHeader(holder.layoutPosition) || isFooter(holder.layoutPosition)
        }
    }
}