package cu.axel.smartdock.models

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle

const val WINDOWING_MODE_FULLSCREEN = 1
const val WINDOWING_MODE_FREEFORM = 5
class AppTask(
    val id: Int, label: String, packageName: String, icon: Drawable, val windowingMode: Int = -1,
    componentName: ComponentName? = null, userHandle: UserHandle = Process.myUserHandle()
) : App(label, packageName, icon, componentName, userHandle)
