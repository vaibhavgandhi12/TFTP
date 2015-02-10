import java.io.*;
import java.net.*;
import java.util.*;

/**
 *
 *	Class TFTP
 *
 *	Author: Vaibhav Gandhi
 *
 *	This class implements a TFTP client that connects to standard TFTP servers.
 *
 *	Usage: javac TFTP.java; java TFTP;
 *			1. To connect to a TFTP server, eg. glados
 *				tftp> connect glados.cs.rit.edu
 *			2. To get a file from the server, eg. foo.txt
 *				tftp> get foo.txt
 *			3. To get all the commands
 *				tftp> ?
 *			4. To put a file
 *				tftp> put file
 *			5. To exit
 *				tftp> quit
 *	CONNECT is always followed by a server name and GET is followed by file name.
 *	TFTP client will not proceed forward util it gets these details.
 */ 
class TFTP {

	// Shared variables
	boolean connected;
	byte [] buffer;
	DatagramPacket packet;
	DatagramSocket socket;
	InetAddress server;
	Scanner sc;
	int tftpPort;
	String mode = "OCTET";
	
	/**
	 * Constructor that calls methods according to the command requested by the user
	 *
	 */
	public TFTP () {
		
		sc = new Scanner(System.in);
		connected = false;
		String command;
		
		while (true) {	
			System.out.print("tftp> ");
			command = sc.next();
			
			if (command.equalsIgnoreCase("connect")) {
			//	Processing the connect command and verifying the server
				String str = sc.next();
				try {
					server = InetAddress.getByName(str);
					tftpPort = 69;
					connected = true;
				} catch (UnknownHostException e) {
					System.err.println(str + ": unknown host");
				}
				
			} else if (command.equalsIgnoreCase("get"))	{
			//	Processing the get command
				String file = sc.next();
				if (connected) {
					try {
						receiveFunction(file);
					} catch (IOException e) {
						System.err.println("IOException thrown!");
					}
				} else {
					System.out.println("usage: <command>get 'file'</command> once connected");
				}
				
			} else if (command.equalsIgnoreCase("put")) {
			//	Processing the put command
				String put = sc.next();
				System.out.println("Error code 2: Access violation");
				
			} else if (command.equalsIgnoreCase("?")) {
			// Processing the help command
				System.out.println("Commands may be abbreviated.  Commands are:\n");
				System.out.println("connect\t\tconnect to remote tftp");
				System.out.println("put\t\tsend file");
				System.out.println("get\t\treceive file");
				System.out.println("quit\t\texit tftp");
				
			} else if (command.equalsIgnoreCase("quit")) {
			//	Processing the quit command
				System.exit(0);
				
			} else {
			//	Processing all invalid commands
				System.out.println("?Invalid command");
			}
		} 
	}
	
	/**
	 *
	 * receiveFunction performs byte assembly of the received file along with
	 * generating the request for the file and acknowledging data packets received
	 *
	 * params:
	 *	file : name of the requested file
	 *
	 */
	private void receiveFunction (String file) throws IOException, SocketException {
		
		//	Creating a read request and sending it to the server
		buffer = rrq(file);
		socket = new DatagramSocket();
		packet = new DatagramPacket(buffer, buffer.length, server, tftpPort);
		socket.setSoTimeout(1000);	//Setting a time out of 1000 milliseconds
		socket.send(packet);
		
		//	Special objects to enable writing to file 
		FileOutputStream fos = new FileOutputStream(new File(file));
		ByteArrayOutputStream object = new ByteArrayOutputStream();
		
		//	Setting up variables used for receiving the file
		boolean complete = false;
		byte [] packetNumber = new byte[]{0, 0};
		byte [] receiveBuffer;
		int number_of_retries = 0;
		DatagramPacket data_packet;
		
		long start = System.currentTimeMillis();
		
		//	Loop that breaks only once the data transmission stops from the server
		while (true) {
		
			receiveBuffer = new byte[516];
			data_packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
			
			//	Try - Catch to handle time out and there are only 3 retransmissions
			try {
				if (number_of_retries < 3) {
					
					socket.receive(data_packet);
					
					number_of_retries = 0;
					int length = data_packet.getLength();
					int port = data_packet.getPort();
					
					if (receiveBuffer[1] == 5) {
					//	Handles errors thrown by the server like File Not Found
						err(length, receiveBuffer);
						break;
					} else if (receiveBuffer[1] == 3) {
					//	Handling of data packet	
						if (packetNumber[0] == receiveBuffer[2] && packetNumber[1] == receiveBuffer[3]) {
						//	Retransmission if acknowledgment is lost
						//	Diagnosis: Data packet with the same block # is received
							ack(receiveBuffer[2], receiveBuffer[3], socket, data_packet, port);
						} else {
						//	Storing data received from the server in an object
							packetNumber[0] = receiveBuffer[2];
							packetNumber[1] = receiveBuffer[3];
							
							byte [] temp = new byte[length-4];
							for (int i = 0; i < (length-4); i++) {
								temp[i] = receiveBuffer[i+4];
							}
							
							object.write(temp);
							ack(packetNumber[0], packetNumber[1], socket, data_packet, port);
						}
					}
					
					if (length < 516) {
					//	Termination condition: When packet smaller than 516 is received
						complete = true;
						break;
					}
					
				} else {
				//	Retransmission timed out
					System.out.println("Transfer timed out.\n");
					break;
				}
			} catch (SocketTimeoutException e) {
				number_of_retries++;
				socket.send(packet);
				continue;
			}
		}

		if (complete) {
		//	Writing all the data to file
			long end = System.currentTimeMillis();
			System.out.println("Received " + object.size() + " bytes in " +
			 ((end - start) / 1) + " milliseconds");
			fos.write(object.toByteArray());
		}
		
		socket.close();
	}
	
	/**
	 *
	 * rrq generates a read request byte array
	 *
	 * @param:
	 *	file : name of the requested File
	 *
	 * @return a byte array containing the built rrq which is fed into a packet
	 *	
	 */
	private byte [] rrq (String file) {
		int index = 0;
		byte [] buffer = new byte[512];
		buffer[index++] = 0;
		buffer[index++] = 1;
		byte[] filename = file.getBytes();
		buffer = toByte(buffer, index, filename);
		index += filename.length;
		buffer[index++] = 0;
		byte[] mode_byte = mode.getBytes();
		buffer = toByte(buffer, index, mode_byte);
		index += mode_byte.length;
		buffer[index++] = 0;
		return buffer;
	}
	
	/**
	 *
	 * ack generates and sends an acknowledgement
	 *
	 * @param:
	 *	block1 & block2 : received blocks
	 *	socket : established connection to send ack over
	 *	packet : ack packet
	 *	port : ack is to be sent over this port
	 *
	 */
	 private void ack (byte block1, byte block2, DatagramSocket socket, DatagramPacket packet,
	 int port) throws IOException {
		byte [] ack = new byte[4];
		ack[0] = 0;
		ack[1] = 4;
		ack[2] = block1;
		ack[3] = block2;
		packet = new DatagramPacket(ack, ack.length, server, port);
		socket.send(packet);
	}
	
	/**
	 *
	 * err checks for the error thrown and prints it
	 *
	 * @param:
	 *	length : length of received error packet
	 *	receiveBuffer : established connection to send ack over
	 *
	 */
	 private void err (int length, byte [] receiveBuffer) {
	 	byte [] temp = new byte[length-5];
		for (int i = 0; i < (length-5); i++) {
			temp[i] = receiveBuffer[i+4];
		}
		System.out.println("Error code " + receiveBuffer[3] + ": " + new String(temp));
	}
		
	/**
	 *
	 * toByte copies one byte array to another
	 *
	 * @param:
	 *	original : array to be copied into
	 *	index : index to start copy from
	 *	toCopy : array that gets copied
	 *
	 */
	 private byte [] toByte (byte [] original, int index, byte [] toCopy) {
		for (int i = 0; i < toCopy.length; i++) {
			original[index + i] = toCopy[i];
		}
		return original;
	}
	
	/**
	 *
	 * Main
	 *
	 * @param:
	 *	args
	 *
	 */
	public static void main (String args[]) {
		new TFTP();
	}

}
