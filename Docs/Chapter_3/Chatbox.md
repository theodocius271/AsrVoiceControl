# Chapter 3: Chatbox UI

## Goal

This chapter replaces the two fixed output views in `MainActivity` with a chat-style message list.
ASR results appear as user messages and match results appear as system messages. The same list can
also display images produced by future actions.

The implementation uses `RecyclerView` rather than manually measuring whether `upper_section` is
full. Message rows can have different heights, especially once images are allowed, so a
screen-capacity calculation would be fragile. `RecyclerView` reuses rows that scroll off screen,
while `ChatboxManager` keeps at most 100 message records and removes the oldest record when that
limit is exceeded.

The resulting ownership is:

```text
MainActivity / Action
        -> ChatboxManager public API
        -> bounded ChatMessage list
        -> RecyclerView adapter
        -> recycled text or image rows in upper_section
```

There is one important ASR detail: partial results must update one pending user message. Appending a
new row for every partial result would fill the list with intermediate hypotheses. The final result
updates that same row and marks it complete.

## Step 1: Add RecyclerView

Add this line under the existing `[versions]` section in `gradle/libs.versions.toml`:

```toml
recyclerview = "1.4.0"
```

Add this line under the existing `[libraries]` section in the same file:

```toml
recyclerview = { module = "androidx.recyclerview:recyclerview", version.ref = "recyclerview" }
```

Then add the dependency to `app/build.gradle`:

```groovy
dependencies {
    implementation libs.recyclerview
    // Keep the existing dependencies here.
}
```

## Step 2: Replace the Upper Section Layout

Replace `app/src/main/res/layout/activity_main.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:id="@+id/main_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#F7F8FA"
    android:orientation="vertical"
    tools:context=".MainActivity">

    <FrameLayout
        android:id="@+id/upper_section"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="4">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/chat_messages"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:overScrollMode="ifContentScrolls"
            android:paddingStart="12dp"
            android:paddingTop="16dp"
            android:paddingEnd="12dp"
            android:paddingBottom="16dp"
            android:scrollbars="vertical" />

    </FrameLayout>

    <FrameLayout
        android:id="@+id/lower_section"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:background="#FFFFFF">

        <ImageButton
            android:id="@+id/btn_microphone"
            android:layout_width="80dp"
            android:layout_height="80dp"
            android:layout_gravity="center"
            android:background="@drawable/circular_button_background_paused"
            android:contentDescription="@string/microphone_button"
            android:scaleType="centerInside"
            android:src="@drawable/ic_microphone" />

    </FrameLayout>

</LinearLayout>
```

`upper_section` remains as the stable host in the activity layout, but application code no longer
adds arbitrary child views to it. `chat_messages` is the only child that renders messages.

## Step 3: Add the Message Row Resources

Create `app/src/main/res/drawable/chat_bubble_user.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#DCEBFF" />
    <corners android:radius="8dp" />
    <stroke
        android:width="1dp"
        android:color="#A9C8F2" />
</shape>
```

Create `app/src/main/res/drawable/chat_bubble_system.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#F0F1F3" />
    <corners android:radius="8dp" />
    <stroke
        android:width="1dp"
        android:color="#D2D5DA" />
</shape>
```

Create `app/src/main/res/layout/item_chat_text.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/message_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingTop="4dp"
    android:paddingBottom="4dp">

    <TextView
        android:id="@+id/message_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:maxWidth="320dp"
        android:paddingStart="14dp"
        android:paddingTop="10dp"
        android:paddingEnd="14dp"
        android:paddingBottom="10dp"
        android:textColor="#202124"
        android:textSize="17sp" />

</LinearLayout>
```

Create `app/src/main/res/layout/item_chat_image.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/message_row"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingTop="4dp"
    android:paddingBottom="4dp">

    <ImageView
        android:id="@+id/message_image"
        android:layout_width="240dp"
        android:layout_height="180dp"
        android:adjustViewBounds="true"
        android:cropToPadding="true"
        android:padding="4dp"
        android:scaleType="centerCrop" />

</LinearLayout>
```

The adapter changes each row's gravity at bind time: user messages are aligned to the end and
system messages to the start. The text bubble has a maximum width and the image row has explicit
dimensions, so long or recycled content cannot force a row beyond the chat window.

## Step 4: Define the Internal Message Model

Create `app/src/main/java/com/top/asrdemo/utils/ChatMessage.java`:

```java
package com.top.asrdemo.utils;

import android.graphics.Bitmap;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Package-private model rendered by ChatMessageAdapter. */
final class ChatMessage {
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
    private final String text;
    private final int imageResource;
    private final Bitmap bitmap;
    private final String contentDescription;

    private ChatMessage(
            long id,
            @NonNull Sender sender,
            @NonNull Kind kind,
            @Nullable String text,
            @DrawableRes int imageResource,
            @Nullable Bitmap bitmap,
            @Nullable String contentDescription) {
        this.id = id;
        this.sender = sender;
        this.kind = kind;
        this.text = text;
        this.imageResource = imageResource;
        this.bitmap = bitmap;
        this.contentDescription = contentDescription;
    }

    static ChatMessage text(long id, @NonNull Sender sender, @NonNull String text) {
        return new ChatMessage(id, sender, Kind.TEXT, text, 0, null, null);
    }

    static ChatMessage imageResource(
            long id,
            @NonNull Sender sender,
            @DrawableRes int imageResource,
            @NonNull String contentDescription) {
        return new ChatMessage(
                id,
                sender,
                Kind.IMAGE_RESOURCE,
                null,
                imageResource,
                null,
                contentDescription);
    }

    static ChatMessage imageBitmap(
            long id,
            @NonNull Sender sender,
            @NonNull Bitmap bitmap,
            @NonNull String contentDescription) {
        return new ChatMessage(
                id,
                sender,
                Kind.IMAGE_BITMAP,
                null,
                0,
                bitmap,
                contentDescription);
    }

    ChatMessage withText(@NonNull String newText) {
        if (kind != Kind.TEXT) {
            throw new IllegalStateException("Only a text message can be updated as text");
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
```

The model stays package-private. Other classes should add and remove messages through
`ChatboxManager`, rather than modifying the adapter's list directly.

## Step 5: Create the Recycling Adapter

Create `app/src/main/java/com/top/asrdemo/utils/ChatMessageAdapter.java`:

```java
package com.top.asrdemo.utils;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.top.asrdemo.R;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView adapter with a bounded in-memory message history. */
final class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TEXT = 1;
    private static final int VIEW_IMAGE = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final int maximumMessageCount;

    ChatMessageAdapter(int maximumMessageCount) {
        if (maximumMessageCount <= 0) {
            throw new IllegalArgumentException("maximumMessageCount must be positive");
        }
        this.maximumMessageCount = maximumMessageCount;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getKind() == ChatMessage.Kind.TEXT
                ? VIEW_TEXT
                : VIEW_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TEXT) {
            View view = inflater.inflate(R.layout.item_chat_text, parent, false);
            return new TextMessageHolder(view);
        }

        View view = inflater.inflate(R.layout.item_chat_image, parent, false);
        return new ImageMessageHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof TextMessageHolder) {
            ((TextMessageHolder) holder).bind(message);
        } else if (holder instanceof ImageMessageHolder) {
            ((ImageMessageHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ImageMessageHolder) {
            ((ImageMessageHolder) holder).clear();
        }
        super.onViewRecycled(holder);
    }

    void append(@NonNull ChatMessage message) {
        if (messages.size() >= maximumMessageCount) {
            messages.remove(0);
            notifyItemRemoved(0);
        }

        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    boolean updateText(long messageId, @NonNull String text) {
        int position = findPosition(messageId);
        if (position < 0) {
            return false;
        }

        ChatMessage current = messages.get(position);
        if (current.getKind() != ChatMessage.Kind.TEXT) {
            return false;
        }

        messages.set(position, current.withText(text));
        notifyItemChanged(position);
        return true;
    }

    boolean remove(long messageId) {
        int position = findPosition(messageId);
        if (position < 0) {
            return false;
        }

        messages.remove(position);
        notifyItemRemoved(position);
        return true;
    }

    boolean contains(long messageId) {
        return findPosition(messageId) >= 0;
    }

    void clearMessages() {
        int oldSize = messages.size();
        if (oldSize == 0) {
            return;
        }
        messages.clear();
        notifyItemRangeRemoved(0, oldSize);
    }

    private int findPosition(long messageId) {
        for (int i = 0; i < messages.size(); i++) {
            if (messages.get(i).getId() == messageId) {
                return i;
            }
        }
        return -1;
    }

    private static int bubbleBackground(ChatMessage message) {
        return message.getSender() == ChatMessage.Sender.USER
                ? R.drawable.chat_bubble_user
                : R.drawable.chat_bubble_system;
    }

    private static int rowGravity(ChatMessage message) {
        return message.getSender() == ChatMessage.Sender.USER
                ? Gravity.END
                : Gravity.START;
    }

    private static final class TextMessageHolder extends RecyclerView.ViewHolder {
        private final LinearLayout row;
        private final TextView textView;

        private TextMessageHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.message_row);
            textView = itemView.findViewById(R.id.message_text);
        }

        private void bind(ChatMessage message) {
            row.setGravity(rowGravity(message));
            textView.setBackgroundResource(bubbleBackground(message));
            textView.setText(message.getText());
        }
    }

    private static final class ImageMessageHolder extends RecyclerView.ViewHolder {
        private final LinearLayout row;
        private final ImageView imageView;

        private ImageMessageHolder(@NonNull View itemView) {
            super(itemView);
            row = itemView.findViewById(R.id.message_row);
            imageView = itemView.findViewById(R.id.message_image);
        }

        private void bind(ChatMessage message) {
            row.setGravity(rowGravity(message));
            imageView.setBackgroundResource(bubbleBackground(message));
            imageView.setContentDescription(message.getContentDescription());
            imageView.setImageDrawable(null);

            if (message.getKind() == ChatMessage.Kind.IMAGE_RESOURCE) {
                imageView.setImageResource(message.getImageResource());
            } else {
                imageView.setImageBitmap(message.getBitmap());
            }
        }

        private void clear() {
            imageView.setImageDrawable(null);
            imageView.setContentDescription(null);
        }
    }
}
```

`append()` enforces the 100-message limit configured by the manager. The `RecyclerView` separately
handles view recycling; pruning the model and recycling its views solve different problems.

## Step 6: Create ChatboxManager

Create `app/src/main/java/com/top/asrdemo/utils/ChatboxManager.java`:

```java
package com.top.asrdemo.utils;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.concurrent.atomic.AtomicLong;

/** The only public entry point for inserting, updating, and removing chat messages. */
public final class ChatboxManager implements AutoCloseable {
    public static final long NO_MESSAGE_ID = -1L;
    private static final int DEFAULT_MAXIMUM_MESSAGE_COUNT = 100;

    private final RecyclerView recyclerView;
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
            int maximumMessageCount) {
        this.recyclerView = recyclerView;
        adapter = new ChatMessageAdapter(maximumMessageCount);

        LinearLayoutManager layoutManager = new LinearLayoutManager(recyclerView.getContext());
        layoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(false);

        // Partial ASR updates should not cross-fade the whole message bubble.
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
    }

    /** Add a completed user text message and return its removable ID. */
    public long addUserText(@Nullable String text) {
        return addText(ChatMessage.Sender.USER, text);
    }

    /** Add a completed system text message and return its removable ID. */
    public long addSystemText(@Nullable String text) {
        return addText(ChatMessage.Sender.SYSTEM, text);
    }

    /** Add or update the one pending ASR hypothesis. */
    public void showPartialUserText(@Nullable String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return;
        }

        runOnMainThread(() -> {
            if (partialUserMessageId != NO_MESSAGE_ID
                    && adapter.updateText(partialUserMessageId, normalized)) {
                scrollToLatest();
                return;
            }

            partialUserMessageId = nextMessageId.getAndIncrement();
            append(ChatMessage.text(
                    partialUserMessageId,
                    ChatMessage.Sender.USER,
                    normalized));
        });
    }

    /** Finalize the pending ASR row, or add a new user row if there was no partial result. */
    public void commitUserText(@Nullable String text) {
        String normalized = normalize(text);
        if (normalized == null) {
            return;
        }

        runOnMainThread(() -> {
            if (partialUserMessageId != NO_MESSAGE_ID
                    && adapter.updateText(partialUserMessageId, normalized)) {
                partialUserMessageId = NO_MESSAGE_ID;
                scrollToLatest();
                return;
            }

            partialUserMessageId = NO_MESSAGE_ID;
            append(ChatMessage.text(
                    nextMessageId.getAndIncrement(),
                    ChatMessage.Sender.USER,
                    normalized));
        });
    }

    public long addUserImage(
            @DrawableRes int imageResource,
            @NonNull String contentDescription) {
        return addImageResource(
                ChatMessage.Sender.USER,
                imageResource,
                contentDescription);
    }

    public long addSystemImage(
            @DrawableRes int imageResource,
            @NonNull String contentDescription) {
        return addImageResource(
                ChatMessage.Sender.SYSTEM,
                imageResource,
                contentDescription);
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

    /** Remove a specific message, for example when an Action is closed. */
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

    /** Clear the complete history. Call this only for an explicit clear-history operation. */
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

    private long addImageResource(
            ChatMessage.Sender sender,
            @DrawableRes int imageResource,
            @NonNull String contentDescription) {
        long messageId = nextMessageId.getAndIncrement();
        runOnMainThread(() -> append(ChatMessage.imageResource(
                messageId,
                sender,
                imageResource,
                contentDescription)));
        return messageId;
    }

    private long addBitmap(
            ChatMessage.Sender sender,
            @NonNull Bitmap bitmap,
            @NonNull String contentDescription) {
        long messageId = nextMessageId.getAndIncrement();
        runOnMainThread(() -> append(ChatMessage.imageBitmap(
                messageId,
                sender,
                bitmap,
                contentDescription)));
        return messageId;
    }

    private void append(ChatMessage message) {
        adapter.append(message);

        // The bounded adapter may have pruned an old pending message.
        if (partialUserMessageId != NO_MESSAGE_ID
                && !adapter.contains(partialUserMessageId)) {
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

    /** MainActivity calls this from onDestroy(). */
    @Override
    public void close() {
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
```

All public mutations are marshalled to the main thread. This lets an action publish a result from a
worker callback without touching Android views from the wrong thread. `ChatboxManager` is still an
activity-scoped object; do not turn it into a static singleton, because a singleton would retain the
destroyed activity's `RecyclerView`.

The `Bitmap` overload is intended for already decoded, display-sized images. Resize camera or model
output before passing it to the manager. The bounded list releases its reference when an old bitmap
message is pruned, but it should not hold 100 full-resolution camera frames.

## Step 7: Wire MainActivity to the Manager

In `MainActivity.java`, remove these fields:

```java
private TextView tvUserInput;
private TextView tvSystemOutput;
```

Add the manager field and import:

```java
import com.top.asrdemo.utils.ChatboxManager;

private ChatboxManager chatboxManager;
```

Remove the `TextView` and `View` imports if they are no longer used. Also add `Locale` for stable
similarity formatting:

```java
import java.util.Locale;
```

Replace the old view initialization and `Actor` construction in `onCreate()` with:

```java
chatboxManager = new ChatboxManager(findViewById(R.id.chat_messages));

micBtn = findViewById(R.id.btn_microphone);
micBtn.setOnClickListener(v -> toggleListening());

actor = new Actor(this, chatboxManager);
actor.start();
```

Replace the body of `asrReceiver.onReceive()` with:

```java
String action = intent.getAction();
if (action == null) {
    return;
}

switch (action) {
    case AsrService.ACTION_PARTIAL_RESULT:
        chatboxManager.showPartialUserText(
                intent.getStringExtra(AsrService.EXTRA_TEXT));
        break;

    case AsrService.ACTION_FINAL_RESULT:
        chatboxManager.commitUserText(
                intent.getStringExtra(AsrService.EXTRA_TEXT));
        break;

    case AsrService.ACTION_ERROR:
        String error = intent.getStringExtra(AsrService.EXTRA_ERROR);
        chatboxManager.addSystemText("ASR error: " + error);
        Toast.makeText(
                MainActivity.this,
                "ASR Error: " + error,
                Toast.LENGTH_SHORT).show();
        stopListening();
        break;

    default:
        break;
}
```

Replace the body of `matcherReceiver.onReceive()` with:

```java
if (!Matcher.ACTION_COMMAND_MATCHED.equals(intent.getAction())) {
    return;
}

String commandId = intent.getStringExtra(Matcher.EXTRA_COMMAND_ID);
String commandText = intent.getStringExtra(Matcher.EXTRA_COMMAND_TEXT);
float similarity = intent.getFloatExtra(Matcher.EXTRA_SIMILARITY, 0.0f);
String originalText = intent.getStringExtra(Matcher.EXTRA_ORIGINAL_TEXT);

if (commandId != null) {
    String label = commandText != null ? commandText : commandId;
    String output = String.format(
            Locale.US,
            "Matched: %s (%.2f%%)",
            label,
            similarity * 100.0f);
    chatboxManager.addSystemText(output);
    Log.i(TAG, output);
} else {
    actor.closeCurrentAction();
    chatboxManager.addSystemText("No matching command");
    Log.i(TAG, "No command matched for: " + originalText);
}
```

Using `Matcher`'s public broadcast constants avoids duplicating protocol strings in the activity.

Delete these obsolete methods from `MainActivity`:

```text
showTextViewWithAnimation(...)
setUserInput(...)
setSystemOutput(...)
clearDisplays()
```

Also remove both calls to `clearDisplays()` from `startListening()` and `stopListening()`. Starting
or pausing the microphone should not erase a conversation. An explicit clear-history button can
call `chatboxManager.clear()` later.

At the end of `onDestroy()`, close objects in this order:

```java
if (actor != null) {
    try {
        actor.close();
    } catch (Exception e) {
        Log.e(TAG, "Error closing actor", e);
    }
}

if (chatboxManager != null) {
    chatboxManager.close();
}
```

Close `Actor` first because its current action may call `ChatboxManager.removeMessage()` while it is
being closed. Keep the existing receiver unregistration, matcher release, and ASR service cleanup
around this block.

## Step 8: Stop Actions from Writing to upper_section

The existing `Greet` action directly adds a `TextView` to `upper_section`. That would bypass the
adapter and break its ownership of the chat list. Pass the activity's manager through `Actor`
instead.

In `Actor.java`, replace the `actionHost` field with:

```java
private final ChatboxManager chatboxManager;
```

Add the import:

```java
import com.top.asrdemo.utils.ChatboxManager;
```

Change the constructor to:

```java
@SuppressWarnings("deprecation")
public Actor(Activity activity, ChatboxManager chatboxManager) {
    this.activity = activity;
    this.chatboxManager = chatboxManager;
    broadcastManager = LocalBroadcastManager.getInstance(activity);
}
```

Change the greeting branch in `createAction()`:

```java
case Commands.COMMAND_GREET:
    return new Greet(chatboxManager);
```

`Actor` no longer needs the `ViewGroup` or `com.top.asrdemo.R` imports.

Replace `Greet.java` with:

```java
package com.top.asrdemo.actions;

import com.top.asrdemo.utils.ChatboxManager;

/** Adds a removable greeting to the shared chat history. */
public final class Greet implements Action {
    private final ChatboxManager chatboxManager;
    private long greetingMessageId = ChatboxManager.NO_MESSAGE_ID;

    public Greet(ChatboxManager chatboxManager) {
        this.chatboxManager = chatboxManager;
    }

    @Override
    public void run() {
        if (greetingMessageId != ChatboxManager.NO_MESSAGE_ID) {
            return;
        }

        greetingMessageId = chatboxManager.addSystemText(
                "Hello, I'm TopVoiceControl");
    }

    @Override
    public void close() {
        if (greetingMessageId == ChatboxManager.NO_MESSAGE_ID) {
            return;
        }

        chatboxManager.removeMessage(greetingMessageId);
        greetingMessageId = ChatboxManager.NO_MESSAGE_ID;
    }
}
```

The returned message ID preserves the existing `Action.close()` behavior: the greeting is removed
when `Actor` starts a new action. A normal ASR or match-history message does not need to retain its
ID and remains until the bounded history prunes it.

Future actions can use the same dependency for images:

```java
long messageId = chatboxManager.addSystemImage(
        R.drawable.ic_launcher_foreground,
        "Result generated by the current action");

// In close(), when the image belongs only to that active action:
chatboxManager.removeMessage(messageId);
```

For a dynamically generated image:

```java
Bitmap preview = createDisplaySizedPreview();
long messageId = chatboxManager.addSystemImage(
        preview,
        "Preview generated by the current action");
```

Always provide a meaningful content description. If an image is purely decorative, pass an empty
string deliberately rather than a filename.

## Step 9: Build and Verify

Build the application:

```bash
./gradlew :app:assembleDebug
```

Then verify these behaviors on a device:

1. Several partial ASR callbacks update one right-aligned user bubble rather than adding rows.
2. The final ASR callback commits that bubble, and the next utterance starts a new bubble.
3. A matcher result adds a left-aligned system bubble.
4. More messages than fit on screen scroll normally and reuse off-screen row views.
5. After 100 records, a new record removes the oldest model entry.
6. Running `Greet` adds its system message, and matching a new action removes that greeting.
7. Drawable and bitmap messages render in image rows without stale recycled images.

Do not test the limit by changing it to the number of messages currently visible. Text wrapping,
font scale, rotation, and image height all change that number. Keep the history bounded by a clear
data limit and let `RecyclerView` manage the visible capacity.
