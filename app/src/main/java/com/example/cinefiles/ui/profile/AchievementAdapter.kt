package com.example.cinefiles.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.cinefiles.R
import com.example.cinefiles.databinding.ItemAchievementBinding

/** Draws the medals of the Profile grid. Tapping one calls [onClick]. */
class AchievementAdapter(
    private val onClick: (AchievementItem) -> Unit
) : ListAdapter<AchievementItem, AchievementAdapter.ViewHolder>(Diff) {

    class ViewHolder(val binding: ItemAchievementBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAchievementBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val achievement = item.achievement
        val context = holder.itemView.context
        val name = context.getString(achievement.name)

        holder.binding.badge.setBadge(
            picture = achievement.icon,
            earned = item.unlocked,
            progressFraction = item.progress.toFloat() / achievement.goal
        )
        // Playing the animation again on a later rebind would be harmless, but we only start it when asked
        if (item.celebrate) holder.binding.badge.playUnlock()

        holder.binding.badgeName.text = name
        holder.itemView.contentDescription = if (item.unlocked) {
            context.getString(R.string.profile_badge_unlocked, name)
        } else {
            context.getString(R.string.profile_badge_locked, name, item.progress, achievement.goal)
        }
        holder.itemView.setOnClickListener { onClick(item) }
    }

    private object Diff : DiffUtil.ItemCallback<AchievementItem>() {
        override fun areItemsTheSame(old: AchievementItem, new: AchievementItem) =
            old.achievement.id == new.achievement.id

        override fun areContentsTheSame(old: AchievementItem, new: AchievementItem) = old == new
    }
}
