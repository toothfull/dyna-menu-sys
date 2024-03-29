package uk.dynamenusystem.dynamenusystem

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.*
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.Framedata
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.util.concurrent.Executor
import kotlin.coroutines.EmptyCoroutineContext

@Suppress("DEPRECATION")
class MenuActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_menu)

		//Hides the system UI such as the status bar and home buttons bar
		fun hideSystemUI() {
			WindowCompat.setDecorFitsSystemWindows(window, false)
			WindowInsetsControllerCompat(
				window,
				findViewById(R.id.menuConstraintLayout)
			).let { controller ->
				controller.hide(WindowInsetsCompat.Type.systemBars())
				controller.systemBarsBehavior =
					WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

			}


		}
		//Runs the function
		hideSystemUI()

		//Finds the layout and locks the swipe to open feature
		val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
		drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)

		val navigationView1 = findViewById<NavigationView>(R.id.navigationView)
		navigationView1.setNavigationItemSelectedListener {

			when (it.itemId) {
				R.id.networkDetailsTab -> {

					val builder = AlertDialog.Builder(this)
					builder.setTitle("Network Stats")
					builder.setMessage(
						"IP address: " + IPandMAC.getIpAddress(applicationContext) + "\n" +
								"Signal strength (1/100): " + IPandMAC.getSignalStrength(
							applicationContext
						).toString()
					)

					builder.setPositiveButton(R.string.okayPrompt) { _, _ ->
					}
					builder.show()


				}

				R.id.downloadLatestTab -> {

					if (webSocketClient.isOpen) {

						val jsonToSend = JsonObject()
						jsonToSend.addProperty("event", "canIHaveDocument")
						jsonToSend.addProperty("data", "")

						webSocketClient.send(jsonToSend.toString())


					} else {
						val builder = AlertDialog.Builder(this)
						builder.setTitle("Unable to download")
						builder.setMessage("Device is not connected to the server. Could not download document")
						builder.setPositiveButton(R.string.okayPrompt) { _, _ ->
						}
						builder.show()
					}

				}

				R.id.pinTab -> {
					startLockTask()
				}

				R.id.unlockDeviceTab -> {
					stopLockTask()
				}

				R.id.deviceStatistics -> {
					val builder = AlertDialog.Builder(this)
					builder.setTitle("Device stats")
					builder.setMessage(
						"Model: " + android.os.Build.MODEL + "\n" +
								"Manufacture: " + android.os.Build.MANUFACTURER + "\n" +
								"Product: " + android.os.Build.PRODUCT + "\n" +
								"Time: " + android.os.Build.TIME + "\n" +
								"Bootloader: " + android.os.Build.BOOTLOADER + "\n" +
								"Brand: " + android.os.Build.BRAND + "\n" +
								"Device: " + android.os.Build.DEVICE + "\n" +
								"Display: " + android.os.Build.DISPLAY + "\n" +
								"Host: " + android.os.Build.HOST + "\n" +
								"ID: " + android.os.Build.ID + "\n" +
								"User: " + android.os.Build.USER + "\n"
					)
					builder.setPositiveButton(R.string.okayPrompt) { _, _ ->
					}
					builder.show()
				}

				R.id.amIConnectedTab -> {
					if (webSocketClient.isOpen) {
						val builder = AlertDialog.Builder(this)
						builder.setTitle("Websocket Status")
						builder.setMessage("You are connected to the server")
						builder.setPositiveButton(R.string.okayPrompt) { _, _ ->
						}
						builder.show()
					} else {
						val builder = AlertDialog.Builder(this)
						builder.setTitle("Websocket Status")
						builder.setMessage("Device is not connected to the server")
						builder.setPositiveButton(R.string.okayPrompt) { _, _ ->
						}
						builder.show()
					}

				}

				R.id.reconnectTab -> {
					webSocketClient.close()
					initWebSocket()
					Toast.makeText(
						applicationContext,
						"Reconnecting to server", Toast.LENGTH_SHORT
					).show()
				}

			}
			true
		}


		//initWebSocket()

	}

	private fun openDrawer() {
		//Opens the drawer when run
		val biometricManager = BiometricManager.from(this)
		when (biometricManager.canAuthenticate(DEVICE_CREDENTIAL)) {
			BiometricManager.BIOMETRIC_SUCCESS ->
				Log.d("DynaMenuSys", "App can authenticate using biometrics.")
			BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
				Log.e("DynaMenuSys", "No biometric features available on this device.")
			BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
				Log.e("DynaMenuSys", "Biometric features are currently unavailable.")
			BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
				val enrollIntent = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
					putExtra(
						Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
						DEVICE_CREDENTIAL
					)
				}
				startActivityForResult(enrollIntent, 1)
			}
			BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> {
				Log.e("DynaMenuSys", "Security Update required.")
			}
			BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> {
				Log.e("DynaMenuSys", "Passcode feature is unsupported")
			}
			BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
				Log.e("DynaMenuSys", "Unknown biometrics")
			}
		}

		lateinit var biometricPrompt: BiometricPrompt
		val executor: Executor = ContextCompat.getMainExecutor(this)
		biometricPrompt = BiometricPrompt(this, executor,
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationError(
					errorCode: Int,
					errString: CharSequence
				) {
					super.onAuthenticationError(errorCode, errString)
					Toast.makeText(
						applicationContext,
						"Authentication error: $errString", Toast.LENGTH_SHORT
					).show()
				}

				override fun onAuthenticationSucceeded(
					result: BiometricPrompt.AuthenticationResult
				) {
					super.onAuthenticationSucceeded(result)
					Toast.makeText(
						applicationContext,
						"Authentication succeeded!", Toast.LENGTH_SHORT
					).show()
					val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
					drawerLayout.bringToFront()
					drawerLayout.openDrawer(GravityCompat.START)

				}

				override fun onAuthenticationFailed() {
					super.onAuthenticationFailed()
					Toast.makeText(
						applicationContext, "Authentication failed",
						Toast.LENGTH_SHORT
					).show()
				}
			})

		val promptInfo: BiometricPrompt.PromptInfo = BiometricPrompt.PromptInfo.Builder()
			.setTitle("Access admin panel")
			.setSubtitle("Enter your passcode to access admin features")
			.setAllowedAuthenticators(DEVICE_CREDENTIAL)
			.setConfirmationRequired(false)
			.build()

		// Prompt appears when user clicks "Log in".
		// Consider integrating with the keystore to unlock cryptographic operations,
		// if needed by your app.
		biometricPrompt.authenticate(promptInfo)


	}

	private fun closeDrawer() {
		//Opens the drawer when run
		val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
		val textLayout = findViewById<ScrollView>(R.id.textLayout)
		drawerLayout.closeDrawer(GravityCompat.START)
		textLayout.bringToFront()
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
		//Waits for the onKeyDown event to take place
		when (keyCode) {
			//Runs function if key down is either volume buttons
			KeyEvent.KEYCODE_VOLUME_UP -> openDrawer()
			KeyEvent.KEYCODE_VOLUME_DOWN -> closeDrawer()
		}
		return true
	}

	private lateinit var webSocketClient: WebSocketClient

	override fun onResume() {
		super.onResume()
		//webSocketClient.connect()
		initWebSocket()
		Log.d("DynaMenuSys", "App resuming")
	}

	override fun onPause() {
		super.onPause()
		webSocketClient.close()
		Log.d("DynaMenuSys", "App closed")
	}

	companion object {
		const val WEB_SOCKET_URL =
			"ws://192.168.1.1:9000/websocket" //"ws://10.0.2.2:9000/websocket"  "ws://dynamenusystem.uk/websocket"
	}

	private fun initWebSocket() {
		Log.d("DynaMenuSys", "Before websocket init")
		val hostURI = URI(WEB_SOCKET_URL)
		createWebSocketClient(hostURI)
		webSocketClient.connect()


	}

	private fun createWebSocketClient(hostURI: URI) {
		Log.d("DynaMenuSys", "Websocket created")
		webSocketClient = object : WebSocketClient(hostURI) {

			override fun onClose(code: Int, reason: String?, remote: Boolean) {
				Log.d("DynaMenuSys", "Websocket closed: '${code}', '${reason}', '${remote}'")
				if (code != 1000) {
					CoroutineScope(EmptyCoroutineContext).launch {
						delay(2000L)
						initWebSocket()
					}
				}
			}

			override fun onError(ex: Exception?) {
				Log.d("DynaMenuSys", "Websocket error: '${ex?.message}'")
			}

			@SuppressLint("SetTextI18n", "CutPasteId")
			override fun onMessage(message: String?) {
				Log.d("DynaMenuSys", "Websocket message received: '${message}'")
				val mainMenuText = findViewById<TextView>(R.id.mainMenuText)

				val jsonFromClient = JsonParser.parseString(message).asJsonObject
				val event = jsonFromClient.get("event").asString
				val data = jsonFromClient.get("data").asJsonObject
				Log.d("DynaMenuSys", event)

				if (event == "hereIsDocument") {
					val fileLines = data.get("fileLines").asJsonArray
					runOnUiThread {
						mainMenuText.visibility = View.INVISIBLE

						mainMenuText.text = ""

						for ((lineNumber, currentLine) in fileLines.withIndex()) {
							var currentLine = currentLine.asString
							var previousLine = ""
							var nextLine = ""

							if (lineNumber > 0) {
								previousLine = fileLines[lineNumber - 1].asString
							} else {
								Log.d(
									"DynaMenuSys",
									"No previous line because this is the first iteration"
								)
							}

							if (lineNumber < (fileLines.size() - 1)) {
								nextLine = fileLines[lineNumber + 1].asString
							} else {
								Log.d(
									"DynaMenuSys",
									"No next line because this is the last iteration"
								)
							}

							currentLine = currentLine.trim()

							val html =
								Markdown.convertMarkdownToHTML(currentLine, previousLine, nextLine)
							Log.d(
								"DynaMenuSys",
								"Current: '$currentLine' | Previous: '$previousLine' | Next: '$nextLine' | HTML: '$html'"
							)

							mainMenuText.text = String.format(
								getString(R.string.fileLineAppend),
								mainMenuText.text,
								html
							)

						}
						mainMenuText.text = Html.fromHtml(mainMenuText.text.toString().trim())

						mainMenuText.visibility = View.VISIBLE
					}

				} else {
//                    Toast.makeText(applicationContext,
//                        "No document was given", Toast.LENGTH_SHORT).show()
				}

			}

			override fun onOpen(handshakedata: ServerHandshake?) {
				Log.d("DynaMenuSys", "Websocket opened: '${handshakedata.toString()}'")

				val dataInJson = JsonObject()
				dataInJson.addProperty("type", "android")

				val jsonDeviceToSend = JsonObject()
				jsonDeviceToSend.addProperty("event", "device")
				jsonDeviceToSend.add("data", dataInJson)
				webSocketClient.send(jsonDeviceToSend.toString())


				val jsonToSend = JsonObject()
				jsonToSend.addProperty("event", "canIHaveDocument")
				jsonToSend.addProperty("data", "")
				webSocketClient.send(jsonToSend.toString())

			}

			override fun onWebsocketPing(connection: WebSocket?, f: Framedata?) {
				super.onWebsocketPing(connection, f)
				Log.d("DynaMenuSys", "Received ping, sending one back to the server...")
			}
		}
	}
}