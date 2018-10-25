package hw;
import java.io.*; 
import java.net.*; 
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.sound.sampled.*;
import java.lang.Double;
//import sun.misc.Signal;
//import sun.misc.SignalHandler;
import java.lang.Math;


/** MyAudio
 * 
 * @author Choong-Soo Lee
 * @version 1.1 Sep. 11, 2018.
 */
public class MyAudio {
	private int sr_cfg;
	private int ss_cfg;
	private float[] sampleRate = {8000, 16000};
	private int[] sampleSizeInBits = {16, 8};
	private int channels = 1;
	private boolean signed = true;
	private boolean bigEndian = true;
	private AudioFormat myAudioFormat;
	private DataLine.Info inputInfo, outputInfo;
	private TargetDataLine inputDataLine;
	private SourceDataLine outputDataLine;
	private byte[] inputData;

	private static final int SAMPLE_RATE_8000 = 0;
	private static final int SAMPLE_RATE_16000 = 1;
	private static final int SAMPLE_SIZE_8 = 1;
	private static final int SAMPLE_SIZE_16 = 2;

	/** Constructs a MyAudio with a sampling rate of 16000 Hz and samples of 16 bits.
	 * 
	 */
	public MyAudio() {
		this(SAMPLE_RATE_16000, SAMPLE_SIZE_16);
	}

	/** Constructs a MyAudio with the specified sampling rate and sample size.
	 * 
	 * @param sr	The sampling rate configuration. MyAudio.SAMPLE_RATE_8000 for 8000 Hz and MyAudio.SAMPLE_RATE_16000 for 16000Hz.
	 * @param ss	The sampling size configuration. MyAudio.SAMPLE_SIZE_8 for 8 bits and MyAudio.SAMPLE_SIZE_16 for 16 bits
	 */
	private MyAudio(int sr, int ss) {
		sr_cfg = sr % 2;
		ss_cfg = (ss - 1) % 2 + 1;
		myAudioFormat = new AudioFormat(sampleRate[sr_cfg], sampleSizeInBits[ss % 2], channels, signed, bigEndian);
		prepareAudio();
	}

	private void prepareAudio() {
		try {
			inputInfo = new DataLine.Info(TargetDataLine.class, myAudioFormat);
			outputInfo = new DataLine.Info(SourceDataLine.class, myAudioFormat);
			inputDataLine = (TargetDataLine)AudioSystem.getLine(inputInfo);
			outputDataLine = (SourceDataLine)AudioSystem.getLine(outputInfo);
			inputDataLine.open(myAudioFormat);
			outputDataLine.open(myAudioFormat);
			inputDataLine.start();
			outputDataLine.start();
		} catch (LineUnavailableException e) {
			System.err.println("Line unavailble: " + e);
			System.exit(-2);
		}
		inputData = new byte[ss_cfg];
	}

	/** Reads and returns a sound sample from the default audio input
	 * 
	 * @return		the integer value representing a sound sample. (-32768 to 32767)
	 */
	public int read() {
		int count = inputDataLine.read(inputData, 0, ss_cfg);
		if (count != ss_cfg) {
			System.err.println("Read the wrong number of bytes: " + count);
			System.exit(-1);
		}
		/*		int value = 0;
		for (int i = 0; i < (ss_cfg + 1); i++)
			value += (int)inputData[i] * Math.pow(Math.pow(2, 8), ss_cfg - i);
			return value;*/
		return byteArrayToInt(inputData);

	}

	/** Plays back the specified sound data to the default audio output
	 * 
	 * @param soundData		a byte array containing sound samples.
	 */
	public void play(byte[] soundData) {
		outputDataLine.write(soundData, 0, soundData.length);
	}

	/** Stops reading and writing to the audio device
	 * 
	 */
	public void stop() {
		inputDataLine.stop();
		outputDataLine.stop();
	}

	private int byteArrayToInt(byte[] b) {
		int size = b.length;
		int value = 0;
		if (size == SAMPLE_SIZE_8)
			value |= b[0] & 0x000000FF;
		else {
			value |= ((b[0] & 0x000000FF) << 8);
			value |= b[1] & 0x000000FF;
		}
		if ((b[0] & 0x80) > 0) {
			value |= 0xFFFF0000;
			if (size == 1)
				value |= 0x0000FF00;
		}

		return value;
	}

	/** Constructs a byte array from an integer.
	 * 
	 * @param n	sound sample in int. (-32768 to 32767)
	 * @return a two-byte array generated from the given int value
	 */
	public static byte[] intToByteArray(int n) {
		return intToByteArray(n, SAMPLE_SIZE_16);
	}
	
	/** Constructs a byte array from an integer.
	 * 
	 * @param n	sound sample in int. (-128 to 127 for 8 bits, -32768 to 32767 for 16 bits)
	 * @param sampleSize    sample size (MyAudio.SAMPLE_SIZE_8 for 8 bits, MyAudio.SAMPLE_SIZE_16 for 16 bits)
	 * @return a byte array generated from the given int value (length matches the given sample size)
	 */
	private static byte[] intToByteArray(int n, int sampleSize) {
		assert((n >= -32768) && (n <= 32767)) : "Sound sample out of range";
		assert((sampleSize == SAMPLE_SIZE_8) || (sampleSize == SAMPLE_SIZE_16)) : "Sample size must be SAMPLE_SIZE_8 (8 bits) or SAMPLE_SIZE_16 (16 bits)";

		// determine the byte size
		byte[] b = new byte[sampleSize];
		if (sampleSize == SAMPLE_SIZE_8) {
			assert((n >= -128) && (n <= 127)) : "Sound sample out of range for 8 bits";
			b[0] = (byte)(n & 0x000000FF);
			return b;
		}
		b[0] = (byte)((n & 0x0000FF00) >> 8);
		b[1] = (byte)(n & 0x000000FF);
		return b;      
	}

	/** Constructs a byte array from an int array.
	 * 
	 * @param samples	an array of sound samples in int. (-32768 to 32767)
	 * @return a byte array generated from the given int array  (length matches twice the length of the given int array)
	 */
	public static byte[] intArrayToByteArray(int[] samples) {
		return intArrayToByteArray(samples, SAMPLE_SIZE_16);
	}
	
	/** Constructs a byte array from an int array.
	 * 
	 * @param samples	an array of sound samples in int. (-128 to 127 for 8 bits, -32768 to 32767 for 16 bits)
	 * @param sampleSize    sample size (SAMPLE_SIZE_8 for 8 bits, SAMPLE_SIZE_16 for 16 bits) 
	 * @return a byte array generated from the given int array  (length matches the product of the given sample size and the length of the given int array)
	 */
	private static byte[] intArrayToByteArray(int[] samples, int sampleSize) {
		assert((sampleSize == SAMPLE_SIZE_8) || (sampleSize == SAMPLE_SIZE_16)) : "Sample size must be SAMPLE_SIZE_8 (8 bits) or SAMPLE_SIZE_16 (16 bits)";

		// determine the byte size
		byte[] b = new byte[sampleSize * samples.length];
		byte[] sampleByte = new byte[sampleSize];
		for (int i = 0; i < samples.length; i++) {
			sampleByte = intToByteArray(samples[i], sampleSize);
			if (sampleSize == SAMPLE_SIZE_8) {
				b[i] = sampleByte[0];
			} else {
				b[2 * i] = sampleByte[0];
				b[2 * i + 1] = sampleByte[1];
			}
		}
		return b;
	}
}

