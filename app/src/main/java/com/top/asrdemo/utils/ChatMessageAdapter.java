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

public class ChatMessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TEXT = 1;
    private static final int VIEW_IMAGE = 2;

    private final List<ChatMessage> messages = new ArrayList<>();
    private final int maximumMessageCount;



    ChatMessageAdapter(int maximumMessageCount) {
        if (maximumMessageCount <= 0) {
            throw new IllegalArgumentException("max msg cnt must be positive");
        }
        this.maximumMessageCount = maximumMessageCount;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getKind() == ChatMessage.Kind.TEXT ? VIEW_TEXT : VIEW_IMAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
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
        return message.getSender() == ChatMessage.Sender.USER ? R.drawable.chat_bubble_user : R.drawable.chat_bubble_system;
    }

    private static int rowGravity(ChatMessage message) {
        return message.getSender() == ChatMessage.Sender.USER ? Gravity.END : Gravity.START;
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

    private static final class ImageMessageHolder extends RecyclerView.ViewHolder{
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
