package ru.wizand.powerwatchdog.ui.statuses

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.powerwatchdog.R
import ru.wizand.powerwatchdog.data.model.StatusItem
import ru.wizand.powerwatchdog.data.model.StatusState
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter


class StatusAdapter : ListAdapter<StatusItem, StatusAdapter.StatusVH>(DiffCallback){

    object DiffCallback : DiffUtil.ItemCallback<StatusItem>() {
        override fun areItemsTheSame(old: StatusItem, new: StatusItem) =
            old.title == new.title

        override fun areContentsTheSame(old: StatusItem, new: StatusItem) =
            old == new
    }

    private val items = mutableListOf<StatusItem>()
    private val checks = mutableListOf<StatusItem>()



    fun setData(data: List<StatusItem>) {
        items.clear()
        items.addAll(data)
//        notifyDataSetChanged()
        checks.clear()
        checks.addAll(data)
        submitList(checks.toList())
    }

    fun updateStatus(position: Int, state: StatusState) {
        items[position].status = state
        notifyItemChanged(position)
    }

    inner class StatusVH(view: View) : RecyclerView.ViewHolder(view) {
        val icon = view.findViewById<ImageView>(R.id.imgStatusIcon)
        val title = view.findViewById<TextView>(R.id.tvTitle)
        val container = view.findViewById<LinearLayout>(R.id.container)
        val status = view.findViewById<TextView>(R.id.tvStatus)
        val btnFix = view.findViewById<Button>(R.id.btnFix)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StatusVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_status, parent, false)
        return StatusVH(v)
    }

    override fun onBindViewHolder(holder: StatusVH, position: Int) {
        val item = items[position]
        holder.title.text = item.title

        holder.itemView.alpha = 0f
        holder.itemView.translationY = 20f
        holder.itemView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()

        val bg = when (item.status) {
            StatusState.OK -> R.drawable.bg_status_ok
            StatusState.WARNING -> R.drawable.bg_status_warning
            StatusState.ERROR -> R.drawable.bg_status_error
            StatusState.CHECKING -> R.drawable.bg_status_checking
            else -> R.drawable.bg_status_item
        }

        holder.container.setBackgroundResource(bg)

        holder.status.text = when (item.status) {
            StatusState.UNKNOWN -> "Ожидание"
            StatusState.CHECKING -> "Проверка…"
            StatusState.OK -> "ОК"
            StatusState.WARNING -> "Есть ограничение"
            StatusState.ERROR -> "Ошибка"
        }

        // Иконки
        val iconRes = when (item.status) {
            StatusState.UNKNOWN -> R.drawable.ic_status_unknown
            StatusState.CHECKING -> R.drawable.ic_status_checking
            StatusState.OK -> R.drawable.ic_status_ok
            StatusState.WARNING -> R.drawable.ic_status_warning
            StatusState.ERROR -> R.drawable.ic_status_error
        }
        holder.icon.setImageResource(iconRes)

        // Анимация для CHECKING
        if (item.status == StatusState.CHECKING) {
            holder.icon.animate().rotationBy(360f).setDuration(800).start()
        } else {
            holder.icon.animate().cancel()
            holder.icon.rotation = 0f
        }

        // Логика кнопки
        holder.btnFix.visibility =
            if (item.fixAction != null && item.status != StatusState.OK)
                View.VISIBLE else View.GONE

        holder.btnFix.setOnClickListener { item.fixAction?.invoke() }
    }


    override fun getItemCount() = items.size
}
