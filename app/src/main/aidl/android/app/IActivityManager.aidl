package android.app;

import android.os.Bundle;

interface IActivityManager {

    List<android.app.ActivityManager.RunningTaskInfo> getTasks(int maxNum);

    void resizeTask(int taskId, in Rect bounds, int resizeMode);

    boolean removeTask(int taskId);

    int startActivityFromRecents(int taskId, in Bundle options);
}