package uz.terrabyte.mapboxsample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.DirectionsWaypoint
import com.mapbox.api.directions.v5.models.LegStep
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
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
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotation
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationManager
import com.mapbox.maps.plugin.annotation.generated.PolylineAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPolylineAnnotationManager
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
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.base.utils.DecodeUtils.completeGeometryToPoints
import com.mapbox.navigation.base.utils.DecodeUtils.stepGeometryToLineString
import com.mapbox.navigation.base.utils.DecodeUtils.stepGeometryToPoints
import com.mapbox.navigation.base.utils.DecodeUtils.stepsGeometryToLineString
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.RoutesSetCallback
import com.mapbox.navigation.core.RoutesSetError
import com.mapbox.navigation.core.RoutesSetSuccess
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.requireMapboxNavigation
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.NavigationRouteLine
import com.mapbox.navigation.ui.maps.route.line.model.RouteLine
import uz.terrabyte.mapboxsample.databinding.ActivityMainBinding
import uz.terrabyte.utills.LocationPermissionHelper
import java.lang.ref.WeakReference


class MainActivity : AppCompatActivity() {


    private val mapboxNavigation: MapboxNavigation by requireMapboxNavigation(
        onInitialize = this::initNavigation
    )

    private val mapboxRouteLineOptions by lazy { MapboxRouteLineOptions.Builder(this).build() }
    private val routeLineApi: MapboxRouteLineApi by lazy { MapboxRouteLineApi(mapboxRouteLineOptions) }
    private val routeLineView: MapboxRouteLineView by lazy { MapboxRouteLineView(mapboxRouteLineOptions) }

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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mapView = binding.mapView

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
            NavigationOptions.Builder(this).accessToken(getString(R.string.mapbox_access_token))
                .build()
        )
    }


    private fun fetchARoute() {

        val originPoint = Point.fromLngLat(60.588573882438624
            , 41.55758213979963
        )
        val destination = Point.fromLngLat( 60.808987130553845,41.46327737134798
        )

        val routeOptions = RouteOptions.builder()
            .applyDefaultNavigationOptions()
            .coordinatesList(listOf(originPoint, destination))
            .waypointsPerRoute(true)
            .alternatives(false)
            .build()
        mapboxNavigation.requestRoutes(routeOptions, object : NavigationRouterCallback {
            override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
            }

            override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
//                    binding.responseTextView.text = """route request failed with:$reasons
//""".trimIndent()
                Log.e("LOG_TAG", "route request failed with $reasons")
//                    binding.fetchARouteButton.visibility = VISIBLE
            }

            override fun onRoutesReady(
                routes: List<NavigationRoute>, routerOrigin: RouterOrigin
            ) {


                mapboxNavigation.setNavigationRoutes(routes) { result ->
                    Log.d("TAG", "onRoutesSet: ${result.error} and ${result}")
                }

                // drawRoutes(routes = routes)
                drawPolyLine(routes.first().directionsRoute.completeGeometryToPoints())
                //   binding.responseTextView.text = """|routes ready (origin: ${routerOrigin::class.simpleName}):|$json""".trimMargin()
            }
        })
        //  binding.fetchARouteButton.visibility = GONE
    }

    private fun onMapReady(mapboxMap: MapboxMap) {

        Log.d("TAG", "onMapReady: ready")
        map = mapboxMap

        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder().center(
                Point.fromLngLat(
                    LONGITUDE, LATITUDE
                )
            ).zoom(ZOOM).build()
        )

        mapView.getMapboxMap().loadStyle(
            (style(styleUri = Style.MAPBOX_STREETS) {
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
//        var annotationManager = mapView.annotations.createPolylineAnnotationManager()
//        annotationManager.annotations =



    }

//    private fun AddMarker(point: Point) {
//        val annotationApi: AnnotationPlugin = AnnotationPluginImplKt.getAnnotations(mapView)
//        val circleAnnotationManager: CircleAnnotationManager =
//            CircleAnnotationManagerKt.createCircleAnnotationManager(
//                annotationApi,
//                AnnotationConfig()
//            )
//        val circleAnnotationOptions = CircleAnnotationOptions()
//            .withPoint(point)
//            .withCircleRadius(7.0)
//            .withCircleColor("#ee4e8b")
//            .withCircleStrokeWidth(1.0)
//            .withDraggable(true)
//            .withCircleStrokeColor("#ffffff")
//        circleAnnotationManager.create(circleAnnotationOptions)
//    }

    private fun drawPolyLine(pointList: List<Point>) {


        Log.d("TAG", "drawPolyLine: ${pointList}")
        val polylineAnnotationManager: PolylineAnnotationManager = mapView.annotations.createPolylineAnnotationManager()
        val polylineAnnotationOptions = PolylineAnnotationOptions()
            .withPoints(pointList)
            .withLineColor("#ee4e8b")
            .withLineWidth(4.0)
        polylineAnnotationManager.create(polylineAnnotationOptions)


    }

    private fun drawRoutes(routes: List<NavigationRoute>) {
        val routeLines = routes.map { NavigationRouteLine(it, null) }
        routeLineApi.setNavigationRouteLines(routeLines) { routeDrawData ->
            map.getStyle()?.let { mapStyle ->
                routeLineView.renderRouteDrawData(mapStyle, routeDrawData)
            }
        }

    }

    private fun initLocationComponent() {
        val locationComponentPlugin = mapView.location
        locationComponentPlugin.updateSettings {
            this.enabled = true
            this.locationPuck = LocationPuck2D(
                bearingImage = AppCompatResources.getDrawable(
                    this@MainActivity,
                    R.drawable.ic_somekinda,
                ), shadowImage = AppCompatResources.getDrawable(
                    this@MainActivity,
                    R.drawable.ic_somekinda,
                ), scaleExpression = interpolate {
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
        mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.location.removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.location.removeOnIndicatorBearingChangedListener(onIndicatorBearingChangedListener)
        mapView.location.removeOnIndicatorPositionChangedListener(onIndicatorPositionChangedListener)
        mapView.gestures.removeOnMoveListener(onMoveListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionHelper.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    companion object {
        private const val GEOJSON_SOURCE_ID = "line"
        private const val LATITUDE = 41.55758213979963
        private const val LONGITUDE = 60.588573882438624
        private const val ZOOM = 14.0
    }

}