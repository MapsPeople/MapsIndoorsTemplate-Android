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
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.mapsindoors.mapssdk.*


/**
 * A fragment representing a list of Items.
 */
class SearchFragment : Fragment(), TextWatcher {
    private lateinit var searchInputTextLayout: TextInputLayout
    private lateinit var searchInputTextView: TextInputEditText
    private lateinit var searchProgress: LinearProgressIndicator
    private lateinit var mLinearLayoutManager: LinearLayoutManager
    private val mAdapter = MPSearchItemRecyclerViewAdapter()
    private lateinit var mRecyclerView: RecyclerView
    private lateinit var mSearchHintText: TextView
    private lateinit var mNoResultHintText: TextView

    private var searchHandler: Handler? = null

    private var mMapsFragment: MapsFragment? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_search_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set the adapter
        mRecyclerView = view.findViewById(R.id.list)
        mLinearLayoutManager = LinearLayoutManager(context)
        with(mRecyclerView) {
            layoutManager = mLinearLayoutManager
            adapter = mAdapter
        }
        mSearchHintText = view.findViewById(R.id.searchHintText)
        mNoResultHintText = view.findViewById(R.id.searchNoResultText)
        searchProgress = view.findViewById(R.id.progress_indicator)
        searchInputTextLayout = view.findViewById(R.id.searchTextField)
        searchInputTextView = view.findViewById(R.id.searchEditText)
        if (!mMapsFragment?.getSearchString().isNullOrEmpty() && mMapsFragment?.getLastSearchResults() != null) {
            searchInputTextView.setText(mMapsFragment?.getSearchString())
            mAdapter.setLocations(mMapsFragment?.getLastSearchResults()!!)
            if (mMapsFragment?.getLastSearchResults() != null) {
                mMapsFragment?.getMapControl()?.displaySearchResults(mMapsFragment!!.getLastSearchResults()!!, false, 0, false, CameraUpdateFactory.zoomBy(0f), 0, null)
            }else {
                mMapsFragment?.getMapControl()?.clearMap()
            }
        }

        searchInputTextView.addTextChangedListener(this)
        searchInputTextView.setOnEditorActionListener { _, i, _ ->
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
                searchInputTextView.clearFocus()
            }
            return@setOnEditorActionListener true
        }

        searchInputTextView.setOnFocusChangeListener { _, focused ->
            if (focused) {
                mMapsFragment?.setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED)
                if (mAdapter.itemCount <= 0) {
                    mSearchHintText.visibility = View.VISIBLE
                    mNoResultHintText.visibility = View.GONE
                    mRecyclerView.isNestedScrollingEnabled = false
                    mRecyclerView.visibility = View.INVISIBLE
                }
            }
        }

        mAdapter.setOnLocationSelectedListener {
            it?.let { mpLocation ->
                mMapsFragment?.selectLocation(mpLocation)
            }
            return@setOnLocationSelectedListener false
        }

        searchInputTextLayout.setEndIconOnClickListener {
            searchInputTextView.setText("")
        }
    }

    private fun startSearch() {
        searchHandler?.removeCallbacks(searchRunner)
        if (searchInputTextView.text?.length!! >= 2) {
            activity?.runOnUiThread {
                searchProgress.visibility = View.VISIBLE
            }
        }else {
            activity?.runOnUiThread {
                searchProgress.visibility = View.GONE
            }
            mAdapter.clear()
            mMapsFragment?.setLastSearchResults(null)
            mRecyclerView.visibility = View.INVISIBLE
            mRecyclerView.isNestedScrollingEnabled = false
            mSearchHintText.visibility = View.VISIBLE
            mNoResultHintText.visibility = View.GONE
        }
        searchHandler = Handler(Looper.myLooper()!!)
        searchHandler!!.postDelayed(searchRunner, 1000)
    }

    private val searchRunner: Runnable = Runnable {
        val text = searchInputTextView.text
        if (text?.length!! >= 2) {
            search(text.toString())
        }
    }

    private fun search(searchText: String) {
        val queryProperties = ArrayList<String>()
        queryProperties.add(LocationPropertyNames.NAME)
        val latLng = mMapsFragment?.getGoogleMap()?.projection?.visibleRegion?.latLngBounds?.center
        val floorIndex = mMapsFragment?.getMapControl()?.currentFloorIndex?.toDouble()

        val query: MPQuery = if (latLng != null && floorIndex != null) {
            val point = Point(latLng.latitude, latLng.longitude, floorIndex)
            MPQuery.Builder().setQuery(searchText).setQueryProperties(queryProperties).setNear(point).build()
        }else {
            MPQuery.Builder().setQuery(searchText).setQueryProperties(queryProperties).build()
        }

        val filter = MPFilter.Builder().build()
        MapsIndoors.getLocationsAsync(query, filter) { locations, error ->
            if (error == null && locations?.isNotEmpty() == true) {
                mLinearLayoutManager.scrollToPositionWithOffset(0, 0)
                mAdapter.setLocations(locations)
                mMapsFragment?.setLastSearchResults(locations as ArrayList<MPLocation>?)
                mAdapter.notifyDataSetChanged()
                mSearchHintText.visibility = View.GONE
                mRecyclerView.visibility = View.VISIBLE
                mRecyclerView.isNestedScrollingEnabled = true
                mNoResultHintText.visibility = View.GONE
            }else {
                mAdapter.clear()
                mMapsFragment?.setLastSearchResults(null)
                mRecyclerView.visibility = View.INVISIBLE
                mRecyclerView.isNestedScrollingEnabled = false
                mSearchHintText.visibility = View.GONE
                mNoResultHintText.visibility = View.VISIBLE
            }
            activity?.runOnUiThread {
                searchProgress.visibility = View.GONE
            }
        }
    }

    override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

    override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
        val text = searchInputTextView.text
        text?.let {
            if (text.isNotEmpty() && text[0] == ' ') {
                searchInputTextView.setText(text.trim())
            }
        }
    }

    override fun afterTextChanged(editable: Editable?) {
        if (!editable.isNullOrEmpty()) {
            startSearch()
        }else {
            mRecyclerView.visibility = View.INVISIBLE
            mRecyclerView.isNestedScrollingEnabled = false
            mSearchHintText.visibility = View.VISIBLE
            mNoResultHintText.visibility = View.GONE
            mAdapter.clear()
            mMapsFragment?.setLastSearchResults(null)
        }
        if (editable != null) {
            mMapsFragment?.setSearchString(editable.toString())
        }else {
            mMapsFragment?.setSearchString(null)
        }
    }

    companion object {
        fun newInstance(mapsFragment: MapsFragment) = SearchFragment().apply {
            mMapsFragment = mapsFragment
        }
    }
}