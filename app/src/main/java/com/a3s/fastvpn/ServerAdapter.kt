package com.a3s.fastvpn

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ServerAdapter(
    private val servers: List<ServerModel>,
    private val onServerSelected: (ServerModel) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ServerViewHolder>() {

    class ServerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFlag: TextView = view.findViewById(R.id.tvServerFlag)
        val tvName: TextView = view.findViewById(R.id.tvServerName)
        val tvPing: TextView = view.findViewById(R.id.tvServerPing)
        val tvBadge: TextView = view.findViewById(R.id.tvVipBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ServerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server_location, parent, false)
        return ServerViewHolder(view)
    }

    override fun onBindViewHolder(holder: ServerViewHolder, position: Int) {
        val item = servers[position]
        holder.tvFlag.text = item.flag
        holder.tvName.text = item.name
        holder.tvPing.text = "${item.ping} ms"

        if (item.isVip) {
            holder.tvBadge.visibility = View.VISIBLE
            holder.tvBadge.text = "VIP"
        } else {
            holder.tvBadge.visibility = View.GONE
        }

        holder.itemView.setOnClickListener {
            onServerSelected(item)
        }
    }

    override fun getItemCount(): Int = servers.size
}
