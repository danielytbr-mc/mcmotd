package io.github.danielytbr.mcmotd;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MinecraftServerPinger {
    
    public static String fetchServerInfoJson(String host, int port) throws IOException {
        return fetchServerInfoJson(host, port, 754); // Default to 1.16.5 protocol
    }
    
    public static String fetchServerInfoJson(String host, int port, int protocolVersion) throws IOException {
        Socket socket = null;
        DataInputStream input = null;
        DataOutputStream output = null;
        
        try {
            // Create socket and connect
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 10000); // 10 second timeout
            socket.setSoTimeout(10000);
            
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());
            
            // Step 1: Send Handshake Packet
            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeBytes);
            
            writeVarInt(handshake, 0x00); // Packet ID for handshake
            writeVarInt(handshake, protocolVersion); // Protocol version
            writeString(handshake, host); // Server address
            handshake.writeShort(port); // Server port
            writeVarInt(handshake, 1); // Next state: 1 for status
            
            handshake.flush();
            byte[] handshakeData = handshakeBytes.toByteArray();
            
            // Send handshake packet
            writeVarInt(output, handshakeData.length);
            output.write(handshakeData);
            output.flush();
            
            // Step 2: Send Request Packet
            ByteArrayOutputStream requestBytes = new ByteArrayOutputStream();
            DataOutputStream request = new DataOutputStream(requestBytes);
            
            writeVarInt(request, 0x00); // Packet ID for request
            request.flush();
            
            byte[] requestData = requestBytes.toByteArray();
            writeVarInt(output, requestData.length);
            output.write(requestData);
            output.flush();
            
            // Step 3: Read Response
            // Read packet length
            int packetLength = readVarInt(input);
            if (packetLength == 0) {
                throw new IOException("Empty packet received");
            }
            
            // Read packet ID
            int packetId = readVarInt(input);
            if (packetId != 0x00) {
                throw new IOException("Invalid packet ID: " + packetId + ", expected 0x00");
            }
            
            // Read the JSON response
            String jsonResponse = readString(input);
            
            // Step 4: Send Ping Packet (optional but good practice)
            try {
                ByteArrayOutputStream pingBytes = new ByteArrayOutputStream();
                DataOutputStream ping = new DataOutputStream(pingBytes);
                
                writeVarInt(ping, 0x01); // Packet ID for ping
                long time = System.currentTimeMillis();
                ping.writeLong(time);
                ping.flush();
                
                byte[] pingData = pingBytes.toByteArray();
                writeVarInt(output, pingData.length);
                output.write(pingData);
                output.flush();
                
                // Read ping response
                readVarInt(input); // Packet length
                readVarInt(input); // Packet ID (should be 0x01)
                input.readLong(); // Should match our time
            } catch (IOException e) {
                // Ping failed, but we already have the status data
            }
            
            return jsonResponse;
            
        } catch (SocketTimeoutException e) {
            throw new IOException("Connection timeout after 10 seconds", e);
        } catch (UnknownHostException e) {
            throw new IOException("Unknown host: " + host, e);
        } catch (EOFException e) {
            throw new IOException("Server closed connection unexpectedly", e);
        } catch (IOException e) {
            throw new IOException("IO Error: " + e.getMessage(), e);
        } finally {
            // Clean up resources
            try {
                if (output != null) output.close();
                if (input != null) input.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    // Test with a simple connection to see if the server is reachable
    public static boolean testConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 5000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
    
    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        do {
            byte temp = (byte) (value & 0x7F);
            value >>>= 7;
            if (value != 0) {
                temp |= 0x80;
            }
            out.writeByte(temp);
        } while (value != 0);
    }
    
    private static void writeString(DataOutputStream out, String string) throws IOException {
        byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
    
    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        
        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            
            if ((currentByte & 0x80) == 0) break;
            
            position += 7;
            if (position >= 32) {
                throw new RuntimeException("VarInt is too big");
            }
        }
        
        return value;
    }
    
    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0) {
            throw new IOException("Invalid string length: " + length);
        }
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}