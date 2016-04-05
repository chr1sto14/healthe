package net.christophertmiller.healthecombo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class Audio_Record extends Activity {
    private static final int RECORDER_SAMPLERATE = 22050;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private short[] sData = null;
    private AudioRecord recorder = null;
    private AudioTrack at = null;
    private Thread recordingThread = null;
    private Thread playingThread = null;
    private Thread calculatingPTP = null;
    private boolean isRecording = false;
    private boolean mStartPlaying = false;
    private TextView textBox = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio__record);

        setButtonHandlers();
        enableButtons(false);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);


        // set textbox
        textBox = (TextView) findViewById(R.id.textPtP);

    }

    private void setButtonHandlers() {
        ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnPlay)).setOnClickListener(btnClick);
        ((Button) findViewById(R.id.btnPtP)).setOnClickListener(btnClick);
    }

    private void enableButton(int id, boolean isEnable) {
        ((Button) findViewById(id)).setEnabled(isEnable);
    }

    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
        enableButton(R.id.btnStop, isRecording);
    }

    int BufferElements2Rec = 1024; // want to play 2048 (2K) since 2 bytes we use only 1024
    int BytesPerElement = 2; // 2 bytes in 16bit format

    private void startRecording() {

        recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                RECORDER_SAMPLERATE, RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING, BufferElements2Rec * BytesPerElement);

        recorder.startRecording();
        isRecording = true;
        recordingThread = new Thread(new writeAudioDataToFile());
        recordingThread.start();
    }

    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF); //grab the first 8 bits
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8); //grab the next 8 bits
            sData[i] = 0;
        }
        return bytes;

    }

    private class writeAudioDataToFile implements Runnable {
        @Override
        public void run() {
            // Write the output audio in byte

            //String filePath = "/sdcard/voice8K16bitmono.pcm";
            sData = new short[BufferElements2Rec];

           /* FileOutputStream os = null;
            try {
                os = new FileOutputStream(filePath);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }*/

            while (isRecording) {
                // gets the voice output from microphone to byte format

                recorder.read(sData, 0, BufferElements2Rec);
                //System.out.println("Short writing to file " + sData.toString());
                // // writes the data to file from buffer
                // // stores the voice buffer
                //byte bData[] = short2byte(sData);
                //os.write(bData, 0, BufferElements2Rec * BytesPerElement);

                // update the UI
                Audio_Record.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new calcPTP().execute();
                    }
                });

            }
            /*try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
    }

    private void stopRecording() {
        // stops the recording activity
        if (null != recorder) {
            isRecording = false;
            recorder.stop();
            recorder.release();
            recorder = null;
            recordingThread = null;
        }
    }


   private void onPush() {
        if (mStartPlaying) {
            int intSize = android.media.AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
            at = new AudioTrack(AudioManager.STREAM_MUSIC, RECORDER_SAMPLERATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, intSize, AudioTrack.MODE_STREAM);

            at.play();
            playingThread = new Thread(new Runnable() {
                public void run() {
                    startPlaying();
                }
            }, "AudioPlayer Thread");
            playingThread.start();

        } else {
            stopPlaying();
        }
    }

    // ----PLAYBACK---------
    private void startPlaying() {

        //Reading the file..
        byte[] byteData = null;
        File file = null;
        file = new File("/sdcard/voice8K16bitmono.pcm"); // for ex. path= "/sdcard/samplesound.pcm" or "/sdcard/samplesound.wav"
        byteData = new byte[(int) file.length()];
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            in.read(byteData, 0, (int) file.length());
            in.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        short shortArr[] = new short[byteData.length/2];

        // short value output file
        File file1 = null;
        file1 = new File("/sdcard/graphMe.txt");
        PrintWriter writer = null;
        try {
            writer = new PrintWriter(file1);
        } catch (IOException e) {
            e.printStackTrace();
        }

        for ( int index = 0; index < byteData.length; index=index+2) {
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.put(byteData[index]);
            bb.put(byteData[index + 1]);
            shortArr[index/2]=bb.getShort(0);
            //System.out.println("Byte -> short: " + shortArr[index/2]);

            //Write the short value to file
            writer.println(shortArr[index/2]);
        }

        writer.close();


        // Set and push to audio track..
        while (mStartPlaying) {
            if (at != null) {

                // Write the byte array to the track
                at.write(byteData, 0, byteData.length);

            } else {
                Log.d("TCAudio", "audio track is not initialised ");
            }


        }
    }

    private void stopPlaying() {
        if (at != null) {
            mStartPlaying = false;
            at.stop();
            at.release();
            at = null;
            playingThread = null;



        }
    }


    class calcPTP extends  AsyncTask<Void,Double,Void> {

        private short[] shortArr = null;

        @Override
        protected void onPreExecute() {
            shortArr = sData;

        }

        @Override
        protected Void doInBackground(Void... params) {
            // ++++ Search for peaks and troughs in shortArr. ++++

            short localMin = 0;
            short localMax = 0;
            int ptpCount = 0;
            boolean peakTroughFlag = false;
            double Ptp = 0;
            double[] PtPvals = new double[10];
            for (int i = 5; i < shortArr.length - 5; i++) {
                // Look for troughs
                if (!peakTroughFlag) {
                    // value < 5 values to left && value < 5 values to right
                    if (((shortArr[i] < shortArr[i - 1]) && (shortArr[i] < shortArr[i - 2]) &&
                            (shortArr[i] < shortArr[i - 3]) && (shortArr[i] < shortArr[i - 4]) && (shortArr[i] < shortArr[i - 5])) &&
                            ((shortArr[i] < shortArr[i + 1]) && (shortArr[i] < shortArr[i + 2]) &&
                                    (shortArr[i] < shortArr[i + 3]) && (shortArr[i] < shortArr[i + 4]) && (shortArr[i] < shortArr[i + 5]))) {
                        localMin = shortArr[i]; // capture the local min
                        peakTroughFlag = !peakTroughFlag;
                    }
                } else if (peakTroughFlag) {
                    // value > 5 values to left && value > 5 values to right
                    if (((shortArr[i] > shortArr[i - 1]) && (shortArr[i] > shortArr[i - 2]) &&
                            (shortArr[i] > shortArr[i - 3]) && (shortArr[i] > shortArr[i - 4]) && (shortArr[i] > shortArr[i - 5])) &&
                            ((shortArr[i] > shortArr[i + 1]) && (shortArr[i] > shortArr[i + 2]) &&
                                    (shortArr[i] > shortArr[i + 3]) && (shortArr[i] > shortArr[i + 4]) && (shortArr[i] > shortArr[i + 5]))) {
                        localMax = shortArr[i]; // capture the local min
                        PtPvals[ptpCount] = (double)(localMax - localMin);
                        ptpCount++;


                        // This will have to be removed for a heart beat signal
                        if (ptpCount == 2) { // found 10 values, average values, reset ptpCount
                            Ptp = calculateAverage(PtPvals);
                            publishProgress(Ptp);
                            ptpCount = 0;
                            try {
                                Thread.sleep(10);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                        }

                        peakTroughFlag = !peakTroughFlag;
                    }
                }

            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Double... values) {
            textBox.setText(String.valueOf(values[0]));
        }

        @Override
        protected void onPostExecute(Void result) {
            //System.out.println("Finished calculating PTP...");
        }
    }

    // helper method to calculate Integer[] average
    private double calculateAverage(double[] marks) {
        double sum = 0;
        for (double mark : marks) {
            sum += mark;
        }
        return sum / marks.length;

    }

    //-------BUTTON CONTROL------------
    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    if (mStartPlaying) { break; }
                    enableButtons(true);
                    Toast.makeText(getApplicationContext(), "Recording raw audio.", Toast.LENGTH_SHORT).show();
                    startRecording();
                    break;
                }
                case R.id.btnStop: {
                    if (mStartPlaying) { break; }
                    enableButtons(false);
                    Toast.makeText(getApplicationContext(), "Audio saved.", Toast.LENGTH_SHORT).show();
                    stopRecording();
                    break;
                }
                case R.id.btnPlay: {
                    if (!mStartPlaying) {
                        Toast.makeText(getApplicationContext(), "Playing raw audio.", Toast.LENGTH_SHORT).show();
                        ((Button) findViewById(R.id.btnPlay)).setText("Stop playing");
                    } else {
                        Toast.makeText(getApplicationContext(), "Stopped playing raw audio.", Toast.LENGTH_SHORT).show();
                        ((Button) findViewById(R.id.btnPlay)).setText("Start playing");
                    }
                    mStartPlaying  = !mStartPlaying;
                    onPush();

                    break;
                }
                case R.id.btnPtP: {
                    if (mStartPlaying || isRecording) { break; }
                    new calcPTP().execute();
                }
            }
        }
    };

   @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }
}