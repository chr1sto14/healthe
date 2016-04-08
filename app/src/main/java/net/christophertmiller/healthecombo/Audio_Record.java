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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class Audio_Record extends Activity {
    private static final int RECORDER_SAMPLERATE = 22050;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

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
        //enableButtons(false);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);


        // set textbox
        textBox = (TextView) findViewById(R.id.textPtp);

    }

    private void setButtonHandlers() {
        ((ImageView) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
    }

   /* private void enableButton(int id, boolean isEnable) {
        ((ImageView) findViewById(id)).setEnabled(isEnable);
    }*/

/*    private void enableButtons(boolean isRecording) {
        enableButton(R.id.btnStart, !isRecording);
    }*/

    int BufferElements2Rec = RECORDER_SAMPLERATE; // want to play 2048 (2K) since 2 bytes we use only 1024
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

/*    //convert short to byte
    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];
        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF); //grab the first 8 bits
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8); //grab the next 8 bits
            sData[i] = 0;
        }
        return bytes;

    }*/

    private class writeAudioDataToFile implements Runnable {

        private short[] sData = null;
        private soundTransferParams sDataShort;
        @Override
        public void run() {
            // Write the output audio in byte

            //String filePath = "/sdcard/voice8K16bitmono.pcm";
            sData = new short[BufferElements2Rec];

/*            FileOutputStream os = null;
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
/*                byte bData[] = short2byte(sData);
                try {
                    os.write(bData, 0, BufferElements2Rec * BytesPerElement);
                } catch (IOException e) {
                    e.printStackTrace();
                }*/

                // update the UI
                sDataShort = new soundTransferParams(sData);

                Audio_Record.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new calcPTP().execute(sDataShort);
                    }
                });
/*                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/


            }
/*            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
    }

    private static class soundTransferParams {
        short[] myArrayShort;

        soundTransferParams(short[] inputArray) {
            this.myArrayShort = inputArray;
        }

        public short[] returnArray() {
            return this.myArrayShort;
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


    class calcPTP extends  AsyncTask<soundTransferParams,Float,Void> {

        //public calcPTP()

        private short[] shortArr = null;

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected Void doInBackground(soundTransferParams... params) {
            // ++++ Search for peaks and troughs in shortArr. ++++

            shortArr = params[0].returnArray();
            int numSamples = shortArr.length;
            int numPeaks = 0;
            boolean freqPeak = false;



            // Frequency
            for (int i = 1000; i < numSamples - 1000; i++) {
                // value > 5 values to left && value > 5 values to right
                for (int j = 1; j < 1000; j++) {
                        if ((shortArr[i] > shortArr[i - j]) && (shortArr[i] > shortArr[i + j])) {
                            freqPeak = true;
                        } else {
                            freqPeak = false;
                            break;
                        }
                }

                if (freqPeak) {
                    numPeaks++;
                    System.out.print(shortArr[i] + " ");
                }

            }

            float numSecondsRecorded = (float)numSamples / (float)RECORDER_SAMPLERATE;
            System.out.println(numPeaks);
            float frequency = numPeaks/numSecondsRecorded;
            publishProgress(frequency);


            // Amplitude

//            short localMin = 0;
//            short localMax = 0;
//            int ptpCount = 0;
//            boolean peakTroughFlag = false;
//            float Ptp = 0;
//            float PtPvals;
//            boolean troughCheck = false;
//            boolean peakCheck = false;
//
//            for (int i = 100; i < shortArr.length - 100; i++) {
//                // Look for troughs
//                if (!peakTroughFlag) {
//                    // value < 5 values to left && value < 5 values to right
//                    for (int j = 1; j < 100; j++) {
//                        if ((shortArr[i] < shortArr[i - j]) && (shortArr[i] < shortArr[i + j])) {
//                            troughCheck = true;
//                        } else {
//                            troughCheck = false;
//                            break;
//                        }
//                    }
//
//                    if (troughCheck) {
//                        localMin = shortArr[i]; // capture the local min
//                        peakTroughFlag = !peakTroughFlag;
//                    }
//                } else if (peakTroughFlag) {
//                    for (int j = 1; j < 100; j++) {
//                        if ((shortArr[i] > shortArr[i - j]) && (shortArr[i] > shortArr[i + j])) {
//                            peakCheck = true;
//                        } else {
//                            peakCheck = false;
//                            break;
//                        }
//                    }
//
//                    if (peakCheck) {
//                        localMax = shortArr[i]; // capture the local min
//
//                        PtPvals = (float)(localMax - localMin);
//                        publishProgress(PtPvals);
//
//
//                        peakTroughFlag = !peakTroughFlag;
//
//                    }
//                    // value > 5 values to left && value > 5 values to right
////                    if (((shortArr[i] > shortArr[i - 1]) && (shortArr[i] > shortArr[i - 2]) &&
////                            (shortArr[i] > shortArr[i - 3]) && (shortArr[i] > shortArr[i - 4]) && (shortArr[i] > shortArr[i - 5])) &&
////                            ((shortArr[i] > shortArr[i + 1]) && (shortArr[i] > shortArr[i + 2]) &&
////                                    (shortArr[i] > shortArr[i + 3]) && (shortArr[i] > shortArr[i + 4]) && (shortArr[i] > shortArr[i + 5]))) {
////                        localMax = shortArr[i]; // capture the local min
////                        PtPvals[ptpCount] = (float)(localMax - localMin);
////                        System.out.print(PtPvals[ptpCount] + " ");
////                        ptpCount++;
////
////
////                        // This will have to be removed for a heart beat signal
////                        if (ptpCount == 30) { // found 10 values, average values, reset ptpCount
////                            System.out.println();
////                            Ptp = calculateAverage(PtPvals);
////                            publishProgress(Ptp);
////                            ptpCount = 0;
////
////
////                        }
////
////                        peakTroughFlag = !peakTroughFlag;
//                    }
//                }
//
//            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Float... values) {
            textBox.setText(String.valueOf((int)((float)(values[0]))));
        }

        @Override
        protected void onPostExecute(Void result) {
            //System.out.println("Finished calculating PTP...");
        }
    }

    // helper method to calculate Integer[] average
    private float calculateAverage(float[] marks) {
        float sum = 0;
        for (float mark : marks) {
            sum += mark;
        }
        return sum / marks.length;

    }

    //-------BUTTON CONTROL------------
    private View.OnClickListener btnClick = new View.OnClickListener() {
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.btnStart: {
                    if (!isRecording) {
                        //enableButtons(true);
                        Toast.makeText(getApplicationContext(), "Recording raw audio.", Toast.LENGTH_SHORT).show();
                        startRecording();
                        break;
                    } else {
                        //enableButtons(false);
                        Toast.makeText(getApplicationContext(), "Audio saved.", Toast.LENGTH_SHORT).show();
                        stopRecording();
                        break;
                    }

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