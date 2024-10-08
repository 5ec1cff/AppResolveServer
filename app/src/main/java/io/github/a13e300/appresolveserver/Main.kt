package io.github.a13e300.appresolveserver

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.pm.PackageManagerHidden
import android.content.pm.UserManagerHidden
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Looper
import android.os.UserManager
import android.system.Os
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Log
import com.google.gson.Gson
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.HelpFormatter
import org.apache.commons.cli.Option
import org.apache.commons.cli.Options
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.util.concurrent.Executors
import kotlin.system.exitProcess

const val TAG = "AppResolverServer"

data class PackageInfoWithUser(
    val info: PackageInfo,
    val user: MutableList<Int> = mutableListOf()
)

data class Request(
    val requirePackages: Boolean = true,
    val requireUsers: Boolean = true,
    val requireLabels: Boolean = true,
    val requireIcons: Boolean = false,
    val filterSystem: Boolean = false,
    val filterNoComponents: Boolean = true, // default: true
    val packageNames: List<String> = emptyList(), // empty meaning all
    val users: List<Int> = emptyList(), // empty meaning all
    val iconW: Int = -1,
    val iconH: Int = -1
)

data class PackageItem(
    val packageName: String,
    val users: List<Int>,
    val label: String?,
    val icon: String?, // icon data url
    val appId: Int, // uid % 100000
    val lastUpdate: Long
)

data class UserItem(
    val id: Int,
    val name: String
)

data class Response(
    val packages: MutableList<PackageItem> = mutableListOf(),
    val users: MutableList<UserItem> = mutableListOf()
)

val executor = Executors.newCachedThreadPool()
val gson = Gson()

@SuppressLint("StaticFieldLeak")
lateinit var context: Context
lateinit var userManager: UserManager
lateinit var packageManager: PackageManager

var onlyRoot = true
var useSystem = true

fun e(msg: String, t: Throwable? = null) {
    println(msg)
    t?.printStackTrace()
    if (t == null) {
        Log.e(TAG, msg)
    } else {
        Log.e(TAG, msg, t)
    }
}

fun d(msg: String) {
    println(msg)
    Log.d(TAG, msg)
}

fun handleClient(socket: LocalSocket) = socket.use {
    if (onlyRoot && socket.peerCredentials.uid != 0) {
        return
    }

    val request = socket.inputStream.readBytes().decodeToString()
        .let { gson.fromJson(it, Request::class.java) }
    println(request)

    val response = Response()

    val flags = if (request.filterNoComponents)
        PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS
    else 0

    val um = userManager as UserManagerHidden
    val packages = mutableMapOf<String, PackageInfoWithUser>()
    um.users.let {
        if (request.users.isNotEmpty()) {
            it.filter { u -> u.id in request.users }
        } else it
    }.forEach { u ->
        if (request.requireUsers) {
            response.users.add(UserItem(u.id, u.name ?: ""))
        }
        if (request.requirePackages) {
            if (request.packageNames.isNotEmpty()) {
                request.packageNames.forEach { name ->
                    try {
                        val pi = (packageManager as PackageManagerHidden).getPackageInfoAsUser(
                            name,
                            flags,
                            u.id
                        )
                        (packages[pi.packageName] ?: PackageInfoWithUser(pi).also {
                            packages[pi.packageName] = it
                        }).also {
                            it.user.add(u.id)
                        }
                    } catch (ignore: NameNotFoundException) {

                    }
                }
            } else {
                (packageManager as PackageManagerHidden).getInstalledPackagesAsUser(flags, u.id)
                    .forEach { pi ->
                        (packages[pi.packageName] ?: PackageInfoWithUser(pi).also {
                            packages[pi.packageName] = it
                        }).also {
                            it.user.add(u.id)
                        }
                    }
            }
        }
    }

    if (request.requirePackages) {
        packages.forEach { (_, info) ->
            val pi = info.info
            if (request.packageNames.isNotEmpty() && pi.packageName !in request.packageNames) return@forEach
            val ai = pi.applicationInfo ?: return@forEach
            if (request.filterSystem && ai.flags.and(ApplicationInfo.FLAG_SYSTEM) != 0) return@forEach
            if (request.filterNoComponents &&
                !(pi.activities?.isNotEmpty() == true
                        || pi.services?.isNotEmpty() == true
                        || pi.receivers?.isNotEmpty() == true
                        || pi.providers?.isNotEmpty() == true)
            ) return@forEach

            val label = if (request.requireLabels) ai.loadLabel(packageManager).toString() else ""

            val icon = if (request.requireIcons) {
                val out = ByteArrayOutputStream()
                val b64out = Base64OutputStream(out, Base64.NO_WRAP)
                ai.loadIcon(packageManager).toBitmap(request.iconW, request.iconH)
                    .compress(Bitmap.CompressFormat.PNG, 100, b64out)
                "data:image/png;base64," + out.toString("UTF-8")
            } else ""

            response.packages.add(
                PackageItem(
                    pi.packageName,
                    info.user,
                    label,
                    icon,
                    ai.uid % 100000,
                    pi.lastUpdateTime
                )
            )
        }
    }

    val pw = PrintWriter(socket.outputStream)
    val gpw = gson.newJsonWriter(pw)
    gson.toJson(response, Response::class.java, gpw)
    gpw.close()
}

fun server(name: String) {
    val serverSock = LocalServerSocket(name)
    while (true) {
        val client = serverSock.accept()
        executor.submit {
            runCatching {
                handleClient(client)
            }.onFailure {
                e("handleClient: ", it)
            }
        }
    }
}

@SuppressLint("QueryPermissionsNeeded")
fun main(args: Array<String>) {
    val options = Options().apply {
        addOption(
            Option.builder("n").longOpt("name").desc("socket name (required)")
                .hasArg(true)
                .required()
                .build()
        )
        addOption(
            Option.builder("r").longOpt("require-root")
                .desc("only allow uid 0 (true|false, default = true)")
                .hasArg(true).type(Boolean::class.java).optionalArg(true).build()
        )
        addOption(
            Option.builder("s").longOpt("use-system")
                .desc("run package resolver in uid 1000 if current is uid 0 (true|false, default = true)")
                .hasArg(true).type(Boolean::class.java).optionalArg(true).build()
        )
    }
    val parser = DefaultParser()
    val helpFormatter = HelpFormatter()
    val cmd = runCatching { parser.parse(options, args) }.onFailure {
        helpFormatter.printHelp("ars", options)
        exitProcess(1)
    }.getOrNull()!!
    val rr = cmd.getOptionValue("r", "true")
    if (rr != "true" && rr != "false") {
        helpFormatter.printHelp("ars", options)
        exitProcess(1)
    }
    onlyRoot = rr == "true"
    val s = cmd.getOptionValue("s", "true")
    if (s != "true" && s != "false") {
        helpFormatter.printHelp("ars", options)
        exitProcess(1)
    }
    useSystem = s == "true"

    if (useSystem && Os.geteuid() == 0) {
        d("main: switch to uid 1000")
        runCatching { Os.seteuid(1000) }.onFailure { e("setuid 1000", it) }
    }

    Looper.prepareMainLooper()
    val at = ActivityThread.systemMain()
    val context = at.systemContext as Context
    userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    packageManager = context.packageManager


    server(cmd.getOptionValue("n"))
}

fun Drawable.toBitmap(w: Int = -1, h: Int = -1): Bitmap {
    val drawable = this

    val realW = if (w <= 0) if (drawable.intrinsicWidth <= 0) 1 else drawable.intrinsicWidth else w
    val realH = if (h <= 0) if (drawable.intrinsicHeight <= 0) 1 else drawable.intrinsicHeight else h

    val bitmap = Bitmap.createBitmap(realW, realH, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, realW, realH)
    drawable.draw(canvas)
    return bitmap
}
