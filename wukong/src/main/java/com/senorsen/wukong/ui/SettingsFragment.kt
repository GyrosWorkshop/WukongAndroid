package com.senorsen.wukong.ui

import android.app.Fragment
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import com.senorsen.wukong.R

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [SettingsFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [SettingsFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class SettingsFragment : PreferenceFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences)
    }



}
