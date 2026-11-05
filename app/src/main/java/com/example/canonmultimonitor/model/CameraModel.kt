package com.example.canonmultimonitor.model

data class CameraConfig(
    val id: Int,
    val name: String,
    var ipAddress: String,
    val port: Int = 8080,
    var isEnabled: Boolean = true
)
