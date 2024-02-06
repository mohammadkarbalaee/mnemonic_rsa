package de.post.ident.internal_basic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.Keep
import androidx.collection.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import de.post.ident.internal_basic.databinding.PiFragmentBranchFinderBinding
import de.post.ident.internal_core.Commons
import de.post.ident.internal_core.rest.BranchDetailDTO
import de.post.ident.internal_core.rest.BranchOpeningTimeDTO
import de.post.ident.internal_core.rest.CaseResponseDTO
import de.post.ident.internal_core.rest.*
import de.post.ident.internal_core.reporting.EmmiCoreReporter
import de.post.ident.internal_core.reporting.LogEvent
import de.post.ident.internal_core.rest.CoreEmmiService
import de.post.ident.internal_core.start.BundleParameter
import de.post.ident.internal_core.start.withParameter
import de.post.ident.internal_core.util.LocalizedStrings
import de.post.ident.internal_core.util.log
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

class BranchFinderFragment : Fragment() {
    companion object {
        private val BRANCH_FINDER_PARAMETER: BundleParameter<CaseResponseDTO> =
            BundleParameter.moshi(CoreEmmiService.moshi, "BRANCH_FINDER")
        fun newInstance(caseResponseDTO: CaseResponseDTO): BranchFinderFragment = BranchFinderFragment()
                .withParameter(caseResponseDTO, BRANCH_FINDER_PARAMETER)

        private val OFFLINE_COUPON_PARAMETER: BundleParameter<Boolean> =
            BundleParameter.moshi(CoreEmmiService.moshi, "OFFLINE_COUPON")
        fun newInstanceOfflineCoupon(caseResponseDTO: CaseResponseDTO): BranchFinderFragment = BranchFinderFragment()
            .withParameter(caseResponseDTO, BRANCH_FINDER_PARAMETER)
            .withParameter(true, OFFLINE_COUPON_PARAMETER)

        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 7777
    }

    private lateinit var viewBinding: PiFragmentBranchFinderBinding
    private lateinit var map: GoogleMap
    private var lastSelectedMarker: Marker? = null
    private var markerCache: MarkerBitmapCache? = null
    private var branches: List<BranchDetailDTO> = mutableListOf()
    private var skipReload: Boolean = true
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private lateinit var toolbar: MaterialToolbar
    private lateinit var menuItem: MenuItem

    private val emmiService: CoreEmmiService = CoreEmmiService
    private val emmiReporter = EmmiCoreReporter

    @Keep
    private enum class ErrorType {
        NETWORK, EMPTY_RESPONSE, PLACES_SERVICE
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = PiFragmentBranchFinderBinding.inflate(inflater, container, false)
        initView(viewBinding)
        return viewBinding.root
    }

    override fun onResume() {
        super.onResume()
        emmiReporter.send(LogEvent.BA_DISPLAY_FINDER)
        menuItem.isVisible = true
    }

    override fun onPause() {
        super.onPause()
        menuItem.isVisible = false
    }

    private fun initView(viewBinding: PiFragmentBranchFinderBinding) {
        viewBinding.piBranchFinderBottomSheet.peekSubtitle.text = LocalizedStrings.getString("branch_finder_further_information")

        val caseResponse = checkNotNull(BRANCH_FINDER_PARAMETER.getParameter(arguments))

        if (caseResponse.toMethodSelection != null && Commons.skipMethodSelection.not()) {
            val methodSelectionButton = viewBinding.piBranchFinderBottomSheet.piMethodSelectionButton.methodSelectionButton
            methodSelectionButton.text = caseResponse.toMethodSelection?.text
            methodSelectionButton.visibility = View.VISIBLE

            methodSelectionButton.setOnClickListener {
                requireActivity().finish()
            }
        }

        toolbar = requireActivity().findViewById(R.id.toolbar_actionbar)
        toolbar.inflateMenu(R.menu.pi_menu_search)
        menuItem = toolbar.menu.findItem(R.id.menu_search)

        // needed for tinting menu icon on older devices
        val draw = menuItem.icon
        if (draw != null) {
            draw.mutate()
            draw.setColorFilter(resources.getColor(R.color.pi_icon_color_on_primary_brand_color), PorterDuff.Mode.SRC_ATOP)
        }

        toolbar.setOnMenuItemClickListener { menu ->
            when (menu.itemId) {
                R.id.menu_search -> {
                    startSearch()
                    true
                }
                else -> false
            }
        }

        markerCache = MarkerBitmapCache(requireContext(), 2) // 2 = normal state, selected state

        //implementation taken from https://github.com/googlecodelabs/android-kotlin-geo-maps
        val mapFragment = childFragmentManager.findFragmentById(R.id.map_view) as SupportMapFragment
        mapFragment.getMapAsync(mapReadyCallback)

        val bottomSheet = viewBinding.piBranchFinderBottomSheet.root
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                if (slideOffset <= 0f) {
                    viewBinding.piBranchFinderBottomSheet.branchType.visibility = View.INVISIBLE
                    viewBinding.piBranchFinderBottomSheet.branchDetailsContainer.visibility = View.INVISIBLE
                    map.setPadding(0, 0, 0, 0)
                } else if (slideOffset > 0f && slideOffset < 1f) {
                    viewBinding.piBranchFinderBottomSheet.branchType.visibility = View.VISIBLE
                    viewBinding.piBranchFinderBottomSheet.peekSubtitle.visibility = View.VISIBLE
                    viewBinding.piBranchFinderBottomSheet.branchDetailsContainer.visibility = View.VISIBLE
                } else if (slideOffset >= 1f) {
                    recalculateMapsMarkerPosition(bottomSheet)
                    viewBinding.piBranchFinderBottomSheet.peekSubtitle.visibility = View.GONE

                    lastSelectedMarker?.let {
                        moveMapTo(it.position.latitude, it.position.longitude, false)
                    }
                }

                viewBinding.piBranchFinderBottomSheet.branchType.alpha = slideOffset
                viewBinding.piBranchFinderBottomSheet.branchDetailsContainer.alpha = slideOffset
                viewBinding.piBranchFinderBottomSheet.peekTitle.alpha = 1f - slideOffset
                viewBinding.piBranchFinderBottomSheet.peekSubtitle.alpha = 1f - slideOffset
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // ;
            }
        })

        viewBinding.piBranchFinderBottomSheet.peekTitle.setOnClickListener {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        if (OFFLINE_COUPON_PARAMETER.getParameter(arguments) ?: false) {
            viewBinding.piBranchFinderBottomSheet.piMethodSelectionButton.methodSelectionButton.visibility = View.GONE
            viewBinding.piBranchFinderBottomSheet.separatorBottom.piSeparator.visibility = View.GONE
        }
    }

    private val mapReadyCallback = object : OnMapReadyCallback {
        override fun onMapReady(googleMap: GoogleMap) {
            map = googleMap
            map.setOnMarkerClickListener { marker ->
                markerClicked(marker)
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                false // do not overwrite map click handling
            }

            // user moved map manually and has stopped
            map.setOnCameraIdleListener(GoogleMap.OnCameraIdleListener {
                if (skipReload) {
                    skipReload = false
                    return@OnCameraIdleListener
                }

                val target = map.cameraPosition.target
                loadBranchInfo(target.latitude, target.longitude)
            })

            enableMyLocation()
        }
    }

    private fun startSearch() {
        val appInfo = requireContext().packageManager.getApplicationInfo(requireContext().packageName, PackageManager.GET_META_DATA)
        val mapsApiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY")

        if (!Places.isInitialized() && mapsApiKey != null) {
            Places.initialize(requireContext(), mapsApiKey)
        }

        val fields: List<Place.Field> = listOf(Place.Field.NAME, Place.Field.LAT_LNG) // choose fields to return

        val intent: Intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountry("de") // restrict results to Germany
                .build(requireContext())
        startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    log("Place: ${place.name}, ${place.latLng}")
                    moveMapTo(place.latLng!!.latitude, place.latLng!!.longitude, true)
                    skipReload = false
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    showErrorToast(ErrorType.PLACES_SERVICE)
                }
                Activity.RESULT_CANCELED -> {
                    // The user canceled the operation.
                }
            }
        }
    }

    private fun recalculateMapsMarkerPosition(bottomSheet: View) {
        bottomSheet.post {
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_SETTLING
                    || bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                map.setPadding(0, 0, 0, bottomSheet.measuredHeight - bottomSheetBehavior.peekHeight)
            }
        }
    }

    private fun loadBranchInfo(latitude: Double, longitude: Double) {

        lifecycleScope.launch {
            viewBinding.piBranchFinderBottomSheet.root.visibility = View.VISIBLE
            viewBinding.piBranchFinderBottomSheet.loadingIndicator.visibility = View.VISIBLE

            try {
                branches = emmiService.getBranchesForLocation(latitude, longitude)
            } catch (err: Throwable) {
                ensureActive()
                showErrorToast(ErrorType.NETWORK)
            }

            lastSelectedMarker = null // remove reference to marker that will be cleared
            map.clear() // remove all previous markers

            if (branches.isEmpty()) {
                showErrorToast(ErrorType.EMPTY_RESPONSE)
            }

            val nearestBranch = branches.minByOrNull { it.localisation.distance }

            branches.forEach { branch ->
                val marker = addMarker(branch)

                if (branch == nearestBranch) {
                    markerClicked(marker)
                }
            }

            moveMapTo(latitude, longitude, false)

            viewBinding.piBranchFinderBottomSheet.loadingIndicator.visibility = View.GONE
        }
    }

    private fun moveMapToGermany() {
        //These coordinates represent the center of Germany (in case the user does not allow location access)
        val latitude = 51.165691
        val longitude = 10.451526
        val zoomLevel = 6f

        val germanyLatLong = LatLng(latitude, longitude)
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(germanyLatLong, zoomLevel))
    }

    private fun moveMapTo(latitude: Double, longitude: Double, zoomIn: Boolean) {
        skipReload = true

        if (zoomIn) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 12f)) //default zoom level for user location
        } else {
            map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(latitude, longitude)))
        }
    }

    private fun addMarker(branchDetail: BranchDetailDTO?): Marker? {
        if (branchDetail != null) {
            val marker = map.addMarker(getMarkerOptions(branchDetail, R.drawable.pi_pin))
            marker?.tag = branchDetail.id
            return marker
        }
        return null
    }

    private fun getMarkerOptions(marker: BranchDetailDTO, @DrawableRes drawableRes: Int): MarkerOptions {
        return MarkerOptions()
            .position(LatLng(marker.localisation.latitude, marker.localisation.longitude))
            .icon(markerCache?.getBitmap(drawableRes)?.let {
                BitmapDescriptorFactory.fromBitmap(it)
            })
    }

    private fun showErrorToast(type: ErrorType) {

        val message = when (type) {
            ErrorType.NETWORK -> LocalizedStrings.getString("branch_finder_network_error")
            ErrorType.EMPTY_RESPONSE -> LocalizedStrings.getString("branch_finder_no_results")
            ErrorType.PLACES_SERVICE -> LocalizedStrings.getString("branch_finder_places_service_failure")
        }

        viewBinding.piBranchFinderBottomSheet.root.visibility = View.GONE

        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun isPermissionGranted(): Boolean {
        return try {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } catch (e: IllegalStateException) {
            log("could not check permission due to lifecycle", e)
            activity?.finish()
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (isPermissionGranted()) {
            map.isMyLocationEnabled = true
            LocationServices.getFusedLocationProviderClient(requireActivity()).lastLocation.addOnCompleteListener(requireActivity()) {
                if (it.result != null) {
                    moveMapTo(it.result!!.latitude, it.result!!.longitude, true)
                    skipReload = false
                } else {
                    moveMapToGermany()
                }
            }
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                enableMyLocation()
            } else {
                moveMapToGermany()
            }
        }
    }

    private fun findBranch(tag: Any?): BranchDetailDTO? = branches.findLast { it.id == tag }

    private fun markerClicked(marker: Marker?) {
        skipReload = true

        lastSelectedMarker?.setIcon(markerCache?.getBitmap(R.drawable.pi_pin)?.let {
            BitmapDescriptorFactory.fromBitmap(it)
        })
        marker?.setIcon(markerCache?.getBitmap(R.drawable.pi_pin_selected)?.let {
            BitmapDescriptorFactory.fromBitmap(it)
        })
        lastSelectedMarker = marker

        setBranchInfos(findBranch(lastSelectedMarker?.tag))
    }

    @SuppressLint("SetTextI18n")
    private fun setBranchInfos(branch: BranchDetailDTO?) {
        branch?.let {
            val sheet = viewBinding.piBranchFinderBottomSheet
            sheet.peekTitle.text = branch.branchType
            sheet.branchType.text = branch.branchType
            sheet.denotation.text = branch.denotation
            branch.branchAddress?.let {
                sheet.branchAddress.text = "${it.street} ${it.streetNumber}, ${it.zip} ${it.city}"
            }

            setOpeningTimes(branch.openingTime)

            recalculateMapsMarkerPosition(sheet.root)
        }
    }

    private fun setOpeningTimes(openingTimes: List<BranchOpeningTimeDTO>?) {
        if (openingTimes == null) return

        viewBinding.piBranchFinderBottomSheet.openingTimesWrapper.removeAllViews()

        val layoutInflater = LayoutInflater.from(activity)
        for (branchOpeningTime in openingTimes) {
            val openingTimesRow: View = layoutInflater.inflate(R.layout.pi_opening_times_row, viewBinding.piBranchFinderBottomSheet.branchDetailsContainer, false)
            val daysInWeek = openingTimesRow.findViewById<TextView>(R.id.days_in_week)
            val timesContainer = openingTimesRow.findViewById<LinearLayout>(R.id.times_container)
            daysInWeek.text = branchOpeningTime.days
            branchOpeningTime.times?.let {
                for (time in it) {
                    val view = layoutInflater.inflate(R.layout.pi_opening_times_time_view, null) as TextView
                    view.text = time
                    timesContainer.addView(view)
                }
            }
            viewBinding.piBranchFinderBottomSheet.openingTimesWrapper.addView(openingTimesRow)
        }
    }
}

// Helper class for being able to also use VECTOR assets as marker pins
// from: https://proandroiddev.com/using-vector-drawables-as-google-map-markers-on-android-1eb69790fc61
class MarkerBitmapCache(
        private val context: Context,
        size: Int // How many different TYPES of markers?
) {
    private val cache = LruCache<Int, Bitmap>(size)

    @Suppress("MagicNumber") // ktlint/detekt exception
    private fun hash(drawable: Int, color: Int? = null): Int {
        var hash = 17
        hash = hash * 31 + drawable
        hash = hash * 31 + (color ?: 0)
        return hash
    }

    fun getBitmap(
            @DrawableRes drawable: Int,
            @ColorRes tintColor: Int? = null
    ): Bitmap? {
        val currentHash = hash(drawable, tintColor ?: 0)

        if (cache[currentHash] == null) {
            drawable.toBitmap(context, tintColor)?.let {
                cache.put(currentHash, it)
            }
        }
        return cache[currentHash]
    }

    private fun Int.toBitmap(context: Context, @ColorRes tintColor: Int? = null): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, this) ?: return null
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val bm = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)

        tintColor?.let {
            DrawableCompat.setTint(drawable, ContextCompat.getColor(context, it))
        }

        val canvas = Canvas(bm)
        drawable.draw(canvas)
        return bm
    }
}
