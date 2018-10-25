// author: Guinevere Gilman
// purpose: UDP image file transfer--client that reads and sends file
// citations: Matthijs and I figured out the bit shifting together

package hw;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Random;

import javax.imageio.ImageIO;

public class UFTClient {
	
	public static void main(String args[]) {
		String host = args[0];
		String filename = args[1];
		int chunk_size = Integer.parseInt(args[2]);
		double corruption = Double.parseDouble(args[3]);
		double loss = Double.parseDouble(args[4]);
		int port = 24000;
		
		// get ip address
		InetAddress IPAddress = null;
		try {
			IPAddress = InetAddress.getByName(host);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		
		// convert image to byte array
		BufferedImage bImage;
		byte[] data = null;
		try {
			bImage = ImageIO.read(new File(filename));
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
		    ImageIO.write(bImage, filename.substring(filename.length() - 3), bos);
		    data = bos.toByteArray();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		// set up the socket
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket();
			socket.setSoTimeout(200);
		} catch (SocketException e) {
			e.printStackTrace();
		}
		
		// variables for sending packets
		byte seq1 = 0;
		byte seq2 = 0;
		byte[] packet_data = new byte[chunk_size + 3];
		byte[] prev = null;
		int number = 0;
		double start = System.currentTimeMillis();
		Random random = new Random();
		double ertt = 0;
		double devrtt = 0;
		
		// variables for stats
		int total_packets = 0;
		int total_retransmit = 0;
		int total_acks = 0;
		int duplicate_acks = 0;
		int total_timeouts = 0;
		
		// send packets chunk by chunk
		for (int i = 0; i <= data.length; i++) {
			if (i % chunk_size == 0 || i == data.length) {
				if (i != 0) {
					// check if this is the last packet or not
					if (i == data.length) {
						packet_data[2] = 1;
					}
					
					boolean retransmit = false;  // used for making sure we don't count one packet as two retransmits
					
					// send the packet, if it is not going to be lost
					long srtt = System.currentTimeMillis();
					if (random.nextDouble() > loss || i == data.length) {
						// it's not lost
						DatagramPacket sendPacket = new DatagramPacket(packet_data, packet_data.length, IPAddress, port);
						try {
							socket.send(sendPacket);
						} catch (IOException e) {
							e.printStackTrace();
						}
						System.out.println("Sent packet #" + Integer.toString(number));
						total_packets++;
					} else {
						// it's lost
						System.out.println("Lost packet #" + Integer.toString(number));
						total_packets++;
						total_retransmit++;
						retransmit = true;
					}
					
					// receive the packet from the server
					try {
						// wait for acknowledgement packet
						DatagramPacket receivePacket = null;
						byte[] receiveData = new byte[2];
						
						receivePacket = new DatagramPacket(receiveData, receiveData.length);
						socket.receive(receivePacket);
						
						byte[] seq = receivePacket.getData();
						
						// check if it's corrupt
						if (random.nextDouble() > corruption || i == data.length) {
							// it's not corrupt
							int n = ((seq[1] & 0xFF) << 8) | (seq[0] & 0xFF);
							if (prev != null && n == (((prev[1] & 0xFF) << 8) | (prev[0] & 0xFF))) {
								// duplicate ACK, needs to be retransmitted
								System.out.println("Received ACK #" + Integer.toString(n) + " (duplicate)");
								i--;
								total_acks++;
								duplicate_acks++;
								total_retransmit++;
							} else {
								System.out.println("Received ACK #" + Integer.toString(n));
								prev = Arrays.copyOfRange(packet_data, 0, packet_data.length);
								total_acks++;
							}
						} else {
							// it is corrupt, need to retransmit
							System.out.println("Received a corrupt ACK");
							i--;
							if (!retransmit) {
								total_retransmit++;
							}
							total_acks++;
						}
					} catch (SocketTimeoutException e) {
						// need to retransmit
						System.out.println("Timeout for packet #" + Integer.toString(number));
						i--;
						total_timeouts++;
						if (!retransmit) {
							total_retransmit++;
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					
					// set the socket timeout
					srtt = System.currentTimeMillis() - srtt;
					ertt = 0.875 * ertt + 0.125 * srtt;
					if (ertt < 1) {
						ertt = 1;
					}
					devrtt = .75 * devrtt + .25 * Math.abs(srtt - ertt);
					int interval = (int) (ertt + 4 * devrtt);
					try {
						socket.setSoTimeout(interval);
					} catch (SocketException e) {
						e.printStackTrace();
					}
					
				}
				if (i < data.length && i % chunk_size == 0) {
					// start a new packet
					packet_data[0] = seq1;
					packet_data[1] = seq2;
					
					// increase sequence number
					number = ((seq2 & 0xFF) << 8) | (seq1 & 0xFF);
					seq1 = (byte) ((number + 1) & 0xFF);
					seq2 = (byte) (((number + 1) >> 8) & 0xFF);
					packet_data[2] = 0;
				}
			}
			if (i < data.length) {
				packet_data[(i % chunk_size) + 3] = data[i];
			}
		}
		System.out.println("Total packets transmitted: " + Integer.toString(total_packets));
		System.out.println("Retransmitted packets: " + Integer.toString(total_retransmit));
		System.out.println("Total ACKs received: " + Integer.toString(total_acks));
		System.out.println("Duplicate ACKs received: " + Integer.toString(duplicate_acks));
		System.out.println("Total timeouts: " + Integer.toString(total_timeouts));
		System.out.println("Elapsed time: " + Double.toString(System.currentTimeMillis() - start));
	}
	
}
