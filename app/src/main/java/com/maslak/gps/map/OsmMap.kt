package com.maslak.gps.map

import android.annotation.SuppressLint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.maslak.gps.R
import com.maslak.gps.data.LatLng
import com.maslak.gps.ui.Palette
import androidx.compose.ui.graphics.toArgb
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

data class CameraRequest(val target: LatLng, val id: Int, val zoom: Double? = null)

private class XyzSource(
    name: String,
    maxZoom: Int,
    urls: Array<String>,
    private val yBeforeX: Boolean,
    copyright: String,
) : OnlineTileSourceBase(name, 0, maxZoom, 256, ".png", urls, copyright) {
    override fun getTileURLString(index: Long): String {
        val z = MapTileIndex.getZoom(index)
        val x = MapTileIndex.getX(index)
        val y = MapTileIndex.getY(index)
        return if (yBeforeX) "$baseUrl$z/$y/$x" else "$baseUrl$z/$x/$y.png"
    }
}

/** خريطة داكنة مشابهة للتصميم */
private val DarkSource = XyzSource(
    "CartoDark", 20,
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/",
    ),
    yBeforeX = false, copyright = "© OpenStreetMap © CARTO"
)

/** قمر صناعي */
private val SatelliteSource = XyzSource(
    "EsriWorldImagery", 19,
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    yBeforeX = true, copyright = "© Esri"
)

/** صبغة الخريطة حسب ألوان النسخة */
private val NavyTint = ColorMatrixColorFilter(ColorMatrix(Palette.MapTint))

private fun LatLng.gp() = GeoPoint(lat, lng)

private class MapHolder {
    var map: MapView? = null
    var lastCameraId = -1
    var satellite: Boolean? = null
    lateinit var pin: Marker
    lateinit var current: Marker
    lateinit var route: Polyline
    val routeMarkers = mutableListOf<Marker>()
    var routeSnapshot: List<LatLng> = emptyList()
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun OsmMap(
    modifier: Modifier,
    pin: LatLng,
    satellite: Boolean,
    camera: CameraRequest,
    route: List<LatLng>,
    current: LatLng?,
    onTap: (LatLng) -> Unit,
) {
    val tap by rememberUpdatedState(onTap)
    val holder = remember { MapHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            MapView(ctx).apply {
                holder.map = this
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                minZoomLevel = 3.0
                maxZoomLevel = 20.0
                controller.setZoom(17.0)
                controller.setCenter(pin.gp())
                setBackgroundColor(0xFF1B2333.toInt())

                // منع التمرير الخارجي من سرقة لمسات الخريطة
                setOnTouchListener { v, e ->
                    if (e.action == MotionEvent.ACTION_DOWN) v.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }

                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                        tap(LatLng(p.latitude, p.longitude)); return true
                    }
                    override fun longPressHelper(p: GeoPoint): Boolean {
                        tap(LatLng(p.latitude, p.longitude)); return true
                    }
                }))

                holder.route = Polyline(this).apply {
                    outlinePaint.color = Palette.Teal.toArgb()
                    outlinePaint.strokeWidth = 9f
                    outlinePaint.isAntiAlias = true
                }
                overlays.add(holder.route)

                holder.pin = Marker(this).apply {
                    icon = ContextCompat.getDrawable(ctx, R.drawable.ic_pin)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    setOnMarkerClickListener { _, _ -> true }
                }
                overlays.add(holder.pin)

                holder.current = Marker(this).apply {
                    icon = ContextCompat.getDrawable(ctx, R.drawable.ic_dot)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                    isEnabled = false
                }
                overlays.add(holder.current)
            }
        },
        update = { map ->
            if (holder.satellite != satellite) {
                holder.satellite = satellite
                map.setTileSource(if (satellite) SatelliteSource else DarkSource)
                map.overlayManager.tilesOverlay.setColorFilter(if (satellite) null else NavyTint)
                map.overlayManager.tilesOverlay.setLoadingBackgroundColor(0xFF1B2333.toInt())
                map.overlayManager.tilesOverlay.setLoadingLineColor(0xFF243047.toInt())
            }
            holder.pin.position = pin.gp()

            if (current != null) {
                holder.current.isEnabled = true
                holder.current.position = current.gp()
            } else {
                holder.current.isEnabled = false
            }

            if (holder.routeSnapshot != route) {
                holder.routeSnapshot = route.toList()
                holder.route.setPoints(route.map { it.gp() })
                holder.routeMarkers.forEach { map.overlays.remove(it) }
                holder.routeMarkers.clear()
                route.forEach { p ->
                    val m = Marker(map).apply {
                        icon = ContextCompat.getDrawable(map.context, R.drawable.ic_route_point)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        position = p.gp()
                        setOnMarkerClickListener { _, _ -> true }
                        }
                    holder.routeMarkers += m
                    map.overlays.add(m)
                }
            }

            if (holder.lastCameraId != camera.id) {
                val first = holder.lastCameraId == -1
                holder.lastCameraId = camera.id
                if (first) {
                    camera.zoom?.let { map.controller.setZoom(it) }
                    map.controller.setCenter(camera.target.gp())
                } else {
                    map.controller.animateTo(camera.target.gp(), camera.zoom ?: map.zoomLevelDouble, 600L)
                }
            }
            map.invalidate()
        },
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder.map?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.map?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder.map?.onDetach()
        }
    }
}
