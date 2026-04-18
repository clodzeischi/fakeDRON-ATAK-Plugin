package com.atakmap.android.fakedron.plugin.drone

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import com.atakmap.android.fakedron.plugin.R
import gov.tak.platform.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DroneControlView(
    private val context: Context,
    private val view: View,
    private val viewModel: DroneViewModel,
    private val lifecycleScope: CoroutineScope
) {
    // View bindings
    private val btnLaunchLand = view.findViewById<Button>(R.id.btn_launch_land)
    private val btnRth        = view.findViewById<Button>(R.id.btn_rth)
    private val tvDroneState  = view.findViewById<TextView>(R.id.tv_drone_state)
    private val tvActualAlt   = view.findViewById<TextView>(R.id.tv_actual_altitude)
    private val tvTargetAlt   = view.findViewById<TextView>(R.id.tv_target_altitude)
    private val tvMgrs        = view.findViewById<TextView>(R.id.tv_mgrs)
    private val sbAltitude    = view.findViewById<SeekBar>(R.id.sb_target_altitude)

    init {
        bindActions()
        observeState()
    }

    private fun bindActions() {
        btnLaunchLand.setOnClickListener { viewModel.onLaunchLand() }
        btnRth.setOnClickListener        { viewModel.onRth() }

        sbAltitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.onTargetAltitudeChanged(progress)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                updateStatusChip(state.status)
                updateLaunchButton(state.status)
                tvTargetAlt.text = "${state.targetAltitude}m"
                tvActualAlt.text = state.actualAltitude?.let { "${it}m" } ?: "---m"
                tvMgrs.text      = state.location ?: "---"
            }
        }
    }

    private fun updateStatusChip(status: FlightStatus) {
        val (label, color) = when (status) {
            FlightStatus.IDLE      -> "● IDLE"      to "#9E9E9E"
            FlightStatus.LAUNCHING -> "● LAUNCHING" to "#F9A825"
            FlightStatus.FLYING    -> "● FLYING"    to "#2E7D32"
            FlightStatus.LANDING   -> "● LANDING"   to "#F9A825"
            FlightStatus.RTH       -> "● RTH"       to "#B71C1C"
        }
        tvDroneState.text = label
        tvDroneState.setTextColor(Color.parseColor(color))
    }

    private fun updateLaunchButton(status: FlightStatus) {
        val (label, color) = when (status) {
            FlightStatus.IDLE,
            FlightStatus.LANDING -> "LAUNCH" to "#2E7D32"
            else                 -> "LAND"   to "#B71C1C"
        }
        btnLaunchLand.text = label
        btnLaunchLand.backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
    }
}