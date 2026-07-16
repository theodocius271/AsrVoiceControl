package com.top.asrdemo.utils;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.LinearLayout;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.concurrent.atomic.AtomicLong;

public class ChatboxManager implements AutoCloseable {
    public static final String TAG = "Chatbox Manager";
    public static final long NO_MESSAGE_ID = -1L;
    private static final int DEFAULT_MAXIMUM_MESSAGE_COUNT = 12;

    public final RecyclerView recyclerView;
    private final ChatMessageAdapter adapter;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong nextMessageId = new AtomicLong(1L);

    private long partialUserMessageId = NO_MESSAGE_ID;
    private volatile boolean closed;

    public ChatboxManager(@NonNull RecyclerView recyclerView) {
        this(recyclerView, DEFAULT_MAXIMUM_MESSAGE_COUNT);
    }

    public ChatboxManager(
            @NonNull RecyclerView recyclerView,
            int maximumMessageCount
    ) {
        this.recyclerView = recyclerView;
        adapter = new ChatMessageAdapter(maximumMessageCount);

        LinearLayoutManager layoutManager = new LinearLayoutManager(recyclerView.getContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);

        // Partial ASR updates do not cross-fade the whole message bubble;
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
    }

    /**
     * Add a complete user text message and return its removable ID
     * @param text
     * @return message ID
     */
    public long addUserText(@Nullable String text) {
        return addText(ChatMessage.Sender.USER, text);
    }

    /** Add a complete system text and return its removable ID */
    public long addSystemText(@Nullable String text) {
        return addText(ChatMessage.Sender.SYSTEM, text);
    }

    public void showPartialUserText(@Nullable String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return;
        }

        runOnMainThread(() -> {
            if (partialUserMessageId != NO_MESSAGE_ID && adapter.updateText(partialUserMessageId, normalized)) {
                scrollToLatest();
                return;
            }

            partialUserMessageId = nextMessageId.getAndIncrement();
            append(ChatMessage.text(partialUserMessageId, ChatMessage.Sender.USER, normalized));
        });
    }

    /** Finalize the pending ASR row, or add a new user row if there was no partial result. */
    public void commitUserText(@Nullable String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return;
        }

        runOnMainThread(() -> {
            if (partialUserMessageId != NO_MESSAGE_ID && adapter.updateText(partialUserMessageId, normalized)) {
                partialUserMessageId = NO_MESSAGE_ID;
                scrollToLatest();
                return ;
            }

            partialUserMessageId = NO_MESSAGE_ID;
            append(ChatMessage.text(nextMessageId.getAndIncrement(), ChatMessage.Sender.USER, normalized));
        });
    }

    public long addUserImage(
            @DrawableRes int imageResource, @NonNull String contentDescription
    ) {
        return addImageResource(
                ChatMessage.Sender.USER,
                imageResource,
                contentDescription
        );
    }

    public long addSystemImage(
            @DrawableRes int imageResource, @NonNull String contentDescription
    ) {
        return addImageResource(
                ChatMessage.Sender.SYSTEM,
                imageResource,
                contentDescription
        );
    }

    public long addUserImage(
            @NonNull Bitmap bitmap,
            @NonNull String contentDescription) {
        return addBitmap(ChatMessage.Sender.USER, bitmap, contentDescription);
    }

    public long addSystemImage(
            @NonNull Bitmap bitmap,
            @NonNull String contentDescription) {
        return addBitmap(ChatMessage.Sender.SYSTEM, bitmap, contentDescription);
    }

    /** Remove a specific message. Avoid using this method */
    public void removeMessage(long messageId) {
        if (messageId == NO_MESSAGE_ID) {
            return;
        }

        runOnMainThread(() -> {
            adapter.remove(messageId);
            if (partialUserMessageId == messageId) {
                partialUserMessageId = NO_MESSAGE_ID;
            }
        });
    }

    /** Clear the complete history */
    public void clear() {
        runOnMainThread(() -> {
            partialUserMessageId = NO_MESSAGE_ID;
            adapter.clearMessages();
        });
    }

    private long addText(ChatMessage.Sender sender, @Nullable String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return NO_MESSAGE_ID;
        }

        long messageId = nextMessageId.getAndIncrement();
        runOnMainThread(() -> append(ChatMessage.text(messageId, sender, normalized)));
        return messageId;
    }

    private long addImageResource(ChatMessage.Sender sender,
                                  @DrawableRes int imageResource,
                                  @NonNull String contentDescription) {
        long messageId = nextMessageId.getAndIncrement();
        runOnMainThread(() -> append(ChatMessage.imageResource(
                messageId, sender, imageResource, contentDescription
        )));
        return messageId;
    }

    private long addBitmap(
            ChatMessage.Sender sender, @NonNull Bitmap bitmap, @NonNull String contentDescription
    ) {
        long messageId = nextMessageId.getAndIncrement();
        runOnMainThread(() -> append(ChatMessage.imageBitmap(
                messageId, sender, bitmap, contentDescription
        )));
        return messageId;
    }

    private void append(ChatMessage message) {
        adapter.append(message);

        // The bounded adapter may have pruned an old pending message.
        if (partialUserMessageId != NO_MESSAGE_ID && !adapter.contains(partialUserMessageId)) {
            partialUserMessageId = NO_MESSAGE_ID;
        }
        scrollToLatest();
    }

    private void scrollToLatest() {
        int lastPosition = adapter.getItemCount() - 1;
        if (lastPosition >= 0) {
            recyclerView.scrollToPosition(lastPosition);
        }
    }

    /** Just trimming */
    @Nullable
    private static String normalize(@Nullable String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void runOnMainThread(@NonNull Runnable operation) {
        if (closed) {
            return;
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            operation.run();
        } else {
            mainHandler.post(() -> {
                if (!closed) {
                    operation.run();
                }
            });
        }
    }

    @Override
    public void close() throws Exception {
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);

        // Activity lifecycle callbacks run on the main thread.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            partialUserMessageId = NO_MESSAGE_ID;
            adapter.clearMessages();
            recyclerView.setAdapter(null);
        }
    }
}
