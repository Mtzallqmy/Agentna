package com.mtzallqmy.agentna.runtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.net.URI
import java.util.concurrent.TimeUnit

sealed interface ToolResult {
    data class Success(val output: String) : ToolResult
    data class Failure(val error: String) : ToolResult
    data class RequiresApproval(val reason: String, val riskLevel: String = "high") : ToolResult
}

class LocalToolRegistry(private val context: Context) {
    private val workspace = File(context.filesDir, "workspace").apply { mkdirs() }.canonicalFile

    private val safeDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isEmpty() || addresses.any(::isForbiddenAddress)) {
                throw java.net.UnknownHostException("Private or local network addresses are blocked")
            }
            return addresses
        }
    }

    private val webClient = OkHttpClient.Builder()
        .dns(safeDns)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun toolInstructions(filesystemAllowed: Boolean, networkAllowed: Boolean): String = buildString {
        appendLine("Available local Android tools:")
        if (filesystemAllowed) {
            appendLine("1. workspace.list {\"path\":\"optional/relative/path\"}")
            appendLine("2. workspace.read {\"path\":\"relative/file.txt\"}")
            appendLine("3. workspace.write {\"path\":\"relative/file.txt\",\"content\":\"...\"}")
            appendLine("4. workspace.delete {\"path\":\"relative/file.txt\"} [approval required]")
        }
        if (networkAllowed) {
            appendLine("5. web.fetch {\"url\":\"https://...\"} [public HTTPS only]")
            appendLine("6. device.open_url {\"url\":\"https://...\"} [approval required]")
        }
        appendLine("7. device.info {}")
        appendLine()
        appendLine("Tool-call protocol: return ONLY one JSON object, with no markdown fence.")
        appendLine("To call a tool: {\"type\":\"tool\",\"tool\":\"workspace.read\",\"arguments\":{\"path\":\"notes.txt\"}}")
        appendLine("To answer: {\"type\":\"final\",\"content\":\"your final response\"}")
        appendLine("Never claim a tool ran unless you received its real result.")
        appendLine("There is no shell/container/browser automation tool.")
    }

    fun execute(tool: String, args: JSONObject, approved: Boolean = false): ToolResult = try {
        when (tool) {
            "workspace.list" -> listWorkspace(args.optString("path"))
            "workspace.read" -> readWorkspace(args.optString("path"))
            "workspace.write" -> writeWorkspace(args.optString("path"), args.optString("content"), approved)
            "workspace.delete" -> deleteWorkspace(args.optString("path"), approved)
            "web.fetch" -> fetchWeb(args.optString("url"))
            "device.info" -> deviceInfo()
            "device.open_url" -> openUrl(args.optString("url"), approved)
            else -> ToolResult.Failure("Unknown tool: $tool")
        }
    } catch (e: Exception) {
        ToolResult.Failure(e.message ?: "Tool failed")
    }

    fun listFiles(): List<File> = workspace.walkTopDown().filter { it.isFile }.take(MAX_LIST_ITEMS).toList()

    fun readFilePreview(file: File, maxChars: Int = 6000): String = runCatching {
        if (!file.canonicalFile.toPath().startsWith(workspace.toPath())) return@runCatching ""
        if (file.length() > MAX_FILE_BYTES) return@runCatching ""
        file.bufferedReader().use { it.readText().take(maxChars) }
    }.getOrDefault("")

    private fun listWorkspace(rawPath: String): ToolResult {
        val dir = safePath(rawPath.ifBlank { "." })
        if (!dir.exists()) return ToolResult.Failure("Path does not exist")
        if (!dir.isDirectory) return ToolResult.Failure("Path is not a directory")
        val entries = dir.listFiles().orEmpty().take(MAX_LIST_ITEMS).map { child ->
            val rel = child.relativeTo(workspace).invariantSeparatorsPath
            if (child.isDirectory) "$rel/" else "$rel (${child.length()} bytes)"
        }
        return ToolResult.Success(if (entries.isEmpty()) "Workspace directory is empty" else entries.joinToString("\n"))
    }

    private fun readWorkspace(rawPath: String): ToolResult {
        val file = safePath(rawPath)
        if (!file.isFile) return ToolResult.Failure("File does not exist")
        if (file.length() > MAX_FILE_BYTES) return ToolResult.Failure("File exceeds the ${MAX_FILE_BYTES / 1024} KB read limit")
        return ToolResult.Success(file.readText(Charsets.UTF_8))
    }

    private fun writeWorkspace(rawPath: String, content: String, approved: Boolean): ToolResult {
        if (rawPath.isBlank()) return ToolResult.Failure("path is required")
        if (content.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
            return ToolResult.Failure("Content exceeds the ${MAX_FILE_BYTES / 1024} KB write limit")
        }
        val file = safePath(rawPath)
        if (file.exists() && !approved) {
            return ToolResult.RequiresApproval(
                "Overwrite existing workspace file: ${file.relativeTo(workspace).invariantSeparatorsPath}",
                "medium"
            )
        }
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return ToolResult.Success("Saved ${file.relativeTo(workspace).invariantSeparatorsPath} (${file.length()} bytes)")
    }

    private fun deleteWorkspace(rawPath: String, approved: Boolean): ToolResult {
        val file = safePath(rawPath)
        if (file == workspace) return ToolResult.Failure("The workspace root cannot be deleted")
        if (!file.exists()) return ToolResult.Failure("Path does not exist")
        if (!approved) {
            return ToolResult.RequiresApproval("Permanently delete ${file.relativeTo(workspace).invariantSeparatorsPath}", "high")
        }
        val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
        return if (deleted) ToolResult.Success("Deleted ${file.name}") else ToolResult.Failure("Could not delete path")
    }

    private fun fetchWeb(rawUrl: String): ToolResult {
        val uri = validatePublicUrl(rawUrl)
        val request = Request.Builder()
            .url(uri.toString())
            .header("Accept", "text/plain,text/html,application/json,application/xml;q=0.9,*/*;q=0.2")
            .header("User-Agent", "Agentna/1.0.0 (Android; On-device Agent)")
            .get()
            .build()
        webClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ToolResult.Failure("HTTP ${response.code}")
            val contentType = response.header("Content-Type").orEmpty().lowercase()
            val allowed = contentType.startsWith("text/") || contentType.contains("json") ||
                contentType.contains("xml") || contentType.isBlank()
            if (!allowed) return ToolResult.Failure("Binary response blocked: ${contentType.take(80)}")
            val body = response.body ?: return ToolResult.Failure("Empty response")
            val bytes = body.source().readByteArray(MAX_FETCH_BYTES + 1L)
            if (bytes.size > MAX_FETCH_BYTES) return ToolResult.Failure("Response exceeds ${MAX_FETCH_BYTES / 1024} KB limit")
            return ToolResult.Success(
                "UNTRUSTED WEB CONTENT\nURL: ${response.request.url}\nHTTP: ${response.code}\n\n" +
                    bytes.toString(Charsets.UTF_8).take(MAX_FETCH_CHARS)
            )
        }
    }

    private fun deviceInfo(): ToolResult = ToolResult.Success(
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}); manufacturer=${Build.MANUFACTURER}; " +
            "model=${Build.MODEL}; workspace=${workspace.absolutePath}"
    )

    private fun openUrl(rawUrl: String, approved: Boolean): ToolResult {
        val uri = validatePublicUrl(rawUrl)
        if (!approved) return ToolResult.RequiresApproval("Open external URL in another app: $uri", "medium")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            ToolResult.Success("Opened $uri with the user's selected browser/app")
        } else {
            ToolResult.Failure("No app is available to open this URL")
        }
    }

    private fun validatePublicUrl(rawUrl: String): URI {
        require(rawUrl.isNotBlank()) { "url is required" }
        val uri = URI(rawUrl.trim())
        require(uri.scheme.equals("https", ignoreCase = true)) { "Only HTTPS URLs are allowed" }
        val host = uri.host?.lowercase() ?: throw IllegalArgumentException("URL host is invalid")
        require(host != "localhost" && !host.endsWith(".localhost")) { "Localhost is blocked" }
        require(uri.userInfo == null) { "URLs with embedded credentials are blocked" }
        require(uri.port == -1 || uri.port in 1..65535) { "URL port is invalid" }
        safeDns.lookup(host)
        return uri
    }

    private fun isForbiddenAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress || isCarrierGradeNat(address) ||
            isIpv6UniqueLocal(address) || isIpv4MappedPrivate(address)

    private fun isIpv6UniqueLocal(address: InetAddress): Boolean {
        val bytes = address.address
        return bytes.size == 16 && ((bytes[0].toInt() and 0xfe) == 0xfc)
    }

    private fun isIpv4MappedPrivate(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != 16) return false
        val mapped = bytes.sliceArray(0..9).all { it.toInt() == 0 } &&
            bytes[10].toInt() == 0xff && bytes[11].toInt() == 0xff
        if (!mapped) return false
        val v4 = InetAddress.getByAddress(bytes.copyOfRange(12, 16))
        return v4.isAnyLocalAddress || v4.isLoopbackAddress || v4.isLinkLocalAddress ||
            v4.isSiteLocalAddress || v4.isMulticastAddress || isCarrierGradeNat(v4)
    }

    private fun isCarrierGradeNat(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        return first == 100 && second in 64..127
    }

    private fun safePath(rawPath: String): File {
        val normalized = rawPath.trim().removePrefix("/").ifBlank { "." }
        require(!normalized.contains('\u0000')) { "Invalid path" }
        val target = File(workspace, normalized).canonicalFile
        require(target.path == workspace.path || target.path.startsWith(workspace.path + File.separator)) {
            "Path escapes app workspace"
        }
        return target
    }

    companion object {
        private const val MAX_FILE_BYTES = 512 * 1024
        private const val MAX_FETCH_BYTES = 768 * 1024
        private const val MAX_FETCH_CHARS = 120_000
        private const val MAX_LIST_ITEMS = 200
    }
}
