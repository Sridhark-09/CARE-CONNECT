package com.example.communityhealthworkerassistantance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class AIChatActivity extends BaseActivity {

    private static final int AUDIO_PERMISSION_CODE = 500;
    private static final String[] MODEL_NAMES = {"gemini-3.8-flash", "gemini-3.5-flash-lite"};
    private static final int MAX_HISTORY = 6;
    private static final int MAX_ROUNDS = 3;
    private static final long RETRY_DELAY_MS = 2500L;
    private static final String TYPING_NOTE = "Assistant is typing...";

    private TextView chatOutput;
    private EditText chatInput;
    private ScrollView chatScroll;
    private Button sendButton;

    private final StringBuilder transcript = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private final GenerativeModelFutures[] models = new GenerativeModelFutures[MODEL_NAMES.length];
    private boolean waiting = false;
    private String waitingNote = TYPING_NOTE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        chatOutput = findViewById(R.id.chatOutput);
        chatInput = findViewById(R.id.chatInput);
        chatScroll = findViewById(R.id.chatScroll);
        sendButton = findViewById(R.id.sendButton);
        Button micButton = findViewById(R.id.micButton);

        sendButton.setOnClickListener(v -> sendMessage());
        micButton.setOnClickListener(v -> requestAudioPermissionAndListen());
    }

    private GenerativeModelFutures getModel(int index) {
        if (models[index] == null) {
            GenerativeModel gm = FirebaseAI.getInstance(GenerativeBackend.googleAI())
                    .generativeModel(MODEL_NAMES[index]);
            models[index] = GenerativeModelFutures.from(gm);
        }
        return models[index];
    }

    private String buildPrompt(String question) {
        String languageName = LanguageHelper.find(LocaleHelper.getSavedLanguage(this)).englishName;

        StringBuilder sb = new StringBuilder();
        sb.append("You are CareConnect Assistant, a health information helper for community health workers in India. ");
        sb.append("Give general educational health information only. ");
        sb.append("Do not diagnose a patient and do not give medicine doses. ");
        sb.append("If the question describes danger signs such as trouble breathing, a very high or long-lasting fever, ");
        sb.append("severe dehydration, heavy bleeding, pregnancy problems, a very sick child, chest pain or fainting, ");
        sb.append("tell the worker to refer the patient to a doctor or the nearest health centre straight away. ");
        sb.append("Use simple words, short sentences and numbered steps. Keep the answer under 150 words. ");
        sb.append("Reply in ").append(languageName).append(".\n\n");

        if (!history.isEmpty()) {
            sb.append("Conversation so far:\n");
            for (int i = 0; i < history.size(); i++) {
                sb.append(history.get(i)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("Worker's question: ").append(question);
        return sb.toString();
    }

    private void sendMessage() {
        if (waiting) return;

        String question = chatInput.getText().toString().trim();
        if (question.isEmpty()) return;

        appendMessage("You: " + question);
        chatInput.setText("");
        waitingNote = TYPING_NOTE;
        setWaiting(true);

        askModel(buildPrompt(question), question, 0, 1);
    }

    private void askModel(String promptText, String question, int modelIndex, int round) {
        Content prompt = new Content.Builder()
                .addText(promptText)
                .build();

        ListenableFuture<GenerateContentResponse> future = getModel(modelIndex).generateContent(prompt);
        Executor mainExecutor = ContextCompat.getMainExecutor(this);

        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                setWaiting(false);

                String reply = result.getText();
                if (reply == null || reply.trim().isEmpty()) {
                    appendMessage("Assistant: (no answer received, please try again)");
                    return;
                }

                reply = reply.trim();
                appendMessage("Assistant: " + reply);
                remember(question, reply);
            }

            @Override
            public void onFailure(Throwable t) {
                String message = t.getMessage() == null ? "" : t.getMessage();
                String lower = message.toLowerCase();

                boolean modelProblem = lower.contains("404")
                        || lower.contains("not found")
                        || lower.contains("no longer available");

                boolean busy = lower.contains("503")
                        || lower.contains("high demand")
                        || lower.contains("overloaded")
                        || lower.contains("unavailable")
                        || lower.contains("try again later");

                // Try the backup model first
                if ((modelProblem || busy) && modelIndex + 1 < MODEL_NAMES.length) {
                    askModel(promptText, question, modelIndex + 1, round);
                    return;
                }

                // Both models are busy: wait a moment and start over
                if (busy && round < MAX_ROUNDS) {
                    waitingNote = "The AI server is busy. Retrying (attempt " + (round + 1)
                            + " of " + MAX_ROUNDS + ")...";
                    render();

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        if (!isFinishing() && !isDestroyed()) {
                            askModel(promptText, question, 0, round + 1);
                        }
                    }, RETRY_DELAY_MS);
                    return;
                }

                setWaiting(false);
                appendMessage("Assistant could not answer: " + friendlyError(message));
            }
        }, mainExecutor);
    }

    private String friendlyError(String message) {
        String lower = message.toLowerCase();

        if (lower.contains("503") || lower.contains("high demand")
                || lower.contains("overloaded") || lower.contains("unavailable")) {
            return "The AI server is very busy right now. Please try again in a minute.";
        }
        if (lower.contains("429") || lower.contains("quota") || lower.contains("rate")) {
            return "Too many requests right now. Wait a minute and try again.";
        }
        if (lower.contains("unable to resolve") || lower.contains("network") || lower.contains("timeout")) {
            return "No internet connection. Check your data or Wi-Fi.";
        }
        if (lower.contains("403") || lower.contains("permission") || lower.contains("api has not been used")) {
            return "The AI service is not enabled for this project. Check Firebase Console, AI Logic. (" + message + ")";
        }
        if (message.isEmpty()) {
            return "Unknown error. Please try again.";
        }
        return message;
    }

    private void remember(String question, String reply) {
        history.add("Worker: " + question);
        history.add("Assistant: " + reply);
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    private void appendMessage(String text) {
        if (transcript.length() > 0) {
            transcript.append("\n\n");
        }
        transcript.append(text);
        render();
    }

    private void setWaiting(boolean value) {
        waiting = value;
        sendButton.setEnabled(!value);
        render();
    }

    private void render() {
        String text = transcript.toString();
        if (waiting) {
            if (!text.isEmpty()) {
                text = text + "\n\n";
            }
            text = text + waitingNote;
        }
        chatOutput.setText(text);
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void requestAudioPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSION_CODE);
        } else {
            VoiceInputHelper.startListening(this, LocaleHelper.getSavedLanguage(this));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == AUDIO_PERMISSION_CODE
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            VoiceInputHelper.startListening(this, LocaleHelper.getSavedLanguage(this));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String spokenText = VoiceInputHelper.extractResult(requestCode, resultCode, data);
        if (spokenText != null) {
            chatInput.setText(spokenText);
        }
    }
}