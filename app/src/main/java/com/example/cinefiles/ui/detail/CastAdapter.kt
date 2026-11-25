package com.example.cinefiles.ui.detail

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cinefiles.data.CastMember
import com.example.cinefiles.databinding.ItemCastBinding
import com.example.cinefiles.util.ImageUrls
import com.example.cinefiles.util.loadImage

/** The Cast row of Movie Details: a round photo, the actor's name and the character. Tap = [onPersonClick]. */
class CastAdapter(
    private val onPersonClick: (CastMember) -> Unit
) : ListAdapter<CastMember, CastAdapter.CastViewHolder>(CastDiffCallback) {

    class CastViewHolder(val binding: ItemCastBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CastViewHolder =
        CastViewHolder(ItemCastBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: CastViewHolder, position: Int) {
        val member = getItem(position)
        val binding = holder.binding

        binding.castName.text = member.name
        binding.castCharacter.text = member.character
        binding.castCharacter.isVisible = member.character != null
        // No photo (or a photo that does not load): the profile icon behind the picture shows through
        binding.castPhoto.loadImage(ImageUrls.profile(member.profilePath), showSurfaceWhileMissing = false)
        binding.root.setOnClickListener { onPersonClick(member) }
    }

    override fun onViewRecycled(holder: CastViewHolder) {
        // The view is about to show another actor: stop its old download so a late picture cannot land on it
        Glide.with(holder.binding.root).clear(holder.binding.castPhoto)
    }

    private object CastDiffCallback : DiffUtil.ItemCallback<CastMember>() {
        override fun areItemsTheSame(oldItem: CastMember, newItem: CastMember) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: CastMember, newItem: CastMember) = oldItem == newItem
    }
}
