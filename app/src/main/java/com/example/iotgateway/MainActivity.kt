package com.example.iotgateway

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import info.mqtt.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage


class MainActivity : AppCompatActivity() {

    // LOCAL HOST : Android emulator uses 10.0.2.2 as local host
    //    private val serverIp = "tcp://10.0.2.2:1883"
    // broker.hivemq.com : broker.hivemq.com is an Internet-enabled mqtt broker server.
    //    private val serverIp = "tcp://broker.hivemq.com:1883"

    // TAG
    val TAG_DEBUG = "iot_study"
    // TOPIC
    val TOPIC_PUB = "/iot_study_data"
    val TOPIC_SUB = "/iot_study_cmd"

    lateinit var mqttclient: MqttAndroidClient

    //firebase realtime db
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initDatabase()
        initUI()
    }

    private fun initUI() {
        setContentView(R.layout.activity_main)

        val connectBtn = findViewById<Button>(R.id.connect_button)
        val publishMsgBtn = findViewById<Button>(R.id.publish_msg_button)
        val subscribeBtn = findViewById<Button>(R.id.subscribe_button)

        connectBtn.setOnClickListener {
            connectMqttServer()
        }

        publishMsgBtn.setOnClickListener {
            publishMessage()
        }

        subscribeBtn.setOnClickListener {
            subscribeMessage()
        }
    }

    private fun initMqtt() {
        val hostTextView = findViewById<EditText>(R.id.mqtt_host_ip)
        val hostString = "tcp://"+hostTextView.text.toString()+":1883"

        val infoHostTextView = findViewById<TextView>(R.id.info_host_textview)
        infoHostTextView.text = hostString

        if (::mqttclient.isInitialized) {
            if (mqttclient.isConnected) {
                mqttclient.disconnect()
            }
        }
        mqttclient = MqttAndroidClient(applicationContext, hostString, MqttClient.generateClientId())
    }

    private fun connectMqttServer() {
        initMqtt()

        if ( !::mqttclient.isInitialized ) {
            return
        }

        val mqttConnectOptions = MqttConnectOptions()
        mqttConnectOptions.isAutomaticReconnect = false
        mqttConnectOptions.isCleanSession = true
        mqttConnectOptions.connectionTimeout = 3
        mqttConnectOptions.keepAliveInterval = 60

        try {
            mqttclient.connect(
                mqttConnectOptions, null, object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        Log.d(TAG_DEBUG, "onSuccess: Successfully connected to the broker")

                        runOnUiThread {
                            val infoHostTextView = findViewById<TextView>(R.id.info_host_textview)
                            val infoHost = infoHostTextView.text.toString()
                            infoHostTextView.text = infoHost + " connected"
                            infoHostTextView.setTextColor(Color.parseColor("#00FF00"))
                        }
                        val disconnectBufferOptions = DisconnectedBufferOptions()
                        disconnectBufferOptions.isBufferEnabled = true
                        disconnectBufferOptions.bufferSize = 100
                        disconnectBufferOptions.isPersistBuffer = false
                        disconnectBufferOptions.isDeleteOldestMessages = false
                        mqttclient.setBufferOpts(disconnectBufferOptions)

                    }
                    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                        Log.d(TAG_DEBUG, "onFailure: ${exception}")

                        runOnUiThread {
                            // UI 코드를 이 안으로 옮긴다.
                            val infoHostTextView = findViewById<TextView>(R.id.info_host_textview)
                            val infoHost = infoHostTextView.text.toString()
                            infoHostTextView.setTextColor(Color.parseColor("#FF0000"))

                            infoHostTextView.text = infoHost + " connect fail"
                        }
                    }
                }
            )
        } catch (ex: MqttException) {
            ex.printStackTrace()
        }
    }

    private fun subscribeMessage() {
        val topicEditTextView = findViewById<EditText>(R.id.subscribe_topic_edittext)
        val subscribeTopic = topicEditTextView.text.toString()
        try {
            mqttclient.subscribe(subscribeTopic, 1, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken) {
                    Log.d(TAG_DEBUG, "onSuccess: Subscribe ${subscribeTopic}")
                }

                override fun onFailure(asyncActionToken: IMqttToken, exception: Throwable) {
                }
            })

            mqttclient.subscribe(subscribeTopic, 1,
                IMqttMessageListener { topic, message -> // message Arrived!
                    Log.d(TAG_DEBUG, ("Message: " + topic + " : " + "${message}"))

                    writeDatabase(topic, "${message}")
                })
        } catch (ex: MqttException) {
            System.err.println("Exception whilst subscribing")
            ex.printStackTrace()
        }
    }

    private fun publishMessage() {
        val topicEditTextView = findViewById<EditText>(R.id.publish_topic_edittext)
        val publishMsgTextView = findViewById<TextView>(R.id.publish_msg_textview)

        val topic = topicEditTextView.text.toString()
        val message = publishMsgTextView.text.toString()
        //mqttclient.publish(TOPIC_PUB, MqttMessage(message.toByteArray()))
        mqttclient.publish(topic, MqttMessage(message.toByteArray()))

        writeDatabase()
    }

    private fun initDatabase() {
        database = Firebase.database

    }

    private fun writeDatabase() {
        val myRef = database.getReference("message")
        myRef.setValue("Hello, Nina!")
    }

    private fun writeDatabase(topic:String, message:String) {
        val myRef = database.getReference(topic)
//        myRef.setValue(message)
        myRef.push().setValue(message)

        myRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                // This method is called once with the initial value and again
                // whenever data at this location is updated.
                for ( childSnapshot in dataSnapshot.children) {
                    val message = childSnapshot.getValue(String::class.java)
                    Log.d(TAG_DEBUG, "Value is: $message")

                }
                //val message = dataSnapshot.child(topic).getValue(String::class.java)
                //Log.d(TAG_DEBUG, "Value is: $message")
            }

            override fun onCancelled(error: DatabaseError) {
                // Failed to read value
                Log.w(TAG_DEBUG, "Failed to read value.", error.toException())
            }
        })
    }
}