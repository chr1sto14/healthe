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
    private TextView textFreq = null;
    private TextView textPtp = null;

    float[] averageHrArray = new float[5];
    int hrCount = 0;
    int ptpCount = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio__record);

        setButtonHandlers();
        //enableButtons(false);

        int bufferSize = AudioRecord.getMinBufferSize(RECORDER_SAMPLERATE,
                RECORDER_CHANNELS, RECORDER_AUDIO_ENCODING);


        // set frequency and amplitude textboxes
        textFreq = (TextView) findViewById(R.id.textFreq);
        textPtp = (TextView) findViewById(R.id.amplitude);

    }

    private void setButtonHandlers() {
        (findViewById(R.id.btnStart)).setOnClickListener(btnClick);
    }

    int BufferElements2Rec = RECORDER_SAMPLERATE*4; // want to play 2048 (2K) since 2 bytes we use only 1024
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



                // digital filter ?

                double smoothing = 15;
                double value = sData[0];
                for (int i = 1; i < sData.length; i++) {
                    double currentValue = sData[i];
                    value += (currentValue - value) / smoothing;
                    sData[i] = (short)value;
                }

                // update the UI
                sDataShort = new soundTransferParams(sData);


                Audio_Record.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        new calcFreq().execute(sDataShort);
                        new calcPTP().execute(sDataShort);
                    }
                });
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


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

            int searchSize = 4594; // This should be equal to have the wavelength of the incoming frequency
            int searchPositionLeft, searchPositionRight;
            int searchPositionLeftMax = searchSize;
            int searchPositionRightMax = searchSize;
            // Amplitude

            short localMin = 0;
            short localMax;
            int ptpCount = 0;
            boolean peakTroughFlag = false;
            float[] PtPvals = new float[5];
            boolean troughCheck = false;
            boolean peakCheck = false;

            for (int i = 1; i < numSamples - 1; i++) {

                if (i < searchSize) {
                    searchPositionLeftMax = i - 1;
                    searchPositionRightMax = searchSize - 1;
                } else if ((i > searchSize) && (i < (numSamples - searchSize))) {
                    searchPositionLeftMax = searchSize - 1;
                    searchPositionRightMax = searchSize - 1;
                } else if (i > (numSamples - searchSize)) {
                    searchPositionLeftMax = searchSize - 1;
                    searchPositionRightMax = numSamples - i - 1;
                }

                // Look for troughs
                if (!peakTroughFlag) {
                    //simultaneous for-loop
                    for (searchPositionLeft = 1, searchPositionRight = 1; (searchPositionLeft <= searchPositionLeftMax || searchPositionRight <= searchPositionRightMax); searchPositionLeft++, searchPositionRight++) {
                        //ensure that no index out of bounds are thrown
                        if (searchPositionLeft > searchPositionLeftMax) { searchPositionLeft = searchPositionLeftMax; }
                        if (searchPositionRight > searchPositionRightMax) { searchPositionRight = searchPositionRightMax; }

                        // Compare values to left and right of i position
                        if ((shortArr[i] < shortArr[i - searchPositionLeft]) && (shortArr[i] < shortArr[i + searchPositionRight])) {
                            troughCheck = true; //peak found for this i position
                        } else {
                            troughCheck = false; // doesn't match, so move to next point
                            break;
                        }
                    }

                    if (troughCheck) {
                        localMin = shortArr[i]; // capture the local min
                        peakTroughFlag = !peakTroughFlag;
                    }
                }
                // Look for peaks
                else if (peakTroughFlag) {
                    //simultaneous for-loop
                    for (searchPositionLeft = 1, searchPositionRight = 1; (searchPositionLeft <= searchPositionLeftMax || searchPositionRight <= searchPositionRightMax); searchPositionLeft++, searchPositionRight++) {
                        //ensure that no index out of bounds are thrown
                        if (searchPositionLeft > searchPositionLeftMax) { searchPositionLeft = searchPositionLeftMax; }
                        if (searchPositionRight > searchPositionRightMax) { searchPositionRight = searchPositionRightMax; }

                        // Compare values to left and right of i position
                        if ((shortArr[i] < shortArr[i - searchPositionLeft]) && (shortArr[i] < shortArr[i + searchPositionRight])) {
                            peakCheck = true; //peak found for this i position
                        } else {
                            peakCheck = false; // doesn't match, so move to next point
                            break;
                        }
                    }

                    if (peakCheck) {
                        if (ptpCount < 4 ) { ptpCount++; } else { ptpCount = 0; } // iterate HR count
                        localMax = shortArr[i]; // capture the local max
                        PtPvals[ptpCount] = (float) (localMax - localMin);
                        publishProgress(Math.abs(calculateAverage(PtPvals)/Float.MAX_VALUE));

                        peakTroughFlag = !peakTroughFlag;

                    }
                }

            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Float... values) {
            textPtp.setText(String.valueOf(((float)(values[0]))));
        }

        @Override
        protected void onPostExecute(Void result) {
            //System.out.println("Finished calculating PTP...");
        }
    }

    class calcFreq extends  AsyncTask<soundTransferParams,Float,Void> {

        //public calcFreq()

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

            int searchSize = 4594; // This should be equal to have the wavelength of the incoming frequency
            int searchPositionLeft, searchPositionRight;
            int searchPositionLeftMax = searchSize;
            int searchPositionRightMax = searchSize;

            // Frequency
            for (int i = 1; i < numSamples - 1; i++) {
                if (i < searchSize) {
                    searchPositionLeftMax = i - 1;
                    searchPositionRightMax = searchSize - 1;
                } else if ((i > searchSize) && (i < (numSamples - searchSize))) {
                    searchPositionLeftMax = searchSize - 1;
                    searchPositionRightMax = searchSize - 1;
                } else if (i > (numSamples - searchSize)) {
                    searchPositionLeftMax = searchSize - 1;
                    searchPositionRightMax = numSamples - i - 1;
                }

                //simultaneous for-loop
                for (searchPositionLeft = 1, searchPositionRight = 1; (searchPositionLeft <= searchPositionLeftMax || searchPositionRight <= searchPositionRightMax); searchPositionLeft++, searchPositionRight++) {
                    //ensure that no index out of bounds are called
                    if (searchPositionLeft > searchPositionLeftMax) { searchPositionLeft = searchPositionLeftMax; }
                    if (searchPositionRight > searchPositionRightMax) { searchPositionRight = searchPositionRightMax; }

                    // Compare values to left and right of i position
                    if ((shortArr[i] > shortArr[i - searchPositionLeft]) && (shortArr[i] > shortArr[i + searchPositionRight]) && (shortArr[i] > 120)) { // 120 value to prevent noise 0.05*Short.MAX_VALUE
                        freqPeak = true; //peak found for this i position
                    } else {
                        freqPeak = false; // doesn't match, so move to next point
                        break;
                    }
                }

                if (freqPeak) { // peak found, so iterate peaks
                    numPeaks++;
                    System.out.print(shortArr[i] + " ");
                }

            }

            if (hrCount < 4 ) { hrCount++; } else { hrCount = 0; } // iterate HR count
            float numSecondsRecorded = (float)numSamples / (float)RECORDER_SAMPLERATE;
            System.out.println("Num peaks: " + numPeaks);
            float frequency = numPeaks/numSecondsRecorded;
            averageHrArray[hrCount] = frequency;
            publishProgress(calculateAverage(averageHrArray));

            return null;
        }

        @Override
        protected void onProgressUpdate(Float... values) {
            textFreq.setText(String.valueOf(((float)(values[0]))));
        }

        @Override
        protected void onPostExecute(Void result) {
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
                        Toast.makeText(getApplicationContext(), "Recording pulse signal", Toast.LENGTH_SHORT).show();
                        startRecording();
                        break;
                    } else {
                        //enableButtons(false);
                        Toast.makeText(getApplicationContext(), "Recording stopped.", Toast.LENGTH_SHORT).show();
                        stopRecording();

                        textPtp.setText("0");
                        textFreq.setText("0");
                        break;
                    }

                }
            }
        }
    };

   @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
       switch (keyCode) {
           case KeyEvent.KEYCODE_VOLUME_DOWN:
               return true;
           case KeyEvent.KEYCODE_VOLUME_UP:
               return true;
       }
       return super.onKeyDown(keyCode, event);
    }
}