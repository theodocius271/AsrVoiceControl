package com.top.asrdemo.utils;

import android.graphics.Bitmap;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ChatMessage {
    enum Sender {
        USER,
        SYSTEM
    }

    enum Kind {
        TEXT,
        IMAGE_RESOURCE,
        IMAGE_BITMAP
    }

    private final long id;
    private final Sender sender;
    private final Kind kind;
    private final int imageResource;
    private final String text;
    private final Bitmap bitmap;
    private final String contentDescription;

    public ChatMessage(long id, @NonNull Sender sender, @NonNull Kind kind, @Nullable String text,
                       @DrawableRes int imageResource, @Nullable Bitmap bitmap, @Nullable String contentDescription) {
        this.id = id;
        this.sender = sender;
        this.kind = kind;
        this.imageResource = imageResource;
        this.text = text;
        this.bitmap = bitmap;
        this.contentDescription = contentDescription;
    }

    static ChatMessage text(long id, @NonNull Sender sender, @NonNull String text) {
        return new ChatMessage(id, sender, Kind.TEXT, text, 0, null, null);
    }

    static ChatMessage imageResource(long id, @NonNull Sender sender, @DrawableRes int imageResource, @NonNull String contentDescription) {
        return new ChatMessage(id, sender, Kind.IMAGE_RESOURCE, null, imageResource, null, contentDescription);
    }

    static ChatMessage imageBitmap(long id, @NonNull Sender sender, @NonNull Bitmap bitmap, @NonNull String contentDescription) {
        return new ChatMessage(id, sender, Kind.IMAGE_BITMAP, null, 0, bitmap, contentDescription);
    }

    ChatMessage withText(@NonNull String newText) {
        if (kind != Kind.TEXT) {
            throw new IllegalStateException("Only a text message can be updated as a text");
        }
        return text(id, sender, newText);
    }

    long getId() {
        return id;
    }

    @NonNull
    Sender getSender() {
        return sender;
    }

    @NonNull
    Kind getKind() {
        return kind;
    }

    @Nullable
    String getText() {
        return text;
    }

    @DrawableRes
    int getImageResource() {
        return imageResource;
    }

    @Nullable
    Bitmap getBitmap() {
        return bitmap;
    }

    @Nullable
    String getContentDescription() {
        return contentDescription;
    }
}
