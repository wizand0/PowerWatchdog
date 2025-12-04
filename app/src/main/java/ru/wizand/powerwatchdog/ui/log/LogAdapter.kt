package ru.wizand.powerwatchdog.ui.log

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ru.wizand.powerwatchdog.data.model.PowerEvent
import ru.wizand.powerwatchdog.data.model.PowerState
import ru.wizand.powerwatchdog.databinding.ItemLogBinding
import ru.wizand.powerwatchdog.utils.TimeUtils
import ru.wizand.powerwatchdog.R

class LogAdapter : ListAdapter<PowerEvent, LogAdapter.ViewHolder>(Diff()) {

    class ViewHolder(val vb: ItemLogBinding) : RecyclerView.ViewHolder(vb.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val vb = ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(vb)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.vb) {
            tvTime.text = TimeUtils.format(item.timestamp)
            tvDesc.text = when (item.type) {
                PowerState.CONNECTED -> holder.itemView.context.getString(R.string.network_connected)
                PowerState.DISCONNECTED -> holder.itemView.context.getString(R.string.network_disconnected)
            }
            ivIcon.setImageResource(
                if (item.type == PowerState.CONNECTED) R.drawable.ic_plug else R.drawable.ic_battery
            )
        }
    }

    class Diff : DiffUtil.ItemCallback<PowerEvent>() {
        override fun areItemsTheSame(oldItem: PowerEvent, newItem: PowerEvent): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PowerEvent, newItem: PowerEvent): Boolean = oldItem == newItem
    }
}