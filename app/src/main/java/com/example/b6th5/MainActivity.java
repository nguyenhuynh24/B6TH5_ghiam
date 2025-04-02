package com.example.b6th5;
import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private MediaRecorder mediaRecorder;
    private MediaPlayer mediaPlayer;
    private Uri outputUri; // URI thay vì đường dẫn file
    private Button startButton, stopButton;
    private ListView recordingsListView;
    private List<String> recordingsList = new ArrayList<>();
    private List<Uri> recordingsUris = new ArrayList<>();
    private static final int REQUEST_PERMISSIONS = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        startButton = findViewById(R.id.startRecordingButton);
        stopButton = findViewById(R.id.stopRecordingButton);
        recordingsListView = findViewById(R.id.recordingsListView);

        if (!checkPermissions()) {
            requestPermissions();
        }

        startButton.setOnClickListener(v -> startRecording());
        stopButton.setOnClickListener(v -> stopRecording());

        loadRecordings();

        recordingsListView.setOnItemClickListener((parent, view, position, id) -> playRecording(recordingsUris.get(position)));
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        }, REQUEST_PERMISSIONS);
    }

    private void startRecording() {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        // Tạo URI trong MediaStore
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, "Recording_" + timeStamp + ".mp4");
        values.put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Recordings"); // Thư mục con trong Music

        ContentResolver contentResolver = getContentResolver();
        outputUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

        if (outputUri != null) {
            try {
                // Lấy FileDescriptor từ URI để MediaRecorder ghi trực tiếp
                ParcelFileDescriptor pfd = contentResolver.openFileDescriptor(outputUri, "w");
                if (pfd != null) {
                    FileDescriptor fd = pfd.getFileDescriptor();
                    mediaRecorder.setOutputFile(fd);
                    mediaRecorder.prepare();
                    mediaRecorder.start();
                    startButton.setEnabled(false);
                    stopButton.setEnabled(true);
                    Toast.makeText(this, "Recording started", Toast.LENGTH_SHORT).show();
                    pfd.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Recording failed", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void stopRecording() {
        if (mediaRecorder != null) {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;

            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            Toast.makeText(this, "Recording stopped", Toast.LENGTH_SHORT).show();

            // Cập nhật danh sách
            loadRecordings();
        }
    }

    private void loadRecordings() {
        recordingsList.clear();
        recordingsUris.clear();

        ContentResolver contentResolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media._ID};
        String selection = MediaStore.Audio.Media.DISPLAY_NAME + " LIKE ?";
        String[] selectionArgs = new String[]{"Recording_%"};

        Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, null);
        if (cursor != null) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME));
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                recordingsList.add(name);
                recordingsUris.add(contentUri);
            }
            cursor.close();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, recordingsList);
        recordingsListView.setAdapter(adapter);
    }

    private void playRecording(Uri uri) {
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            Toast.makeText(this, "Playing recording", Toast.LENGTH_SHORT).show();
            mediaPlayer.setOnCompletionListener(mp -> {
                mediaPlayer.release();
                mediaPlayer = null;
            });
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaRecorder != null) {
            mediaRecorder.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
    }
}