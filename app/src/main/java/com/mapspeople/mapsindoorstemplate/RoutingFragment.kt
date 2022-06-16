package com.mapspeople.mapsindoorstemplate

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapsindoors.mapssdk.*
import com.mapsindoors.mapssdk.errors.MIError
import com.mapspeople.mapsindoorstemplate.databinding.FragmentRoutingBinding

class RoutingFragment : Fragment(), TextWatcher {
    private var toMPLocation: MPLocation? = null
    private var fromMPLocation: MPLocation? = null

    private var _binding: FragmentRoutingBinding? = null

    private val binding get() = _binding!!

    private val mAdapter = MPSearchItemRecyclerViewAdapter()
    private lateinit var mLayoutManager: LinearLayoutManager
    private var mMapsFragment: MapsFragment? = null

    private var searchingFrom = false
    private var searchingTo = false

    private var searchHandler: Handler? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRoutingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mLayoutManager = LinearLayoutManager(context)
        with(binding.list) {
            layoutManager = mLayoutManager
            adapter = mAdapter
        }

        binding.toSearchEditText.setText(toMPLocation?.name)
        setHelperText()
        binding.toSearchEditText.setOnFocusChangeListener { _, focused ->
            if (focused) {
                searchingTo = true
                binding.searchHintText.visibility = View.VISIBLE
                binding.bigView.visibility = View.INVISIBLE
                binding.searchNoResultText.visibility = View.GONE
                binding.buttonLayout.visibility = View.GONE
                binding.list.visibility = View.INVISIBLE
                binding.listSeparator.visibility = View.VISIBLE
                binding.toSearchTextField.isHelperTextEnabled = false
                MapsIndoors.getPositionProvider()?.latestPosition?.point?.let {
                    var locations = ArrayList<MPLocation>()
                    locations.add(MPLocation.Builder("myposition").setFloor(0).setName("My Position").setPosition(it).build())
                    showSearch(locations, null)
                }
            }else {
                binding.toSearchTextField.isHelperTextEnabled = true
                binding.listSeparator.visibility = View.GONE
                mAdapter.clear()
                searchingTo = false
                checkForKeyboardHide()
            }
        }
        binding.toSearchEditText.addTextChangedListener(this)
        binding.toSearchEditText.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                if (binding.toSearchEditText.text?.length!! <= 2) {
                    //binding.toSearchEditText.clearFocus()
                }else {
                    val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm?.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
            return@setOnEditorActionListener true
        }

        binding.fromSearchEditText.setOnFocusChangeListener { _, focused ->
            if (focused) {
                searchingFrom = true
                binding.searchHintText.visibility = View.VISIBLE
                binding.searchNoResultText.visibility = View.GONE
                binding.bigView.visibility = View.INVISIBLE
                binding.buttonLayout.visibility = View.GONE
                binding.list.visibility = View.INVISIBLE
                binding.listSeparator.visibility = View.VISIBLE
                binding.fromSearchTextField.isHelperTextEnabled = false
                MapsIndoors.getPositionProvider()?.latestPosition?.point?.let {
                    var locations = ArrayList<MPLocation>()
                    locations.add(MPLocation.Builder("myposition").setFloor(0).setName("My Position").setPosition(it).build())
                    showSearch(locations, null)
                }
            }else {
                binding.fromSearchTextField.isHelperTextEnabled = true
                binding.listSeparator.visibility = View.GONE
                searchingFrom = false
                mAdapter.clear()
                checkForKeyboardHide()
            }
        }
        binding.fromSearchEditText.addTextChangedListener(this)
        binding.fromSearchEditText.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                if (binding.fromSearchEditText.text?.length!! <= 2) {
                    //binding.fromSearchEditText.clearFocus()
                }else {
                    val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                    imm?.hideSoftInputFromWindow(view?.windowToken, 0)
                }
            }
            return@setOnEditorActionListener true
        }

        mAdapter.setOnLocationSelectedListener {
            it?.let {
                if (searchingFrom) {
                    fromMPLocation = it
                    binding.fromSearchEditText.clearFocus()
                } else if (searchingTo) {
                    toMPLocation = it
                    mAdapter.clear()
                    binding.toSearchEditText.clearFocus()
                }
            }
            return@setOnLocationSelectedListener true
        }

        binding.reverseBtn.setOnClickListener {
            val tempToLocation = fromMPLocation
            val tempFromLocation = toMPLocation
            fromMPLocation = tempFromLocation
            toMPLocation = tempToLocation
            binding.fromSearchEditText.setText(fromMPLocation?.name)
            binding.toSearchEditText.setText(toMPLocation?.name)
            setHelperText()
        }

        binding.closeBtn.setOnClickListener {
            toMPLocation?.let {
                mMapsFragment?.setDescriptionView(it)
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }

        binding.directionsStartButton.setOnClickListener {
            if (fromMPLocation != null && toMPLocation != null) {
                mMapsFragment?.setCurrentLocation(toMPLocation!!)
                mMapsFragment?.setAccessibility(binding.accessibilitySwitch.isChecked)
                mMapsFragment?.getRoutingProvider()?.query(fromMPLocation!!.point, toMPLocation!!.point)
            }
        }

        mMapsFragment?.setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED)

        binding.fromSearchEditText.requestFocus()
        binding.fromSearchEditText.onFocusChangeListener.onFocusChange(binding.fromSearchEditText, true)
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.showSoftInput(binding.fromSearchEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun startSearch() {
        searchHandler?.removeCallbacks(searchRunner)
        val text = if (searchingFrom) {
            binding.fromSearchEditText.text
        }else {
            binding.toSearchEditText.text
        }

        if (text?.length!! >= 2) {
            activity?.runOnUiThread {
                binding.progressIndicator.visibility = View.VISIBLE
            }
        }else {
            activity?.runOnUiThread {
                binding.progressIndicator.visibility = View.GONE
            }
        }

        searchHandler = Handler(Looper.myLooper()!!)
        searchHandler!!.postDelayed(searchRunner, 1000)
    }

    private val searchRunner: Runnable = Runnable {
        val text = if (searchingFrom) {
            binding.fromSearchEditText.text
        }else {
            binding.toSearchEditText.text
        }

        if (text?.length!! >= 2) {
            search(text.toString())
        }
    }

    private fun search(searchText: String) {
        val query = MPQuery.Builder().setQuery(searchText).build()
        val filter = MPFilter.Builder().build()
        var mpLocation: MPLocation? = null
        MapsIndoors.getPositionProvider()?.latestPosition?.point?.let {
            mpLocation = MPLocation.Builder("myposition").setFloor(0).setName("My Position").setPosition(it).build()
        }
        MapsIndoors.getLocationsAsync(query, filter) { locations, error ->
            if (mpLocation != null) {
                locations?.add(0, mpLocation)
            }

            showSearch(locations, error)
            activity?.runOnUiThread {
                binding.progressIndicator.visibility = View.GONE
            }
        }
    }

    private fun showSearch(locations: MutableList<MPLocation>?, error: MIError?) {
        if (error == null && locations?.isNotEmpty() == true) {
            if (searchingFrom || searchingTo) {
                binding.searchHintText.visibility = View.GONE
                binding.list.visibility = View.VISIBLE
                binding.listSeparator.visibility = View.VISIBLE
                binding.searchNoResultText.visibility = View.GONE
                mLayoutManager.scrollToPositionWithOffset(0,0)
                mAdapter.setLocations(locations)
                mAdapter.notifyDataSetChanged()
            }
        }else {
            binding.list.visibility = View.INVISIBLE
            binding.listSeparator.visibility = View.VISIBLE
            binding.searchHintText.visibility = View.GONE
            binding.searchNoResultText.visibility = View.VISIBLE
        }
    }

    private fun checkForKeyboardHide() {
        Handler(Looper.myLooper()!!).postDelayed(hideKeyBoardRunner, 50)
    }

    private val hideKeyBoardRunner: Runnable = Runnable {
        if (!searchingTo && !searchingFrom) {
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
            imm?.hideSoftInputFromWindow(view?.windowToken, 0)

            binding.list.visibility = View.GONE
            binding.listSeparator.visibility = View.GONE
            binding.searchNoResultText.visibility = View.GONE
            binding.searchHintText.visibility = View.GONE
            binding.bigView.visibility = View.GONE
            binding.buttonLayout.visibility = View.VISIBLE
        }
        if (fromMPLocation != null) {
            binding.fromSearchEditText.setText(fromMPLocation?.name)
        }
        if (toMPLocation != null) {
            binding.toSearchEditText.setText(toMPLocation?.name)
        }
        setHelperText()

        binding.directionsStartButton.isEnabled = toMPLocation != null && fromMPLocation != null
    }

    private fun setHelperText() {
        if (toMPLocation != null && !searchingTo) {
            val areaString = toMPLocation?.getField("area name")?.value
            if (areaString != null) {
                if (toMPLocation?.floorName != null) {
                    binding.toSearchTextField.helperText = "Floor: " + toMPLocation?.floorName + " - " + areaString
                }else {
                    binding.toSearchTextField.helperText = areaString
                }
                binding.toSearchTextField.isHelperTextEnabled = true
            }else if (toMPLocation?.floorName != null){
                binding.toSearchTextField.helperText = "Floor: " + toMPLocation?.floorName
                binding.toSearchTextField.isHelperTextEnabled = true
            }else {
                binding.toSearchTextField.isHelperTextEnabled = false
            }
        }else {
            binding.toSearchTextField.isHelperTextEnabled = false
        }

        if (fromMPLocation != null && !searchingFrom) {
            val areaString = fromMPLocation?.getField("area name")?.value
            if (areaString != null) {
                if (fromMPLocation?.floorName != null) {
                    binding.fromSearchTextField.helperText = "Floor: " + fromMPLocation?.floorName + " - " + areaString
                }else {
                    binding.fromSearchTextField.helperText = areaString
                }
                binding.fromSearchTextField.isHelperTextEnabled = true
            }else if (fromMPLocation?.floorName != null) {
                binding.fromSearchTextField.helperText = "Floor: " + fromMPLocation?.floorName
                binding.fromSearchTextField.isHelperTextEnabled = true
            }else {
                binding.fromSearchTextField.isHelperTextEnabled = true
            }
        }else {
            binding.fromSearchTextField.isHelperTextEnabled = false
        }
    }

    companion object {
        fun newInstance(location: MPLocation, mapsFragment: MapsFragment) = RoutingFragment().apply {
            toMPLocation = location
            mMapsFragment = mapsFragment
        }
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        val text = when {
            searchingFrom -> {
                binding.fromSearchEditText.text
            }
            searchingTo -> {
                binding.toSearchEditText.text
            }
            else -> {
                null
            }
        }

        text?.let {
            if (text.isNotEmpty() && text[0] == ' ') {
                if (searchingFrom) {
                    binding.fromSearchEditText.setText(text.trim())
                }else {
                    binding.toSearchEditText.setText(text.trim())
                }
            }
        }
    }

    override fun afterTextChanged(editable: Editable?) {
        if (!editable.isNullOrEmpty() && (searchingTo || searchingFrom)) {
            startSearch()
        }else if (editable.isNullOrEmpty()){
            MapsIndoors.getPositionProvider()?.latestPosition?.point?.let {
                var locations = ArrayList<MPLocation>()
                locations.add(MPLocation.Builder("myposition").setFloor(0).setName("My Position").setPosition(it).build())
                showSearch(locations, null)
            }
            binding.progressIndicator.visibility = View.GONE
        }
    }
}