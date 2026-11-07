package com.a3s.fastvpn

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ServerAdapter(
    private val servers: List<ServerModel>,
    private val onSelect: (ServerModel) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvFlag: TextView = v.findViewById(R.id.tvServerFlag)
        val tvCountry: TextView = v.findViewById(R.id.tvServerCountry)
        val tvVipBadge: TextView = v.findViewById(R.id.tvVipBadge)
        val tvIp: TextView = v.findViewById(R.id.tvServerIp)
        val tvPing: TextView = v.findViewById(R.id.tvServerPing)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_server_location, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = servers[position]
        holder.tvFlag.text = item.flagEmoji
        holder.tvCountry.text = item.countryName
        holder.tvIp.text = if (item.isVip) "VIP Dedicated Node" else "Free Public Server"

        holder.tvVipBadge.visibility = if (item.isVip) View.VISIBLE else View.GONE

        if (item.pingMs > 0) {
            holder.tvPing.text = "${item.pingMs} ms"
            holder.tvPing.setTextColor(if (item.pingMs < 100) 0xFF00E676.toInt() else 0xFFFFC107.toInt())
        } else {
            holder.tvPing.text = "-- ms"
        }

        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount(): Int = servers.size
}
