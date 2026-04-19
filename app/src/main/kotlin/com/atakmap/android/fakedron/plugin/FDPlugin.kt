package com.atakmap.android.fakedron.plugin

import android.content.Context
import android.graphics.drawable.Drawable
import com.atak.plugins.impl.PluginContextProvider
import com.atak.plugins.impl.PluginLayoutInflater
import com.atakmap.android.fakedron.plugin.drone.DroneControlView
import com.atakmap.android.fakedron.plugin.drone.DroneViewModel
import com.atakmap.android.maps.MapView
import gov.tak.api.commons.graphics.Bitmap
import gov.tak.api.plugin.IPlugin
import gov.tak.api.plugin.IServiceController
import gov.tak.api.ui.IHostUIService
import gov.tak.api.ui.Pane
import gov.tak.api.ui.PaneBuilder
import gov.tak.api.ui.ToolbarItem
import gov.tak.api.ui.ToolbarItemAdapter
import gov.tak.platform.marshal.MarshalManager
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

class FDPlugin(serviceController: IServiceController) : IPlugin {
    var serviceController: IServiceController?
    var pluginContext: Context? = null
    var uiService: IHostUIService?
    var toolbarItem: ToolbarItem?
    var pluginPane: Pane? = null
    val mapView = MapView.getMapView()
    private val viewModel by lazy {
        DroneViewModel(mapView)
    }
    private val pluginScope = MainScope()

    init {
        this.serviceController = serviceController
        val ctxProvider = serviceController
            .getService(PluginContextProvider::class.java)
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext()
            pluginContext!!.setTheme(R.style.ATAKPluginTheme)
        }

        uiService = serviceController.getService(IHostUIService::class.java)

        toolbarItem = ToolbarItem.Builder(
            pluginContext!!.getString(R.string.app_name),
            MarshalManager.marshal(
                pluginContext!!.getResources().getDrawable(R.drawable.icon_drone),
                Drawable::class.java,
                Bitmap::class.java
            )
        )
            .setListener(object : ToolbarItemAdapter() {
                override fun onClick(item: ToolbarItem?) {
                    showPane()
                }
            }).setIdentifier(pluginContext!!.getPackageName())
            .build()
    }

    override fun onStart() {
        if (uiService == null) return

        uiService!!.addToolbarItem(toolbarItem)
    }

    override fun onStop() {
        uiService!!.removeToolbarItem(toolbarItem)
        viewModel.onDestroy()
        pluginScope.cancel()
    }

    private fun showPane() {
        if (pluginPane == null) {

            val view = PluginLayoutInflater.inflate(
                pluginContext,
                R.layout.main_layout,
                null
            )

            pluginPane = PaneBuilder(view)
                .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.4)
                .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.4)
                .build()

            DroneControlView(
                context = pluginContext!!,
                view = view,
                viewModel = viewModel,
                lifecycleScope = MainScope()
            )
        }

        // if the plugin pane is not visible, show it!
        if (!uiService!!.isPaneVisible(pluginPane)) {
            uiService!!.showPane(pluginPane, null)
        }
    }
}