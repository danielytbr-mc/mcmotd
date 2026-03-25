package io.github.danielytbr.mcmotd;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import android.graphics.Color;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends Activity {
    
    private static final String TAG = "MCMOTD";
    private EditText etServerIp;
    private EditText etServerPort;
    private Spinner spinnerProtocol;
    private Button btnFetch;
    private ProgressBar progressBar;
    private TextView tvResult;
    private TextView tvDebug;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Initialize views
        etServerIp = findViewById(R.id.et_server_ip);
        etServerPort = findViewById(R.id.et_server_port);
        spinnerProtocol = findViewById(R.id.spinner_protocol);
        btnFetch = findViewById(R.id.btn_fetch);
        progressBar = findViewById(R.id.progress_bar);
        tvResult = findViewById(R.id.tv_result);
        tvDebug = findViewById(R.id.tv_debug);
        
        // Make text scrollable
        tvResult.setMovementMethod(new ScrollingMovementMethod());
        tvDebug.setMovementMethod(new ScrollingMovementMethod());
        
        // Set default port
        etServerPort.setText("25565");
        
        // Set test servers for debugging
        etServerIp.setText("mc.hypixel.net");
        
        // Setup protocol spinner
        String[] protocols = {
            "1.16.5 (Protocol 754) - Most compatible",
            "1.8.9 (Protocol 47) - Old servers",
            "1.12.2 (Protocol 340)",
            "1.13.2 (Protocol 404)",
            "1.14.4 (Protocol 578)",
            "1.15.2 (Protocol 736)",
            "1.17.1 (Protocol 756)",
            "1.18.2 (Protocol 758)",
            "1.19.4 (Protocol 762)",
            "1.20.4 (Protocol 765)",
            "Auto-detect (Try all protocols)"
        };
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, protocols);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProtocol.setAdapter(adapter);
        
        // Set click listener
        btnFetch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchServerInfo();
            }
        });
    }
    
    private void fetchServerInfo() {
        final String ip = etServerIp.getText().toString().trim();
        final String portStr = etServerPort.getText().toString().trim();
        final int protocolOption = spinnerProtocol.getSelectedItemPosition();
        
        // Validate inputs
        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter server IP", Toast.LENGTH_SHORT).show();
            return;
        }
        
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                Toast.makeText(this, "Invalid port number (1-65535)", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid port number", Toast.LENGTH_SHORT).show();
            return;
        }
        
        final int finalPort = port;
        
        // Show loading indicator
        progressBar.setVisibility(View.VISIBLE);
        btnFetch.setEnabled(false);
        tvDebug.setText("");
        tvResult.setText("Testing connection to " + ip + ":" + finalPort + "...");
        tvResult.setTextColor(Color.YELLOW);
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                // First test if the server is reachable
                final boolean isReachable = MinecraftServerPinger.testConnection(ip, finalPort);
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isReachable) {
                            tvDebug.append("✗ Cannot reach server at " + ip + ":" + finalPort + "\n");
                            tvDebug.append("  • Check if the server is online\n");
                            tvDebug.append("  • Check your internet connection\n");
                            tvDebug.append("  • Try a different server\n\n");
                            tvDebug.setTextColor(Color.RED);
                        } else {
                            tvDebug.append("✓ Server is reachable!\n");
                            tvDebug.append("  Attempting to query server info...\n\n");
                            tvDebug.setTextColor(Color.GREEN);
                        }
                    }
                });
                
                if (!isReachable) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResult.setText("Cannot reach server!\n\nMake sure the server is online and the IP/port is correct.");
                            tvResult.setTextColor(Color.RED);
                            progressBar.setVisibility(View.GONE);
                            btnFetch.setEnabled(true);
                        }
                    });
                    return;
                }
                
                // Try to fetch server info
                String jsonResponse = null;
                Exception lastException = null;
                
                if (protocolOption == 10) { // Auto-detect
                    // Try multiple protocols
                    int[] protocolsToTry = {754, 47, 340, 404, 578, 736, 756, 758, 762, 765};
                    String[] protocolNames = {"1.16.5", "1.8.9", "1.12.2", "1.13.2", "1.14.4", 
                                              "1.15.2", "1.17.1", "1.18.2", "1.19.4", "1.20.4"};
                    
                    for (int i = 0; i < protocolsToTry.length; i++) {
                        final int proto = protocolsToTry[i];
                        final String protoName = protocolNames[i];
                        
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvDebug.append("Trying " + protoName + " (Protocol " + proto + ")...\n");
                            }
                        });
                        
                        try {
                            jsonResponse = MinecraftServerPinger.fetchServerInfoJson(ip, finalPort, proto);
                            if (jsonResponse != null && !jsonResponse.isEmpty()) {
                                final String finalJson = jsonResponse;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        tvDebug.append("✓ Success with " + protoName + "!\n\n");
                                    }
                                });
                                break;
                            }
                        } catch (Exception e) {
                            lastException = e;
                            final String error = e.getMessage();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvDebug.append("✗ Failed: " + (error != null ? error : "Unknown error") + "\n");
                                }
                            });
                        }
                    }
                } else {
                    // Use specific protocol
                    int protocolVersion = getProtocolVersion(protocolOption);
                    String protocolName = getProtocolName(protocolOption);
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvDebug.append("Using " + protocolName + " (Protocol " + protocolVersion + ")\n");
                        }
                    });
                    
                    try {
                        jsonResponse = MinecraftServerPinger.fetchServerInfoJson(ip, finalPort, protocolVersion);
                    } catch (Exception e) {
                        lastException = e;
                        final String error = e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                tvDebug.append("✗ Failed: " + (error != null ? error : "Unknown error") + "\n");
                            }
                        });
                    }
                }
                
                final String finalJson = jsonResponse;
                final Exception finalException = lastException;
                
                if (finalJson != null && !finalJson.isEmpty()) {
                    final String formattedResult = formatServerInfo(finalJson);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            tvResult.setText(formattedResult);
                            tvResult.setTextColor(Color.WHITE);
                            progressBar.setVisibility(View.GONE);
                            btnFetch.setEnabled(true);
                            tvDebug.append("✓ Server info fetched successfully!\n");
                            Toast.makeText(MainActivity.this, "Server info fetched!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String errorMsg = "Failed to fetch server info\n\n";
                            if (finalException != null) {
                                errorMsg += "Error: " + finalException.getMessage() + "\n\n";
                                
                                StringWriter sw = new StringWriter();
                                PrintWriter pw = new PrintWriter(sw);
                                finalException.printStackTrace(pw);
                                tvDebug.append("\nStack trace:\n" + sw.toString().substring(0, Math.min(500, sw.toString().length())) + "\n");
                            }
                            
                            errorMsg += "Troubleshooting:\n";
                            errorMsg += "• Try a different protocol version\n";
                            errorMsg += "• Some servers block ping requests\n";
                            errorMsg += "• Try a known working server like mc.hypixel.net\n";
                            errorMsg += "• Check if the server is running Minecraft Java Edition\n";
                            
                            tvResult.setText(errorMsg);
                            tvResult.setTextColor(Color.RED);
                            progressBar.setVisibility(View.GONE);
                            btnFetch.setEnabled(true);
                        }
                    });
                }
            }
        }).start();
    }
    
    private int getProtocolVersion(int option) {
        switch (option) {
            case 0: return 754;  // 1.16.5
            case 1: return 47;   // 1.8.9
            case 2: return 340;  // 1.12.2
            case 3: return 404;  // 1.13.2
            case 4: return 578;  // 1.14.4
            case 5: return 736;  // 1.15.2
            case 6: return 756;  // 1.17.1
            case 7: return 758;  // 1.18.2
            case 8: return 762;  // 1.19.4
            case 9: return 765;  // 1.20.4
            default: return 754;
        }
    }
    
    private String getProtocolName(int option) {
        switch (option) {
            case 0: return "1.16.5";
            case 1: return "1.8.9";
            case 2: return "1.12.2";
            case 3: return "1.13.2";
            case 4: return "1.14.4";
            case 5: return "1.15.2";
            case 6: return "1.17.1";
            case 7: return "1.18.2";
            case 8: return "1.19.4";
            case 9: return "1.20.4";
            default: return "Unknown";
        }
    }
    
    private String formatServerInfo(String jsonString) {
        try {
            JSONObject json = new JSONObject(jsonString);
            StringBuilder result = new StringBuilder();
            
            result.append("═══════════════════════════════════════\n");
            result.append("     MINECRAFT SERVER INFORMATION      \n");
            result.append("═══════════════════════════════════════\n\n");
            
            // Parse MOTD
            String motd = "";
            if (json.has("description")) {
                Object description = json.get("description");
                if (description instanceof JSONObject) {
                    JSONObject descObj = (JSONObject) description;
                    if (descObj.has("text")) {
                        motd = descObj.getString("text");
                    } else if (descObj.has("extra")) {
                        StringBuilder motdBuilder = new StringBuilder();
                        for (int i = 0; i < descObj.getJSONArray("extra").length(); i++) {
                            JSONObject part = descObj.getJSONArray("extra").getJSONObject(i);
                            if (part.has("text")) {
                                motdBuilder.append(part.getString("text"));
                            }
                        }
                        motd = motdBuilder.toString();
                    }
                } else {
                    motd = description.toString();
                }
            }
            
            motd = cleanMinecraftColors(motd);
            
            result.append("📝 MOTD:\n");
            result.append("───────────────────────────────────────\n");
            result.append(motd).append("\n\n");
            
            // Parse players
            if (json.has("players")) {
                JSONObject players = json.getJSONObject("players");
                int online = players.getInt("online");
                int max = players.getInt("max");
                
                result.append("👥 PLAYER COUNT:\n");
                result.append("───────────────────────────────────────\n");
                result.append(online).append(" / ").append(max).append(" players online\n\n");
                
                if (players.has("sample") && players.getJSONArray("sample").length() > 0) {
                    result.append("📋 ONLINE PLAYERS (Sample):\n");
                    result.append("───────────────────────────────────────\n");
                    int count = Math.min(players.getJSONArray("sample").length(), 10);
                    for (int i = 0; i < count; i++) {
                        JSONObject player = players.getJSONArray("sample").getJSONObject(i);
                        result.append("  • ").append(player.getString("name")).append("\n");
                    }
                    result.append("\n");
                }
            }
            
            // Parse version
            if (json.has("version")) {
                JSONObject version = json.getJSONObject("version");
                result.append("🔄 SERVER VERSION:\n");
                result.append("───────────────────────────────────────\n");
                result.append(version.getString("name")).append("\n");
                result.append("Protocol: ").append(version.getInt("protocol")).append("\n\n");
            }
            
            result.append("═══════════════════════════════════════\n");
            return result.toString();
            
        } catch (JSONException e) {
            return "Raw JSON Response:\n" + jsonString;
        }
    }
    
    private String cleanMinecraftColors(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");
    }
}