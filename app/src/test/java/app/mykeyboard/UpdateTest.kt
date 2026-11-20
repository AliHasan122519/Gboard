package app.mykeyboard

import app.mykeyboard.update.ReleaseParser
import app.mykeyboard.update.UpdateInfo
import app.mykeyboard.update.Versions
import org.junit.Assert.*
import org.junit.Test

class UpdateTest {
    @Test fun versionComparison() {
        assertTrue(Versions.isNewer("v1.0.1", "1.0.0"))
        assertTrue(Versions.isNewer("1.10.0", "1.9.9"))
        assertTrue(Versions.isNewer("2.0", "1.9.9"))
        assertFalse(Versions.isNewer("1.0.0", "1.0.0"))
        assertFalse(Versions.isNewer("v1.0", "1.0.0"))
        assertFalse(Versions.isNewer("0.9.9", "1.0.0"))
        assertEquals(listOf(1, 2, 3), Versions.parse("v1.2.3-beta"))
    }

    private fun release(extra: String = "", tag: String = "v1.2.0", asset: String = """{"name":"MyKeyboard-1.2.0.apk","browser_download_url":"https://github.com/x/y/releases/download/v1.2.0/MyKeyboard-1.2.0.apk","size":1234,"digest":"sha256:ABCDEF0123"}""") =
        """{"tag_name":"$tag","body":"- new fonts\n- fixes","draft":false,"prerelease":false$extra,"assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},$asset]}"""

    @Test fun parsesLatestRelease() {
        val i = ReleaseParser.parse(release())!!
        assertEquals("1.2.0", i.version); assertEquals("v1.2.0", i.tag)
        assertEquals(1234L, i.size); assertEquals("abcdef0123", i.sha256)
        assertTrue(i.apkUrl.endsWith(".apk")); assertTrue(i.notes.contains("new fonts"))
    }

    @Test fun ignoresUnsafeOrIncompleteReleases() {
        assertNull(ReleaseParser.parse(release(extra = "").replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(ReleaseParser.parse(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(ReleaseParser.parse(release(asset = """{"name":"a.apk","browser_download_url":"http://insecure/a.apk","size":1}""")))   // not https
        assertNull(ReleaseParser.parse("""{"tag_name":"v9","assets":[{"name":"readme.md","browser_download_url":"https://x/readme.md"}]}"""))   // no apk
        assertNull(ReleaseParser.parse("not json"))
    }

    @Test fun releaseWithoutDigestStillParses() {
        val i = ReleaseParser.parse(release(asset = """{"name":"a.apk","browser_download_url":"https://h/a.apk","size":5}"""))!!
        assertNull(i.sha256)
    }

    @Test fun updateInfoJsonRoundTrip() {
        val i = ReleaseParser.parse(release())!!
        assertEquals(i, UpdateInfo.fromJson(i.toJson()))
        assertNull(UpdateInfo.fromJson(null)); assertNull(UpdateInfo.fromJson("{}"))
    }

    private val sha = "a".repeat(64)
    private fun manifest(url: String = "https://raw.githubusercontent.com/o/r/b/update/MyKeyboard-2.apk", hash: String = sha) =
        """{"versionName":"1.0.1","versionCode":2,"apkUrl":"$url","sha256":"$hash","size":99,"notes":"- faster delete"}"""

    @Test fun manifestIsParsedAndValidated() {
        val i = app.mykeyboard.update.ManifestParser.parse(manifest())!!
        assertEquals("1.0.1", i.version); assertEquals(99L, i.size); assertEquals(sha, i.sha256); assertTrue(i.notes.contains("faster"))
        assertTrue(Versions.isNewer(i.version, "1.0.0")); assertFalse(Versions.isNewer(i.version, "1.0.1"))
        assertNull("must be https", app.mykeyboard.update.ManifestParser.parse(manifest(url = "http://raw.githubusercontent.com/x.apk")))
        assertNull("only GitHub hosts", app.mykeyboard.update.ManifestParser.parse(manifest(url = "https://evil.example.com/x.apk")))
        assertNull("checksum required", app.mykeyboard.update.ManifestParser.parse(manifest(hash = "abc")))
        assertNull(app.mykeyboard.update.ManifestParser.parse("garbage"))
    }
}
