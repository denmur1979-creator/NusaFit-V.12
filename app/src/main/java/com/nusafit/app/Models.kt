package com.nusafit.app

data class Profile(
    val gender: String = "",
    val birthDateMs: Long = 0L,
    val age: Int = 0,
    val heightCm: Double = 0.0,
    val weightKg: Double = 0.0
)
data class Point(val lat:Double,val lng:Double,val time:Long)
data class ActivityRecord(val id:String,val name:String,val type:String,val start:Long,val end:Long,val distanceKm:Double,val calories:Double,val fatG:Double,val points:List<Point>,val media:List<String>)
