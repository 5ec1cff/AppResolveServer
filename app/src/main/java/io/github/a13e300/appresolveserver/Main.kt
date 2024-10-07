package io.github.a13e300.appresolveserver

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.UserManagerHidden
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Looper
import android.os.UserManager
import android.util.Base64
import android.util.Base64OutputStream
import java.io.ByteArrayOutputStream

data class PackageInfoWithUser(
    val info: PackageInfo,
    val user: MutableList<Int> = mutableListOf()
)

@SuppressLint("QueryPermissionsNeeded")
fun main(args: Array<String>) {
    Looper.prepareMainLooper()
    val at = ActivityThread.systemMain()
    val ctx = at.systemContext as Context
    val um = (ctx.getSystemService(Context.USER_SERVICE) as UserManager) as UserManagerHidden
    val pms = ctx.packageManager
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS or PackageManager.GET_RECEIVERS
    val packages = mutableMapOf<String, PackageInfoWithUser>()
    um.users.also {
        println("<p>${it.size} Users</p>")
        println("<p>${it.joinToString(",")}</p>")
    }.forEach { u ->
        (pms as PackageManagerHidden).getInstalledPackagesAsUser(flags, u.id).forEach { pi ->
            (packages[pi.packageName] ?: PackageInfoWithUser(pi).also { packages[pi.packageName] = it }).also {
                it.user.add(u.id)
            }
        }
    }

    packages.values.filter {
        val pi = it.info
        pi.activities?.isNotEmpty() == true
                || pi.services?.isNotEmpty() == true
                || pi.receivers?.isNotEmpty() == true
                || pi.providers?.isNotEmpty() == true
    }.sortedByDescending { x -> x.info.lastUpdateTime }.also {
        println("<p>count ${it.size}</p>")
        println("<table border><thead><tr><th>PackageName</th><th>AppId</th><th>Users</th><th>Label</th><th>Icon</th></tr></thead><tbody>")
    }.forEach {
        val pi = it.info
        val app = pi.applicationInfo!!
        print("<tr><th>${pi.packageName}</th><td>${app.uid}</td><td>${it.user.joinToString(",")}</td><td>${app.loadLabel(pms)}</td>")
        print("<td><img src=\"data:image/png;base64,")
        val out = ByteArrayOutputStream()
        val b64out = Base64OutputStream(out, Base64.NO_WRAP)
        app.loadIcon(pms).toBitmap(50, 50).compress(Bitmap.CompressFormat.PNG, 100, b64out)
        out.writeTo(System.out)
        print("\"></td></tr>")
    }
    println("</tbody></table>")
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
