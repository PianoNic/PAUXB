package ch.pianonic.pauxb.data

import android.content.Context
import android.content.SharedPreferences
import ch.pianonic.pauxb.ui.screens.LinuxApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the user's configured Linux apps across sessions using SharedPreferences.
 */
class AppStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pauxb_apps", Context.MODE_PRIVATE)

    fun saveApps(apps: List<LinuxApp>) {
        val jsonArray = JSONArray()
        apps.forEach { app ->
            jsonArray.put(JSONObject().apply {
                put("id", app.id)
                put("name", app.name)
                put("command", app.command)
                put("packageName", app.packageName)
            })
        }
        prefs.edit().putString("apps", jsonArray.toString()).apply()
    }

    fun loadApps(): List<LinuxApp> {
        val json = prefs.getString("apps", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                LinuxApp(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    command = obj.getString("command"),
                    packageName = obj.getString("packageName")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
