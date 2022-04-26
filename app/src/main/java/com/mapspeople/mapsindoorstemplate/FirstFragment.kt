package com.mapspeople.mapsindoorstemplate

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.mapspeople.mapsindoorstemplate.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonFirst.setOnClickListener {
            var strings = ArrayList<String>()
            strings.add("Sample user role")
            strings.add("Sample user role 2")

            //TODO:Create bundle if you want to use userroles on startup
            //var bundle = Bundle().apply {
            //    putStringArrayList("userroles", strings)
            //}

            //findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, null, NavOptions.Builder().setLaunchSingleTop(true).build())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}