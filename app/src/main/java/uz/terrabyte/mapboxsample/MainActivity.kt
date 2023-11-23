package uz.terrabyte.mapboxsample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.MapboxDirections
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.core.constants.Constants.PRECISION_6
import com.mapbox.geojson.Feature
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.expressions.dsl.generated.interpolate
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorBearingChangedListener
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import uz.terrabyte.mapboxsample.databinding.ActivityMainBinding
import uz.terrabyte.utills.LocationPermissionHelper
import java.lang.ref.WeakReference


class MainActivity : AppCompatActivity() {

    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onInitialize = this::initNavigation
    )
    private lateinit var locationPermissionHelper: LocationPermissionHelper

    private val onIndicatorBearingChangedListener = OnIndicatorBearingChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().bearing(it).build())
    }

    private val onIndicatorPositionChangedListener = OnIndicatorPositionChangedListener {
        mapView.getMapboxMap().setCamera(CameraOptions.Builder().center(it).build())
        mapView.gestures.focalPoint = mapView.getMapboxMap().pixelForCoordinate(it)
    }

    private val onMoveListener = object : OnMoveListener {
        override fun onMoveBegin(detector: MoveGestureDetector) {
            onCameraTrackingDismissed()
        }

        override fun onMove(detector: MoveGestureDetector): Boolean {
            return false
        }

        override fun onMoveEnd(detector: MoveGestureDetector) {}
    }

    private lateinit var mapView: MapView
    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapView = MapView(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        locationPermissionHelper = LocationPermissionHelper(WeakReference(this))
        locationPermissionHelper.checkPermissions {
            onMapReady(mapboxMap = mapView.getMapboxMap())
        }

        binding.button.setOnClickListener {
            fetchARoute()
        }


    }

    private fun initNavigation() {
        MapboxNavigationApp.setup(
            NavigationOptions.Builder(this)
                .accessToken(getString(R.string.mapbox_access_token))
                .build()
        )
    }


    private fun fetchARoute() {

        val originPoint = Point.fromLngLat(
            41.55758213979963, 60.588573882438624
        )
        val destination = Point.fromLngLat(
            41.550074708879364, 60.58194161940992
        )

        val routeOptions = RouteOptions.builder()
// applies the default parameters to route options
            .applyDefaultNavigationOptions()
            .applyLanguageAndVoiceUnitOptions(this)
// lists the coordinate pair i.e. origin and destination
// If you want to specify waypoints you can pass list of points instead of null
            .coordinatesList(listOf(originPoint, destination))
// set it to true if you want to receive alternate routes to your destination
            .alternatives(false)
// provide the bearing for the origin of the request to ensure
// that the returned route faces in the direction of the current user movement
//            .bearingsList(
//                listOf(
//                    Bearing.builder()
//                        .angle(location.bearing.toDouble())
//                        .degrees(45.0)
//                        .build(),
//                    null
//                )
//            )
            .build()
        mapboxNavigation.requestRoutes(
            routeOptions,
            object : NavigationRouterCallback {
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
// This particular callback is executed if you invoke
// mapboxNavigation.cancelRouteRequest()
//                    binding.responseTextView.text = "route request canceled"
//                    binding.fetchARouteButton.visibility = VISIBLE
                }

                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
//                    binding.responseTextView.text = """route request failed with:$reasons
//""".trimIndent()
                    Log.e("LOG_TAG", "route request failed with $reasons")
//                    binding.fetchARouteButton.visibility = VISIBLE
                }

                override fun onRoutesReady(
                    routes: List<NavigationRoute>,
                    routerOrigin: RouterOrigin
                ) {
// GSON instance used only to print the response prettily
                    val gson = GsonBuilder().setPrettyPrinting().create()
                    val json = routes.map {
                        gson.toJson(
                            JsonParser.parseString(it.directionsRoute.toJson())
                        )
                    }
                    mapboxNavigation.setNavigationRoutes(routes)

                    Log.d("TAG", "onRoutesReady: $json")
                 //   binding.responseTextView.text = """|routes ready (origin: ${routerOrigin::class.simpleName}):|$json""".trimMargin()
                }
            }
        )
      //  binding.fetchARouteButton.visibility = GONE
    }

    private fun onMapReady(mapboxMap: MapboxMap) {

        map = mapboxMap
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .zoom(14.0)
                .build()
        )

        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder().center(
                Point.fromLngLat(
                    LATITUDE,
                    LONGITUDE
                )
            ).zoom(ZOOM).build()
        )

        mapView.getMapboxMap().loadStyle(
            (
                    style(styleUri = Style.MAPBOX_STREETS) {
                        +geoJsonSource(GEOJSON_SOURCE_ID) {
                            url("asset://from_crema_to_council_crest.geojson")
                        }
                        +lineLayer("linelayer", GEOJSON_SOURCE_ID) {
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                            lineOpacity(0.7)
                            lineWidth(8.0)
                            lineColor("#888")
                        }
                    })
        )
    }

    private fun setupGesturesListener() {
        mapView.gestures.addOnMoveListener(onMoveListener)
    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                bearingImage = AppCompatResources.getDrawable(
                    this@MainActivity,
                    R.drawable.ic_somekinda,
                ),
                shadowImage = AppCompatResources.getDrawable(
                    this@MainActivity,
                    R.drawable.ic_somekinda,
                ),
                scaleExpression = interpolate {
                    linear()
                    zoom()
                    stop {
                        literal(0.0)
                        literal(0.6)
                    }
                    stop {
                        literal(20.0)
                        literal(1.0)
                    }
                }.toJson()
            )
        }
        locationComponentPlugin.addOnIndicatorPositionChangedListener(
            onIndicatorPositionChangedListener
        )
        locationComponentPlugin.addOnIndicatorBearingChangedListener(
            onIndicatorBearingChangedListener
        )
    }

    private fun onCameraTrackingDismissed() {
        Toast.makeText(this, "onCameraTrackingDismissed", Toast.LENGTH_SHORT).show()
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location
            .removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location
            .removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val GEOJSON_SOURCE_ID = "line"
        private const val LATITUDE = -122.486052
        private const val LONGITUDE = 37.830348
        private const val ZOOM = 14.0
    }

}