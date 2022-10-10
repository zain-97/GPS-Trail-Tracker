package com.zain.gpstrailtracker

import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.zain.gpstrailtracker.MainActivity.Companion.KEY_SECONDS
import com.zain.gpstrailtracker.SharedPreferenceUtil.getDistanceBetweenTwoPoints
import kotlinx.android.synthetic.main.activity_result.*
import kotlinx.android.synthetic.main.activity_result.averageSpeedTV
import kotlinx.android.synthetic.main.activity_result.distanceTV
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.lang.StringBuilder
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import android.location.LocationManager
import com.zain.gpstrailtracker.LocationService.Companion.FILE_PATH
import com.zain.gpstrailtracker.SharedPreferenceUtil.getTime


class ResultActivity : AppCompatActivity() {



    private var previousLocation:Location?=null
    private var totalMeters: Long = 0
    private var averageSpeed: Long = 0
    private var speedSum: Long = 0
    private var intervals: Int = 0
    private var speed: Float = 0f
    private var maxEle: Float = 0f
    private var minEle: Float = 0f
    private var runningTime: Int?=null


    private var locationsList=ArrayList<Location>()
    private var speedList=ArrayList<Float>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)


        //get running time from main activity intent
        runningTime=intent.extras?.getInt(KEY_SECONDS)

        //close the activity when user press back button
        backBtn.setOnClickListener { finish() }

        readXmlFile()

    }





    //calculate and show data in chart
    private fun initChart() {


        for((index,item) in locationsList.withIndex()){

            //Get fastest speed in minute
            val newSpeed=calculateTracking(item)
           if(newSpeed>speed){
               speed=newSpeed
           }

            if(speed>10){speed=10f}


            //Intervals consist of 5 seconds
            /*Here we checking when total intervals time equals to 1 minute
            we put data to chart because we are showing data in chart with 1 min interval */
            if (index==1||intervals%12==0) {
               speedList.add(speed)
            }


        }

        //after data is processed show in chat
        val adapter=ChartAdapter(speedList)
        barChart.adapter=adapter


        //set data to textViews
        val averageSpeedString= String.format("%.1f",averageSpeed.toFloat())+" Km / hr"
        val distanceString= String.format("%.1f",totalMeters.toFloat()/1000)+" Km"
        val timeString=getTime(runningTime.toString())
        timeTV.text=timeString
        averageSpeedTV.text=averageSpeedString
        distanceTV.text=distanceString

        val maxEle=maxEle
        val minEle=minEle
        val minAltitudeString= String.format("%.2f",minEle.toFloat())
        val maxAltitudeString= String.format("%.2f",maxEle.toFloat())

        minAltitude.text=minAltitudeString
        maxAltitude.text=maxAltitudeString



    }




    //Calculating data on the basis of all locations recorded
    private fun calculateTracking(location: Location):Float {

        var kmPerHour:Float=0f
        //Getting total intervals of 5 seconds
        intervals++

        previousLocation?.also {

            val meters=getDistanceBetweenTwoPoints(it.latitude,it.longitude,location.latitude,location.longitude)
            totalMeters= (totalMeters+meters).toLong()
            //Interval is 5 seconds long so here to get 1 second distance we are dividing total meters by 5
            val meterPerSecond=meters/5
            //Getting meters per hour by multiplying meter per seconds to 3600 because  1 hour is equal to 3600 seconds
            val metersPerHour=3600*meterPerSecond
            kmPerHour=metersPerHour/1000
            speedSum= (speedSum+kmPerHour).toLong()
            averageSpeed=speedSum/intervals

        }

        previousLocation=location

        return kmPerHour

    }



    //Read data from xml file
    private fun readXmlFile(){

        val factory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        val builder: DocumentBuilder = factory.newDocumentBuilder()

        val document: Document = builder.parse(FILE_PATH)
        val gpx: Element = document.documentElement

        val gpxNodes: NodeList = gpx.childNodes


        var gp = 0
        var tr = 0
        var trsg = 0
        while (gp <  gpxNodes.length) {
            val gpxChild = gpxNodes.item(gp)
            gp++
            if (gpxChild.nodeType != Node.ELEMENT_NODE) continue

            if (gpxChild.nodeName.equals("trk")){

                val trk = gpxChild as Element
                val trkNodes: NodeList = trk.childNodes



                while (tr <  trkNodes.length) {
                    val trChild = trkNodes.item(tr)
                    tr++
                    if (trChild.nodeType != Node.ELEMENT_NODE) continue

                    if(trChild.nodeName.equals("name")) {
                        val name = getCharacterData(trChild)
                        Log.v("GPXFILE:",name)
                    }

                    if(trChild.nodeName.equals("trkseg")){

                        val trkseg = trChild as Element
                        val trksegNodes: NodeList = trkseg.childNodes

                        while (trsg <  trksegNodes.length) {
                            val trsgChild = trksegNodes.item(trsg)
                            trsg++
                            if (trsgChild.nodeType != Node.ELEMENT_NODE) continue

                            if(trsgChild.nodeName.equals("trkpt")){

                                val trsgElement=trsgChild as Element
                                val lat=trsgElement.getAttribute("lat")
                                val lon=trsgElement.getAttribute("lon")
                                val ele=getCharacterData(findFirstNamedElement(trsgChild,"ele"))

                                //check if new ele is less than to previous min ele
                                if(minEle==0f||minEle>ele.toFloat()){
                                    minEle=ele.toFloat()
                                }

                                //check if new ele is greater than to previous max ele
                                if(maxEle==0f||maxEle<ele.toFloat()){
                                    maxEle=ele.toFloat()
                                }

                                //convert lat lng to location
                                val location = Location(LocationManager.GPS_PROVIDER)
                                location.latitude =lat.toDouble()
                                location.longitude = lon.toDouble()


                                //add locations to a list
                                locationsList.add(location)

                            }


                        }

                        //after getting data from xml file show in chart and textViews
                        initChart()

                    }

                }
            }


        }


    }




    // find an element by its parent node and element name
    private fun findFirstNamedElement(parent: Node, tagName: String): Node? {
        val children: NodeList = parent.childNodes
        var i = 0
        while (i < children.length) {
            val child: Node = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) {
                i++
                continue
            }
            if (child.nodeName.equals(tagName)) return child
            i++
        }
        return null
    }


    //get data in a node
    private fun getCharacterData(parent: Node?): String {
        val text = StringBuilder()
        if (parent == null) return text.toString()
        val children = parent.childNodes
        var k = 0
        while (k <  children.length) {
            val child = children.item(k)
            if (child.nodeType != Node.TEXT_NODE) break
            text.append(child.nodeValue)
            k++
        }
        return text.toString()
    }



}