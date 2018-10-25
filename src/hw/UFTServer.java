// author: Guinevere Gilman
// purpose: UDP image file transfer--server that receives and writes file

package hw;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Random;

public class UFTServer {
	
	public static void main(String args[]) {
		String filename = args[0];
		double corruption = Double.parseDouble(args[1]);
		double loss = Double.parseDouble(args[2]);
		int port = 24000;
		
		// set up output stream
		OutputStream outputStream = null;
		try {
			outputStream = new FileOutputStream(filename, true);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		// set up the socket
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(port);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		// variables for statistics
		int total_packets = 0;
		int duplicate_packets = 0;
		int total_acks = 0;
		int duplicate_acks = 0;
		
		// keep receiving packets until we're told we received the last one
		byte endOfFile = 0;
		byte[] prev = null;
		Random random = new Random();
		while (endOfFile == 0) {
			DatagramPacket receivePacket = null;
			byte[] receiveData = new byte[1003];
			try {
				// receive the packet from the client
				receivePacket = new DatagramPacket(receiveData, receiveData.length);
				socket.receive(receivePacket);
				total_packets++;
				
				// get ip & port of client
				InetAddress IPAddress = receivePacket.getAddress();
				int rec_port = receivePacket.getPort();
				
				// according to protocol, get the seq #, EoF, and image data
				byte[] sequence = Arrays.copyOfRange(receivePacket.getData(), 0, 2);
				endOfFile = receivePacket.getData()[2];
				byte[] image = Arrays.copyOfRange(receivePacket.getData(), 3, receivePacket.getLength());

				// first check if the packet will be corrupted
				if (random.nextDouble() >= corruption || endOfFile != 0) {
					// it's not corrupted
					int n = ((sequence[1] & 0xFF) << 8) | (sequence[0] & 0xFF);
					if (prev != null && n == (((prev[1] & 0xFF) << 8) | (prev[0] & 0xFF))) {
						System.out.println("Received packet #" + Integer.toString(n) + " (duplicate)");
						duplicate_packets++;
						duplicate_acks++;
					} else {
						System.out.println("Received packet #" + Integer.toString(n));
						// append the bytes to the image file
						outputStream.write(image);
					}
					prev = Arrays.copyOfRange(sequence, 0, 2);
					
					// send acknowledgement packet, if it isn't lost
					if (random.nextDouble() >= loss || endOfFile != 0) {
						DatagramPacket sendData = new DatagramPacket(sequence, sequence.length, IPAddress, rec_port);
						socket.send(sendData);
						System.out.println("Sent ACK #" + n);
						total_acks++;
					} else {
						System.out.println("Lost ACK #" + n);
						total_acks++;
					}
				} else {
					// it's corrupted, so send a duplicate acknowledgement
					System.out.println("Received a corrupt packet");
					
					if (prev != null) {
						duplicate_acks++;
						// send acknowledgement packet, if it isn't lost
						int n = ((prev[1] & 0xFF) << 8) | (prev[0] & 0xFF);
						if (random.nextDouble() >= loss || endOfFile != 0) {
							DatagramPacket sendData = new DatagramPacket(prev, prev.length, IPAddress, rec_port);
							socket.send(sendData);
							System.out.println("Sent ACK #" + n);
							total_acks++;
						} else {
							System.out.println("Lost Ack #" + n);
							total_acks++;
						}
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		System.out.println("Total packets received: " + Integer.toString(total_packets));
		System.out.println("Duplicate packets received: " + Integer.toString(duplicate_packets));
		System.out.println("Total ACKs sent: " + Integer.toString(total_acks));
		System.out.println("Duplicate ACKs sent: " + Integer.toString(duplicate_acks));
	}

}
