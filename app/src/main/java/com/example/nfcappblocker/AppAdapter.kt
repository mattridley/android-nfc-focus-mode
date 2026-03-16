package com.example.nfcappblocker

import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val packageManager: PackageManager,
    private val onWhitelistToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var apps = mutableListOf<AppItem>()

    data class AppItem(
        val packageName: String,
        val appName: String,
        var isWhitelisted: Boolean
    )

    fun setApps(newApps: List<AppItem>) {
        apps = newApps.toMutableList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        holder.appName.text = app.appName
        
        try {
            holder.appIcon.setImageDrawable(packageManager.getApplicationIcon(app.packageName))
        } catch (e: Exception) {
            // Use default icon
        }

        // Remove listener before setting state to avoid triggering callback
        holder.whitelistCheckBox.setOnCheckedChangeListener(null)
        holder.whitelistCheckBox.isChecked = app.isWhitelisted
        
        holder.whitelistCheckBox.setOnCheckedChangeListener { _, isChecked ->
            app.isWhitelisted = isChecked
            onWhitelistToggle(app.packageName, isChecked)
        }

        holder.itemView.setOnClickListener {
            holder.whitelistCheckBox.toggle()
        }
    }

    override fun getItemCount(): Int = apps.size

    class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val whitelistCheckBox: CheckBox = view.findViewById(R.id.whitelistCheckBox)
    }
}
