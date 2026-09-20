package com.nusafit.app
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import java.text.SimpleDateFormat
import java.util.*
class StatsChartView(c:Context,private val records:List<ActivityRecord>):View(c){
 private val p=Paint(Paint.ANTI_ALIAS_FLAG);private val labels=ArrayList<String>();private val vals=DoubleArray(7)
 init{for(i in 6 downTo 0){val cal=Calendar.getInstance();cal.add(Calendar.DAY_OF_YEAR,-i);val key=SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.time);labels.add(SimpleDateFormat("EEE",Locale.getDefault()).format(cal.time));vals[6-i]=records.filter{SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date(it.start))==key}.sumOf{it.distanceKm}}}
 override fun onDraw(c:Canvas){super.onDraw(c);val w=width.toFloat();val h=height.toFloat();val maxV=maxOf(1.0,vals.maxOrNull()?:1.0);p.textSize=28f;for(i in 0..6){val x=(i+0.5f)*w/7f;val bh=((vals[i]/maxV)*(h-55f)).toFloat();c.drawRect(x-22,h-35-bh,x+22,h-35,p);p.textSize=22f;c.drawText(labels[i],x-18,h-8,p)}}
 override fun onMeasure(w:Int,h:Int){setMeasuredDimension(MeasureSpec.getSize(w),180)}
}
