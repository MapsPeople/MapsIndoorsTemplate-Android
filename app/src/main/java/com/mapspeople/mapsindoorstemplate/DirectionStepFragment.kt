package com.mapspeople.mapsindoorstemplate

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.mapsindoors.mapssdk.Highway
import com.mapspeople.mapsindoorstemplate.databinding.FragmentDirectionStepBinding
import java.util.*
import java.util.concurrent.TimeUnit

class DirectionStepFragment : Fragment() {
    private var mStep: String? = null
    private var mIconType: String? = null
    private var mDuration: Int? = null
    private var mDistance: Int? = null
    private var actionNames: Array<String?>? = null

    private val mActionFileId = intArrayOf(
        R.drawable.ic_vec_sig_lift,
        R.drawable.ic_vec_sig_escalator,
        R.drawable.ic_vec_sig_stairs,
        R.drawable.ic_vec_sig_stairs,
        R.drawable.misdk_ic_ramp,
        R.drawable.misdk_ic_wheelchairlift,
        R.drawable.misdk_ic_wheelchairramp,
        R.drawable.misdk_ic_ladder
    )

    private var _binding: FragmentDirectionStepBinding? = null

    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FragmentDirectionStepBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.stepTextView.text = mStep
        when (mIconType) {
            "pin" -> {
                Glide.with(this).load(R.drawable.ic_baseline_area).into(binding.directionType)
            }
            "enter" -> {
                Glide.with(this).load(R.drawable.ic_vec_sig_enter).into(binding.directionType)
            }
            "exit" -> {
                Glide.with(this).load(R.drawable.ic_vec_sig_exit).into(binding.directionType)
            }
            else -> {
                getActionNames().forEachIndexed { index, s ->
                    if (mIconType == s) {
                        Glide.with(this).load(mActionFileId[index]).into(binding.directionType)
                    }
                }
            }
        }
        if (Locale.getDefault().country == "US") {
            binding.distanceTextView.text = (mDistance?.times(3.281))?.toInt().toString() + " feet"
        }else {
            binding.distanceTextView.text = mDistance?.toString() + " m"
        }
        mDuration?.let {
            if (it < 60) {
                binding.durationTextView.text = it.toString() + " sec"
            }else {
                binding.durationTextView.text = TimeUnit.MINUTES.convert(it.toLong(), TimeUnit.SECONDS).toString() + " min"
            }
        }
    }

    fun getActionNames(): Array<String?> {
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
        fun newInstance(step: String, iconType: String, distance: Int?, duration: Int?) =
            DirectionStepFragment().apply {
                mStep = step
                mIconType = iconType
                mDistance = distance
                mDuration = duration
            }
    }
}