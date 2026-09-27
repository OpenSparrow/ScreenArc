package com.screenarc.app.model

enum class ArcDesignStyle(val title: String, val subtitle: String) {
    ORBIT("Orbit", "Full 360° circular orbit with orbital guide rings"),
    FAN("Fan Arc", "Ergonomic 180° semi-circular fan arc"),
    DIAL("Segmented Dial", "Tactile radial sectors and precision dial")
}
