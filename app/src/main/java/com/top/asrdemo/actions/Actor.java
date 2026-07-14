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
import com.top.asrdemo.commands.Matcher;

public class Actor extends BroadcastReceiver implements AutoCloseable {

    private static final String TAG = "Actor";

    @SuppressWarnings("deprecation") private final LocalBroadcastManager broadcastManager;
    private final ViewGroup actionHost;

    private Action currentAction;
    private boolean registered;

    public Actor(Activity activity) {
        broadcastManager = LocalBroadcastManager.getInstance(activity);
        actionHost = activity.findViewById(R.id.upper_section);
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
            case Matcher.COMMAND_GREET:
                return new Greet(actionHost);
            default:
                return null;
        }
    }

    private void closeCurrentAction() {
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



    @Override
    public void close() throws Exception {
        if (registered) {
            broadcastManager.unregisterReceiver(this);
            registered = false;
        }
        closeCurrentAction();
    }
}
