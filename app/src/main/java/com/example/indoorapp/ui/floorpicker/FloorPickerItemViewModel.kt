/*
 *
 * COPYRIGHT 2023 ESRI
 *
 * TRADE SECRETS: ESRI PROPRIETARY AND CONFIDENTIAL
 * Unpublished material - all rights reserved under the
 * Copyright Laws of the United States and applicable international
 * laws, treaties, and conventions.
 *
 * For additional information, contact:
 * Environmental Systems Research Institute, Inc.
 * Attn: Contracts and Legal Services Department
 * 380 New York Street
 * Redlands, California, 92373
 * USA
 *
 * email: contracts@esri.com
 *
 */
package com.example.indoorapp.ui.floorpicker

import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import com.example.indoorapp.ui.floorpicker.models.Floor
import com.example.indoorapp.util.RecyclerViewItem
import kotlinx.coroutines.flow.MutableStateFlow

class FloorPickerItemViewModel(
    val floor: Floor,
    private val currentFloor: MutableStateFlow<Floor?>,
    private val pickerState: MutableStateFlow<FloorPickerState>
) : RecyclerViewItem() {

    val isSelected = currentFloor.asLiveData().map { it == floor }

    fun onClick() {
        if (isSelected.value == false) {
            currentFloor.value = floor
        }
        // Auto close floor list
        pickerState.value = FloorPickerState.COLLAPSED
    }
}
