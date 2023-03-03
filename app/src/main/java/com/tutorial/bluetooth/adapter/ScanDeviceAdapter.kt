package com.tutorial.bluetooth.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tutorial.bluetooth.data.Device
import com.tutorial.bluetooth.databinding.ItemScanDeviceBinding

class ScanDeviceAdapter : RecyclerView.Adapter<ScanDeviceAdapter.ScanDeviceViewHolder>() {
    private var scanList: List<Device> = ArrayList()

    // Item點擊。
    lateinit var onItemClickCallback: ((Int, Device) -> Unit)

    // 設定資料進來。
    fun setterData(scanList: List<Device>) {
        this.scanList = scanList
        notifyItemChanged(0, scanList.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanDeviceViewHolder =
        ScanDeviceViewHolder(ItemScanDeviceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = scanList.size

    override fun onBindViewHolder(holder: ScanDeviceViewHolder, position: Int) {
        val item = scanList[position]
        holder.binding.apply {
            tvName.text = item.name
            tvId.text = item.id

            root.setOnClickListener { onItemClickCallback.invoke(position, item) }
        }
    }

    inner class ScanDeviceViewHolder(val binding: ItemScanDeviceBinding) : RecyclerView.ViewHolder(binding.root)
}