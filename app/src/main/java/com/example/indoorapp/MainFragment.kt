package com.example.indoorapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.httpcore.authentication.ArcGISAuthenticationChallenge
import com.arcgismaps.httpcore.authentication.ArcGISAuthenticationChallengeHandler
import com.arcgismaps.httpcore.authentication.ArcGISAuthenticationChallengeResponse
import com.arcgismaps.httpcore.authentication.TokenCredential
import com.arcgismaps.location.Location.SourceProperties.Values.POSITION_SOURCE_GNSS
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.example.indoorapp.databinding.FragmentMainBinding
import com.example.indoorapp.ui.floorpicker.FloorPickerItemViewModel
import com.example.indoorapp.util.RecyclerViewAdapter
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel
    private lateinit var fragmentMainBinding: FragmentMainBinding

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result.values.none { value -> !value }) {
                viewModel.connectToPortal()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        ArcGISEnvironment.applicationContext = requireContext()
        ArcGISEnvironment.authenticationManager.arcGISAuthenticationChallengeHandler =
            object : ArcGISAuthenticationChallengeHandler {
                override suspend fun handleArcGISAuthenticationChallenge(challenge: ArcGISAuthenticationChallenge): ArcGISAuthenticationChallengeResponse {
                    val tokenCredential = TokenCredential.create(
                        viewModel.portalURL,
                        "vwoell_ViennaRDC",
                        "GeoSwoellv98!",
                        1000
                    ).getOrElse {
                        return ArcGISAuthenticationChallengeResponse.ContinueAndFail
                    }

                    return ArcGISAuthenticationChallengeResponse.ContinueWithCredential(
                        tokenCredential
                    )
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        fragmentMainBinding = FragmentMainBinding.inflate(layoutInflater)
        lifecycle.addObserver(fragmentMainBinding.mapView)

        fragmentMainBinding.lifecycleOwner = viewLifecycleOwner
        fragmentMainBinding.viewModel = viewModel

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.drop(1).collect { uiState ->
                    handleUiStateUpdate(uiState)
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.locationDetailsState.drop(1).collect { data ->
                    data?.let { updateLocationDetailsView(it) }
                }
            }
        }

        val adapter = FloorPickerAdapter(this)
        fragmentMainBinding.floorPickerRecycler.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.floorPickerViewModel.floors.observe(
                    viewLifecycleOwner
                ) { floors ->
                    val items = floors.map {
                        FloorPickerItemViewModel(
                            it,
                            viewModel.floorPickerViewModel.currentFloor,
                            viewModel.floorPickerViewModel.pickerState
                        )
                    }
                    adapter.submitList(items)
                }
            }
        }

        val startStopButton = fragmentMainBinding.startStopButton
        startStopButton.setOnClickListener {
            if (startStopButton.text == resources.getString(R.string.startILDSButton)) {
                viewModel.startIndoorsLocationDataSource()
            } else {
                viewModel.stopIndoorsLocationDataSource()
            }
        }

        if (savedInstanceState == null) {
            if (requireContext().checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                requireContext().checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        requireContext().checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
            ) {
                val requestPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                } else {
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                }

                requestPermissionLauncher.launch(requestPermissions)
            } else {
                viewModel.connectToPortal()
            }
        }

        return fragmentMainBinding.root
    }

    private fun handleUiStateUpdate(uiState: UiState) {
        fragmentMainBinding.progressBarHolder.isVisible = uiState.showProgressBar
        fragmentMainBinding.startStopButton.isVisible = uiState.startStopButtonVisibility
        uiState.startStopButtonText?.let { fragmentMainBinding.startStopButton.setText(it) }

        uiState.errorString?.let { showAlert(it) }

        when (uiState.mapState) {
            MapState.INIT -> {
                fragmentMainBinding.mapView.apply {
                    map = uiState.map
                    viewpointChanged.onEach {
                        viewModel.floorPickerViewModel.visibleExtent = visibleArea?.extent
                        updateForClosestFacility()
                    }.launchIn(lifecycleScope)
                    navigationChanged.onEach { navigating ->
                        if (!navigating) {
                            updateForClosestFacility()
                        }
                    }.launchIn(lifecycleScope)
                }
            }

            MapState.MAP_LOADED -> {
                uiState.indoorsLocationDataSource?.let { indoorsLocationDataSource ->
                    val locationDisplay = fragmentMainBinding.mapView.locationDisplay
                    locationDisplay.dataSource = indoorsLocationDataSource
                    locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.CompassNavigation)
                    viewModel.startIndoorsLocationDataSource()
                }

                viewModel.floorPickerViewModel.apply {
                    visibleExtent = fragmentMainBinding.mapView.visibleArea?.extent
                    updateForClosestFacility()
                }
            }

            else -> {}
        }
    }

    private fun updateForClosestFacility() {
        lifecycleScope.launch { viewModel.floorPickerViewModel.queryForClosestFacility() }
    }

    private fun updateLocationDetailsView(data: LocationDetailsState) {
        fragmentMainBinding.locationDataView.isVisible = data.isVisible
        fragmentMainBinding.floor.text = getString(R.string.debug_floor, data.floor)
        fragmentMainBinding.positionSource.text =
            getString(R.string.debug_position_source, data.positionSourceText)
        fragmentMainBinding.horizontalAccuracy.text =
            getString(R.string.debug_horizontal_accuracy, data.horizontalAccuracyText)
        fragmentMainBinding.senderCount.text =
            if (data.positionSourceText == POSITION_SOURCE_GNSS) {
                getString(R.string.debug_network_count, data.senderCount)
            } else {
                getString(R.string.debug_transmitter_count, data.senderCount)
            }
    }

    private fun showAlert(@StringRes textResource: Int) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.alert_title)
        builder.setMessage(textResource)

        builder.setPositiveButton(android.R.string.ok, null)
        builder.show()
    }

    private inner class FloorPickerAdapter(lifecycleOwner: LifecycleOwner) :
        RecyclerViewAdapter<FloorPickerItemViewModel>(lifecycleOwner, 1) {
        override fun getItemViewType(position: Int): Int = R.layout.item_floor_picker_list_item
    }
}