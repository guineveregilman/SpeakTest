// Author: Guinevere Gilman
// Basic two-person VoIP software that can specify loss rate/sample interval

package hw;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;

public class Speak {
	
	// instance variables
	private String host;
	private int chunk_size;
	private double loss_rate;
	private boolean repeat;
	private boolean backup;
	private int port = 20443;
	static DatagramSocket socket = null;
	static InetAddress IPAddress = null;
	
	// constructor
	public Speak(String host, int chunk_size, double loss_rate, boolean repeat, boolean backup) {
		this.host = host;
		this.chunk_size = chunk_size;
		this.loss_rate = loss_rate;
		this.repeat = repeat;
		this.backup = backup;
	}
	
	public String get_host() {
		return host;
	}

	public int get_port() {
		return port;
	}
	
	public int get_chunkSize() {
		return chunk_size;
	}
	
	public double get_lossRate() {
		return loss_rate;
	}
	
	public boolean getRepeat() {
		return repeat;
	}
	
	public boolean getBackup() {
		return backup;
	}
	
	public static void main(String args[]) {
		// args[0] = host name
		// args[1] = chunk size
		// args[2] = loss rate
		
		Speak speak = new Speak("localhost", 40, 0, false, true);
		MyAudio myAudio = new MyAudio();
		
		// set up the socket
		try {
			socket = new DatagramSocket(speak.get_port());
			socket.setSoTimeout(200);
		} catch (SocketException e2) {
			e2.printStackTrace();
		}
		
		// get ip address
		try {
			IPAddress = InetAddress.getByName(speak.get_host());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		// thread writes data to the socket
		Runnable sampleAudio = new Runnable() {
			public void run() {
				int[] curr = new int[16 * speak.get_chunkSize()];
				int[] prev = new int[16 * speak.get_chunkSize()];
				byte[] data = new byte[curr.length*4];
				int count = 0;
				while (true) {					
					// sample audio for the specified interval
					curr[count] = myAudio.read();
					count++;
	
					if (count == curr.length) {
						// send the sampled audio, but first check if it needs to be dropped
						Random random = new Random();
						if (random.nextDouble() >= speak.get_lossRate()) {
							
							byte[] cByte = MyAudio.intArrayToByteArray(curr);
							byte[] pByte = MyAudio.intArrayToByteArray(prev);
							
							System.arraycopy(pByte, 0, data, 0, 2*curr.length);
							System.arraycopy(cByte, 0, data, 2*curr.length, 2*curr.length);
							DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, speak.get_port());
							try {
								socket.send(sendPacket);
							} catch (IOException e) {
								e.printStackTrace();
							}
							prev = curr;
						}
						count = 0;
					}
				}
			}
		};
		
		// thread that reads audio data from the socket and plays it
		Runnable playAudio = new Runnable() {
			public void run() {
				// keep a copy of the last received audio data :)
				byte[] last_played = null;
				byte[] prev = null;
				byte[] curr = null;
				
				// receive the audio data
				while(true) {
					DatagramPacket receivePacket = null;
					byte[] receiveData = new byte[64 * speak.get_chunkSize()];
					try {
						receivePacket = new DatagramPacket(receiveData, receiveData.length);
						socket.receive(receivePacket);
						curr = receivePacket.getData();
					} catch (IOException e) {
						// lost the packet
						e.printStackTrace();
						curr = null;
					}
					// play the audio
					if (prev != null) {
						// play the previous packet
						byte[] data = Arrays.copyOfRange(prev, prev.length/2, prev.length);
						myAudio.play(data);
						last_played = prev;
					} else if (curr != null) {
						// play the backup on this current packet
						if (speak.getBackup()) {
							byte[] data = Arrays.copyOfRange(curr, 0, curr.length/2);
							myAudio.play(data);
							last_played = curr;
						}
					} else if (last_played != null) {
						// repeat the last played packet
						if (speak.getRepeat()) {
							byte[] data = Arrays.copyOfRange(last_played, last_played.length/2, last_played.length);
							myAudio.play(data);
						}
					}
					// otherwise, silence
					prev = curr;
				}
			}
		};
		
		// make the threads
		Thread sample = new Thread(sampleAudio);
		Thread play = new Thread(playAudio);
		
		// start the threads, let them talk until they quit
		play.start();
		sample.start();
	}
	
}
