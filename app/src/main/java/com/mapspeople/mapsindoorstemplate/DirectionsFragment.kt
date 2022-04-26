package com.mapspeople.mapsindoorstemplate

import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.tabs.TabLayoutMediator
import com.mapsindoors.mapssdk.*
import com.mapspeople.mapsindoorstemplate.databinding.FragmentDirectionsBinding

class DirectionsFragment : Fragment() {
    private var mRoute: Route? = null
    private var mLocation: MPLocation? = null
    private var mMapsFragment: MapsFragment? = null
    private var actionNames: Array<String?>? = null

    private var _binding: FragmentDirectionsBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentDirectionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.locationName.text = "To " + mLocation?.name

        val directionsAdapter = DirectionsStepAdapter(this)
        val viewPager = binding.stepViewPager
        viewPager.adapter = directionsAdapter
        viewPager.registerOnPageChangeCallback(object : OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                mMapsFragment?.getDirectionsRenderer()?.setRouteLegIndex(position)
            }
        })

        TabLayoutMediator(binding.tabDots, viewPager) { tab, _ ->
            tab.select()
        }.attach()

        binding.closeBtn.setOnClickListener {
            mMapsFragment?.getDirectionsRenderer()?.clear()
            mLocation?.let {
                mMapsFragment?.setDescriptionView(it)
            }
        }

        mMapsFragment?.setOnLegSelectedListener {
            viewPager.setCurrentItem(it, true)
        }

        mMapsFragment?.setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED)
    }

    fun getStepName(startStep: RouteStep, endStep: RouteStep): String {
        val startStepStartPointZIndex = startStep.startLocation?.zIndex
        val startStepStartFloorName = startStep.startLocation?.floorName
        var highway: String? = null
        getActionNames().forEach {
            it?.let {
                if (startStep.highway == it) {
                    highway = if (it == Highway.STEPS) {
                        "stairs"
                    }else {
                        it
                    }
                }
            }
        }
        if (highway != null) {
            return String.format("Take %s to %s %s", highway, "Level", if (endStep.endLocation?.floorName.isNullOrEmpty()) endStep.endLocation?.zIndex else endStep.endLocation?.floorName)
        }
        var result = getString(R.string.step_walk_to)

        if (startStepStartFloorName == endStep.endLocation?.floorName) {
            return result
        }

        val endStepEndFloorName = endStep.endLocation?.floorName

        result = if (TextUtils.isEmpty(endStepEndFloorName)) {
            String.format("Level %s to %s", if (TextUtils.isEmpty(startStepStartFloorName)) startStepStartPointZIndex else startStepStartFloorName, endStep.endPoint.zIndex)
        } else {
            String.format("Level %s to %s", if (TextUtils.isEmpty(startStepStartFloorName)) startStepStartPointZIndex else startStepStartFloorName, endStepEndFloorName)
        }
        return result
    }

    private fun getActionNames(): Array<String?> {
        if (actionNames == null) {
            actionNames = arrayOf(
                Highway.ELEVATOR,
                Highway.ESCALATOR,
                Highway.STEPS,
                Highway.TRAVELATOR,
                Highway.RAMP,
                Highway.WHEELCHAIRRAMP,
                Highway.WHEELCHAIRLIFT,
                Highway.LADDER
            )
        }
        return actionNames!!
    }


    companion object {
        @JvmStatic
        fun newInstance(route: Route, location: MPLocation, mapsFragment: MapsFragment) = DirectionsFragment().apply {
            mRoute = route
            mLocation = location
            mMapsFragment = mapsFragment
        }
    }

    inner class DirectionsStepAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int {
            return if (mRoute != null) {
                mRoute!!.legs.size
            }else {
                0
            }
        }

        override fun createFragment(position: Int): Fragment {
            if (position == mRoute?.legs?.size!! - 1) {
                return DirectionStepFragment.newInstance("Walk to " + mLocation?.name, "pin", mRoute?.legs!![position]?.distance?.toInt(), mRoute?.legs!![position]?.duration?.toInt())
            } else {
                val leg = mRoute?.legs!![position]
                val firstStep = leg.steps.first()
                val lastFirstStep = mRoute?.legs!![position + 1].steps.first()
                val lastStep = mRoute?.legs!![position + 1].steps.last()

                val firstBuilding = MapsIndoors.getBuildings()?.getBuilding(firstStep.startPoint.latLng)
                val lastBuilding  = MapsIndoors.getBuildings()?.getBuilding(lastStep.startPoint.latLng)
                return if (firstBuilding != null && lastBuilding != null) {
                    DirectionStepFragment.newInstance(getStepName(lastFirstStep, lastStep), lastFirstStep.highway, leg.distance.toInt(), leg.duration.toInt())
                }else if (firstBuilding != null) {
                    DirectionStepFragment.newInstance("Exit: " + firstBuilding.name, "exit", leg.distance.toInt(), leg.duration.toInt())
                }else {
                    DirectionStepFragment.newInstance("Enter: " + lastBuilding?.name, "enter", leg.distance.toInt(), leg.duration.toInt())
                }
            }
        }
    }
}