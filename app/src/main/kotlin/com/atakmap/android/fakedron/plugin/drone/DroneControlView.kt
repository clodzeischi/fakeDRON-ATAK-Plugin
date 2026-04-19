package com.atakmap.android.fakedron.plugin.drone

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.atakmap.android.fakedron.plugin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DroneControlView(
    private val context: Context,
    view: View,
    private val viewModel: DroneViewModel,
    private val lifecycleScope: CoroutineScope
) {
    // View bindings
    private val btnLaunchLand = view.findViewById<Button>(R.id.btn_launch_land)
    private val btnRth = view.findViewById<Button>(R.id.btn_rth)
    private val tvDroneState = view.findViewById<TextView>(R.id.tv_drone_state)
    private val tvActualAlt = view.findViewById<TextView>(R.id.tv_actual_altitude)
    private val tvTargetAlt = view.findViewById<TextView>(R.id.tv_target_altitude)
    private val tvMgrs = view.findViewById<TextView>(R.id.tv_mgrs)
    private val sbAltitude = view.findViewById<SeekBar>(R.id.sb_target_altitude)
    private val btnGoto = view.findViewById<Button>(R.id.btn_goto)

    // Other vars
    private val altitudes = listOf(100, 200, 300, 400, 500)
    private var previousStatus: FlightStatus = FlightStatus.IDLE

    init {
        bindActions()
        observeState()
    }

    private fun bindActions() {
        btnLaunchLand.setOnClickListener { viewModel.onLaunchLand() }
        btnRth.setOnClickListener { viewModel.onRth() }
        btnGoto.setOnClickListener { viewModel.onFlyToMapPoint() }

        sbAltitude.max = altitudes.size - 1  // max = 4, giving us 5 steps (0..4)
        sbAltitude.progress = 0              // default to 100m

        sbAltitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) viewModel.onTargetAltitudeChanged(altitudes[progress])
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                updateStatusChip(state.status)
                updateFlightControls(state.status)
                updateGotoButton(state.isTargeting)
                tvTargetAlt.text = "${state.targetAltitude}m"
                tvActualAlt.text = state.actualAltitude?.let { "${it}m" }
                    ?: context.getString(R.string.label_blank_alt)
                tvMgrs.text = state.location
                    ?: context.getString(R.string.label_blank_location)

                // only exit targeting on the tick that status changes away from FLYING
                if (previousStatus == FlightStatus.FLYING
                    && state.status != FlightStatus.FLYING
                    && state.isTargeting) {
                    viewModel.onFlyToMapPoint()
                }
                previousStatus = state.status
            }
        }

        lifecycleScope.launch {
            viewModel.toastMessage.collect { message ->
                message?.let {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearToast()
                }
            }
        }
    }

    private fun updateStatusChip(status: FlightStatus) {
        val (label, color) = when (status) {
            FlightStatus.IDLE ->
                context.getString(R.string.label_status_idle) to context.getColor(R.color.idle)

            FlightStatus.LAUNCHING ->
                context.getString(R.string.label_status_launching) to context.getColor(R.color.launching)

            FlightStatus.FLYING ->
                context.getString(R.string.label_status_flying) to context.getColor(R.color.flying)

            FlightStatus.LANDING ->
                context.getString(R.string.label_status_landing) to context.getColor(R.color.landing)

            FlightStatus.RTH ->
                context.getString(R.string.label_status_rth) to context.getColor(R.color.rth)
        }
        tvDroneState.text = label
        tvDroneState.setTextColor(color)
    }


    private fun updateFlightControls(status: FlightStatus) {
        // launch/land label and color
        val (label, color) = when (status) {
            FlightStatus.IDLE,
            FlightStatus.LANDING -> context.getString(R.string.label_btn_launch) to context.getColor(
                R.color.launching
            )

            else -> context.getString(R.string.label_btn_land) to context.getColor(R.color.landing)
        }
        btnLaunchLand.text = label
        btnLaunchLand.backgroundTintList = ColorStateList.valueOf(color)

        // launch/land enabled in all non-LANDING states
        btnLaunchLand.isEnabled = status != FlightStatus.LANDING

        // RTH active when FLYING or already in RTH
        val rthEnabled = status == FlightStatus.FLYING || status == FlightStatus.RTH
        btnRth.isEnabled = rthEnabled
        btnRth.alpha = if (rthEnabled) 1.0f else 0.4f

        // goto — FLYING and RTH, disabled during RTH only blocks map interaction
        val gotoEnabled = status == FlightStatus.FLYING || status == FlightStatus.RTH
        btnGoto.isEnabled = gotoEnabled
        btnGoto.alpha = if (gotoEnabled) 1.0f else 0.4f

        // slider — disabled during LANDING and RTH
        val sliderEnabled = status != FlightStatus.LANDING && status != FlightStatus.RTH
        sbAltitude.isEnabled = sliderEnabled
        sbAltitude.alpha = if (sliderEnabled) 1.0f else 0.4f
    }

    private fun updateGotoButton(isTargeting: Boolean) {
        val (label, color) = if (isTargeting) {
            context.getString(R.string.label_btn_goto_active) to context.getColor(
                R.color.background_btn_active
            )
        } else {
            context.getString(R.string.label_btn_goto) to context.getColor(
                R.color.background_btn
            )
        }
        btnGoto.text = label
        btnGoto.backgroundTintList = ColorStateList.valueOf(color)
    }
}