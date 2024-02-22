package com.stm.bledemo.activity.scan

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.stm.bledemo.R
import com.stm.bledemo.activity.scan.fragment.AdvertisingDataFragment
import com.stm.bledemo.ble.BLEManager
import com.stm.bledemo.databinding.RowScanResultBinding
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.db.NULL
import kotlin.collections.ArrayList
import java.time.LocalDateTime
import android.graphics.Color
import android.os.CountDownTimer


@SuppressLint("NotifyDataSetChanged", "MissingPermission")
class ScanAdapter (
    private val items: List<ScanResult>,
    private val delegate: Delegate
) : RecyclerView.Adapter<ScanAdapter.ViewHolder>() {

    private val itemsCopy: ArrayList<ScanResult> = arrayListOf()

    companion object {
        var prevEvents = mutableMapOf<String, AdvEvent>()
        var prevEventTimestamps = mutableMapOf<String, String>()
    }

    interface Delegate {
        fun onConnectButtonClick(result: ScanResult)
        fun onItemClick(dialog: DialogFragment)
    }

    inner class ViewHolder(val binding: RowScanResultBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.connectButton.setOnClickListener {
                val result = items[bindingAdapterPosition]
                delegate.onConnectButtonClick(result)
            }
            itemView.setOnClickListener {
                val result = items[bindingAdapterPosition]
                val dialog = AdvertisingDataFragment(result)
                delegate.onItemClick(dialog)
            }
        }
    }

    inner class AdvEvent(val hour: Int, val minute: Int, val second: Int, val eventId: Int) {

        fun eventString(): String? {
           val idMap = mapOf(
               0 to "None",
               1 to "Light Touch",
               2 to "Contact",
               3 to "Key Scratch",
               4 to "Collision",
               5 to "Glassbreak",
               6 to "Front Motion",
               7 to "Rear Motion",
               8 to "Front and Rear Motion",
               9 to "No Motion",
           )
            return idMap[this.eventId]
        }
        override fun equals(other: Any?): Boolean {
            if (other == null) return false
            if (this === other) return true
            if (other !is AdvEvent) return false
            return !(hour != other.hour || minute != other.minute || second != other.second || eventId != other.eventId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = DataBindingUtil.inflate<RowScanResultBinding>(
            inflater,
            R.layout.row_scan_result,
            parent,
            false
        )
        return ViewHolder(binding)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = items[position]
        val now = LocalDateTime.now()
        var eventString = "Last Event: None"
        var eventDidChange = false

        val manufacturerData = result.scanRecord?.getManufacturerSpecificData(3)
        if (manufacturerData != null) {
            val hour = manufacturerData[0].toInt()
            val minute = manufacturerData[1].toInt()
            val second = manufacturerData[2].toInt()
            val eventId = manufacturerData[3].toInt()
            val currEvent = AdvEvent(hour, minute, second, eventId)
            if (prevEvents.containsKey(result.device.address)) {
                if (prevEvents[result.device.address] != currEvent) {
                    eventString = "Last Event: ${currEvent.eventString()} " + now
                    prevEventTimestamps[result.device.address] = now.toString()
                    eventDidChange = true
                } else {
                    eventString = "Last Event: ${prevEvents[result.device.address]?.eventString()} " + prevEventTimestamps[result.device.address]
                }
            } else {
                eventString = "Last Event: ${currEvent.eventString()} " + now
                prevEventTimestamps[result.device.address] = now.toString()
                eventDidChange = true
            }
            prevEvents[result.device.address] = currEvent

        }

        with(holder.binding) {
            deviceName.text = result.device.name ?: "Unnamed"
            macAddress.text = result.device.address
            signalStrength.text = "${result.rssi} dBm"
            event.text = eventString
            if (eventDidChange) {
                scanRow.setBackgroundColor(Color.RED)
                val timer = object: CountDownTimer(2000, 2000) {
                    override fun onTick(millisUntilFinished: Long) {
                    }

                    override fun onFinish() {
                        scanRow.setBackgroundColor(Color.BLACK)
                    }
                }
                timer.start()
            }
            connectButton.visibility = if (!result.isConnectable) View.GONE else View.VISIBLE
        }
    }

    override fun getItemCount() = items.size

    // Filter Recycler View by given text
    fun filter(value: String, type:String) {
        if (value.isNotEmpty()) {
            itemsCopy.clear()
            itemsCopy.addAll(items)

            when (type) {
                "name" -> BLEManager.deviceNameFilter = value
                "rssi" -> BLEManager.deviceRSSIFilter = value
            }
            BLEManager.scanResults.clear()

            for (item in itemsCopy) {
                if (filterCompare(item, value, type)) {
                    BLEManager.scanResults.add(item)
                }
            }

            notifyDataSetChanged()
        }
    }

    fun filterCompare(item: ScanResult, value: String, type: String): Boolean {
        if (value.isEmpty()) return true

        return if (type == "name") {
            item.device.name != null && item.device.name.uppercase().contains(value.uppercase())
        } else if (type == "address") {
            item.device.address.startsWith(value)
        } else {
            item.rssi >= value.toInt()
        }
    }
}