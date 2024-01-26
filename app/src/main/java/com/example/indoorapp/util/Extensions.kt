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
package com.example.indoorapp.util

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.geometry.Point
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.mapping.layers.DisplayFilter
import com.arcgismaps.mapping.layers.FeatureLayer
import com.arcgismaps.mapping.layers.ManualDisplayFilterDefinition

fun Point.projectPointIfNecessary(spatialReference: SpatialReference): Point? =
    projectIfNecessary(spatialReference) as? Point

fun Geometry.projectIfNecessary(spatialReference: SpatialReference): Geometry? {
    return if (spatialReference == this.spatialReference) {
        this
    } else {
        GeometryEngine.projectOrNull(this, spatialReference)
    }
}

fun FeatureLayer.filterForFloorVisibility(expression: String?) {
    expression?.let {
        val displayFilter = DisplayFilter.createWithNameAndWhereClause("FloorFilter", it)
        displayFilterDefinition = ManualDisplayFilterDefinition(
            displayFilter,
            listOf(displayFilter)
        )
    } ?: kotlin.run {
        displayFilterDefinition = null
    }
}

fun Context.appVersionName(): String {
    return try {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        pInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }
}

fun Context.appVersionCode(): String {
    return try {
        val pInfo = packageManager.getPackageInfo(packageName, 0)
        "${PackageInfoCompat.getLongVersionCode(pInfo)}"
    } catch (e: PackageManager.NameNotFoundException) {
        ""
    }
}