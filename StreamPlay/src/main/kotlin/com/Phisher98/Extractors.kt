package com.phisher98

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import com.lagradost.api.Log
import com.lagradost.cloudstream3.APIHolder.getCaptchaToken
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.extractors.DoodLaExtractor
import com.lagradost.cloudstream3.extractors.Filesim
import com.lagradost.cloudstream3.extractors.GMPlayer
import com.lagradost.cloudstream3.extractors.Jeniusplay
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.VidHidePro
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.VidhideExtractor
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.phisher98.StreamPlay.Companion.animepaheAPI
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.text.isBlank


open class Playm4u : ExtractorApi() {
    override val name = "Playm4u"
    override val mainUrl = "https://play9str.playm4u.xyz"
    override val requiresReferer = true
    private val password = "plhq@@@22"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url, referer = referer).document
        val script = document.selectFirst("script:containsData(idfile =)")?.data() ?: return
        val passScript = document.selectFirst("script:containsData(domain_ref =)")?.data() ?: return

        val pass = passScript.substringAfter("CryptoJS.MD5('").substringBefore("')")
        val amount = passScript.substringAfter(".toString()), ").substringBefore("));").toInt()

        val idFile = "idfile".findIn(script)
        val idUser = "idUser".findIn(script)
        val domainApi = "DOMAIN_API".findIn(script)
        val nameKeyV3 = "NameKeyV3".findIn(script)
        val dataEnc = caesarShift(
            mahoa(
                "Win32|$idUser|$idFile|$referer",
                md5(pass)
            ), amount
        ).toHex()

        val captchaKey =
            document.select("script[src*=https://www.google.com/recaptcha/api.js?render=]")
                .attr("src").substringAfter("render=")
        val token = getCaptchaToken(
            url,
            captchaKey,
            referer = referer
        )

        val source = app.post(
            domainApi, data = mapOf(
                "namekey" to nameKeyV3,
                "token" to "$token",
                "referrer" to "$referer",
                "data" to "$dataEnc|${md5(dataEnc + password)}",
            ), referer = "$mainUrl/"
        ).parsedSafe<Source>()

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = source?.data ?: return,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = Qualities.P1080.value
            }
        )

        subtitleCallback.invoke(
            SubtitleFile(
                source.sub?.substringBefore("|")?.toLanguage() ?: return,
                source.sub.substringAfter("|"),
            )
        )

    }

    private fun caesarShift(str: String, amount: Int): String {
        var output = ""
        val adjustedAmount = if (amount < 0) amount + 26 else amount
        for (element in str) {
            var c = element
            if (c.isLetter()) {
                val code = c.code
                c = when (code) {
                    in 65..90 -> ((code - 65 + adjustedAmount) % 26 + 65).toChar()
                    in 97..122 -> ((code - 97 + adjustedAmount) % 26 + 97).toChar()
                    else -> c
                }
            }
            output += c
        }
        return output
    }

    private fun mahoa(input: String, key: String): String {
        val a = CryptoJS.encrypt(key, input)
        return a.replace("U2FsdGVkX1", "")
            .replace("/", "|a")
            .replace("+", "|b")
            .replace("=", "|c")
            .replace("|", "-z")
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    private fun String.toHex(): String {
        return this.toByteArray().joinToString("") { "%02x".format(it) }
    }

    private fun String.findIn(data: String): String {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1) ?: ""
    }

    private fun String.toLanguage(): String {
        return if (this == "EN") "English" else this
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
        @JsonProperty("sub") val sub: String? = null,
    )

}

open class M4ufree : ExtractorApi() {
    override val name = "M4ufree"
    override val mainUrl = "https://play.playm4u.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = session.get(url, referer = referer).document
        val script = document.selectFirst("script:containsData(idfile =)")?.data() ?: return

        val idFile = "idfile".findIn(script)
        val idUser = "idUser".findIn(script)

        val video = session.post(
            "https://api-plhq.playm4u.xyz/apidatard/$idUser/$idFile",
            data = mapOf("referrer" to "$referer"),
            headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
            )
        ).text.let { AppUtils.tryParseJson<Source>(it) }?.data

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video ?: return,
                INFER_TYPE
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.P720.value
            }
        )

    }

    private fun String.findIn(data: String): String? {
        return "$this\\s*=\\s*[\"'](\\S+)[\"'];".toRegex().find(data)?.groupValues?.get(1)
    }

    data class Source(
        @JsonProperty("data") val data: String? = null,
    )

}

class VCloudGDirect : ExtractorApi() {
    override val name: String = "V-Cloud GD"
    override val mainUrl: String = "https://fastdl.icu"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val source = app.get(url).document.selectFirst("#vd")?.attr("href") ?: ""
        if (source.isBlank()) {
            Log.e("Error:", "Failed to extract video link from $url")
            //loadExtractor(url, subtitleCallback, callback) // Passes original URL, not an empty string
            return
        }
        callback.invoke(
            newExtractorLink(
                "V-Cloud GD 10 Gbps",
                "V-Cloud GD 10 Gbps",
                url = source
            ) {
                this.quality = getQualityFromName(source)
            }
        )
    }
}


class VCloud : ExtractorApi() {
    override val name: String = "V-Cloud"
    override val mainUrl: String = "https://vcloud.lol"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        var href = url

        if (href.contains("api/index.php")) {
            href = runCatching {
                app.get(url).document.selectFirst("div.main h4 a")?.attr("href")
            }.getOrNull() ?: return
        }

        val doc = runCatching { app.get(href).document }.getOrNull() ?: return
        val scriptTag = doc.selectFirst("script:containsData(url)")?.data() ?: ""
        val urlValue = Regex("var url = '([^']*)'").find(scriptTag)?.groupValues?.getOrNull(1).orEmpty()
        if (urlValue.isEmpty()) return

        val document = runCatching { app.get(urlValue).document }.getOrNull() ?: return
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()

        val headerdetails = cleanTitle(header)

        val labelExtras = buildString {
            if (headerdetails.isNotEmpty()) append("[$headerdetails]")
            if (size.isNotEmpty()) append("[$size]")
        }

        val div = document.selectFirst("div.card-body") ?: return

        div.select("h2 a.btn").amap {
            val link = it.attr("href")
            val text = it.text()
            val quality = getIndexQuality(header)
            val source = name

            when {
                text.contains("Download [FSL Server]") -> {
                    callback.invoke(
                        newExtractorLink(
                            "[FSL Server]",
                            "[FSL Server]",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("Download [Server : 1]") -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source [Server]",
                            "$source $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("BuzzServer") -> {
                    val dlink = app.get("$link/download", referer = link, allowRedirects = false).headers["hx-redirect"] ?: ""
                    val baseUrl = getBaseUrl(link)
                    if (dlink.isNotEmpty()) {
                        callback.invoke(
                            newExtractorLink(
                                "[BuzzServer]",
                                "[BuzzServer] $labelExtras",
                                baseUrl + dlink,
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w("Error:", "Not Found")
                    }
                }

                text.contains("pixel", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "Pixeldrain",
                            "Pixeldrain $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("10Gbps", ignoreCase = true) -> {
                    var currentLink = link
                    var redirectUrl: String?

                    while (true) {
                        val response = app.get(currentLink, allowRedirects = false)
                        redirectUrl = response.headers["location"]
                        if (redirectUrl == null) {
                            Log.e("HubCloud", "10Gbps: No redirect")
                            return@amap
                        }
                        if ("link=" in redirectUrl) break
                        currentLink = redirectUrl
                    }
                    val finalLink = redirectUrl.substringAfter("link=") ?: return@amap
                    callback.invoke(
                        newExtractorLink(
                            "10Gbps [Download]",
                            "10Gbps [Download] $labelExtras",
                            finalLink,
                        ) { this.quality = quality }
                    )
                }

                text.contains("S3 Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "S3 Server",
                            "S3 Server $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}




open class Streamruby : ExtractorApi() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = "/e/(\\w+)".toRegex().find(url)?.groupValues?.get(1) ?: return
        val response = app.post(
            "$mainUrl/dl", data = mapOf(
                "op" to "embed",
                "file_code" to id,
                "auto" to "1",
                "referer" to "",
            ), referer = referer
        )
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

open class Uploadever : ExtractorApi() {
    override val name = "Uploadever"
    override val mainUrl = "https://uploadever.in"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var res = app.get(url, referer = referer).document
        val formUrl = res.select("form").attr("action")
        var formData = res.select("form input").associate { it.attr("name") to it.attr("value") }
            .filterKeys { it != "go" }
            .toMutableMap()
        val formReq = app.post(formUrl, data = formData)

        res = formReq.document
        val captchaKey =
            res.select("script[src*=https://www.google.com/recaptcha/api.js?render=]").attr("src")
                .substringAfter("render=")
        val token = getCaptchaToken(url, captchaKey, referer = "$mainUrl/")
        formData = res.select("form#down input").associate { it.attr("name") to it.attr("value") }
            .toMutableMap()
        formData["adblock_detected"] = "0"
        formData["referer"] = url
        res = app.post(
            formReq.url,
            data = formData + mapOf("g-recaptcha-response" to "$token"),
            cookies = formReq.cookies
        ).document
        val video = res.select("div.download-button a.btn.btn-dow.recaptchav2").attr("href")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video,
                INFER_TYPE
            ) {
                this.quality = Qualities.Unknown.value
            }
        )
    }

}

open class Netembed : ExtractorApi() {
    override var name: String = "Netembed"
    override var mainUrl: String = "https://play.netembed.xyz"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = getAndUnpack(response.text)
        val m3u8 = Regex("((https:|http:)//.*\\.m3u8)").find(script)?.groupValues?.getOrNull(1) ?: return

        generateM3u8(this.name, m3u8, "$mainUrl/").forEach(callback)
    }
}

open class Ridoo : ExtractorApi() {
    override val name = "Ridoo"
    override var mainUrl = "https://ridoo.net"
    override val requiresReferer = true
    open val defaulQuality = Qualities.P1080.value

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 = Regex("file:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        val quality = "qualityLabels.*\"(\\d{3,4})[pP]\"".toRegex().find(script)?.groupValues?.get(1)
        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = m3u8 ?: return,
                INFER_TYPE
            ) {
                this.referer = mainUrl
                this.quality = quality?.toIntOrNull() ?: defaulQuality
            }
        )
    }

}

open class Streamvid : ExtractorApi() {
    override val name = "Streamvid"
    override val mainUrl = "https://streamvid.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val response = app.get(url, referer = referer)
        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        }
        val m3u8 =
            Regex("src:\\s*\"(.*?m3u8.*?)\"").find(script ?: return)?.groupValues?.getOrNull(1)
        generateM3u8(
            name,
            m3u8 ?: return,
            mainUrl
        ).forEach(callback)
    }

}

open class Embedrise : ExtractorApi() {
    override val name = "Embedrise"
    override val mainUrl = "https://embedrise.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val res = app.get(url, referer = referer).document
        val title = res.select("title").text()
        val video = res.select("video#player source").attr("src")

        callback.invoke(
            newExtractorLink(
                this.name,
                this.name,
                url = video,
                INFER_TYPE
            ) {
                this.referer = "$mainUrl/"
                this.quality = getIndexQuality(title)
            }
        )
    }

}

class FilemoonNl : Ridoo() {
    override val name = "FilemoonNl"
    override var mainUrl = "https://filemoon.nl"
    override val defaulQuality = Qualities.Unknown.value
}

class AllinoneDownloader : Filesim() {
    override var name = "MultiMovies API"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Alions : Ridoo() {
    override val name = "Alions"
    override var mainUrl = "https://alions.pro"
    override val defaulQuality = Qualities.Unknown.value
}

class Pixeldra : PixelDrain() {
    override val mainUrl = "https://pixeldra.in"
}

class Snolaxstream : Filesim() {
    override val mainUrl = "https://snolaxstream.online"
    override val name = "Snolaxstream"
}

class bulbasaur : Filesim() {
    override val mainUrl = "https://file-mi11ljwj-embed.com"
    override val name = "Filemoon"
}
class do0od : DoodLaExtractor() {
    override var mainUrl = "https://do0od.com"
}

class doodre : DoodLaExtractor() {
    override var mainUrl = "https://dood.re"
}

class TravelR : GMPlayer() {
    override val name = "TravelR"
    override val mainUrl = "https://travel-russia.xyz"
}

class Mwish : Filesim() {
    override val name = "Mwish"
    override var mainUrl = "https://mwish.pro"
}

class Animefever : Filesim() {
    override val name = "Animefever"
    override var mainUrl = "https://animefever.fun"
}

class Multimovies : Ridoo() {
    override val name = "Multimovies"
    override var mainUrl = "https://multimovies.cloud"
}

class MultimoviesSB : StreamSB() {
    override var name = "Multimovies"
    override var mainUrl = "https://multimovies.website"
}

class Yipsu : Voe() {
    override val name = "Yipsu"
    override var mainUrl = "https://yip.su"
}

class Filelions : VidhideExtractor() {
    override var name = "Filelions"
    override var mainUrl = "https://alions.pro"
    override val requiresReferer = false
}


class Embedwish : Filesim() {
    override val name = "Embedwish"
    override var mainUrl = "https://embedwish.com"
}

class dwish : VidhideExtractor() {
    override var mainUrl = "https://dwish.pro"
}

class dlions : VidhideExtractor() {
    override var name = "Dlions"
    override var mainUrl = "https://dlions.pro"
}

class Animezia : VidhideExtractor() {
    override var name = "MultiMovies API"
    override var mainUrl = "https://animezia.cloud"
}

class MixDropSi : MixDrop(){
    override var mainUrl = "https://mixdrop.si"
}

class MixDropPs : MixDrop(){
    override var mainUrl = "https://mixdrop.ps"
}

class Servertwo : VidhideExtractor() {
    override var name = "MultiMovies Vidhide"
    override var mainUrl = "https://server2.shop"
}

class Filelion : Filesim() {
    override val name = "Filelion"
    override var mainUrl = "https://filelions.to"
}

class MultimoviesAIO: StreamWishExtractor() {
    override var name = "Multimovies Cloud AIO"
    override var mainUrl = "https://allinonedownloader.fun"
}

class Rapidplayers: StreamWishExtractor() {
    override var mainUrl = "https://rapidplayers.com"
}

class Flaswish : Ridoo() {
    override val name = "Flaswish"
    override var mainUrl = "https://flaswish.com"
    override val defaulQuality = Qualities.Unknown.value
}

class Comedyshow : Jeniusplay() {
    override val mainUrl = "https://comedyshow.to"
    override val name = "Comedyshow"
}

class Graceaddresscommunity : Voe() {
    override var mainUrl = "https://graceaddresscommunity.com"
}

class Sethniceletter : Voe() {
    override var mainUrl = "https://sethniceletter.com"
}

class Maxfinishseveral : Voe() {
    override var mainUrl = "https://maxfinishseveral.com"
}


class Tellygossips : ExtractorApi() {
    override val mainUrl = "https://flow.tellygossips.net"
    override val name = "Tellygossips"
    override val requiresReferer = false
    private val referer = "http://tellygossips.net/"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url, referer = this.referer).text
        val link = doc.substringAfter("src\":\"").substringBefore("\",")
        callback(
            newExtractorLink(
                name,
                name,
                url = link,
                INFER_TYPE
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

class Tvlogy : ExtractorApi() {
    override val mainUrl = "https://hls.tvlogy.to"
    override val name = "Tvlogy"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfter("data=")
        val data = mapOf(
            "hash" to id,
            "r" to "http%3A%2F%2Ftellygossips.net%2F"
        )
        val headers = mapOf("X-Requested-With" to "XMLHttpRequest")
        val meta = app.post("$url&do=getVideo", headers = headers, referer = url, data = data)
            .parsedSafe<MetaData>() ?: return
        callback(
            newExtractorLink(
                name,
                name,
                url = meta.videoSource,
                ExtractorLinkType.M3U8
            ) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            }
        )
    }

    data class MetaData(
        val hls: Boolean,
        val videoSource: String
    )

}

open class Modflix : ExtractorApi() {
    override val name: String = "Modflix"
    override val mainUrl: String = "https://video-seed.xyz"
    override val requiresReferer = true

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun getUrl(
        finallink: String,
        quality: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val token = finallink.substringAfter("https://video-seed.xyz/?url=")
        val downloadlink = app.post(
            url = "https://video-seed.xyz/api",
            data = mapOf(
                "keys" to token
            ),
            referer = finallink,
            headers = mapOf(
                "x-token" to "video-seed.xyz",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:125.0) Gecko/20100101 Firefox/125.0"
            )
        )
        val finaldownloadlink =
            downloadlink.toString().substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
        val link = finaldownloadlink
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = link,
            ) {
                this.quality = getQualityFromName(quality)
            }
        )
    }
}

class furher : Filesim() {
    override val name: String = "AZSeries"
    override var mainUrl = "https://furher.in"
}
class fastdlserver : GDFlix() {
    override var mainUrl = "https://fastdlserver.online"
}

class GDFlix1 : GDFlix() {
    override val mainUrl: String = "https://new3.gdflix.cfd"
}

class GDFlix2 : GDFlix() {
    override val mainUrl: String = "https://new2.gdflix.cfd"
}

class PixelServer : PixelDrain() {
    override val mainUrl: String = "https://pixeldrain.dev"
}

open class PixelDrain : ExtractorApi() {
    override val name            = "PixelDrain"
    override val mainUrl         = "https://pixeldrain.com"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val mId = Regex("/u/(.*)").find(url)?.groupValues?.get(1)
        if (mId.isNullOrEmpty())
        {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = url
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        } else {
            callback.invoke(
                newExtractorLink(
                    this.name,
                    this.name,
                    url = "$mainUrl/api/file/${mId}?download"
                ) {
                    this.referer = url
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }
}

class HubCloud : ExtractorApi() {
    override val name = "Hub-Cloud"
    override val mainUrl = "https://hubcloud.ink"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val realUrl = url.takeIf {
            try { URL(it); true } catch (e: Exception) { Log.e("HubCloud", "Invalid URL: ${e.message}"); false }
        } ?: return

        val baseUrl=getBaseUrl(realUrl)

        val href = try {
            if ("hubcloud.php" in realUrl) {
                realUrl
            } else {
                val rawHref = app.get(realUrl).document.select("#download").attr("href")
                if (rawHref.startsWith("http", ignoreCase = true)) {
                    rawHref
                } else {
                    baseUrl.trimEnd('/') + "/" + rawHref.trimStart('/')
                }
            }
        } catch (e: Exception) {
            Log.e("HubCloud", "Failed to extract href: ${e.message}")
            ""
        }
        if (href.isBlank()) {
            Log.w("HubCloud", "No valid href found")
            return
        }

        val document = app.get(href).document
        val size = document.selectFirst("i#size")?.text().orEmpty()
        val header = document.selectFirst("div.card-header")?.text().orEmpty()

        val headerDetails = cleanTitle(header)

        val labelExtras = buildString {
            if (headerDetails.isNotEmpty()) append("[$headerDetails]")
            if (size.isNotEmpty()) append("[$size]")
        }
        val quality = getIndexQuality(header)

        document.select("div.card-body h2 a.btn").amap { element ->
            val link = element.attr("href")
            val text = element.text()

            when {
                text.contains("FSL Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source [FSL Server]",
                            "$source [FSL Server] $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("Download File", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source",
                            "$source $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("BuzzServer", ignoreCase = true) -> {
                    val buzzResp = app.get("$link/download", referer = link, allowRedirects = false)
                    val dlink = buzzResp.headers["hx-redirect"].orEmpty()
                    if (dlink.isNotBlank()) {
                        callback.invoke(
                            newExtractorLink(
                                "$source [BuzzServer]",
                                "$source [BuzzServer] $labelExtras",
                                dlink,
                            ) { this.quality = quality }
                        )
                    } else {
                        Log.w("HubCloud", "BuzzServer: No redirect")
                    }
                }
                text.contains("pixeldra", ignoreCase = true) || text.contains("pixel", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source Pixeldrain",
                            "$source Pixeldrain $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("S3 Server", ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink(
                            "$source S3 Server",
                            "$source S3 Server $labelExtras",
                            link,
                        ) { this.quality = quality }
                    )
                }

                text.contains("10Gbps", ignoreCase = true) -> {
                    var currentLink = link
                    var redirectUrl: String?

                    while (true) {
                        val response = app.get(currentLink, allowRedirects = false)
                        redirectUrl = response.headers["location"]
                        if (redirectUrl == null) {
                            Log.e("HubCloud", "10Gbps: No redirect")
                            return@amap
                        }
                        if ("link=" in redirectUrl) break
                        currentLink = redirectUrl
                    }
                    val finalLink = redirectUrl.substringAfter("link=") ?: return@amap
                    callback.invoke(
                        newExtractorLink(
                            "$source 10Gbps [Download]",
                            "$source 10Gbps [Download] $labelExtras",
                            finalLink,
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    loadExtractor(link, "", subtitleCallback, callback)
                }
            }
        }
    }

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.P2160.value
    }

    private fun getBaseUrl(url: String): String {
        return try {
            URI(url).let { "${it.scheme}://${it.host}" }
        } catch (_: Exception) {
            ""
        }
    }
}


class oxxxfile : ExtractorApi() {
    override val name = "OXXFile"
    override val mainUrl = "https://new.oxxfile.info"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id = url.substringAfterLast("s/")
        val api = app.get("$mainUrl/api/s/$id").parsedSafe<oxxfile>() ?: return

        api.pixeldrainLink?.takeIf { it.isNotBlank() }?.let { pixeldrainUrl ->
            loadSourceNameExtractor(
                "OXXFile",
                pixeldrainUrl,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }

        api.hubcloudLink.takeIf { it.isNotBlank() }?.let { hubcloudUrl ->
            loadSourceNameExtractor(
                "OXXFile",
                hubcloudUrl,
                "$mainUrl/",
                subtitleCallback,
                callback
            )
        }
    }
}


class Driveleech : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.org"
}

class DriveleechPro : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.pro"
}

class DriveleechNet : Driveseed() {
    override val name: String = "Driveleech"
    override val mainUrl: String = "https://driveleech.net"
}

open class Driveseed : ExtractorApi() {
    override val name: String = "Driveseed"
    override val mainUrl: String = "https://driveseed.org"
    override val requiresReferer = false

    private fun getIndexQuality(str: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(str ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }

    private suspend fun CFType1(url: String): List<String> {
        return runCatching {
            app.get("$url?type=1").document
                .select("a.btn-success")
                .mapNotNull { it.attr("href").takeIf { href -> href.startsWith("http") } }
        }.getOrElse {
            Log.e("Driveseed", "CFType1 error: ${it.message}")
            emptyList()
        }
    }

    private suspend fun resumeCloudLink(baseUrl: String, path: String): String? {
        return runCatching {
            app.get(baseUrl + path).document
                .selectFirst("a.btn-success")?.attr("href")
                ?.takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "ResumeCloud error: ${it.message}")
            null
        }
    }

    private suspend fun resumeBot(url: String): String? {
        return runCatching {
            val response = app.get(url)
            val docString = response.document.toString()
            val ssid = response.cookies["PHPSESSID"].orEmpty()
            val token = Regex("formData\\.append\\('token', '([a-f0-9]+)'\\)").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val path = Regex("fetch\\('/download\\?id=([a-zA-Z0-9/+]+)'").find(docString)?.groupValues?.getOrNull(1).orEmpty()
            val baseUrl = url.substringBefore("/download")

            if (token.isEmpty() || path.isEmpty()) return@runCatching null

            val json = app.post(
                "$baseUrl/download?id=$path",
                requestBody = FormBody.Builder().addEncoded("token", token).build(),
                headers = mapOf("Accept" to "*/*", "Origin" to baseUrl, "Sec-Fetch-Site" to "same-origin"),
                cookies = mapOf("PHPSESSID" to ssid),
                referer = url
            ).text

            JSONObject(json).getString("url").takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "ResumeBot error: ${it.message}")
            null
        }
    }

    private suspend fun instantLink(finallink: String): String? {
        return runCatching {
            val uri = URI(finallink)
            val host = uri.host ?: if (finallink.contains("video-leech")) "video-leech.pro" else "video-seed.pro"
            val token = finallink.substringAfter("url=")
            val response = app.post(
                "https://$host/api",
                data = mapOf("keys" to token),
                referer = finallink,
                headers = mapOf("x-token" to host)
            ).text

            response.substringAfter("url\":\"")
                .substringBefore("\",\"name")
                .replace("\\/", "/")
                .takeIf { it.startsWith("http") }
        }.getOrElse {
            Log.e("Driveseed", "InstantLink error: ${it.message}")
            null
        }
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val Basedomain = getBaseUrl(url)

        val document = try {
            if (url.contains("r?key=")) {
                val temp = app.get(url).document.selectFirst("script")
                    ?.data()
                    ?.substringAfter("replace(\"")
                    ?.substringBefore("\")")
                    .orEmpty()
                app.get(mainUrl + temp).document
            } else {
                app.get(url).document
            }
        } catch (e: Exception) {
            Log.e("Driveseed", "getUrl page load error: ${e.message}")
            return
        }

        val qualityText = document.selectFirst("li.list-group-item")?.text().orEmpty()
        val rawFileName = qualityText.replace("Name : ", "").trim()
        val fileName = cleanTitle(rawFileName)
        val size = document.selectFirst("li:nth-child(3)")?.text().orEmpty().replace("Size : ", "").trim()

        val labelExtras = buildString {
            if (fileName.isNotEmpty()) append("[$fileName]")
            if (size.isNotEmpty()) append("[$size]")
        }

        document.select("div.text-center > a").forEach { element ->
            val text = element.text()
            val href = element.attr("href")
            Log.d("Driveseed", "Link: $href")

            if (href.isNotBlank()) {
                when {
                    text.contains("Instant Download", ignoreCase = true) -> {
                        instantLink(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name Instant(Download)",
                                    "$name Instant(Download) $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Resume Worker Bot", ignoreCase = true) -> {
                        resumeBot(href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeBot(VLC)",
                                    "$name ResumeBot(VLC) $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Direct Links", ignoreCase = true) -> {
                        CFType1(Basedomain + href).forEach { link ->
                            callback(
                                newExtractorLink(
                                    "$name CF Type1",
                                    "$name CF Type1 $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Resume Cloud", ignoreCase = true) -> {
                        resumeCloudLink(Basedomain, href)?.let { link ->
                            callback(
                                newExtractorLink(
                                    "$name ResumeCloud",
                                    "$name ResumeCloud $labelExtras",
                                    url = link
                                ) {
                                    this.quality = getIndexQuality(qualityText)
                                }
                            )
                        }
                    }

                    text.contains("Cloud Download", ignoreCase = true) -> {
                        callback(
                            newExtractorLink(
                                "$name Cloud Download",
                                "$name Cloud Download $labelExtras",
                                url = href
                            ) {
                                this.quality = getIndexQuality(qualityText)
                            }
                        )
                    }
                }
            }
        }
    }
}


class Kwik : ExtractorApi() {
    override val name            = "Kwik"
    override val mainUrl         = "https://kwik.si"
    override val requiresReferer = true

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val res = app.get(url,referer=animepaheAPI)
        val script =
            res.document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data()
        val unpacked = getAndUnpack(script ?: return)
        val m3u8 =Regex("source=\\s*'(.*?m3u8.*?)'").find(unpacked)?.groupValues?.getOrNull(1) ?:""
        callback.invoke(
            newExtractorLink(
                name,
                name,
                url = m3u8,
                type = INFER_TYPE
            ) {
                this.quality = getQualityFromName(referer)
            }
        )
    }
}


//Credit Thanks to https://github.com/SaurabhKaperwan/CSX/blob/7256fe183966412b2323beb15d03331009bfb80f/CineStream/src/main/kotlin/com/megix/Extractors.kt#L108
class Pahe : ExtractorApi() {
    override val name = "Pahe"
    override val mainUrl = "https://pahe.win"
    override val requiresReferer = true
    private val kwikParamsRegex = Regex("""\("(\w+)",\d+,"(\w+)",(\d+),(\d+),\d+\)""")
    private val kwikDUrl = Regex("action=\"([^\"]+)\"")
    private val kwikDToken = Regex("value=\"([^\"]+)\"")
    private val client = OkHttpClient()

    private fun decrypt(fullString: String, key: String, v1: Int, v2: Int): String {
        val keyIndexMap = key.withIndex().associate { it.value to it.index }
        val sb = StringBuilder()
        var i = 0
        val toFind = key[v2]

        while (i < fullString.length) {
            val nextIndex = fullString.indexOf(toFind, i)
            val decodedCharStr = buildString {
                for (j in i until nextIndex) {
                    append(keyIndexMap[fullString[j]] ?: -1)
                }
            }

            i = nextIndex + 1

            val decodedChar = (decodedCharStr.toInt(v2) - v1).toChar()
            sb.append(decodedChar)
        }

        return sb.toString()
    }

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val noRedirects = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        val initialRequest = Request.Builder()
            .url("$url/i")
            .get()
            .build()

        val kwikUrl = "https://" + noRedirects.newCall(initialRequest).execute()
            .header("location")!!.substringAfterLast("https://")

        val fContentRequest = Request.Builder()
            .url(kwikUrl)
            .header("referer", "https://kwik.cx/")
            .get()
            .build()

        val fContent = client.newCall(fContentRequest).execute()
        val fContentString = fContent.body.string()

        val (fullString, key, v1, v2) = kwikParamsRegex.find(fContentString)!!.destructured
        val decrypted = decrypt(fullString, key, v1.toInt(), v2.toInt())

        val uri = kwikDUrl.find(decrypted)!!.destructured.component1()
        val tok = kwikDToken.find(decrypted)!!.destructured.component1()

        val noRedirectClient = OkHttpClient().newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(client.cookieJar)
            .build()

        var code = 419
        var tries = 0
        var content: Response? = null

        while (code != 302 && tries < 20) {
            val formBody = FormBody.Builder()
                .add("_token", tok)
                .build()

            val postRequest = Request.Builder()
                .url(uri)
                .header("user-agent", " Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .header("referer", fContent.request.url.toString())
                .header("cookie",  fContent.headers("set-cookie").firstOrNull().toString())
                .post(formBody)
                .build()

            content = noRedirectClient.newCall(postRequest).execute()
            code = content.code
            tries++
        }

        val location = content?.header("location").toString()
        content?.close()

        callback(
            newExtractorLink(
                name,
                name,
                url = location,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}


open class Embtaku : ExtractorApi() {
    override var name = "Embtaku"
    override var mainUrl = "https://embtaku.pro"
    override val requiresReferer = false

    override suspend fun
            getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val responsecode= app.get(url)
        val serverRes = responsecode.document
        serverRes.select("ul.list-server-items").amap {
            val href=it.attr("data-video")
            loadCustomExtractor("Anichi [Embtaku]",href,"",subtitleCallback,callback)
        }
    }
}

class GDMirrorbot : ExtractorApi() {
    override var name = "GDMirrorbot"
    override var mainUrl = "https://gdmirrorbot.nl"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val host = getBaseUrl(app.get(url).url)
        val embedId = url.substringAfterLast("/")
        val postData = mapOf("sid" to embedId)

        val responseJson = app.post("$host/embedhelper.php", data = postData).text
        val jsonElement = JsonParser.parseString(responseJson)
        if (!jsonElement.isJsonObject) return

        val root = jsonElement.asJsonObject
        val siteUrls = root["siteUrls"]?.asJsonObject ?: return
        val siteFriendlyNames = root["siteFriendlyNames"]?.asJsonObject

        val decodedMresult: JsonObject = when {
            root["mresult"]?.isJsonObject == true -> {
                root["mresult"]?.asJsonObject!!
            }
            root["mresult"]?.isJsonPrimitive == true -> {
                val mresultBase64 = root["mresult"]?.asString ?: return
                try {
                    val jsonStr = base64Decode(mresultBase64)
                    JsonParser.parseString(jsonStr).asJsonObject
                } catch (e: Exception) {
                    Log.e("Error:", "Failed to decode mresult base64: $e")
                    return
                }
            }
            else -> return
        }

        val commonKeys = siteUrls.keySet().intersect(decodedMresult.keySet())

        for (key in commonKeys) {
            val base = siteUrls[key]?.asString?.trimEnd('/') ?: continue
            val path = decodedMresult[key]?.asString?.trimStart('/') ?: continue
            val fullUrl = "$base/$path"

            val friendlyName = siteFriendlyNames?.get(key)?.asString ?: key

            try {
                when (friendlyName) {
                    "EarnVids" -> {
                        VidhideExtractor().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    "StreamHG" -> {
                        VidHidePro().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    "RpmShare", "UpnShare", "StreamP2p" -> {
                        VidStack().getUrl(fullUrl, referer, subtitleCallback, callback)
                    }
                    else -> {
                        loadExtractor(fullUrl, referer ?: mainUrl, subtitleCallback, callback)
                    }
                }
            } catch (_: Exception) {
                Log.e("Error:", "Failed to extract from $friendlyName at $fullUrl")
                continue
            }
        }

    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let { "${it.scheme}://${it.host}" }
    }
}

class MegaUp : ExtractorApi() {
    override var name = "MegaUp"
    override var mainUrl = "https://megaup.live"
    override val requiresReferer = true

    companion object {
        private val HEADERS = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
                "Accept" to "text/html, *//*; q=0.01",
                "Accept-Language" to "en-US,en;q=0.5",
                "Sec-GPC" to "1",
                "Sec-Fetch-Dest" to "empty",
                "Sec-Fetch-Mode" to "cors",
                "Sec-Fetch-Site" to "same-origin",
                "Priority" to "u=0",
                "Pragma" to "no-cache",
                "Cache-Control" to "no-cache",
                "referer" to "https://animekai.to/",
        )
    }

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        val mediaUrl = url.replace("/e/", "/media/").replace("/e2/", "/media/")
        val displayName = referer ?: this.name
        val encodedResult = app.get(mediaUrl, headers = HEADERS)
        .parsedSafe<AnimeKaiResponse>()
        ?.result

        if (encodedResult == null) return

        val body = """
        {
        "text": "$encodedResult",
        "agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
        }
        """.trimIndent()
            .trim()
            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        val m3u8Data=app.post(BuildConfig.KAIDEC, requestBody = body).text

        if (m3u8Data.isBlank()) {
            Log.d("Phisher", "Encoded result is null or empty")
            return
        }

        try {
            val root = JSONObject(m3u8Data)
            val result = root.optJSONObject("result")
            if (result == null) {
                Log.d("Error:", "No 'result' object in M3U8 JSON")
                return
            }

            val sources = result.optJSONArray("sources") ?: JSONArray()
            if (sources.length() > 0) {
                val firstSourceObj = sources.optJSONObject(0)
                val m3u8File = when {
                    firstSourceObj != null -> firstSourceObj.optString("file").takeIf { it.isNotBlank() }
                    else -> {
                        val maybeString = sources.optString(0)
                        maybeString.takeIf { it.isNotBlank() }
                    }
                }
                if (m3u8File != null) {
                    generateM3u8(displayName, m3u8File, mainUrl).forEach(callback)
                } else {
                    Log.d("Error:", "No 'file' found in first source")
                }
            } else {
                Log.d("Error:", "No sources found in M3U8 data")
            }

            val tracks = result.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until tracks.length()) {
                val trackObj = tracks.optJSONObject(i) ?: continue
                val label = trackObj.optString("label").trim().takeIf { it.isNotEmpty() }
                val file = trackObj.optString("file").takeIf { it.isNotBlank() }
                if (label != null && file != null) {
                    subtitleCallback(SubtitleFile(label, file))
                }
            }
        } catch (_: JSONException) {
            Log.e("Error", "Failed to parse M3U8 JSON")
        }
      }

    data class AnimeKaiResponse(
        @JsonProperty("status") val status: Int,
        @JsonProperty("result") val result: String
    )
}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
open class GDFlix : ExtractorApi() {
    override val name = "GDFlix"
    override val mainUrl = "https://new6.gdflix.dad"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        source: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val newUrl = try {
            app.get(url)
                .document
                .selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")
        } catch (e: Exception) {
            Log.e("Error", "Failed to fetch redirect: ${e.localizedMessage}")
            return
        } ?: url

        val document = app.get(newUrl).document
        val fileName = document.select("ul > li.list-group-item:contains(Name)").text()
            .substringAfter("Name : ")
        val fileSize = document.select("ul > li.list-group-item:contains(Size)").text()
            .substringAfter("Size : ")

        document.select("div.text-center a").amap { anchor ->
            val text = anchor.select("a").text()

            when {
                text.contains("DIRECT DL",ignoreCase = true) -> {
                    val link = anchor.attr("href")
                    callback.invoke(
                        newExtractorLink("$source GDFlix[Direct]", "$source GDFlix[Direct] [$fileSize]", link) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("Index Links",ignoreCase = true) -> {
                    try {
                        val link = anchor.attr("href")
                        app.get("https://new6.gdflix.dad$link").document
                            .select("a.btn.btn-outline-info").amap { btn ->
                                val serverUrl = "https://new6.gdflix.dad" + btn.attr("href")
                                app.get(serverUrl).document
                                    .select("div.mb-4 > a").amap { sourceAnchor ->
                                        val sourceurl = sourceAnchor.attr("href")
                                        callback.invoke(
                                            newExtractorLink("$source GDFlix[Index]", "$source GDFlix[Index] [$fileSize]", sourceurl) {
                                                this.quality = getIndexQuality(fileName)
                                            }
                                        )
                                    }
                            }
                    } catch (e: Exception) {
                        Log.d("Index Links", e.toString())
                    }
                }

                text.contains("DRIVEBOT",ignoreCase = true) -> {
                    try {
                        val driveLink = anchor.attr("href")
                        val id = driveLink.substringAfter("id=").substringBefore("&")
                        val doId = driveLink.substringAfter("do=").substringBefore("==")
                        val baseUrls = listOf("https://drivebot.sbs", "https://drivebot.cfd")

                        baseUrls.amap { baseUrl ->
                            val indexbotLink = "$baseUrl/download?id=$id&do=$doId"
                            val indexbotResponse = app.get(indexbotLink, timeout = 100L)

                            if (indexbotResponse.isSuccessful) {
                                val cookiesSSID = indexbotResponse.cookies["PHPSESSID"]
                                val indexbotDoc = indexbotResponse.document

                                val token = Regex("""formData\.append\('token', '([a-f0-9]+)'\)""")
                                    .find(indexbotDoc.toString())?.groupValues?.get(1).orEmpty()

                                val postId = Regex("""fetch\('/download\?id=([a-zA-Z0-9/+]+)'""")
                                    .find(indexbotDoc.toString())?.groupValues?.get(1).orEmpty()

                                val requestBody = FormBody.Builder()
                                    .add("token", token)
                                    .build()

                                val headers = mapOf("Referer" to indexbotLink)
                                val cookies = mapOf("PHPSESSID" to "$cookiesSSID")

                                val downloadLink = app.post(
                                    "$baseUrl/download?id=$postId",
                                    requestBody = requestBody,
                                    headers = headers,
                                    cookies = cookies,
                                    timeout = 100L
                                ).text.let {
                                    Regex("url\":\"(.*?)\"").find(it)?.groupValues?.get(1)?.replace("\\", "").orEmpty()
                                }

                                callback.invoke(
                                    newExtractorLink("$source GDFlix[DriveBot]", "$source GDFlix[DriveBot] [$fileSize]", downloadLink) {
                                        this.referer = baseUrl
                                        this.quality = getIndexQuality(fileName)
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("DriveBot", e.toString())
                    }
                }

                text.contains("Instant DL",ignoreCase = true) -> {
                    try {
                        val instantLink = anchor.attr("href")
                        val link = app.get(instantLink, allowRedirects = false)
                            .headers["location"]?.substringAfter("url=").orEmpty()

                        callback.invoke(
                            newExtractorLink("$source GDFlix[Instant Download]", "$source GDFlix[Instant Download] [$fileSize]", link) {
                                this.quality = getIndexQuality(fileName)
                            }
                        )
                    } catch (e: Exception) {
                        Log.d("Instant DL", e.toString())
                    }
                }

                text.contains("CLOUD DOWNLOAD",ignoreCase = true) -> {
                    callback.invoke(
                        newExtractorLink("$source GDFlix[CLOUD]", "$source GDFlix[CLOUD] [$fileSize]", anchor.attr("href")) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }

                text.contains("GoFile",ignoreCase = true) -> {
                    try {
                        app.get(anchor.attr("href")).document
                            .select(".row .row a").amap { gofileAnchor ->
                                val link = gofileAnchor.attr("href")
                                if (link.contains("gofile")) {
                                    Gofile().getUrl(link, "", subtitleCallback, callback)
                                }
                            }
                    } catch (e: Exception) {
                        Log.d("Gofile", e.toString())
                    }
                }

                text.contains("PixelDrain",ignoreCase = true) || text.contains("Pixel",ignoreCase = true)-> {
                    callback.invoke(
                        newExtractorLink(
                            "$source GDFlix[Pixeldrain]",
                            "$source GDFlix[Pixeldrain] [$fileSize]",
                            anchor.attr("href"),
                        ) { this.quality = quality }
                    )
                }

                else -> {
                    Log.d("Error", "No Server matched")
                }
            }
        }

        // Cloudflare backup links
        try {
            val types = listOf("type=1", "type=2")
            types.map { type ->
                val sourceurl = app.get("${newUrl.replace("file", "wfile")}?$type")
                    .document.select("a.btn-success").attr("href")

                if (source?.isNotEmpty() == true) {
                    callback.invoke(
                        newExtractorLink("$source GDFlix[CF]", "$source GDFlix[CF] [$fileSize]", sourceurl) {
                            this.quality = getIndexQuality(fileName)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.d("CF", e.toString())
        }
    }
}


class Gofile : ExtractorApi() {
    override val name = "Gofile"
    override val mainUrl = "https://gofile.io"
    override val requiresReferer = false
    private val mainApi = "https://api.gofile.io"

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {

        try {
            val id = Regex("/(?:\\?c=|d/)([\\da-zA-Z-]+)").find(url)?.groupValues?.get(1) ?: return
            val responseText = app.post("$mainApi/accounts").text
            val json = JSONObject(responseText)
            val token = json.getJSONObject("data").getString("token")

            val globalJs = app.get("$mainUrl/dist/js/global.js").text
            val wt = Regex("""appdata\.wt\s*=\s*["']([^"']+)["']""")
                .find(globalJs)?.groupValues?.getOrNull(1) ?: return

            val responseTextfile = app.get(
                "$mainApi/contents/$id?wt=$wt",
                headers = mapOf("Authorization" to "Bearer $token")
            ).text

            val fileDataJson = JSONObject(responseTextfile)

            val data = fileDataJson.getJSONObject("data")
            val children = data.getJSONObject("children")
            val firstFileId = children.keys().asSequence().first()
            val fileObj = children.getJSONObject(firstFileId)

            val link = fileObj.getString("link")
            val fileName = fileObj.getString("name")
            val fileSize = fileObj.getLong("size")

            val sizeFormatted = if (fileSize < 1024L * 1024 * 1024) {
                "%.2f MB".format(fileSize / 1024.0 / 1024)
            } else {
                "%.2f GB".format(fileSize / 1024.0 / 1024 / 1024)
            }

            callback.invoke(
                newExtractorLink(
                    "Gofile",
                    "Gofile [$sizeFormatted]",
                    link
                ) {
                    this.quality = getQuality(fileName)
                    this.headers = mapOf("Cookie" to "accountToken=$token")
                }
            )
        } catch (e: Exception) {
            Log.e("Gofile", "Error occurred: ${e.message}")
        }
    }

    private fun getQuality(fileName: String?): Int {
        return Regex("(\\d{3,4})[pP]").find(fileName ?: "")?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Qualities.Unknown.value
    }
}


class UqloadsXyz : ExtractorApi() {
    override val name = "Uqloadsxyz"
    override val mainUrl = "https://uqloads.xyz"
    override val requiresReferer = true

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var response = app.get(url.replace("/download/", "/e/"), referer = referer)
        val iframe = response.document.selectFirst("iframe")
        if (iframe != null) {
            response = app.get(
                iframe.attr("src"), headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ), referer = response.url
            )
        }

        val script = if (!getPacked(response.text).isNullOrEmpty()) {
            getAndUnpack(response.text)
        } else {
            response.document.selectFirst("script:containsData(sources:)")?.data()
        } ?: return
        val regex = Regex("""hls2":"(?<hls2>[^"]+)"|hls4":"(?<hls4>[^"]+)"""")
        val links = regex.findAll(script)
            .mapNotNull { matchResult ->
                val hls2 = matchResult.groups["hls2"]?.value
                val hls4 = matchResult.groups["hls4"]?.value
                when {
                    hls2 != null -> hls2
                    hls4 != null -> "https://uqloads.xyz$hls4"
                    else -> null
                }
            }.toList()
        links.forEach { m3u8->
            generateM3u8(
                name,
                m3u8,
                mainUrl
            ).forEach(callback)
        }

    }
}

open class Megacloud : ExtractorApi() {
    override val name = "Megacloud"
    override val mainUrl = "https://megacloud.blog"
    override val requiresReferer = false

    @SuppressLint("NewApi")
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val mainheaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
            "Accept" to "*/*",
            "Accept-Language" to "en-US,en;q=0.5",
            "Accept-Encoding" to "gzip, deflate, br, zstd",
            "Origin" to "$mainUrl/",
            "Referer" to "$mainUrl/",
            "Connection" to "keep-alive",
            "Pragma" to "no-cache",
            "Cache-Control" to "no-cache"
        )
        try {
            // --- Primary API Method ---
            val headers = mapOf(
                "Accept" to "*/*",
                "X-Requested-With" to "XMLHttpRequest",
                "Referer" to mainUrl
            )

            val id = url.substringAfterLast("/").substringBefore("?")
            val responsenonce = app.get(url, headers = headers).text

            val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responsenonce)
            val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""").find(responsenonce)
            val nonce = match1?.value ?: match2?.let { it.groupValues[1] + it.groupValues[2] + it.groupValues[3] }

            val apiUrl = "$mainUrl/embed-2/v3/e-1/getSources?id=$id&_k=$nonce"
            val gson = Gson()
            val response = try {
                val json = app.get(apiUrl, headers).text
                gson.fromJson(json, MegacloudResponse::class.java)
            } catch (_: Exception) {
                null
            }

            val encoded = response?.sources?.firstOrNull()?.file
                ?: throw Exception("No sources found")
            val key = try {
                val keyJson = app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json").text
                gson.fromJson(keyJson, Megakey::class.java)?.mega
            } catch (_: Exception) { null }

            val m3u8: String = if (".m3u8" in encoded) {
                encoded
            } else {
                val decodeUrl = "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"
                val fullUrl = "$decodeUrl?encrypted_data=${URLEncoder.encode(encoded, "UTF-8")}" +
                        "&nonce=${URLEncoder.encode(nonce, "UTF-8")}" +
                        "&secret=${URLEncoder.encode(key, "UTF-8")}"

                val decryptedResponse = app.get(fullUrl).text
                Regex("\"file\":\"(.*?)\"").find(decryptedResponse)?.groupValues?.get(1)
                    ?: throw Exception("Video URL not found in decrypted response")
            }

            generateM3u8(name, m3u8, mainUrl, headers = mainheaders).forEach(callback)

            response.tracks.forEach { track ->
                if (track.kind == "captions" || track.kind == "subtitles") {
                    subtitleCallback(SubtitleFile(track.label, track.file))
                }
            }

        } catch (e: Exception) {
            // --- Fallback using WebViewResolver ---
            Log.e("Megacloud", "Primary method failed, using fallback: ${e.message}")

            val jsToClickPlay = """
                (() => {
                    const btn = document.querySelector('.jw-icon-display.jw-button-color.jw-reset');
                    if (btn) { btn.click(); return "clicked"; }
                    return "button not found";
                })();
            """.trimIndent()

            val m3u8Resolver = WebViewResolver(
                interceptUrl = Regex("""\.m3u8"""),
                additionalUrls = listOf(Regex("""\.m3u8""")),
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("Megacloud", "JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L
            )

            val vttResolver = WebViewResolver(
                interceptUrl = Regex("""\.vtt"""),
                additionalUrls = listOf(Regex("""\.vtt""")),
                script = jsToClickPlay,
                scriptCallback = { result -> Log.d("Megacloud", "Subtitle JS Result: $result") },
                useOkhttp = false,
                timeout = 15_000L
            )

            try {
                val vttResponse = app.get(url = url, referer = mainUrl, interceptor = vttResolver)
                val subtitleUrls = listOf(vttResponse.url)
                    .filter { it.endsWith(".vtt") && !it.contains("thumbnails", ignoreCase = true) }
                subtitleUrls.forEachIndexed { _, subUrl ->
                    subtitleCallback(SubtitleFile("English", subUrl))
                }

                val fallbackM3u8 = app.get(url = url, referer = mainUrl, interceptor = m3u8Resolver).url
                generateM3u8(name, fallbackM3u8, mainUrl, headers = mainheaders).forEach(callback)

            } catch (ex: Exception) {
                Log.e("Megacloud", "Fallback also failed: ${ex.message}")
            }
        }
    }

    data class MegacloudResponse(
        val sources: List<Source>,
        val tracks: List<Track>,
        val encrypted: Boolean,
        val intro: Intro,
        val outro: Outro,
        val server: Long
    )

    data class Source(
        val file: String,
        val type: String
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val default: Boolean? = null
    )

    data class Intro(val start: Long, val end: Long)
    data class Outro(val start: Long, val end: Long)
    data class Megakey(val rabbit: String, val mega: String)
}



class Cdnstreame : ExtractorApi() {
    override val name = "Cdnstreame"
    override val mainUrl = "https://cdnstreame.net"
    override val requiresReferer = false

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl,
            "User-Agent" to USER_AGENT
        )

        val id = url.substringAfterLast("/").substringBefore("?")
        val apiUrl = "$mainUrl/embed-1/v2/e-1/getSources?id=$id"

        val response = app.get(apiUrl, headers = headers)
            .parsedSafe<MegacloudResponse>() ?: return

        val key = app.get("https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json")
            .parsedSafe<Megakey>()?.rabbit ?: return

        val decryptedJson = decryptOpenSSL(response.sources, key)
        val m3u8Url = parseSourceJson(decryptedJson).firstOrNull()?.file ?: return

        val m3u8Headers = mapOf("Referer" to mainUrl, "Origin" to mainUrl)
        generateM3u8(name, m3u8Url, mainUrl, headers = m3u8Headers).forEach(callback)

        response.tracks
            .filter { it.kind in listOf("captions", "subtitles") }
            .forEach { track ->
                subtitleCallback(SubtitleFile(track.label, track.file))
            }
    }

    data class MegacloudResponse(
        val sources: String,
        val tracks: List<MegacloudTrack>,
        val encrypted: Boolean,
        val intro: MegacloudIntro,
        val outro: MegacloudOutro,
        val server: Long
    )

    data class MegacloudTrack(val file: String, val label: String, val kind: String, val default: Boolean?)
    data class MegacloudIntro(val start: Long, val end: Long)
    data class MegacloudOutro(val start: Long, val end: Long)
    data class Megakey(val mega: String, val rabbit: String)
    data class Source2(val file: String, val type: String)

    private fun parseSourceJson(json: String): List<Source2> = runCatching {
        val jsonArray = JSONArray(json)
        List(jsonArray.length()) {
            val obj = jsonArray.getJSONObject(it)
            Source2(obj.getString("file"), obj.getString("type"))
        }
    }.getOrElse {
        Log.e("parseSourceJson", "Failed to parse JSON: ${it.message}")
        emptyList()
    }

    private fun opensslKeyIv(password: ByteArray, salt: ByteArray, keyLen: Int = 32, ivLen: Int = 16): Pair<ByteArray, ByteArray> {
        var d = ByteArray(0)
        var d_i = ByteArray(0)
        while (d.size < keyLen + ivLen) {
            d_i = MessageDigest.getInstance("MD5").digest(d_i + password + salt)
            d += d_i
        }
        return d.copyOfRange(0, keyLen) to d.copyOfRange(keyLen, keyLen + ivLen)
    }

    @SuppressLint("NewApi")
    private fun decryptOpenSSL(encBase64: String, password: String): String {
        return runCatching {
            val data = Base64.getDecoder().decode(encBase64)
            require(data.copyOfRange(0, 8).contentEquals("Salted__".toByteArray()))
            val salt = data.copyOfRange(8, 16)
            val (key, iv) = opensslKeyIv(password.toByteArray(), salt)

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            }

            String(cipher.doFinal(data.copyOfRange(16, data.size)))
        }.getOrElse {
            Log.e("decryptOpenSSL", "Decryption failed: ${it.message}")
            ""
        }
    }
}

class Streameeeeee : Megacloud() {
    override var mainUrl = "https://streameeeeee.site"
    override val requiresReferer = true

}

class Videostr : ExtractorApi() {
    override val name = "Videostr"
    override val mainUrl = "https://videostr.net"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Accept" to "*/*",
            "X-Requested-With" to "XMLHttpRequest",
            "Referer" to mainUrl
        )

        val id = url.substringAfterLast("/").substringBefore("?")
        val responsenonce = app.get(url, headers = headers).text
        val match1 = Regex("""\b[a-zA-Z0-9]{48}\b""").find(responsenonce)
        val match2 = Regex("""\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b.*?\b([a-zA-Z0-9]{16})\b""")
            .find(responsenonce)

        val nonce = match1?.value ?: match2?.let {
            it.groupValues[1] + it.groupValues[2] + it.groupValues[3]
        } ?: throw Exception("Nonce not found")

        val apiUrl = "$mainUrl/embed-1/v3/e-1/getSources?id=$id&_k=$nonce"
        val gson = Gson()
        val response = try {
            val json = app.get(apiUrl, headers).text
            gson.fromJson(json, VideostrResponse::class.java)
        } catch (e: Exception) {
            throw Exception("Failed to parse VideostrResponse: ${e.message}")
        }

        Log.d("Videostr", "Parsed VideostrResponse: $response")

        val key = try {
            val keyJson = app.get(
                "https://raw.githubusercontent.com/yogesh-hacker/MegacloudKeys/refs/heads/main/keys.json"
            ).text
            gson.fromJson(keyJson, Megakey::class.java)?.vidstr
        } catch (e: Exception) {
            throw Exception("Failed to parse Megakey: ${e.message}")
        } ?: throw Exception("Decryption key not found")

        val encodedSource = response.sources.firstOrNull()?.file
            ?: throw Exception("No sources found in response")

        val m3u8: String = if (".m3u8" in encodedSource) {
            encodedSource
        } else {
            val decodeUrl =
                "https://script.google.com/macros/s/AKfycbxHbYHbrGMXYD2-bC-C43D3njIbU-wGiYQuJL61H4vyy6YVXkybMNNEPJNPPuZrD1gRVA/exec"

            val fullUrl = buildString {
                append(decodeUrl)
                append("?encrypted_data=").append(URLEncoder.encode(encodedSource, "UTF-8"))
                append("&nonce=").append(URLEncoder.encode(nonce, "UTF-8"))
                append("&secret=").append(URLEncoder.encode(key, "UTF-8"))
            }

            val decryptedResponse = app.get(fullUrl).text
            Regex("\"file\":\"(.*?)\"")
                .find(decryptedResponse)
                ?.groupValues?.get(1)
                ?: throw Exception("Video URL not found in decrypted response")
        }

        val m3u8headers = mapOf(
            "Referer" to "$mainUrl/",
            "Origin" to mainUrl
        )

        try {
            generateM3u8(name, m3u8, mainUrl, headers = m3u8headers).forEach(callback)
        } catch (e: Exception) {
            Log.e("Videostr", "Error generating M3U8: ${e.message}")
        }

        response.tracks.forEach { track ->
            if (track.kind == "captions" || track.kind == "subtitles") {
                subtitleCallback(
                    SubtitleFile(
                        track.label,
                        track.file
                    )
                )
            }
        }
    }

    data class VideostrResponse(
        val sources: List<VideostrSource>,
        val tracks: List<Track>,
        val encrypted: Boolean,
        @SerializedName("_f") val f: String,
        val server: Long,
    )

    data class VideostrSource(
        val file: String,
        val type: String,
    )

    data class Track(
        val file: String,
        val label: String,
        val kind: String,
        val s: String,
        val default: Boolean?,
    )

    data class Megakey(
        val rabbit: String,
        val mega: String,
        val vidstr: String
    )
}


class Hubdrive : ExtractorApi() {
    override val name = "Hubdrive"
    override val mainUrl = "https://hubdrive.fit"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val href=app.get(url).document.select(".btn.btn-primary.btn-user.btn-success1.m-1").attr("href")
        if (href.contains("hubcloud"))
        {
            HubCloud().getUrl(href,"HubDrive",subtitleCallback, callback)
        }
        else
            loadExtractor(href,"HubDrive",subtitleCallback, callback)
    }
}

class HUBCDN : ExtractorApi() {
    override val name = "HUBCDN"
    override val mainUrl = "https://hubcdn.fans"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(url).document
        val scriptText = doc.selectFirst("script:containsData(var reurl)")?.data()

        val encodedUrl = Regex("reurl\\s*=\\s*\"([^\"]+)\"")
            .find(scriptText ?: "")
            ?.groupValues?.get(1)
            ?.substringAfter("?r=")

        val decodedUrl = encodedUrl?.let { base64Decode(it) }?.substringAfterLast("link=")


        if (decodedUrl != null) {
            callback(
                newExtractorLink(
                    this.name,
                    this.name,
                    decodedUrl,
                    INFER_TYPE,
                )
                {
                    this.quality=Qualities.Unknown.value
                }
            )
        }
    }
}

class Hubstreamdad : Hblinks() {
    override var mainUrl = "https://hblinks.dad"
}

open class Hblinks : ExtractorApi() {
    override val name = "Hblinks"
    override val mainUrl = "https://hblinks.pro"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document

        document.select("h3 a,h5 a,div.entry-content p a").forEach {
            val link = it.absUrl("href").ifBlank { it.attr("href") }
            val lower = link.lowercase()

            when {
                "hubdrive" in lower -> Hubdrive().getUrl(link, name, subtitleCallback, callback)
                "hubcloud" in lower -> HubCloud().getUrl(link, name, subtitleCallback, callback)
                "hubcdn" in lower -> HUBCDN().getUrl(link, name, subtitleCallback, callback)
                else -> loadSourceNameExtractor(name, link, "", subtitleCallback, callback)
            }
        }
    }
}



internal class Molop : ExtractorApi() {
    override val name = "Molop"
    override val mainUrl = "https://molop.art"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers= mapOf("user-agent" to "okhttp/4.12.0")
        val res = app.get(url, referer = referer, headers = headers).document
        val sniffScript = res.selectFirst("script:containsData(sniff\\()")
            ?.data()
            ?.substringAfter("sniff(")
            ?.substringBefore(");") ?: return
        val cleaned = sniffScript.replace(Regex("\\[.*?]"), "")
        val regex = Regex("\"(.*?)\"")
        val args = regex.findAll(cleaned).map { it.groupValues[1].trim() }.toList()
        val token = args.lastOrNull().orEmpty()
        val m3u8 = "$mainUrl/m3u8/${args[1]}/${args[2]}/master.txt?s=1&cache=1&plt=$token"
        generateM3u8(name, m3u8, mainUrl, headers = headers).forEach(callback)
    }
}

class showflixupnshare : VidStack() {
    override var name: String = "VidStack"
    override var mainUrl: String = "https://showflix.upns.one"
}


class Rubyvidhub : VidhideExtractor() {
    override var mainUrl = "https://rubyvidhub.com"
}

class smoothpre : VidhideExtractor() {
    override var mainUrl = "https://smoothpre.com"
    override var requiresReferer = true
}


internal class Akirabox : ExtractorApi() {
    override val name = "Akirabox"
    override val mainUrl = "https://akirabox.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val id=url.substringAfter("$mainUrl/").substringBefore("/")
        val m3u8= app.post("$mainUrl/$id/file/generate", headers = mapOf("x-csrf-token" to "L57KI068FpaS5Ttgo1W20tQMlFhtEwCJGkOgIdSH")).parsedSafe<AkiraboxRes>()?.downloadLink
        if (m3u8!=null)
        {
            callback.invoke(
                newExtractorLink(
                    name,
                    name,
                    m3u8,
                    ExtractorLinkType.M3U8
                )
                {
                    this.referer=url
                    this.quality=Qualities.P1080.value
                    this.headers=headers

                }
            )
        }
    }

    data class AkiraboxRes(
        @JsonProperty("download_link")
        val downloadLink: String,
    )

}

class BuzzServer : ExtractorApi() {
    override val name = "BuzzServer"
    override val mainUrl = "https://buzzheavier.com"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        try {
            val qualityText = app.get(url).document.selectFirst("div.max-w-2xl > span")?.text()
            val quality = getQualityFromName(qualityText)
            val response = app.get("$url/download", referer = url, allowRedirects = false)
            val redirectUrl = response.headers["hx-redirect"] ?: ""

            if (redirectUrl.isNotEmpty()) {
                callback.invoke(
                    newExtractorLink(
                        "BuzzServer",
                        "BuzzServer",
                        redirectUrl,
                    ) {
                        this.quality = quality
                    }
                )
            } else {
                Log.w("BuzzServer", "No redirect URL found in headers.")
            }
        } catch (e: Exception) {
            Log.e("BuzzServer", "Exception occurred: ${e.message}")
        }
    }
}


class FilemoonV2 : ExtractorApi() {
    override var name = "Filemoon"
    override var mainUrl = "https://filemoon.to"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val headers = mapOf(
            "Referer" to url,
            "Sec-Fetch-Dest" to "iframe",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "cross-site",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:137.0) Gecko/20100101 Firefox/137.0"
        )


        val href = app.get(url,headers).document.selectFirst("iframe")?.attr("src") ?: ""
        val scriptContent = app.get(
            href,
            headers = mapOf("Accept-Language" to "en-US,en;q=0.5", "sec-fetch-dest" to "iframe")
        ).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()

        val m3u8 = JsUnpacker(scriptContent).unpack()?.let { unpacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unpacked)?.groupValues?.get(1)
        }

        if (m3u8 != null) {
            generateM3u8(
                name,
                m3u8,
                mainUrl,
                headers = headers
            ).forEach(callback)
        } else {
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val m3u82 = app.get(
                href,
                referer = referer,
                interceptor = resolver
            ).url

            if (m3u82.isNotEmpty()) {
                generateM3u8(
                    name,
                    m3u82,
                    mainUrl,
                    headers = headers
                ).forEach(callback)
            } else {
                Log.d("Error", "No m3u8 intercepted in fallback.")
            }
        }
    }
}

class StreamwishHG : StreamWishExtractor() {
    override val mainUrl = "https://hglink.to"
}



class Vidora : ExtractorApi() {
    override val name = "Vidora"
    override val mainUrl = "https://vidora.stream"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = url.replace("/download/", "/e/")
        var pageResponse = app.get(embedUrl, referer = referer)

        val iframeElement = pageResponse.document.selectFirst("iframe")
        if (iframeElement != null) {
            val iframeUrl = iframeElement.attr("src")
            pageResponse = app.get(
                iframeUrl,
                headers = mapOf(
                    "Accept-Language" to "en-US,en;q=0.5",
                    "Sec-Fetch-Dest" to "iframe"
                ),
                referer = pageResponse.url
            )
        }
        val headers= mapOf("origin" to mainUrl)
        val scriptData = if (!getPacked(pageResponse.text).isNullOrEmpty()) {
            getAndUnpack(pageResponse.text)
        } else {
            pageResponse.document.selectFirst("script:containsData(sources:)")?.data()
        }

        val m3u8Url = scriptData?.let {
            Regex("""file:\s*"(.*?m3u8.*?)"""").find(it)?.groupValues?.getOrNull(1)
        }

        if (!m3u8Url.isNullOrEmpty()) {
            generateM3u8(
                name,
                m3u8Url,
                mainUrl,
                headers=headers
            ).forEach(callback)
        } else {
            // Fallback using WebViewResolver
            val resolver = WebViewResolver(
                interceptUrl = Regex("""(m3u8|master\.txt)"""),
                additionalUrls = listOf(Regex("""(m3u8|master\.txt)""")),
                useOkhttp = false,
                timeout = 15_000L
            )

            val interceptedUrl = app.get(
                url = pageResponse.url,
                referer = referer,
                interceptor = resolver
            ).url

            if (interceptedUrl.isNotEmpty()) {
                generateM3u8(
                    name,
                    interceptedUrl,
                    mainUrl
                ).forEach(callback)
            } else {
                Log.d("Filesim", "No m3u8 found via script or WebView fallback.")
            }
        }
    }
}