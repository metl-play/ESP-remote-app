package xyz.metl_group.esp_remote_app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "DevicePrefs";
    private static final String KEY_DEVICES = "devices";
    private DeviceManager deviceManager;
    private ExecutorService executorService;
    private WebView mWebView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ArrayAdapter<DeviceConfig> adapter;
    private Spinner deviceSpinner;

    @SuppressLint("SetJavaScriptEnabled")

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        executorService = Executors.newSingleThreadExecutor();
        deviceManager = new DeviceManager();
        //loadDevices();
        executorService.execute(this::loadDevices);
        Handler handler = new Handler(Looper.getMainLooper());

        // Spinner for selecting devices
        deviceSpinner = findViewById(R.id.device_spinner);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceManager.getDevices());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deviceManager.selectDevice(position);
                mWebView.loadUrl(deviceManager.getSelectedDevice().getUrl());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Initialising the WebView
        mWebView = findViewById(R.id.webview);
        mWebView.setWebViewClient(new WebViewClient());

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Initialising the ActivityResultLauncher
        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (mFilePathCallback != null) {
                        Uri[] results = null;
                        if (result.getResultCode() == RESULT_OK) {
                            Intent data = result.getData();
                            if (data != null) {
                                String dataString = data.getDataString();
                                if (dataString != null) {
                                    results = new Uri[]{Uri.parse(dataString)};
                                }
                            }
                        }
                        mFilePathCallback.onReceiveValue(results);
                        mFilePathCallback = null;
                    }
                }
        );

        // File Chooser for uploading new ESP-Remote Firmware to controller
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mFilePathCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                filePickerLauncher.launch(Intent.createChooser(intent, "Select File"));
                return true;
            }
        });

        // Button to enable the WebView with the link DeviceUrl/update
        Button updateButton = findViewById(R.id.update_button);
        updateButton.setOnClickListener(v -> {
            DeviceConfig selectedDevice = deviceManager.getSelectedDevice();
            if (selectedDevice != null) {
                String url = selectedDevice.getUrl();
                if (mWebView.getVisibility() == View.VISIBLE) {
                    mWebView.setVisibility(View.GONE);
                    mWebView.loadUrl(url);
                } else {
                    mWebView.loadUrl(url + "/update");
                    handler.postDelayed(() -> mWebView.setVisibility(View.VISIBLE), 300);
                }
            }
        });

        // WebViewClient to handle the Version display
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String finalUrl) {
                super.onPageFinished(view, finalUrl);
                // Here the button text version is read out and displayed in a button in the app
                mWebView.evaluateJavascript("document.querySelector('button[name=\"version\"]').innerText;", value -> {
                    Button versionButton = findViewById(R.id.version_button);
                    versionButton.setText(value.replace("\"", ""));
                });
            }
        });

        Button addDeviceButton = findViewById(R.id.add_device_button);
        addDeviceButton.setOnClickListener(v -> showAddDeviceDialog());
    }

    private void showAddDeviceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Device");

        View viewInflated = getLayoutInflater().inflate(R.layout.dialog_add_device, null);
        final EditText inputName = viewInflated.findViewById(R.id.device_name);
        final EditText inputUrl = viewInflated.findViewById(R.id.device_url);

        builder.setView(viewInflated);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            dialog.dismiss();
            String deviceName = inputName.getText().toString();
            String deviceUrl = inputUrl.getText().toString();
            DeviceConfig newDevice = new DeviceConfig(deviceName, deviceUrl);
            deviceManager.addDevice(newDevice);
            adapter.notifyDataSetChanged();
            //saveDevices();
            executorService.execute(this::saveDevices);
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void saveDevices() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Set<String> deviceSet = new HashSet<>();
        for (DeviceConfig device : deviceManager.getDevices()) {
            deviceSet.add(device.getName() + ";" + device.getUrl());
        }
        editor.putStringSet(KEY_DEVICES, deviceSet);
        editor.apply();
    }

    private void loadDevices() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> deviceSet = prefs.getStringSet(KEY_DEVICES, new HashSet<>());
        for (String deviceString : deviceSet) {
            String[] parts = deviceString.split(";");
            if (parts.length == 2) {
                deviceManager.addDevice(new DeviceConfig(parts[0], parts[1]));
            }
        }
        if (!deviceManager.getDevices().isEmpty()) {
            deviceManager.selectDevice(0);
            deviceSpinner.setSelection(0);
            runOnUiThread(() -> adapter.notifyDataSetChanged());
        }
    }

    private void sendHttpRequest(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");

            int responseCode = con.getResponseCode();
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Log response or handle it
            System.out.println(response);
            System.out.println(responseCode);

        } catch (Exception e) {
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    public void onButtonClick(int inputNumber) {
        DeviceConfig selectedDevice = deviceManager.getSelectedDevice();
        if (selectedDevice != null) {
            String url = selectedDevice.getUrl();
            String subUrl = url + "/get?input" + inputNumber + "=Submit";
            executorService.execute(() -> sendHttpRequest(subUrl));
        }
    }

    public void onStartShutdownClick(View view) {
        onButtonClick(1);
    }

    public void onRestartClick(View view) {
        onButtonClick(2);
    }

    public void onForceShutdownClick(View view) {
        onButtonClick(3);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

}
