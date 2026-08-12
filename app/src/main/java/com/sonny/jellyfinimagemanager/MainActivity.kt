
package com.sonny.jellyfinimagemanager

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private val client = OkHttpClient()
    private var serverUrl = ""
    private var token = ""
    private var userId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,32,32,32) }
        val serverInput = EditText(this).apply { hint = "Server URL ex: http://192.168.1.10:8096" }
        val userInput = EditText(this).apply { hint = "Brugernavn" }
        val passInput = EditText(this).apply { hint = "Kodeord"; inputType = 129 }
        val loginBtn = Button(this).apply { text = "Login" }
        val searchInput = EditText(this).apply { hint = "Søg film/serie..." }
        val listView = ListView(this)
        val log = TextView(this)

        layout.addView(serverInput); layout.addView(userInput); layout.addView(passInput)
        layout.addView(loginBtn); layout.addView(searchInput); layout.addView(listView); layout.addView(log)
        setContentView(layout)

        val items = mutableListOf<Pair<String,String>>() // id + name
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mutableListOf())
        listView.adapter = adapter

        fun toast(s:String) = runOnUiThread { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); log.text = s }

        loginBtn.setOnClickListener {
            serverUrl = serverInput.text.toString().trim().trimEnd('/')
            val user = userInput.text.toString()
            val pass = passInput.text.toString()
            if(serverUrl.isEmpty()) { toast("Mangler server URL"); return@setOnClickListener }
            lifecycleScope.launch {
                try {
                    val json = JSONObject().put("Username", user).put("Pw", pass)
                    val req = Request.Builder().url("$serverUrl/Users/AuthenticateByName")
                        .header("X-Emby-Authorization", "MediaBrowser Client=\"JellyfinImageManager\", Device=\"Android\", DeviceId=\"sonny-app\", Version=\"3.0\"")
                        .post(json.toString().toRequestBody("application/json".toMediaType())).build()
                    val resp = withContext(Dispatchers.IO){ client.newCall(req).execute() }
                    val body = resp.body?.string() ?: ""
                    val obj = JSONObject(body)
                    token = obj.getString("AccessToken")
                    userId = obj.getJSONObject("User").getString("Id")
                    toast("Login OK: ${obj.getJSONObject("User").getString("Name")}")
                } catch(e:Exception){ toast("Login fejl: ${e.message}") }
            }
        }

        searchInput.setOnEditorActionListener { v,_,_ ->
            val q = v.text.toString()
            lifecycleScope.launch {
                try {
                    val url = "$serverUrl/Items?Recursive=true&SearchTerm=$q&IncludeItemTypes=Movie,Series&Fields=PrimaryImageAspectRatio&userId=$userId"
                    val req = Request.Builder().url(url).header("X-Emby-Token", token).build()
                    val resp = withContext(Dispatchers.IO){ client.newCall(req).execute() }
                    val arr = JSONObject(resp.body?.string() ?: "").getJSONArray("Items")
                    items.clear(); val names = mutableListOf<String>()
                    for(i in 0 until arr.length()){
                        val it = arr.getJSONObject(i)
                        items.add(it.getString("Id") to it.getString("Name"))
                        names.add(it.getString("Name") + " (" + it.getString("Type") + ")")
                    }
                    runOnUiThread { adapter.clear(); adapter.addAll(names); adapter.notifyDataSetChanged() }
                } catch(e:Exception){ toast("Søg fejl: ${e.message}") }
            }
            true
        }

        listView.setOnItemClickListener { _,_,pos,_ ->
            val (id, name) = items[pos]
            // Hent RemoteImages
            lifecycleScope.launch {
                try {
                    val url = "$serverUrl/Items/$id/RemoteImages?Type=Primary,Backdrop,Logo,Thumb"
                    val req = Request.Builder().url(url).header("X-Emby-Token", token).build()
                    val resp = withContext(Dispatchers.IO){ client.newCall(req).execute() }
                    val json = JSONObject(resp.body?.string() ?: "")
                    val images = json.getJSONArray("Images")
                    if(images.length()==0){ toast("Ingen remote billeder fundet for $name"); return@launch }
                    // Tag første billede og download til Jellyfin
                    val first = images.getJSONObject(0)
                    val imageUrl = first.getString("Url")
                    val provider = first.getString("ProviderName")
                    val type = first.getString("Type")
                    val downloadUrl = "$serverUrl/Items/$id/RemoteImages/Download?Type=$type&ImageUrl=${java.net.URLEncoder.encode(imageUrl,"UTF-8")}&ProviderName=$provider"
                    val dReq = Request.Builder().url(downloadUrl).header("X-Emby-Token", token).post("".toRequestBody(null)).build()
                    val dResp = withContext(Dispatchers.IO){ client.newCall(dReq).execute() }
                    toast(if(dResp.isSuccessful) "✅ Nyt billede sat for $name!" else "Fejl ved download: ${dResp.code}")
                } catch(e:Exception){ toast("Remote fejl: ${e.message}") }
            }
        }
    }
}
