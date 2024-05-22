package xyz.metl_group.esp_remote_app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


public class MainActivity extends AppCompatActivity {
    private DeviceManager deviceManager;
    private ExecutorService executorService;
    private WebView mWebView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @SuppressLint("SetJavaScriptEnabled")

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        deviceManager = new DeviceManager();
        deviceManager.addDevice(new DeviceConfig("ESP8266", "http://esp8266"));

        deviceManager.selectDevice(0);

        executorService = Executors.newSingleThreadExecutor();

        Spinner deviceSpinner = findViewById(R.id.device_spinner);
        ArrayAdapter<DeviceConfig> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, deviceManager.getDevices());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        deviceSpinner.setAdapter(adapter);
        deviceSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deviceManager.selectDevice(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        mWebView = findViewById(R.id.webview);
        mWebView.setWebViewClient(new WebViewClient());
        //mWebView.loadUrl("https://bing.com");
        //mWebView.loadUrl(url);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Initialisiere den ActivityResultLauncher
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


        Button updateButton = findViewById(R.id.update_button);
        updateButton.setOnClickListener(v -> {
            DeviceConfig selectedDevice = deviceManager.getSelectedDevice();
            if (selectedDevice != null) {
                String url = selectedDevice.getUrl();
                if (mWebView.getVisibility() == View.VISIBLE) {
                    mWebView.setVisibility(View.GONE);
                    mWebView.loadUrl(url);
                } else {
                    //mWebView.loadUrl(url);
                    mWebView.loadUrl(url + "/update");
                    mWebView.setVisibility(View.VISIBLE);
                }
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String finalUrl) {
                super.onPageFinished(view, finalUrl);

                // Hier wird der Button-Text Version ausgelesen und in einem Button in der App dargestellt
                mWebView.evaluateJavascript("document.querySelector('button[name=\"version\"]').innerText;", value -> {
                    Button versionButton = findViewById(R.id.version_button);
                    versionButton.setText(value.replace("\"", ""));
                });
            }
        });
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

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onButtonClick(View view, int inputNumber) {
        DeviceConfig selectedDevice = deviceManager.getSelectedDevice();
        if (selectedDevice != null) {
            String url = selectedDevice.getUrl();
            String subUrl = url + "/get?input" + inputNumber + "=Submit";
            executorService.execute(() -> sendHttpRequest(subUrl));
        }
    }

    public void onStartShutdownClick(View view) {
        onButtonClick(view, 1);
    }

    public void onRestartClick(View view) {
        onButtonClick(view, 2);
    }

    public void onForceShutdownClick(View view) {
        onButtonClick(view, 3);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
