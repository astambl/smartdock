package cu.axel.smartdock.utils

import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.Notification
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.view.Display
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import cu.axel.smartdock.models.App
import cu.axel.smartdock.models.AppTask
import cu.axel.smartdock.models.DockApp
import cu.axel.smartdock.models.WINDOWING_MODE_FREEFORM
import cu.axel.smartdock.models.WINDOWING_MODE_FULLSCREEN
import cu.axel.smartdock.wrappers.ActivityManagerWrapper
import java.io.File
import kotlin.math.max

object AppUtils {
    const val PINNED_LIST = "pinned.lst"
    const val DOCK_PINNED_LIST = "dock_pinned.lst"
    const val DESKTOP_LIST = "desktop.lst"
    var currentApp = ""
    fun getInstalledPackages(context: Context): List<App> {
        val apps = ArrayList<App>()
        val packages = context.packageManager.getInstalledPackages(0)
        packages.forEach { packageInfo ->
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                apps.add(
                    App(
                        appInfo.loadLabel(context.packageManager).toString(),
                        appInfo.packageName,
                        appInfo.loadIcon(context.packageManager)
                    )
                )
            }
        }
        return apps.sortedWith(compareBy { it.name })
    }

    fun getInstalledApps(context: Context): ArrayList<App> {
        val apps = ArrayList<App>()
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        var appsInfo = mutableListOf<LauncherActivityInfo>()
        for (profile in userManager.userProfiles) appsInfo.addAll(
            launcherApps.getActivityList(
                null,
                profile
            )
        )

        appsInfo = appsInfo.sortedWith(compareBy { it.label.toString() }).toMutableList()

        //TODO: Filter Google App
        for (appInfo in appsInfo) {
            apps.add(
                App(
                    appInfo.label.toString(),
                    appInfo.componentName.packageName,
                    appInfo.getIcon(0),
                    appInfo.componentName,
                    appInfo.user
                )
            )
        }
        return apps
    }

    fun getPinnedApps(context: Context, type: String): ArrayList<App> {
        val file = File(context.filesDir, type)
        val apps = ArrayList<App>()
        val appsInfo = mutableListOf<LauncherActivityInfo>()
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        if (file.exists()) {
            for (line in file.readLines()) {
                if (line.isBlank() || line.startsWith("pair:")) continue
                val info = line.split(" ")
                val packageName = info[0]
                val userHandle = userManager.getUserForSerialNumber(info[1].toLong())
                val list = launcherApps.getActivityList(packageName, userHandle)
                if (list.isNullOrEmpty()) unpinApp(context, packageName, type)
                appsInfo.addAll(list)
            }
        }

        for (appInfo in appsInfo) {
            apps.add(
                App(
                    appInfo.label.toString(),
                    appInfo.componentName.packageName,
                    appInfo.getIcon(0),
                    appInfo.componentName,
                    appInfo.user
                )
            )
        }
        return apps
    }

    fun pinApp(context: Context, app: App, type: String) {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val file = File(context.filesDir, type)
        file.appendText("${app.packageName} ${userManager.getSerialNumberForUser(app.userHandle)}\n")
    }

    fun unpinApp(context: Context, packageName: String, type: String) {
        val file = File(context.filesDir, type)
        val updatedList = file.readLines().filter { it.split(" ")[0] != packageName }
        if (updatedList.isNotEmpty()) file.writeText(updatedList.joinToString("\n") + "\n")
        else {
            file.writeText("")
        }
    }

    fun moveApp(context: Context, app: App, type: String, direction: Int) {
        val file = File(context.filesDir, type)
        val lines = file.readLines().toMutableList()

        val lineIndex = lines.indexOfFirst { it.split(" ")[0] == app.packageName }

        if (lineIndex != -1) {
            if (direction == 0 && lineIndex > 0) {
                val line = lines.removeAt(lineIndex)
                lines.add(lineIndex - 1, line)
            } else if (direction == 1 && lineIndex < lines.size - 1) {
                val line = lines.removeAt(lineIndex)
                lines.add(lineIndex + 1, line)
            }

            file.writeText(lines.joinToString("\n") + "\n")
        }
    }

    fun isPinned(context: Context, app: App, type: String): Boolean {
        val file = File(context.filesDir, type)
        if (!file.exists()) return false
        file.readLines().forEach { line ->
            if (line.split(" ")[0] == app.packageName) return true
        }
        return false
    }

    fun isGame(packageManager: PackageManager, packageName: String): Boolean {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            info.category == ApplicationInfo.CATEGORY_GAME
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun getCurrentLauncher(packageManager: PackageManager): String {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo!!.activityInfo.packageName
    }

    fun getRunningTasks(
        activityManager: ActivityManager,
        iActivityManager: ActivityManagerWrapper? = null,
        packageManager: PackageManager,
        max: Int,
        launcherApps: LauncherApps? = null
    ): ArrayList<AppTask> {
        val tasksInfo =
            iActivityManager?.getRunningTasks(max) ?: activityManager.getRunningTasks(max)
        if (!tasksInfo.isNullOrEmpty())
            currentApp = tasksInfo.first().baseActivity!!.packageName
        val getWindowingModeMethod =
            ActivityManager.RunningTaskInfo::class.java.getMethod("getWindowingMode")
        val appTasks = ArrayList<AppTask>()
        for (taskInfo in tasksInfo) {
            try {
                //Exclude systemui, launcher and other system apps from the tasklist
                if (taskInfo.baseActivity!!.packageName.contains("com.android.systemui") || taskInfo.baseActivity!!.packageName.contains(
                        "com.google.android.packageinstaller"
                    ) || taskInfo.baseActivity!!.className == "com.android.quickstep.RecentsActivity"
                ) continue

                //Hack to save Dock settings activity from being excluded
                if (!(taskInfo.topActivity!!.className == "cu.axel.smartdock.activities.MainActivity" || taskInfo.topActivity!!.className == "cu.axel.smartdock.activities.DebugActivity") && taskInfo.topActivity!!.packageName == getCurrentLauncher(
                        packageManager
                    )
                ) continue

                if (!taskInfo.isRunning)
                    continue

                val windowingMode = getWindowingModeMethod.invoke(taskInfo) as Int
                val profileApp = findProfileActivity(taskInfo, launcherApps)
                    ?: if (isInstalled(packageManager, taskInfo)) null
                    else findInOtherProfiles(taskInfo, launcherApps)
                if (profileApp != null)
                    // App running in another profile (e.g. work profile)
                    appTasks.add(
                        AppTask(
                            taskInfo.id,
                            profileApp.label.toString(),
                            taskInfo.topActivity!!.packageName,
                            profileApp.getBadgedIcon(0),
                            windowingMode,
                            profileApp.componentName,
                            profileApp.user
                        )
                    )
                else
                    appTasks.add(
                        AppTask(
                            taskInfo.id,
                            packageManager.getActivityInfo(taskInfo.topActivity!!, 0)
                                .loadLabel(packageManager).toString(),
                            taskInfo.topActivity!!.packageName,
                            packageManager.getActivityIcon(taskInfo.topActivity!!),
                            windowingMode
                        )
                    )
            } catch (_: PackageManager.NameNotFoundException) {
            } catch (_: ReflectiveOperationException) {
            }
        }
        return appTasks
    }

    /** Returns the launcher entry if the task belongs to a profile other than ours. */
    private fun findProfileActivity(
        taskInfo: ActivityManager.RunningTaskInfo,
        launcherApps: LauncherApps?
    ): LauncherActivityInfo? {
        if (launcherApps == null) return null
        val userId = try {
            taskInfo.javaClass.getField("userId").getInt(taskInfo)
        } catch (_: ReflectiveOperationException) {
            return null
        }
        // UserHandle.hashCode() is the user id
        if (userId == Process.myUserHandle().hashCode()) return null
        val profile = launcherApps.profiles.firstOrNull { it.hashCode() == userId } ?: return null
        val packageName = taskInfo.topActivity?.packageName ?: return null
        return launcherApps.getActivityList(packageName, profile).firstOrNull()
    }

    private fun isInstalled(packageManager: PackageManager, taskInfo: ActivityManager.RunningTaskInfo) =
        try {
            packageManager.getActivityInfo(taskInfo.topActivity!!, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }

    /** Fallback when the task's user can't be read: app only exists in another profile. */
    private fun findInOtherProfiles(
        taskInfo: ActivityManager.RunningTaskInfo,
        launcherApps: LauncherApps?
    ): LauncherActivityInfo? {
        if (launcherApps == null) return null
        val packageName = taskInfo.topActivity?.packageName ?: return null
        return launcherApps.profiles
            .filter { it != Process.myUserHandle() }
            .firstNotNullOfOrNull { launcherApps.getActivityList(packageName, it).firstOrNull() }
    }

    fun getRecentTasks(context: Context, max: Int): ArrayList<AppTask> {
        val ignoredApps =
            listOf<String>(context.packageName, getCurrentLauncher(context.packageManager))
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        val usageStats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, start, System.currentTimeMillis()
        ).sortedWith(compareByDescending { it.lastTimeUsed })
            .filterNot { ignoredApps.contains(it.packageName) }
        val appTasks = ArrayList<AppTask>()
        if (usageStats.isNotEmpty())
            currentApp = usageStats[0].packageName
        for (stat in usageStats) {
            val app = stat.packageName
            try {
                if (isLaunchable(context, app)) {
                    appTasks.add(
                        AppTask(
                            -1,
                            getPackageLabel(context, app),
                            app,
                            context.packageManager.getApplicationIcon(app)
                        )
                    )
                }
            } catch (_: PackageManager.NameNotFoundException) {
            }
            if (appTasks.size >= max) break
        }
        return appTasks
    }

    fun isSystemApp(context: Context, app: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(app, 0)
            appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun isLaunchable(context: Context, app: String): Boolean {
        val resolveInfo = context.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(app), 0
        )
        return resolveInfo.isNotEmpty()
    }

    fun getPackageLabel(context: Context, packageName: String): String {
        try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            return packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: PackageManager.NameNotFoundException) {
        }
        return ""
    }

    fun getAppIcon(context: Context, app: String): Drawable {
        return try {
            context.packageManager.getApplicationIcon(app)
        } catch (_: PackageManager.NameNotFoundException) {
            AppCompatResources.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
        }
    }

    /** Measured top edge of the pinned dock on the main screen; windows end exactly here */
    @Volatile
    var dockTopOnScreen: Int? = null

    /** How far the dock is currently lifted above a keyboard (windows shrink by this much) */
    @Volatile
    var dockLift: Int = 0

    fun makeLaunchBounds(
        context: Context, mode: String, dockHeight: Int, displayId: Int = Display.DEFAULT_DISPLAY
    ): Rect {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        val bounds = DeviceUtils.getDisplayBounds(context, displayId)
        val deviceWidth = bounds.width()
        val deviceHeight = bounds.height()
        val statusBarHeight = DeviceUtils.getStatusBarHeight(context)
        val navHeight = DeviceUtils.getNavBarHeight(context)
        val usableHeight =
            if (DeviceUtils.shouldApplyNavbarFix()) deviceHeight - max(
                dockHeight,
                navHeight
            ) - statusBarHeight
            else deviceHeight - dockHeight - statusBarHeight
        val scaleFactor = sharedPreferences.getString("scale_factor", "1.0")!!.toFloat()
        when (mode) {
            "standard" -> {
                left = (deviceWidth / (5 * scaleFactor)).toInt()
                top = ((usableHeight + statusBarHeight) / (7 * scaleFactor)).toInt()
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }

            "maximized" -> {
                right = deviceWidth
                bottom = usableHeight
            }

            "portrait" -> {
                left = deviceWidth / 3
                top = usableHeight / 15
                right = deviceWidth - left
                bottom = usableHeight + dockHeight - top
            }

            "tiled-left" -> {
                right = deviceWidth / 2
                bottom = usableHeight
            }

            "tiled-center" -> {
                left = deviceWidth / 4
                right = deviceWidth - left
                bottom = usableHeight
            }

            "tiled-top" -> {
                right = deviceWidth
                bottom = (usableHeight + statusBarHeight) / 2
            }

            "tiled-right" -> {
                left = deviceWidth / 2
                right = deviceWidth
                bottom = usableHeight
            }

            "tiled-bottom" -> {
                right = deviceWidth
                top = (usableHeight + statusBarHeight) / 2
                bottom = usableHeight + statusBarHeight
            }
        }
        // Keep title bars clear of the status bar
        if (top < statusBarHeight && mode in listOf(
                "maximized", "tiled-left", "tiled-right", "tiled-center", "tiled-top"
            )
        ) top = statusBarHeight
        // Make windows meet the dock exactly, using where the dock really is
        val dockTop = dockTopOnScreen?.minus(dockLift)
        if (dockTop != null && displayId == Display.DEFAULT_DISPLAY && dockTop > top) {
            if (mode in listOf("maximized", "tiled-left", "tiled-right", "tiled-center", "tiled-bottom"))
                bottom = dockTop
            else if (bottom > dockTop)
                bottom = dockTop
        }
        return Rect(left, top, right, bottom)
    }

    fun makeActivityOptions(
        context: Context, mode: String, dockHeight: Int, displayId: Int
    ): ActivityOptions {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val secondary = sharedPreferences.getBoolean("prefer_last_display", false)

        val display =
            if (displayId != Display.DEFAULT_DISPLAY)
                displayId
            else
                if (secondary)
                    DeviceUtils.getSecondaryDisplay(context).displayId
                else 0
        val options: ActivityOptions = ActivityOptions.makeBasic()

        val windowMode: Int
        if (mode == "fullscreen")
            windowMode = WINDOWING_MODE_FULLSCREEN
        else {
            windowMode = WINDOWING_MODE_FREEFORM
            options.launchBounds = makeLaunchBounds(
                context, mode, dockHeight, display
            )
        }
        options.launchDisplayId = display
        val methodName = "setLaunchWindowingMode"
        val method = ActivityOptions::class.java.getMethod(methodName, Int::class.javaPrimitiveType)
        method.invoke(options, windowMode)
        return options
    }

    fun resizeTask(context: Context, mode: String, taskId: Int, dockHeight: Int) {
        if (taskId < 0) return
        val bounds = makeLaunchBounds(context, mode, dockHeight)
        DeviceUtils.runAsRoot(
            "am task resize " + taskId + " " + bounds.left + " " + bounds.top + " " + bounds.right + " " + bounds.bottom
        )
    }

    fun containsTask(apps: ArrayList<DockApp>, task: AppTask): Int {
        for (i in apps.indices) {
            if (apps[i].packageName == task.packageName) return i
        }
        return -1
    }

    fun isMediaNotification(notification: Notification) =
        notification.extras[Notification.EXTRA_TEMPLATE].toString() == "android.app.Notification\$MediaStyle"

    fun uninstallApp(context: Context, packageName: String) {
        if (isSystemApp(context, packageName))
            DeviceUtils.runAsRoot("pm uninstall --user 0 $packageName")
        else context.startActivity(
            Intent(
                Intent.ACTION_UNINSTALL_PACKAGE,
                "package:$packageName".toUri()
            )
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
