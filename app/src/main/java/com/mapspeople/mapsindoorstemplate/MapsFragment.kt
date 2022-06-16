package com.mapspeople.mapsindoorstemplate

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mapsindoors.mapssdk.*
import com.mapsindoors.mapssdk.errors.MIError
import com.mapspeople.mapsindoorstemplate.databinding.FragmentMapBinding
import com.mapspeople.mapsindoorstemplate.positioning.GPSPositionProvider
import java.util.*
import kotlin.collections.ArrayList


class MapsFragment : Fragment(), OnMapReadyCallback, OnRouteResultListener {
    private var _binding: FragmentMapBinding? = null

    private val binding get() = _binding!!
    private lateinit var mMap: GoogleMap
    private var mMapView: View? = null
    private lateinit var mMapControl: MapControl
    private lateinit var mBottomSheetBehavior: BottomSheetBehavior<FrameLayout>
    private var currentLocation: MPLocation? = null
    private var directionsRendered: Boolean = false

    private lateinit var mapFragment: SupportMapFragment

    private var mDirectionsRenderer: MPDirectionsRenderer? = null
    private var mRoutingProvider: MPRoutingProvider? = null
    private var lastSearchString: String? = null
    private var lastSearchList: ArrayList<MPLocation>? = null

    private val SEARCH_FRAGMENT = "search"
    private val ROUTING_FRAGMENT = "routing"
    private val DIRECTION_FRAGMENT = "direction"
    private val DESCRIPTION_FRAGMENT = "description"

    private var mOnLegSelectedListener: OnLegSelectedListener? = null

    private var currentFragment = "search"

    private var locationId: String? = null
    private var showDialog: Boolean = true

    private var positioningProvider: GPSPositionProvider? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the layout for this fragment
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mMapView = mapFragment.view
        mapFragment.getMapAsync(this)
        setupBottomSheet()

        val userRoleStrings = arguments?.getStringArrayList("userroles")
        val userRoles = ArrayList<UserRole>()
        MapsIndoors.getUserRoles()?.forEach {
            if (userRoleStrings?.contains(it.value) == true) {
                userRoles.add(it)
            }
        }
        if (MapsIndoors.isReady()) {
            MapsIndoors.applyUserRoles(userRoles)
        }else {
            MapsIndoors.addOnMapsIndoorsReadyListener {
                MapsIndoors.applyUserRoles(userRoles)
            }
        }

        if (savedInstanceState == null) {
            binding.loadView.visibility = View.VISIBLE
        }else {
            locationId = savedInstanceState.getString("locationID")
        }

        binding.backBtn.setOnClickListener {
            //TODO: Change this to your app specific back navigation.
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment, null,  NavOptions.Builder().setLaunchSingleTop(true).build())
        }

        requireActivity()
            .onBackPressedDispatcher
            .addCallback(viewLifecycleOwner, object: OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (activity?.currentFocus != null && activity?.currentFocus is EditText) {
                        activity?.currentFocus?.clearFocus()
                    }else {
                        when (currentFragment) {
                            SEARCH_FRAGMENT -> {
                                if (mBottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                                    setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
                                }else {
                                    isEnabled = false
                                    requireActivity().onBackPressed()
                                }
                            }
                            ROUTING_FRAGMENT -> {
                                if (currentLocation != null) {
                                    setDescriptionView(currentLocation!!)
                                }else {
                                    setSearchView()
                                }
                            }
                            DIRECTION_FRAGMENT -> {
                                mDirectionsRenderer?.clear()
                                if (currentLocation != null) {
                                    setDescriptionView(currentLocation!!)
                                }else {
                                    setSearchView()
                                }
                            }
                            DESCRIPTION_FRAGMENT -> {
                                mMapControl.deSelectLocation()
                                setSearchView()
                            }
                            else -> {
                                isEnabled = false
                                requireActivity().onBackPressed()
                            }
                        }
                    }
                }
            })
    }

    private fun initMapControl() {
        context?.let { _context ->
            mMapControl = MapControl(_context)
            mMapControl.setGoogleMap(mMap, mMapView!!)
            mMap.setPadding(0,0,0,mBottomSheetBehavior.peekHeight)
            mMapControl.setMapPadding(0, 0, 0, mBottomSheetBehavior.peekHeight)
            mMapControl.setLocationClusteringEnabled(false)
            mMapControl.setOnLocationSelectedListener {
                it?.let {
                    if (currentFragment != DIRECTION_FRAGMENT) {
                        if (directionsRendered) {
                            mDirectionsRenderer?.clear()
                        }
                        if (currentLocation != it) {
                            setDescriptionView(it)
                        }
                    }
                }
                return@setOnLocationSelectedListener false
            }

            mMapControl.setOnMapClickListener { _, _ ->
                if (activity?.currentFocus is EditText) {
                    activity?.runOnUiThread {
                        activity?.currentFocus?.clearFocus()
                        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                    }
                }
                return@setOnMapClickListener false
            }

            mMapControl.init { error ->
                if (error == null) {
                    val building = MapsIndoors.getVenues()?.defaultVenue
                    building?.let {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(it.latLngBoundingBox!!, 40))
                    }

                    if (positioningProvider == null) {
                        positioningProvider = GPSPositionProvider(_context)
                        MapsIndoors.setPositionProvider(positioningProvider)
                        MapsIndoors.startPositioning()
                        mMapControl.showUserPosition(true)
                    }

                    if (binding.loadView.visibility == View.VISIBLE) {
                        binding.loadIndicator.animate().alpha(0.0f).duration = 200
                        binding.loadView.animate()
                            .setStartDelay(300)
                            .translationY(binding.loadView.height.toFloat()-mBottomSheetBehavior.peekHeight)
                            .setDuration(1000)
                            .setListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator?) {
                                    super.onAnimationEnd(animation)
                                    binding.standardBottomSheet.visibility = View.VISIBLE
                                    binding.standardBottomSheet.animate().setDuration(500).alpha(1f).setListener(object : AnimatorListenerAdapter() {
                                        override fun onAnimationEnd(animation: Animator?) {
                                            super.onAnimationEnd(animation)
                                            binding.loadView.visibility = View.GONE
                                        }
                                    })
                                }
                            })
                    }else {
                        binding.standardBottomSheet.alpha = 1f
                        binding.standardBottomSheet.visibility = View.VISIBLE
                        if (locationId != null) {
                            MapsIndoors.getLocationById(locationId)?.let { setDescriptionView(it) }
                        }
                    }
                }
            }
        }
    }

    fun setOnLegSelectedListener(onLegSelectedListener: OnLegSelectedListener) {
        mOnLegSelectedListener = onLegSelectedListener
    }

    fun getDirectionsRenderer(): MPDirectionsRenderer {
        if (mDirectionsRenderer == null) {
            mDirectionsRenderer = MPDirectionsRenderer(requireContext(), mMap, mMapControl) { i: Int ->
                //Listener call back for when the user changes route leg. (By default is only called when a user presses the RouteLegs end marker)
                if (mOnLegSelectedListener != null) {
                    mOnLegSelectedListener?.onLegSelected(i)
                }else {
                    mDirectionsRenderer?.setRouteLegIndex(i)
                    mMapControl.selectFloor(mDirectionsRenderer!!.currentFloor)
                }
            }
        }

        return mDirectionsRenderer!!
    }

    fun getRoutingProvider(): MPRoutingProvider {
        if (mRoutingProvider == null) {
            mRoutingProvider = MPRoutingProvider()
            mRoutingProvider!!.setOnRouteResultListener(this)
        }
        showDialog = true
        return mRoutingProvider!!
    }

    fun setAccessibility(accessibility: Boolean) {
        val routingProvider = getRoutingProvider()
        routingProvider.clearRouteRestrictions()
        if (accessibility) {
            routingProvider.addRouteRestriction(Highway.STEPS)
            routingProvider.addRouteRestriction(Highway.LADDER)
            routingProvider.addRouteRestriction(Highway.ESCALATOR)
        }else {
            routingProvider.addRouteRestriction(Highway.ELEVATOR)
        }
    }

    fun setCurrentLocation(location: MPLocation?) {
        currentLocation = location
    }

    private fun setupBottomSheet() {
        val bottomSheet: FrameLayout = binding.root.findViewById(R.id.standardBottomSheet)
        mBottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        mBottomSheetBehavior.addBottomSheetCallback(object: BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    mMapControl.enableFloorSelector(true)
                    mMapControl.floorSelector?.setSelectedFloorByZIndex(mMapControl.currentFloorIndex)
                    if (currentFragment == SEARCH_FRAGMENT) {
                        val view = view?.findViewById<EditText>(R.id.searchEditText)
                        view?.clearFocus()
                        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                    }else if (currentFragment == ROUTING_FRAGMENT) {
                        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                    }
                }else {
                    if (newState == BottomSheetBehavior.STATE_EXPANDED && currentFragment == SEARCH_FRAGMENT) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            val view = view?.findViewById<EditText>(R.id.searchEditText)
                            view?.requestFocus()
                            view?.let {
                                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                                imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                            }
                        }, 100)
                    }
                    mMapControl.enableFloorSelector(false)
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
        parentFragmentManager.beginTransaction().replace(R.id.standardBottomSheet, SearchFragment.newInstance(this)).addToBackStack(null).commit()
        //Set the map padding to the height of the bottom sheets peek height. To not obfuscate the google logo.
        setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
    }

    fun setSearchView() {
        setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
        currentFragment = SEARCH_FRAGMENT
        parentFragmentManager.beginTransaction().replace(R.id.standardBottomSheet, SearchFragment.newInstance(this)).commit()
    }

    fun setDescriptionView(location: MPLocation) {
        mMapControl.clearMap()
        setCurrentLocation(location)
        mMapControl.selectLocation(location)
        currentFragment = DESCRIPTION_FRAGMENT
        parentFragmentManager.beginTransaction().replace(R.id.standardBottomSheet, DescriptionFragment.newInstance(location, this)).commit()
    }

    fun setRoutingView(location: MPLocation) {
        mMapControl.clearMap()
        currentFragment = ROUTING_FRAGMENT
        parentFragmentManager.beginTransaction().replace(R.id.standardBottomSheet, RoutingFragment.newInstance(location, this)).commit()
    }

    fun setSearchString(text: String?) {
        lastSearchString = text
    }

    fun setLastSearchResults(locations: ArrayList<MPLocation>?) {
        if (locations != null) {
            activity?.runOnUiThread {
                mMapControl.displaySearchResults(locations, false, 0, false, CameraUpdateFactory.zoomBy(0f), 0, null)
            }
        }else {
            mMapControl.clearMap()
        }
        lastSearchList = locations
    }

    fun getLastSearchResults(): ArrayList<MPLocation>? = lastSearchList

    fun getSearchString(): String? {
        return lastSearchString
    }

    fun getMapControl(): MapControl {
        return mMapControl
    }

    fun getGoogleMap(): GoogleMap {
        return mMap
    }

    private fun setDirectionsView(route: Route, location: MPLocation?) {
        location?.let {
            parentFragmentManager.beginTransaction().replace(R.id.standardBottomSheet, DirectionsFragment.newInstance(route, it, this)).commit()
            directionsRendered = true
            currentFragment = DIRECTION_FRAGMENT
            activity?.runOnUiThread {
                mBottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    fun setBottomSheetState(state: Int) {
        activity?.runOnUiThread {
            mBottomSheetBehavior.state = state
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (currentFragment == DESCRIPTION_FRAGMENT && currentLocation != null) {
            outState.putString("locationID", currentLocation!!.id)
        }
        outState.putBoolean("restarted", true)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mDirectionsRenderer?.clear()
        if (mMapControl != null) {
            mMapControl.onDestroy()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        initMapControl()

        setupCompass()
    }

    override fun onStart() {
        super.onStart()
        if (this::mMapControl.isInitialized) {
            mMapControl.onStart()
            positioningProvider?.let {
                MapsIndoors.startPositioning()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (this::mMapControl.isInitialized) {
            mMapControl.onStop()
            positioningProvider?.let {
                MapsIndoors.stopPositioning()
            }
        }
    }

    fun setupCompass() {
        mapFragment.view?.let { mapView ->
            mapView.findViewWithTag<View>("GoogleMapMyLocationButton").parent?.let { parent ->
                val vg: ViewGroup = parent as ViewGroup
                vg.post {
                    val mapCompass: View = parent.getChildAt(4)
                    val rlp = RelativeLayout.LayoutParams(mapCompass.height, mapCompass.height)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_LEFT, 0)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                    rlp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0)

                    val topMargin = (10 * Resources.getSystem().displayMetrics.density).toInt()
                    rlp.setMargins(0, topMargin, topMargin, 0)
                    mapCompass.layoutParams = rlp
                }
            }
        }
    }

    fun selectLocation(location: MPLocation) {
        mMapControl.selectLocation(location)
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onRouteResult(route: Route?, error: MIError?) {
        if (error != null || route == null) {
            activity?.runOnUiThread {
                context?.let {
                    if (showDialog) {
                        MaterialAlertDialogBuilder(it, R.style.ThemeOverlay_MaterialComponents_MaterialAlertDialog)
                            .setTitle(getString(R.string.no_route))
                            .setMessage(getString(R.string.no_route_message))
                            .setPositiveButton(getString(R.string.ok)) {dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                        showDialog = false
                    }
                }
            }
        }else{
            getDirectionsRenderer().setRoute(route)
            activity?.runOnUiThread {
                getDirectionsRenderer().initMap(true)
                setDirectionsView(route, currentLocation)
            }
        }
    }
}