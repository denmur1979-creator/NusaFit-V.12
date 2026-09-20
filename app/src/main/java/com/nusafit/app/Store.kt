package com.nusafit.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val P="nusafit"; private const val PROFILE="profile"; private const val ACTIVITIES="activities"; private const val MEDIA="media_"
    private fun p(c:Context)=c.getSharedPreferences(P,Context.MODE_PRIVATE)
    fun saveProfile(c:Context,x:Profile){
        p(c).edit().putString(PROFILE,JSONObject().apply{
            put("gender",x.gender); put("birthDateMs",x.birthDateMs); put("age",x.age); put("height",x.heightCm); put("weight",x.weightKg)
        }.toString()).apply()
    }
    fun profile(c:Context):Profile{
        val s=p(c).getString(PROFILE,null)?:return Profile()
        val o=JSONObject(s)
        return Profile(o.optString("gender"),o.optLong("birthDateMs",0L),o.optInt("age"),o.optDouble("height"),o.optDouble("weight"))
    }
    fun addActivity(c:Context,a:ActivityRecord){val arr=JSONArray(p(c).getString(ACTIVITIES,"[]"));arr.put(toJson(a));p(c).edit().putString(ACTIVITIES,arr.toString()).apply()}
    fun activities(c:Context):List<ActivityRecord>{val arr=JSONArray(p(c).getString(ACTIVITIES,"[]"));return (0 until arr.length()).map{val a=fromJson(arr.getJSONObject(it));a.copy(media=media(c,a.id))}.sortedByDescending{it.start}}
    fun addMedia(c:Context,id:String,uris:List<String>){p(c).edit().putString(MEDIA+id,JSONArray(uris).toString()).apply()}
    fun media(c:Context,id:String):List<String>{val a=JSONArray(p(c).getString(MEDIA+id,"[]"));return (0 until a.length()).map{a.getString(it)}}
    private fun toJson(a:ActivityRecord)=JSONObject().apply{put("id",a.id);put("name",a.name);put("type",a.type);put("start",a.start);put("end",a.end);put("distance",a.distanceKm);put("calories",a.calories);put("fat",a.fatG);put("points",JSONArray().apply{a.points.forEach{put(JSONObject().apply{put("lat",it.lat);put("lng",it.lng);put("time",it.time)})}})}
    private fun fromJson(o:JSONObject):ActivityRecord{val id=o.getString("id");val q=o.optJSONArray("points")?:JSONArray();val pts=(0 until q.length()).map{val x=q.getJSONObject(it);Point(x.getDouble("lat"),x.getDouble("lng"),x.getLong("time"))};return ActivityRecord(id,o.getString("name"),o.getString("type"),o.getLong("start"),o.getLong("end"),o.getDouble("distance"),o.getDouble("calories"),o.getDouble("fat"),pts,mediaDummy())}
    private fun mediaDummy()=emptyList<String>()
}
