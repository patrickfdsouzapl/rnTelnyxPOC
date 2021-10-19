package com.telnyx.webrtc.sdk

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.telnyx.webrtc.sdk.model.GatewayState
import com.telnyx.webrtc.sdk.model.LogLevel
import com.telnyx.webrtc.sdk.model.SocketMethod
import com.telnyx.webrtc.sdk.socket.TxSocket
import com.telnyx.webrtc.sdk.telnyx_rtc.BuildConfig
import com.telnyx.webrtc.sdk.testhelpers.*
import com.telnyx.webrtc.sdk.testhelpers.extensions.CoroutinesTestExtension
import com.telnyx.webrtc.sdk.testhelpers.extensions.InstantExecutorExtension
import com.telnyx.webrtc.sdk.utilities.ConnectivityHelper
import com.telnyx.webrtc.sdk.verto.receive.ReceivedMessageBody
import com.telnyx.webrtc.sdk.verto.receive.SocketResponse
import com.telnyx.webrtc.sdk.verto.receive.StateResponse
import com.telnyx.webrtc.sdk.verto.send.SendingMessageBody
import com.telnyx.webrtc.sdk.verto.send.StateParams
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.rules.TestRule
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Spy
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals

@ExtendWith(InstantExecutorExtension::class, CoroutinesTestExtension::class)
class TelnyxClientTest : BaseTest() {

    @MockK
    private var mockContext: Context = Mockito.mock(Context::class.java)

    @MockK
    lateinit var connectivityHelper: ConnectivityHelper

    @MockK
    lateinit var connectivityManager: ConnectivityManager

    @MockK lateinit var activeNetwork: Network

    @MockK lateinit var capabilities: NetworkCapabilities


    @Spy
    private lateinit var socket: TxSocket

    @Spy
    lateinit var client: TelnyxClient

    @MockK
    lateinit var audioManager: AudioManager

    @get:Rule
    val rule: TestRule = InstantTaskExecutorRule()

    @Before
    fun setUp() {
        MockKAnnotations.init(this, true, true, true)
        networkCallbackSetup()

        BuildConfig.IS_TESTING.set(true);

        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { mockContext.getSystemService(AppCompatActivity.AUDIO_SERVICE) } returns audioManager

        client = TelnyxClient(mockContext)
    }

    private fun networkCallbackSetup() {
        var registered: Boolean? = null
        var available: Boolean? = null
        val callback = object : ConnectivityHelper.NetworkCallback() {
            override fun onNetworkAvailable() {
                available = true
            }

            override fun onNetworkUnavailable() {
                available = false
            }
        }
        mockkConstructor(NetworkRequest.Builder::class)
        mockkObject(NetworkRequest.Builder())
        val request = mockk<NetworkRequest>()
        val manager = mockk<ConnectivityManager>()
        every {
            anyConstructed<NetworkRequest.Builder>().addCapability(any()).addCapability(any())
                .build()
        } returns request
        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { connectivityManager.registerNetworkCallback(any(), callback) } just Runs
        every { connectivityManager.registerNetworkCallback(any(), callback) } answers { registered = true }
        every { connectivityManager.unregisterNetworkCallback(callback) } answers { registered = false }
        every { connectivityHelper.isNetworkEnabled(mockContext) } returns true
        every { connectivityHelper.registerNetworkStatusCallback(mockContext, callback) } just Runs

        every { mockContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every {connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityHelper.isNetworkEnabled(mockContext) } returns false
        every { connectivityManager.getNetworkCapabilities(activeNetwork) } returns capabilities
        every { capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) } returns false

        connectivityHelper.registerNetworkStatusCallback(mockContext, callback)
    }

    @Test
    fun `initiate connection`() {
        client.connect()
        assertEquals(client.isNetworkCallbackRegistered, true)
    }

    @Test
    fun `disconnect connection`() {
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))

        client.disconnect()
        assertEquals(client.isNetworkCallbackRegistered, false)
        assertEquals(client.socket.isConnected, false)
        assertEquals(client.socket.isLoggedIn, false)
        assertEquals(client.socket.ongoingCall, false)
    }

    @Test
    fun `attempt connection without network`() {
        socket = Mockito.spy(
            TxSocket(
                host_address = "rtc.telnyx.com",
                port = 14938,
            )
        )
        client = Mockito.spy(TelnyxClient(mockContext))
        client.connect()
        assertEquals(
            client.socketResponseLiveData.getOrAwaitValue(),
            SocketResponse.error("No Network Connection")
        )
    }

    @Test
    fun `login with valid credentials - login sent to socket and json received`() {
        client = Mockito.spy(TelnyxClient(mockContext))
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))
        client.connect()

        val config = CredentialConfig(
            MOCK_USERNAME,
            MOCK_PASSWORD,
            "Test",
            "000000000",
            null,
            null,
            null,
            LogLevel.ALL
        )
        client.credentialLogin(config)

        Thread.sleep(3000)
        Mockito.verify(client.socket, Mockito.times(1)).send(any(SendingMessageBody::class.java))
    }

    @Test
    fun `login with invalid credentials - login sent to socket and json received`() {
        client = Mockito.spy(TelnyxClient(mockContext))
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))
        client.connect()

        val config = CredentialConfig(
            "asdfasass",
            "asdlkfhjalsd",
            "test",
            "000000000",
            null,
            null,
            null,
            LogLevel.ALL
        )
        client.credentialLogin(config)

        val jsonMock = Mockito.mock(JsonObject::class.java)

        Thread.sleep(3000)
        Mockito.verify(client.socket, Mockito.times(1)).send(any(SendingMessageBody::class.java))
        Mockito.verify(client, Mockito.times(0)).onClientReady(jsonMock)
    }

    @Test
    fun `login with valid token - login sent to socket and json received`() {
        client = Mockito.spy(TelnyxClient(mockContext))
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))

        client.connect()

        val config = TokenConfig(
            MOCK_TOKEN,
            "test",
            "000000",
            null,
            null,
            null,
            LogLevel.ALL
        )
        client.tokenLogin(config)

        Thread.sleep(3000)
        Mockito.verify(client.socket, Mockito.times(1)).send(dataObject = any(SendingMessageBody::class.java))
    }

    @Test
    fun `login with invalid token - login sent to socket and json received`() {
        client = Mockito.spy(TelnyxClient(mockContext))
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))

        client.connect()

        val config = TokenConfig(
            anyString(),
            "test",
            "00000",
            null,
            null,
            null,
            LogLevel.ALL
        )
        client.tokenLogin(config)

        val jsonMock = Mockito.mock(JsonObject::class.java)

        Thread.sleep(3000)
        Mockito.verify(client.socket, Mockito.times(1)).send(dataObject = any(SendingMessageBody::class.java))
        Mockito.verify(client, Mockito.times(0)).onClientReady(jsonMock)
    }

    @Test
    fun `get raw ringtone`() {
        assertDoesNotThrow {
            client.getRawRingtone()
        }
    }

    @Test
    fun `get raw ringback tone`() {
        assertDoesNotThrow {
            client.getRawRingbackTone()
        }
    }

    @Test
    fun `try and send message when isConnected is false - do not connect before doing login`() {
        client = Mockito.spy(TelnyxClient(mockContext))
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))

        val config = CredentialConfig(
            "asdfasass",
            "asdlkfhjalsd",
            "test",
            "000000000",
            null,
            null,
            null,
            LogLevel.ALL
        )

        client.credentialLogin(config)

        val jsonMock = Mockito.mock(JsonObject::class.java)

        Thread.sleep(2000)
        Mockito.verify(client, Mockito.times(0)).onClientReady(jsonMock)
    }

    @Test
    fun `login with credentials - No network available error thrown`() {
        client = Mockito.spy(TelnyxClient(mockContext))
        client.socket = Mockito.spy(TxSocket(
            host_address = "rtc.telnyx.com",
            port = 14938,
        ))
        client.connect()

        val config = CredentialConfig(
            "asdfasass",
            "asdlkfhjalsd",
            "test",
            "000000000",
            null,
            null,
            null,
            LogLevel.ALL
        )
        client.credentialLogin(config)

        Thread.sleep(1000)
        assertEquals(client.socketResponseLiveData.getOrAwaitValue(), SocketResponse.error("No Network Connection"))
    }

    @Test
    fun `Check login successful fires once REGED received`() {
        client = Mockito.spy(TelnyxClient(mockContext))

        val sessid = UUID.randomUUID().toString()
        val params = StateParams(state = GatewayState.REGED.state)

        val stateResult = StateResponse(sessid = sessid, params = params)

        val stateMessageBody = ReceivedMessageBody(
            method = SocketMethod.GATEWAY_STATE.methodName,
            result = stateResult
        )
        val gatewayJson = Gson().toJson(stateMessageBody)
        val jsonObject: JsonObject = JsonParser().parse(gatewayJson).asJsonObject
        client.onGatewayStateReceived(jsonObject)
        Mockito.verify(client, Mockito.times(1)).onLoginSuccessful(sessid)
    }

    @Test
    fun `Check gateway times out once NOREG is received`() {
        client = Mockito.spy(TelnyxClient(mockContext))

        val sessid = UUID.randomUUID().toString()
        val params = StateParams(state = GatewayState.NOREG.state)

        val stateResult = StateResponse(sessid = sessid, params = params)

        val stateMessageBody = ReceivedMessageBody(
            method = SocketMethod.GATEWAY_STATE.methodName,
            result = stateResult
        )
        val gatewayJson = Gson().toJson(stateMessageBody)
        val jsonObject: JsonObject = JsonParser().parse(gatewayJson).asJsonObject
        client.onGatewayStateReceived(jsonObject)
        assertEquals(client.socketResponseLiveData.getOrAwaitValue(), SocketResponse.error("Gateway registration has timed out"))
    }


    //Extension function for getOrAwaitValue for unit tests
    fun <T> LiveData<T>.getOrAwaitValue(
        time: Long = 10,
        timeUnit: TimeUnit = TimeUnit.SECONDS,
    ): T {
        var data: T? = null
        val latch = CountDownLatch(1)
        val observer = object : Observer<T> {
            override fun onChanged(o: T?) {
                data = o
                latch.countDown()
                this@getOrAwaitValue.removeObserver(this)
            }
        }

        this.observeForever(observer)

        // Don't wait indefinitely if the LiveData is not set.
        if (!latch.await(time, timeUnit)) {
            throw TimeoutException("LiveData value was never set.")
        }

        @Suppress("UNCHECKED_CAST")
        return data as T
    }
}





