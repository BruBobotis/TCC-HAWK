package com.example.tcc_hawk.data.mqtt

import android.content.Context
import android.util.Log
import com.example.tcc_hawk.data.model.VitalsPayload
import com.google.gson.Gson
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage

object MqttManager {

    private const val TAG = "MQTT_HAWK"
    private const val SERVER_URI = "tcp://broker.hivemq.com:1883"
    private const val TOPIC = "hawk/idoso/vitais"

    private var mqttClient: MqttAndroidClient? = null
    private var connected = false
    private var connecting = false

    fun init(context: Context) {
        try {
            if (mqttClient != null) return

            val clientId = "hawk_android_${System.currentTimeMillis()}"

            mqttClient = MqttAndroidClient(
                context.applicationContext,
                SERVER_URI,
                clientId
            )

            connect()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar MQTT: ${e.message}", e)
        }
    }
    private fun connect() {
        try {
            val client = mqttClient ?: return

            if (connected || connecting) return

            connecting = true

            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = false
                connectionTimeout = 10
                keepAliveInterval = 20
            }

            client.connect(
                options,
                null,
                object : IMqttActionListener {
                    override fun onSuccess(asyncActionToken: IMqttToken?) {
                        connected = true
                        connecting = false
                        Log.d(TAG, "MQTT conectado")
                    }

                    override fun onFailure(
                        asyncActionToken: IMqttToken?,
                        exception: Throwable?
                    ) {
                        connected = false
                        connecting = false
                        Log.e(TAG, "Falha MQTT: ${exception?.message}", exception)
                    }
                }
            )

        } catch (e: Exception) {
            connected = false
            connecting = false
            Log.e(TAG, "Erro connect MQTT: ${e.message}", e)
        }
    }

    fun publishVitals(payload: VitalsPayload) {
        val client = mqttClient

        if (client == null) {
            Log.w(TAG, "MQTT ainda não inicializado")
            return
        }

        if (!client.isConnected) {
            Log.w(TAG, "MQTT desconectado, tentando reconectar")
            connected = false
            connect()
            return
        }

        try {
            val json = Gson().toJson(payload)

            val message = MqttMessage().apply {
                this.payload = json.toByteArray(Charsets.UTF_8)
                qos = 1
                isRetained = false
            }

            client.publish(TOPIC, message)

            Log.d(TAG, "Publicado MQTT: $json")

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao publicar MQTT: ${e.message}")
        }
    }
}