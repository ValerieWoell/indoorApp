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
package com.example.indoorapp.ui.floorpicker.models

object AIIM {
    object Layer {
        const val facilities = "Facilities"
        const val levels = "Levels"
        const val details = "Details"
        const val units = "Units"
        const val pathways = "Pathways"
        const val pathwaysDebug = "Pathways_debug"
        const val transitions = "Transitions"
        const val transitionsDebug = "Transitions_debug"
    }

    object IdAlias {
        const val facility = "Facility ID"
        const val level = "Level ID"
    }

    object FloorFieldAlias {
        const val verticalOrder = "Vertical Order"
        const val fromVerticalOrder = "From Vertical Order"
        const val name = "Name"
        const val shortName = "Short Name"
    }

    object TransitionFieldAlias {
        const val transitionType = "Transition Type"
    }

    object TransitionType {
        const val entranceExit = 7
    }
}


