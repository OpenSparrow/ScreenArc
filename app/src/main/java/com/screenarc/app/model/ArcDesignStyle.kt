package com.screenarc.app.model

enum class ArcDesignStyle(val title: String, val subtitle: String) {
    ORBIT("Orbit", "Full 360° circular orbit with orbital guide rings"),
    MINIMAL("Minimal", "Ultra-clean hairline guides with subtle ambient glow"),
    PALETTE("Palette", "Vibrant swatches extracted from screenshot palette"),
    BLOOM("Bloom", "Expressive flower-petal blooming distribution"),
    FAN("Fan Arc", "Ergonomic 180° semi-circular fan arc"),
    DIAL("Segmented Dial", "Tactile radial sectors and precision dial")
}
