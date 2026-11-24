package cu.axel.smartdock.models

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle

class DockApp : App {
    val tasks: ArrayList<AppTask>
    /** Set when this dock entry is an app pair */
    var pair: List<App>? = null

    constructor(
        name: String, packageName: String, icon: Drawable,
        componentName: ComponentName? = null, userHandle: UserHandle = Process.myUserHandle()
    ) : super(name, packageName, icon, componentName, userHandle) {
        tasks = ArrayList()
    }

    constructor(task: AppTask) : super(
        task.name, task.packageName, task.icon, task.componentName, task.userHandle
    ) {
        tasks = ArrayList()
        tasks.add(task)
    }

    fun addTask(task: AppTask) {
        tasks.add(task)
    }

    override val icon: Drawable
        get() = if (tasks.size == 1) tasks[0].icon else super.icon
}
