package ru.wizand.powerwatchdog.ui.log

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
        val context = holder.itemView.context

        with(holder.vb) {
            tvTime.text = TimeUtils.format(item.timestamp)

            // 1. Определение текста описания
            // Если есть кастомное сообщение, используем его. Иначе берем стандартное по типу.
            tvDesc.text = if (!item.message.isNullOrBlank()) {
                item.message
            } else {
                when (item.type) {
                    PowerState.CONNECTED -> context.getString(R.string.network_connected)
                    PowerState.DISCONNECTED -> context.getString(R.string.network_disconnected)
                    PowerState.ERROR -> "Ошибка процесса"
                    PowerState.INFO -> "Информация"
                }
            }

            // 2. Определение иконки
            val iconRes = when (item.type) {
                PowerState.CONNECTED -> R.drawable.ic_plug
                PowerState.DISCONNECTED -> R.drawable.ic_battery
                // Используем стандартные Android иконки, если своих пока нет в ресурсах
                PowerState.ERROR -> android.R.drawable.stat_notify_error
                PowerState.INFO -> android.R.drawable.ic_dialog_info
            }
            ivIcon.setImageResource(iconRes)

            // 3. Стилизация ошибок (цвет текста)
            // Важно сбрасывать цвет, так как View переиспользуются
            if (item.type == PowerState.ERROR) {
                tvDesc.setTextColor(Color.RED)
            } else {
                // Сброс на черный или дефолтный цвет темы (лучше использовать attr/textColorPrimary в реальном проекте)
                tvDesc.setTextColor(Color.BLACK)
            }
        }
    }

    class Diff : DiffUtil.ItemCallback<PowerEvent>() {
        override fun areItemsTheSame(oldItem: PowerEvent, newItem: PowerEvent): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: PowerEvent, newItem: PowerEvent): Boolean = oldItem == newItem
    }
}