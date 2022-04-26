package com.mapspeople.mapsindoorstemplate

import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapsindoors.mapssdk.MPLocation
import com.mapsindoors.mapssdk.MapsIndoors
import com.mapspeople.mapsindoorstemplate.databinding.FragmentDescriptionBinding


class DescriptionFragment : Fragment() {
    private var mpLocation: MPLocation? = null
    private var _binding: FragmentDescriptionBinding? = null

    private val binding get() = _binding!!

    private var mMapsFragment: MapsFragment? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDescriptionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.locationName.text = mpLocation?.name

        if (!mpLocation?.categories.isNullOrEmpty()) {
            val strings = ArrayList<String>()
            mpLocation?.categories?.forEach { category ->
                MapsIndoors.getCategories()?.getValue(category)?.let {
                    strings.add(it)
                }
            }
            binding.categoryTextView.text = strings.joinToString(", ")
        }else {
            binding.categoryLayout.visibility = View.GONE
        }

        if (mpLocation?.floorName != null) {
            binding.floorTxt.text = mpLocation?.floorName
        }else {
            binding.floorTxt.visibility = View.GONE
        }

        if (mpLocation?.description != null) {
            if (mpLocation?.description?.length!! > 200) {
                val text = mpLocation?.description?.substring(0, 220) + "..."
                val finalTxt = mpLocation?.description!!

                val spannableString = SpannableString(text + System.getProperty("line.separator") + "Show More")
                val span1: ClickableSpan = object : ClickableSpan() {
                    override fun onClick(textView1: View) {
                        // do some thing
                        val ss1 = SpannableString(finalTxt + System.getProperty("line.separator") + "Show Less")
                        val span2: ClickableSpan = object : ClickableSpan() {
                            override fun onClick(textView: View) {
                                // do some thing
                                if (textView is TextView) {
                                    textView.text = spannableString
                                    textView.movementMethod = LinkMovementMethod.getInstance()
                                }
                            }
                        }
                        ss1.setSpan(span2, finalTxt.length, ss1.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        ss1.setSpan(ForegroundColorSpan(Color.BLUE), finalTxt.length, ss1.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        if (textView1 is TextView) {
                            textView1.text = (ss1)
                            textView1.movementMethod = LinkMovementMethod.getInstance()
                        }
                    }
                }
                spannableString.setSpan(span1, 224, 233, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannableString.setSpan(ForegroundColorSpan(Color.BLUE), 224, 233, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

                binding.descriptionTextView.text = spannableString
                binding.descriptionTextView.movementMethod = LinkMovementMethod.getInstance()
            }else {
                binding.descriptionTextView.text = mpLocation?.description
            }
        }else {
            binding.descriptionLayout.visibility = View.GONE
        }

        binding.directionsBtn.setOnClickListener {
            mpLocation?.let {
                mMapsFragment?.setRoutingView(it)
            }
        }

        binding.closeBtn.setOnClickListener {
            mMapsFragment?.setSearchView()
        }



        mMapsFragment?.setBottomSheetState(BottomSheetBehavior.STATE_EXPANDED)
    }

    companion object {
        fun newInstance(location: MPLocation, mapsFragment: MapsFragment) = DescriptionFragment().apply {
            mpLocation = location
            mMapsFragment = mapsFragment
        }
    }
}