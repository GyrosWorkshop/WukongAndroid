package com.senorsen.wukong.ui

import android.app.Fragment
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

import com.senorsen.wukong.R
import com.senorsen.wukong.service.WukongService

/**
 * A simple [Fragment] subclass.
 * Activities that contain this fragment must implement the
 * [MainFragment.OnFragmentInteractionListener] interface
 * to handle interaction events.
 * Use the [MainFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MainFragment : Fragment() {

    // TODO: Rename and change types of parameters
    private var mParam1: String? = null
    private var mParam2: String? = null

    private var mListener: OnFragmentInteractionListener? = null

    lateinit var serviceIntent: Intent

    private val REQUEST_COOKIES = 0

    var cookies: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (arguments != null) {
            mParam1 = arguments.getString(ARG_PARAM1)
            mParam2 = arguments.getString(ARG_PARAM2)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_COOKIES -> {
                if (data == null) return
                cookies = data.getStringExtra("cookies")
                Toast.makeText(activity.applicationContext, "Sign in successfully.", Toast.LENGTH_SHORT).show()

                val sharedPref = activity.applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                val editor = sharedPref.edit()
                        .remove("cookies")
                        .putString("cookies", cookies)
                        .apply()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        val view = inflater.inflate(R.layout.fragment_main, container, false)

        view.findViewById(R.id.button_settings).setOnClickListener {
            fragmentManager.beginTransaction()
                    .replace(R.id.fragment, SettingsFragment(), "SETTINGS")
                    .addToBackStack("tag")
                    .commit()
        }

        view.findViewById(R.id.sign_in).setOnClickListener {
            startActivityForResult(Intent(activity, WebViewActivity::class.java), REQUEST_COOKIES)
        }

        val channelEdit = view.findViewById(R.id.channel_id) as EditText

        val startServiceButton = view.findViewById(R.id.start_service) as Button
        startServiceButton.setOnClickListener {
            if (cookies == null) {
                cookies = activity.applicationContext
                        .getSharedPreferences("wukong", Context.MODE_PRIVATE)
                        .getString("cookies", "")
            }

            if (channelEdit.text.isNullOrBlank()) {
                channelEdit.error = "required"
                return@setOnClickListener
            }

            activity.applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE)
                    .edit()
                    .putString("channel", channelEdit.text.toString()).apply()

            activity.stopService(Intent(activity, WukongService::class.java))

            serviceIntent = Intent(activity, WukongService::class.java)
                    .putExtra("cookies", cookies)
                    .putExtra("channel", channelEdit.text.toString().trim())
            activity.startService(serviceIntent)
        }

        val stopServiceButton = view.findViewById(R.id.stop_service) as Button
        stopServiceButton.setOnClickListener {
            activity.stopService(Intent(activity, WukongService::class.java))
        }

        channelEdit.text = SpannableStringBuilder(activity.applicationContext.getSharedPreferences("wukong", Context.MODE_PRIVATE).getString("channel", ""))
        channelEdit.setSelection(channelEdit.text.toString().length)

        return view
    }

    // TODO: Rename method, update argument and hook method into UI event
    fun onButtonPressed(uri: Uri) {
        if (mListener != null) {
            mListener!!.onFragmentInteraction(uri)
        }
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is OnFragmentInteractionListener) {
            mListener = context as OnFragmentInteractionListener?
        } else {
            throw RuntimeException(context!!.toString() + " must implement OnFragmentInteractionListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     *
     *
     * See the Android Training lesson [Communicating with Other Fragments](http://developer.android.com/training/basics/fragments/communicating.html) for more information.
     */
    interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        fun onFragmentInteraction(uri: Uri)
    }

    companion object {
        // TODO: Rename parameter arguments, choose names that match
        // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
        private val ARG_PARAM1 = "param1"
        private val ARG_PARAM2 = "param2"

        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.

         * @param param1 Parameter 1.
         * *
         * @param param2 Parameter 2.
         * *
         * @return A new instance of fragment MainFragment.
         */
        // TODO: Rename and change types and number of parameters
        fun newInstance(param1: String, param2: String): MainFragment {
            val fragment = MainFragment()
            val args = Bundle()
            args.putString(ARG_PARAM1, param1)
            args.putString(ARG_PARAM2, param2)
            fragment.arguments = args
            return fragment
        }
    }
}// Required empty public constructor
