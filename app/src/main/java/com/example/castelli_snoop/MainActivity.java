package com.example.castelli_snoop;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.airbnb.lottie.LottieAnimationView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
    );

    private LottieAnimationView lottieAnimationView;
    private Button flashlightToggle, flashlightLock, singButton, stopButton, muteButton;
    private android.view.View backgroundView;
    private SensorManager sensorManager;
    private Sensor lightSensor;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashlightOn = false;
    private boolean isFlashlightLocked = false;
    private boolean isDarkMode = false;

    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Handler handler;
    private MediaPlayer mediaPlayer;
    private MediaPlayer songPlayer;
    private MediaPlayer versePlayer;
    private Random random;

    private static final float DARK_THRESHOLD = 10.0f;
    private static final long RANDOM_ANIMATION_MIN = 5000;
    private static final long RANDOM_ANIMATION_MAX = 10000;
    private static final long RANDOM_SOUND_MIN = 8000;
    private static final long RANDOM_SOUND_MAX = 15000;

    private boolean isPlayingSound = false;
    private boolean isAnimating = false;
    private boolean isSongPlaying = false;
    private boolean isMuted = false;
    private boolean canRecordAudio = true;
    private boolean isBackgroundFlashing = false;
    private int currentSongIndex = 0;
    private Handler animationSpeedHandler;
    private Runnable flashRunnable;

    private int[] songFiles = {
            R.raw.song1,
            R.raw.song2,
            R.raw.song3
    };

    private int[] verseFiles = {
            R.raw.verse1,
            R.raw.verse2,
    };

    private int scaredSoundFile = R.raw.scared;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lottieAnimationView = findViewById(R.id.lottieAnimationView);
        backgroundView = findViewById(R.id.main);
        flashlightToggle = findViewById(R.id.flashlightToggle);
        flashlightLock = findViewById(R.id.flashlightLock);
        singButton = findViewById(R.id.singButton);
        stopButton = findViewById(R.id.stopButton);
        muteButton = findViewById(R.id.muteButton);

        handler = new Handler(Looper.getMainLooper());
        animationSpeedHandler = new Handler(Looper.getMainLooper());
        random = new Random();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        setupButtons();
        checkPermissions();

        scheduleRandomSound();
    }

    private void setupButtons() {
        flashlightToggle.setOnClickListener(v -> {
            if (!isFlashlightLocked) {
                toggleFlashlight(!isFlashlightOn);
            }
        });

        flashlightLock.setOnClickListener(v -> {
            isFlashlightLocked = !isFlashlightLocked;
            if (isFlashlightLocked) {
                flashlightLock.setBackgroundColor(Color.parseColor("#4CAF50"));
                flashlightLock.setText("BLOCCATA");
            } else {
                flashlightLock.setBackgroundColor(Color.parseColor("#9E9E9E"));
                flashlightLock.setText("MANTIENI");
            }
        });

        singButton.setOnClickListener(v -> {
            playNextSong();
        });

        stopButton.setOnClickListener(v -> {
            stopSong();
        });

        muteButton.setOnClickListener(v -> {
            isMuted = !isMuted;
            if (isMuted) {
                muteButton.setBackgroundColor(Color.parseColor("#9E9E9E"));
                muteButton.setText("MUTO");
                stopAllAudio();
            } else {
                muteButton.setBackgroundColor(Color.parseColor("#8d10cc"));
                muteButton.setText("AUDIO");
            }
        });
    }

    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA
        };

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        } else {
            startRecording();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startRecording();
            } else {
                Toast.makeText(this, "Permessi necessari non concessi", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                BUFFER_SIZE
        );

        isRecording = true;
        audioRecord.startRecording();

        new Thread(() -> {
            short[] buffer = new short[BUFFER_SIZE];
            File audioFile = new File(getCacheDir(), "recorded_audio.pcm");
            FileOutputStream fos = null;

            while (isRecording) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    double sum = 0;
                    for (short s : buffer) {
                        sum += Math.abs(s);
                    }
                    double amplitude = sum / buffer.length;

                    if (amplitude > 1000 && canRecordAudio && !isPlayingSound && !isSongPlaying) {
                        try {
                            if (fos == null) {
                                fos = new FileOutputStream(audioFile);
                            }
                            byte[] byteBuffer = new byte[buffer.length * 2];
                            for (int i = 0; i < buffer.length; i++) {
                                byteBuffer[i * 2] = (byte) (buffer[i] & 0xff);
                                byteBuffer[i * 2 + 1] = (byte) ((buffer[i] >> 8) & 0xff);
                            }
                            fos.write(byteBuffer);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else if (fos != null && !isPlayingSound && canRecordAudio && amplitude <= 1000) {
                        try {
                            fos.close();
                            fos = null;
                            playRecordedAudio(audioFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void playRecordedAudio(File audioFile) {
        if (isPlayingSound || isMuted) return;

        handler.post(() -> {
            try {
                File wavFile = new File(getCacheDir(), "temp_audio.wav");
                convertPcmToWav(audioFile, wavFile);

                if (mediaPlayer != null) {
                    mediaPlayer.release();
                }

                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(wavFile.getAbsolutePath());
                mediaPlayer.prepare();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    mediaPlayer.setPlaybackParams(
                            mediaPlayer.getPlaybackParams().setSpeed(1.5f).setPitch(1.2f)
                    );
                }

                isPlayingSound = true;
                canRecordAudio = false;
                mediaPlayer.start();
                playAnimationForRepeat();

                mediaPlayer.setOnCompletionListener(mp -> {
                    // Ritardo prima di riabilitare la registrazione per evitare il loop
                    handler.postDelayed(() -> {
                        isPlayingSound = false;
                        canRecordAudio = true;
                    }, 500); // Mezzo secondo di pausa
                });

            } catch (IOException e) {
                e.printStackTrace();
                isPlayingSound = false;
                canRecordAudio = true;
            }
        });
    }

    private void convertPcmToWav(File pcmFile, File wavFile) throws IOException {
        FileInputStream fis = new FileInputStream(pcmFile);
        FileOutputStream fos = new FileOutputStream(wavFile);

        long totalDataLen = fis.getChannel().size() + 36;
        long totalAudioLen = fis.getChannel().size();
        int channels = 1;
        long byteRate = SAMPLE_RATE * channels * 16 / 8;

        byte[] header = new byte[44];
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = (byte) channels; header[23] = 0;
        header[24] = (byte) (SAMPLE_RATE & 0xff);
        header[25] = (byte) ((SAMPLE_RATE >> 8) & 0xff);
        header[26] = (byte) ((SAMPLE_RATE >> 16) & 0xff);
        header[27] = (byte) ((SAMPLE_RATE >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (channels * 16 / 8);
        header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        fos.write(header, 0, 44);

        byte[] buffer = new byte[1024];
        int read;
        while ((read = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, read);
        }

        fis.close();
        fos.close();
    }

    private void playAnimationForRepeat() {
        if (isAnimating) return;
        isAnimating = true;
        lottieAnimationView.setSpeed(2.0f);
        lottieAnimationView.setProgress(0f);
        lottieAnimationView.playAnimation();

        handler.postDelayed(() -> {
            isAnimating = false;
            lottieAnimationView.setSpeed(1.0f);
        }, 2000);
    }

    private void playAnimation() {
        if (isAnimating) return;
        isAnimating = true;
        lottieAnimationView.setSpeed(1.0f);
        lottieAnimationView.setProgress(0f);
        lottieAnimationView.playAnimation();

        handler.postDelayed(() -> {
            isAnimating = false;
        }, 2000);
    }

    private void playAnimationContinuous(int duration) {
        if (isAnimating) return;
        isAnimating = true;
        lottieAnimationView.setSpeed(1.0f);
        lottieAnimationView.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        lottieAnimationView.setProgress(0f);
        lottieAnimationView.playAnimation();

        handler.postDelayed(() -> {
            lottieAnimationView.setRepeatCount(0);
            lottieAnimationView.cancelAnimation();
            isAnimating = false;
        }, duration);
    }

    private void playAnimationWithSpeedVariation(int duration) {
        if (isAnimating) return;
        isAnimating = true;
        lottieAnimationView.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
        lottieAnimationView.setProgress(0f);
        lottieAnimationView.playAnimation();

        animateSpeedContinuously(duration);

        handler.postDelayed(() -> {
            isAnimating = false;
            lottieAnimationView.setRepeatCount(0);
            lottieAnimationView.setSpeed(1.0f);
            animationSpeedHandler.removeCallbacksAndMessages(null);
        }, duration);
    }

    private void animateSpeedContinuously(int totalDuration) {
        final int cycleTime = 3000;
        final int steps = 30;
        final int stepDelay = cycleTime / steps;

        final Runnable speedCycle = new Runnable() {
            int step = 0;

            @Override
            public void run() {
                if (!isAnimating || isMuted) return;

                float progress = (float) (step % steps) / steps;
                float speed;

                if (progress < 0.5f) {
                    speed = 1.0f + (progress * 2.0f);
                } else {
                    speed = 2.0f - ((progress - 0.5f) * 2.0f);
                }

                lottieAnimationView.setSpeed(speed);

                step++;
                animationSpeedHandler.postDelayed(this, stepDelay);
            }
        };

        animationSpeedHandler.post(speedCycle);
    }

    private void scheduleRandomSound() {
        long delay = RANDOM_SOUND_MIN +
                random.nextInt((int)(RANDOM_SOUND_MAX - RANDOM_SOUND_MIN));

        handler.postDelayed(() -> {
            if (!isPlayingSound && !isSongPlaying && !isMuted) {
                playRandomVerse();
            }
            scheduleRandomSound();
        }, delay);
    }

    private void playRandomVerse() {
        if (isPlayingSound || isSongPlaying || isMuted) return;

        try {
            int randomVerse = verseFiles[random.nextInt(verseFiles.length)];

            if (versePlayer != null) {
                versePlayer.release();
            }

            versePlayer = MediaPlayer.create(this, randomVerse);
            if (versePlayer != null) {
                isPlayingSound = true;
                canRecordAudio = false;

                final int verseDuration = versePlayer.getDuration();
                versePlayer.start();
                playAnimationContinuous(verseDuration);

                versePlayer.setOnCompletionListener(mp -> {
                    isPlayingSound = false;
                    canRecordAudio = true;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            isPlayingSound = false;
            canRecordAudio = true;
        }
    }

    private void playNextSong() {
        if (isPlayingSound || isMuted) return;

        if (isSongPlaying) {
            stopSong();
            currentSongIndex = (currentSongIndex + 1) % songFiles.length;
        }

        try {
            if (songPlayer != null) {
                songPlayer.release();
            }

            songPlayer = MediaPlayer.create(this, songFiles[currentSongIndex]);
            if (songPlayer != null) {
                isSongPlaying = true;
                isPlayingSound = true;
                canRecordAudio = false;

                int duration = songPlayer.getDuration();
                songPlayer.start();
                playAnimationWithSpeedVariation(duration);

                stopButton.setEnabled(true);
                singButton.setEnabled(true);

                songPlayer.setOnCompletionListener(mp -> {
                    isSongPlaying = false;
                    isPlayingSound = false;
                    canRecordAudio = true;
                    stopButton.setEnabled(false);
                    currentSongIndex = (currentSongIndex + 1) % songFiles.length;
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "File canzone non trovato!", Toast.LENGTH_SHORT).show();
            isSongPlaying = false;
            isPlayingSound = false;
            canRecordAudio = true;
            stopButton.setEnabled(false);
            singButton.setEnabled(true);
        }
    }

    private void stopSong() {
        if (songPlayer != null && isSongPlaying) {
            songPlayer.stop();
            songPlayer.release();
            songPlayer = null;
        }
        isSongPlaying = false;
        isPlayingSound = false;
        canRecordAudio = true;
        stopButton.setEnabled(false);
        singButton.setEnabled(true);
        animationSpeedHandler.removeCallbacksAndMessages(null);
        lottieAnimationView.setSpeed(1.0f);
        lottieAnimationView.setRepeatCount(0);
        lottieAnimationView.cancelAnimation();
        isAnimating = false;

        currentSongIndex = (currentSongIndex + 1) % songFiles.length;
    }

    private void stopAllAudio() {
        if (songPlayer != null && isSongPlaying) {
            songPlayer.stop();
            songPlayer.release();
            songPlayer = null;
        }
        if (versePlayer != null && versePlayer.isPlaying()) {
            versePlayer.stop();
            versePlayer.release();
            versePlayer = null;
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        isSongPlaying = false;
        isPlayingSound = false;
        canRecordAudio = true;
        stopButton.setEnabled(false);
        singButton.setEnabled(true);
        animationSpeedHandler.removeCallbacksAndMessages(null);
        lottieAnimationView.setSpeed(1.0f);
        lottieAnimationView.setRepeatCount(0);
        lottieAnimationView.cancelAnimation();
        isAnimating = false;
    }

    private void playScaredSound() {
        if (isPlayingSound || isMuted) return;

        try {
            if (versePlayer != null) {
                versePlayer.release();
            }

            versePlayer = MediaPlayer.create(this, scaredSoundFile);
            if (versePlayer != null) {
                isPlayingSound = true;
                canRecordAudio = false;

                final int scaredDuration = versePlayer.getDuration();
                versePlayer.start();
                playAnimationContinuous(scaredDuration);

                versePlayer.setOnCompletionListener(mp -> {
                    isPlayingSound = false;
                    canRecordAudio = true;
                });
            } else {
                android.media.ToneGenerator toneGen = new android.media.ToneGenerator(
                        android.media.AudioManager.STREAM_MUSIC, 100);
                toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500);

                isPlayingSound = true;
                canRecordAudio = false;
                playAnimation();
                handler.postDelayed(() -> {
                    isPlayingSound = false;
                    canRecordAudio = true;
                }, 1000);
            }
        } catch (Exception e) {
            e.printStackTrace();
            android.media.ToneGenerator toneGen = new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC, 100);
            toneGen.startTone(android.media.ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500);

            isPlayingSound = true;
            canRecordAudio = false;
            playAnimation();
            handler.postDelayed(() -> {
                isPlayingSound = false;
                canRecordAudio = true;
            }, 1000);
        }
    }

    private void toggleFlashlight(boolean on) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cameraManager.setTorchMode(cameraId, on);
                isFlashlightOn = on;

                if (on) {
                    flashlightToggle.setBackgroundColor(Color.parseColor("#4CAF50"));
                    flashlightToggle.setText("ACCESA");
                } else {
                    flashlightToggle.setBackgroundColor(Color.parseColor("#F44336"));
                    flashlightToggle.setText("SPENTA");
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundFlashing() {
        if (isBackgroundFlashing) return;
        isBackgroundFlashing = true;

        flashRunnable = new Runnable() {
            boolean isWhite = false;

            @Override
            public void run() {
                if (!isBackgroundFlashing || !isDarkMode) return;

                if (isWhite) {
                    backgroundView.setBackgroundColor(Color.parseColor("#7dab48"));
                } else {
                    backgroundView.setBackgroundColor(Color.WHITE);
                }
                isWhite = !isWhite;

                handler.postDelayed(this, 50);
            }
        };

        handler.post(flashRunnable);
    }

    private void stopBackgroundFlashing() {
        isBackgroundFlashing = false;
        if (flashRunnable != null) {
            handler.removeCallbacks(flashRunnable);
        }
        backgroundView.setBackgroundColor(Color.parseColor("#7dab48"));
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];

            if (lux < DARK_THRESHOLD && !isDarkMode && !isFlashlightLocked) {
                isDarkMode = true;
                playScaredSound();
                toggleFlashlight(true);
                startBackgroundFlashing();
            } else if (lux >= DARK_THRESHOLD && isDarkMode && !isFlashlightLocked) {
                isDarkMode = false;
                toggleFlashlight(false);
                stopBackgroundFlashing();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isRecording = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
        }
        if (mediaPlayer != null) {
            mediaPlayer.release();
        }
        if (songPlayer != null) {
            songPlayer.release();
        }
        if (versePlayer != null) {
            versePlayer.release();
        }
        if (!isFlashlightLocked) {
            toggleFlashlight(false);
        }
        stopBackgroundFlashing();
        handler.removeCallbacksAndMessages(null);
        animationSpeedHandler.removeCallbacksAndMessages(null);
    }
}