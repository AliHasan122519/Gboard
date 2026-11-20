package app.mykeyboard.update

import org.json.JSONObject

/** A newer release found on GitHub. */
data class UpdateInfo(
    val tag: String,
    val version: String,
    val notes: String,
    val apkUrl: String,
    val size: Long,
    /** Lower-case hex SHA-256 of the APK when GitHub reports it, otherwise null. */
    val sha256: String?,
) {
    fun toJson(): String = JSONObject().put("tag", tag).put("version", version).put("notes", notes)
        .put("apkUrl", apkUrl).put("size", size).put("sha256", sha256 ?: JSONObject.NULL).toString()

    companion object {
        fun fromJson(s: String?): UpdateInfo? = runCatching {
            val o = JSONObject(s ?: return null)
            UpdateInfo(o.getString("tag"), o.getString("version"), o.optString("notes"), o.getString("apkUrl"),
                o.optLong("size"), if (o.isNull("sha256")) null else o.getString("sha256"))
        }.getOrNull()
    }
}

object Versions {
    /** "v1.2.10" -> [1,2,10]. Non-numeric parts (e.g. "-beta") are ignored. */
    fun parse(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    /** > 0 when [a] is newer than [b]. */
    fun compare(a: String, b: String): Int {
        val x = parse(a); val y = parse(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    fun isNewer(candidate: String, current: String) = compare(candidate, current) > 0
}

object ReleaseParser {
    /**
     * Parses the body of GET /repos/{owner}/{repo}/releases/latest.
     * Returns null for drafts, pre-releases and releases without an .apk asset.
     */
    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val tag = o.getString("tag_name")
        val assets = o.getJSONArray("assets")
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) || !url.startsWith("https://")) continue
            val digest = a.optString("digest", "")
            val sha = if (digest.startsWith("sha256:")) digest.removePrefix("sha256:").lowercase() else null
            return UpdateInfo(tag, tag.removePrefix("v").removePrefix("V"), o.optString("body").trim().take(1500), url, a.optLong("size"), sha)
        }
        null
    }.getOrNull()
}

object ManifestParser {
    private val ALLOWED_HOSTS = setOf("raw.githubusercontent.com", "github.com", "objects.githubusercontent.com")

    /**
     * Parses update/latest.json: {"versionName":"1.0.1","versionCode":2,"apkUrl":"https://...","sha256":"...","size":123,"notes":"..."}.
     * The APK must be served over https from GitHub and must carry a SHA-256 so the download can be verified.
     */
    fun parse(json: String): UpdateInfo? = runCatching {
        val o = JSONObject(json)
        val name = o.getString("versionName")
        val url = o.getString("apkUrl")
        val host = java.net.URI(url).host ?: return null
        val sha = o.getString("sha256").lowercase()
        if (!url.startsWith("https://") || host !in ALLOWED_HOSTS || !Regex("[0-9a-f]{64}").matches(sha)) return null
        UpdateInfo("v$name", name, o.optString("notes").trim().take(1500), url, o.optLong("size"), sha)
    }.getOrNull()
}
