package com.top.asrdemo.actions;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.top.asrdemo.R;
import com.top.asrdemo.utils.ChatboxManager;

public class Greet implements Action {
    private final ChatboxManager chatboxManager;

    public Greet(ChatboxManager chatboxManager) {
        this.chatboxManager = chatboxManager;
    }

    @Override
    public void run() {
        chatboxManager.addSystemText("Hello, I'm TopVoiceControl");
    }

    @Override
    public void close() {

    }
}
