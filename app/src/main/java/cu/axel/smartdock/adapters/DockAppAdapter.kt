package cu.axel.smartdock.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import cu.axel.smartdock.R
import cu.axel.smartdock.models.DockApp
import cu.axel.smartdock.utils.AppUtils
import cu.axel.smartdock.utils.ColorUtils
import cu.axel.smartdock.utils.IconPackUtils
import cu.axel.smartdock.utils.OnSwipeListener.Direction
import cu.axel.smartdock.utils.Utils

class DockAppAdapter(
    private val context: Context, private var apps: ArrayList<DockApp>,
    private val listener: OnDockAppClickListener, private val iconPackUtils: IconPackUtils?
) : RecyclerView.Adapter<DockAppAdapter.ViewHolder>() {
    private var iconBackground = 0
    private val iconPadding: Int
    private val iconTheming: Boolean
    private val tintIndicators: Boolean
    private val iconSwipesEnabled: Boolean

    interface OnDockAppClickListener {
        fun onDockAppClicked(app: DockApp, view: View)
        fun onDockAppLongClicked(app: DockApp, view: View)
        fun onDockAppSwiped(app: DockApp, direction: Direction): Boolean = false
    }

    init {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        iconTheming = sharedPreferences.getString("icon_pack", "") != ""
        iconSwipesEnabled = sharedPreferences.getBoolean("fork_icon_swipes", true) ||
                sharedPreferences.getBoolean("fork_icon_swipe_close", true)
        iconPadding =
            Utils.dpToPx(context, sharedPreferences.getString("icon_padding", "5")!!.toInt())
        tintIndicators = sharedPreferences.getBoolean("tint_indicators", false)
        when (sharedPreferences.getString("icon_shape", "circle")) {
            "circle" -> iconBackground = R.drawable.circle
            "round_rect" -> iconBackground = R.drawable.round_square
            "default" -> iconBackground = -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, arg1: Int): ViewHolder {
        val itemLayoutView =
            LayoutInflater.from(parent.context).inflate(R.layout.app_task_entry, null)
        return ViewHolder(itemLayoutView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val app = apps[position]
        val size = app.tasks.size
        if (size > 0) {
            if (tintIndicators)
                ColorUtils.applyColor(
                    viewHolder.runningIndicator,
                    ColorUtils.manipulateColor(ColorUtils.getDrawableDominantColor(app.icon), 2f)
                )

            viewHolder.runningIndicator.alpha = if (app.tasks[0].id != -1) 1f else 0f
            val isCurrent = app.packageName == AppUtils.currentApp ||
                    app.pair?.any { it.packageName == AppUtils.currentApp } == true
            viewHolder.runningIndicator.layoutParams.width =
                Utils.dpToPx(context, if (isCurrent) 16 else 8)

            if (size > 1) {
                viewHolder.taskCounter.text = size.toString()
                viewHolder.taskCounter.alpha = 1f
            } else
                viewHolder.taskCounter.alpha = 0f
        } else {
            viewHolder.runningIndicator.alpha = 0f
            viewHolder.taskCounter.alpha = 0f
        }
        if (iconPackUtils != null && app.pair == null)
            viewHolder.iconIv.setImageDrawable(iconPackUtils.getAppThemedIcon(app.packageName))
        else
            viewHolder.iconIv.setImageDrawable(app.icon)
        if (iconBackground != -1) {
            viewHolder.iconIv.setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            viewHolder.iconIv.setBackgroundResource(iconBackground)
            ColorUtils.applyColor(
                viewHolder.iconIv,
                ColorUtils.getDrawableDominantColor(viewHolder.iconIv.drawable)
            )
        }
        viewHolder.bind(app, listener)
    }

    override fun getItemCount(): Int {
        return apps.size
    }

    fun updateApps(newApps: ArrayList<DockApp>) {
        apps = newApps
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var iconIv: ImageView = itemView.findViewById(R.id.icon_iv)
        var taskCounter: TextView = itemView.findViewById(R.id.task_count_badge)
        var runningIndicator: View = itemView.findViewById(R.id.running_indicator)

        fun bind(app: DockApp, listener: OnDockAppClickListener) {
            itemView.setOnClickListener { view -> listener.onDockAppClicked(app, view) }
            itemView.setOnLongClickListener { view ->
                listener.onDockAppLongClicked(app, view)
                true
            }
            val swipeThreshold = Utils.dpToPx(itemView.context, 30)
            // The dock sits at the bottom edge, so there's little room to swipe down
            val downSwipeThreshold = Utils.dpToPx(itemView.context, 6)
            var downX = 0f
            var downY = 0f
            var swiped = false
            itemView.setOnTouchListener { view, event ->
                if (event.buttonState == MotionEvent.BUTTON_SECONDARY) {
                    listener.onDockAppLongClicked(app, view)
                    return@setOnTouchListener true
                }
                if (!iconSwipesEnabled) return@setOnTouchListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        swiped = false
                        // Keep the icon row from scrolling so swipes reach the icon
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (swiped) return@setOnTouchListener true
                        val dx = event.rawX - downX
                        val dy = event.rawY - downY
                        val direction = if (abs(dx) > abs(dy)) {
                            if (abs(dx) > swipeThreshold)
                                if (dx > 0) Direction.RIGHT else Direction.LEFT
                            else null
                        } else if (dy < 0 && -dy > swipeThreshold) Direction.UP
                        else if (dy > 0 && dy > downSwipeThreshold) Direction.DOWN
                        else null
                        if (direction != null) {
                            // Act as soon as the swipe is long enough, before the
                            // finger leaves the dock or the system takes the gesture
                            swiped = true
                            view.cancelLongPress()
                            view.isPressed = false
                            listener.onDockAppSwiped(app, direction)
                            return@setOnTouchListener true
                        }
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Swallow the release so a swipe never counts as a tap
                        if (swiped) {
                            swiped = false
                            return@setOnTouchListener true
                        }
                    }
                }
                false
            }
        }
    }
}