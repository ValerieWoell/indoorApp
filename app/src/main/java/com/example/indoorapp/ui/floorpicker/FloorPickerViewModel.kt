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

import androidx.lifecycle.MutableLiveData
import com.arcgismaps.data.Feature
import com.arcgismaps.data.QueryFeatureFields
import com.arcgismaps.data.QueryParameters
import com.arcgismaps.data.ServiceFeatureTable
import com.arcgismaps.geometry.Envelope
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.geometry.Point
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.layers.FeatureLayer
import com.example.indoorapp.ui.floorpicker.models.AIIM
import com.example.indoorapp.ui.floorpicker.models.Floor
import com.example.indoorapp.util.filterForFloorVisibility
import com.example.indoorapp.util.projectPointIfNecessary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

enum class FloorPickerState {
    HIDDEN,
    COLLAPSED,
    EXPANDED
}

class FloorPickerViewModel(scope: CoroutineScope) {
    val pickerState = MutableStateFlow(FloorPickerState.HIDDEN)
    val currentFloor = MutableStateFlow<Floor?>(null)
    val floors =
        MutableLiveData(emptyList<Floor>())  // We need LiveData to get updated also with same values

    var visibleExtent: Envelope? = null
    var closestFacilityId: String? = null
    var agsMap: ArcGISMap? = null

    init {
        currentFloor.distinctUntilChanged { floor1, floor2 ->
            floor1?.levelId == floor2?.levelId
        }.onEach { selectedFloor ->
            selectedFloor?.let {
                filterFeaturesForFloor(it)
            }
        }.launchIn(scope)
    }

    private fun filterFeaturesForFloor(floor: Floor) {
        currentFloor.value = floor
        agsMap?.let { queryDefinitionExpression(it, floor) }
    }

    suspend fun queryForClosestFacility() {
        val facilitiesLayer =
            agsMap?.operationalLayers?.first { layer -> layer.name == AIIM.Layer.facilities } as? FeatureLayer
                ?: return
        val extent = visibleExtent ?: return

        queryForClosestFacility(facilitiesLayer, extent)?.let {
            updateClosestFacilityIdAndQueryLevelsData(it)
        } ?: kotlin.run {
            closestFacilityId = null
            pickerState.value = FloorPickerState.HIDDEN
        }
    }

    private fun getFacilityId(facilityFeature: Feature): String? {
        val featureTable = facilityFeature.featureTable ?: return null
        val facilityIdFieldName =
            featureTable.fields.firstOrNull { it.alias.equals(AIIM.IdAlias.facility, true) }?.name
                ?: return null

        return facilityFeature.attributes[facilityIdFieldName] as? String
    }

    private suspend fun updateClosestFacilityIdAndQueryLevelsData(newFacilityFeature: Feature) {
        getFacilityId(newFacilityFeature)?.let { newFacilityId ->
            if (newFacilityId != closestFacilityId) {
                closestFacilityId = newFacilityId
                queryLevelsData(newFacilityId)
            }
        }
    }

    private suspend fun queryLevelsData(facilityId: String) {
        val levelsLayer =
            agsMap?.operationalLayers?.first { layer -> layer.name == AIIM.Layer.levels } as? FeatureLayer
                ?: return

        queryLevelsData(facilityId, levelsLayer)?.let { floorList ->
            if (floorList.isEmpty()) {
                pickerState.tryEmit(FloorPickerState.HIDDEN)
            } else {
                // If the first state is hidden, then we want to set the initial state of the tool to collapsed,
                // otherwise, keep it to whatever the state the user had it in before
                //
                val currentState = pickerState.value
                if (currentState == FloorPickerState.HIDDEN) {
                    pickerState.tryEmit(FloorPickerState.COLLAPSED)
                } else {
                    pickerState.tryEmit(currentState)
                }
            }

            floors.postValue(floorList)
            currentFloor.tryEmit(floorList.firstOrNull { it.verticalOrder == 0 }
                ?: floorList.lastOrNull())
        }
    }

    private fun queryDefinitionExpression(agsMap: ArcGISMap, floor: Floor) {
        agsMap.operationalLayers.forEach { layer ->
            if (layer.name == AIIM.Layer.pathways || layer.name == AIIM.Layer.transitions || layer.name == "IPS_Recordings") {
                layer.isVisible = false
            }
            if (layer.name == AIIM.Layer.details || layer.name == AIIM.Layer.units || layer.name == AIIM.Layer.levels) {
                val featureLayer = layer as? FeatureLayer
                (layer as? FeatureLayer)?.featureTable?.fields?.firstOrNull {
                    it.alias.equals(
                        AIIM.IdAlias.level,
                        true
                    )
                }?.name?.let { levelIdFieldName ->
                    featureLayer?.filterForFloorVisibility("($levelIdFieldName = '${floor.levelId}')")
                }
            }
            if (layer.name == AIIM.Layer.pathwaysDebug) {
                val featureLayer = layer as? FeatureLayer
                (layer as? FeatureLayer)?.featureTable?.fields?.firstOrNull {
                    it.alias.equals(
                        AIIM.FloorFieldAlias.verticalOrder,
                        true
                    )
                }?.name?.let { verticalOrderFieldName ->
                    featureLayer?.filterForFloorVisibility("($verticalOrderFieldName = '${floor.verticalOrder}')")
                }
            }
            if (layer.name == AIIM.Layer.transitionsDebug) {
                val featureLayer = layer as? FeatureLayer
                featureLayer?.featureTable?.let { featureTable ->
                    val fromVerticalOrderFieldName = featureTable.fields.firstOrNull {
                        it.alias.equals(
                            AIIM.FloorFieldAlias.fromVerticalOrder,
                            true
                        )
                    }
                    val transitionTypeFieldName = featureTable.fields.firstOrNull {
                        it.alias.equals(
                            AIIM.TransitionFieldAlias.transitionType,
                            true
                        )
                    }
                    if (fromVerticalOrderFieldName != null && transitionTypeFieldName != null) {
                        featureLayer.filterForFloorVisibility("($transitionTypeFieldName = ${AIIM.TransitionType.entranceExit}) AND ($fromVerticalOrderFieldName = ${floor.verticalOrder})")
                    }
                }
            }
        }
    }

    private suspend fun queryForClosestFacility(
        facilitiesLayer: FeatureLayer,
        visibleExtent: Envelope
    ): Feature? {
        val featureTable = facilitiesLayer.featureTable as? ServiceFeatureTable ?: return null

        val queryParams = QueryParameters()
        queryParams.geometry = visibleExtent

        val result = featureTable.queryFeatures(queryParams, QueryFeatureFields.LoadAll).getOrNull()
        val listOfFacilityGeometries = result?.mapNotNull { it.geometry } ?: return null
        if (listOfFacilityGeometries.isEmpty()) return null

        return result.elementAt(
            indexOfNearestGeometry(
                listOfFacilityGeometries,
                visibleExtent.center
            )
        )
    }

    private suspend fun queryLevelsData(
        facilityId: String,
        levelsLayer: FeatureLayer
    ): List<Floor>? {
        val featureTable = levelsLayer.featureTable as? ServiceFeatureTable ?: return null

        // Clear definition expression before querying the levelsLayer in case it was previously set. Otherwise the number of results is wrong.
        val levelsDefinitionExpression = levelsLayer.definitionExpression
        levelsLayer.definitionExpression = ""

        val queryParams = QueryParameters()
        val facilityIdFieldName =
            featureTable.fields.firstOrNull { it.alias.equals(AIIM.IdAlias.facility, true) }?.name
                ?: return null
        queryParams.whereClause = "$facilityIdFieldName = '$facilityId'"

        val result = featureTable.queryFeatures(queryParams, QueryFeatureFields.LoadAll).getOrNull()
        val floors = result?.mapNotNull { feature ->
            val attributes = feature.attributes
            val levelIdFieldName = featureTable.fields.firstOrNull {
                it.alias.equals(
                    AIIM.IdAlias.level,
                    true
                )
            }?.name ?: return null
            val verticalOrderFieldName = featureTable.fields.firstOrNull {
                it.alias.equals(
                    AIIM.FloorFieldAlias.verticalOrder,
                    true
                )
            }?.name ?: return null
            val nameFieldName = featureTable.fields.firstOrNull {
                it.alias.equals(
                    AIIM.FloorFieldAlias.name,
                    true
                )
            }?.name ?: return null
            val shortNameFieldName = featureTable.fields.firstOrNull {
                it.alias.equals(
                    AIIM.FloorFieldAlias.shortName,
                    true
                )
            }?.name ?: return null

            val levelId = attributes[levelIdFieldName] as? String
            val verticalOrder = attributes[verticalOrderFieldName] as? Int
            val name = attributes[nameFieldName] as? String
            val shortName = attributes[shortNameFieldName] as? String

            if (levelId != null && verticalOrder != null && name != null && shortName != null) {
                Floor(levelId, verticalOrder, name, shortName)
            } else {
                null
            }
        }
        levelsLayer.definitionExpression = levelsDefinitionExpression
        return floors?.sortedBy { it.verticalOrder }
    }

    private fun indexOfNearestGeometry(geometries: List<Geometry>, centerPoint: Point): Int {
        fun calculateDistance(geometry: Geometry): Double? {
            val projectedCenterPoint =
                centerPoint.projectPointIfNecessary(geometry.spatialReference!!) ?: return null
            return GeometryEngine.nearestCoordinate(geometry, projectedCenterPoint)?.distance
        }

        var nearest: Pair<Int, Double?> = 0 to calculateDistance(geometries[0])
        val remainingGeometries = geometries.drop(0)
        remainingGeometries.forEachIndexed { index, geometry ->
            val distance = calculateDistance(geometry)
            if (nearest.second == null) {
                nearest = index to distance
            } else if (distance != null && distance < nearest.second!!) {
                nearest = index to distance
            }
        }
        return nearest.first
    }
}
