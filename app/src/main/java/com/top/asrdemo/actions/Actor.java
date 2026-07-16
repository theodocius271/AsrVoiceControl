package com.top.asrdemo.actions;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.ViewGroup;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.top.asrdemo.R;
import com.top.asrdemo.commands.Commands;
import com.top.asrdemo.commands.Matcher;
import com.top.asrdemo.utils.ChatboxManager;

public class Actor extends BroadcastReceiver implements AutoCloseable {

    private static final String TAG = "Actor";

    @SuppressWarnings("deprecation") private final LocalBroadcastManager broadcastManager;
    private final ChatboxManager chatboxManager;
    private final Activity activity;

    private Action currentAction;
    private boolean registered;

    @SuppressWarnings("deprecation")
    public Actor(Activity activity, ChatboxManager chatboxManager) {
        this.activity = activity;
        broadcastManager = LocalBroadcastManager.getInstance(activity);
        this.chatboxManager = chatboxManager;
    }

    public void start() {
        if (registered) {
            return;
        }

        IntentFilter filter = new IntentFilter(Matcher.ACTION_COMMAND_MATCHED);
        broadcastManager.registerReceiver(this, filter);
        registered = true;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Matcher.ACTION_COMMAND_MATCHED.equals(intent.getAction())) {
            return;
        }

        String commandId = intent.getStringExtra(Matcher.EXTRA_COMMAND_ID);
        if (commandId == null) {
            // failed matches affect nothing
            return;
        }

        Action nextAction = createAction(commandId);
        if (nextAction == null) {
            Log.w(TAG, "No action registered for command: " + commandId);
            return;
        }
        closeCurrentAction();
        currentAction = nextAction;

        try {
            currentAction.run();
            Log.i(TAG, "Started action for command: " + commandId);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to run action for command: " + commandId, e);
            closeCurrentAction();
        }
    }

    private Action createAction(String commandId) {
        switch (commandId) {
            case Commands.COMMAND_GREET:
                return new Greet(chatboxManager);
            case Commands.COMMAND_INCREASE_BRIGHTNESS:
                return new IncreaseBrightness(activity, chatboxManager);
            case Commands.COMMAND_DECREASE_BRIGHTNESS:
                return new DecreaseBrightness(activity, chatboxManager);
            case Commands.COMMAND_INCREASE_VOLUME:
                return new IncreaseVolume(activity);
            case Commands.COMMAND_DECREASE_VOLUME:
                return new DecreaseVolume(activity);
            default:
                return null;
        }
    }

    public void closeCurrentAction() {
        if (currentAction == null) {
            return;
        }

        try {
            currentAction.close();
        } catch (Exception e) {
            Log.e(TAG, "Failed to close current action", e);
        } finally {
            currentAction = null;
        }
    }

    public void retryCurrentAction() {
        if (currentAction == null) {
            return ;
        }

        try {
            currentAction.run();
        } catch (RuntimeException e) {
            Log.e(TAG, "Filed to retry current action", e);
            closeCurrentAction();
        }
    }

    @Override
    public void close() throws Exception {
        if (registered) {
            broadcastManager.unregisterReceiver(this);
            registered = false;
        }
        closeCurrentAction();
    }
}
