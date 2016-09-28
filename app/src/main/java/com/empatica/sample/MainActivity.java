package com.empatica.sample;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import com.empatica.empalink.ConnectionNotAllowedException;
import com.empatica.empalink.EmpaDeviceManager;
import com.empatica.empalink.config.EmpaSensorStatus;
import com.empatica.empalink.config.EmpaSensorType;
import com.empatica.empalink.config.EmpaStatus;
import com.empatica.empalink.delegate.EmpaDataDelegate;
import com.empatica.empalink.delegate.EmpaStatusDelegate;

import com.android.volley.RequestQueue;
import com.android.volley.Request;
import com.android.volley.Request.Method;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.HurlStack;
import com.android.volley.toolbox.DiskBasedCache;
import com.android.volley.toolbox.BasicNetwork;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.VolleyError;
import com.android.volley.Network;
import com.android.volley.Cache;
import com.android.volley.AuthFailureError;
import com.android.volley.Response;
import com.android.volley.Response.Listener;
import com.android.volley.Response.ErrorListener;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import android.content.Context;

import org.json.JSONObject;
import org.json.JSONException;
import org.json.JSONArray;

import java.util.Map;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.io.UnsupportedEncodingException;


public class MainActivity extends AppCompatActivity implements EmpaDataDelegate, EmpaStatusDelegate {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final long STREAMING_TIME = 2400000; // Stops streaming after an hour

    private static final String EMPATICA_API_KEY = "b239ff6f4ff6433292fec91f5125490e";

    private int c = 0;
    private int index = 0;
    private boolean send = true;
    private boolean posted = false;
    private boolean start = false;
    private float [] ibiValues = new float [1500];
    private int indexGsr = 0;
    private boolean sendGsr = true;
    private float [] gsrValues = new float [1500];
    private boolean postedGsr = false;

    private EmpaDeviceManager deviceManager;

    private TextView accel_xLabel;
    private TextView accel_yLabel;
    private TextView accel_zLabel;
    private TextView bvpLabel;
    private TextView edaLabel;
    private TextView ibiLabel;
    private TextView temperatureLabel;
    private TextView batteryLabel;
    private TextView statusLabel;
    private TextView deviceNameLabel;
    private RelativeLayout dataCnt;
    private Handler handler = new Handler();

    public Map<String, String> params = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize vars that reference UI components
        statusLabel = (TextView) findViewById(R.id.status);
        dataCnt = (RelativeLayout) findViewById(R.id.dataArea);
        accel_xLabel = (TextView) findViewById(R.id.accel_x);
        accel_yLabel = (TextView) findViewById(R.id.accel_y);
        accel_zLabel = (TextView) findViewById(R.id.accel_z);
        bvpLabel = (TextView) findViewById(R.id.bvp);
        edaLabel = (TextView) findViewById(R.id.eda);
        ibiLabel = (TextView) findViewById(R.id.ibi);
        temperatureLabel = (TextView) findViewById(R.id.temperature);
        batteryLabel = (TextView) findViewById(R.id.battery);
        deviceNameLabel = (TextView) findViewById(R.id.deviceName);


        // Create a new EmpaDeviceManager. MainActivity is both its data and status delegate.
        deviceManager = new EmpaDeviceManager(getApplicationContext(), this, this);
        // Initialize the Device Manager using your API key. You need to have Internet access at this point.
        deviceManager.authenticateWithAPIKey(EMPATICA_API_KEY);

    }

    @Override
    protected void onPause() {
        super.onPause();
        deviceManager.stopScanning();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        deviceManager.cleanUp();
    }

    @Override
    public void didDiscoverDevice(BluetoothDevice bluetoothDevice, String deviceName, int rssi, boolean allowed) {
        // Check if the discovered device can be used with your API key. If allowed is always false,
        // the device is not linked with your API key. Please check your developer area at
        // https://www.empatica.com/connect/developer.php
        if (allowed) {
            // Stop scanning. The first allowed device will do.
            deviceManager.stopScanning();
            try {
                // Connect to the device
                deviceManager.connectDevice(bluetoothDevice);
                updateLabel(deviceNameLabel, "To: " + deviceName);
            } catch (ConnectionNotAllowedException e) {
                // This should happen only if you try to connect when allowed == false.
                Toast.makeText(MainActivity.this, "Sorry, you can't connect to this device", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void didRequestEnableBluetooth() {
        // Request the user to enable Bluetooth
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // The user chose not to enable Bluetooth
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            // You should deal with this
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void didUpdateSensorStatus(EmpaSensorStatus status, EmpaSensorType type) {
        // No need to implement this right now
    }

    @Override
    public void didUpdateStatus(EmpaStatus status) {
        // Update the UI
        updateLabel(statusLabel, status.name());

        // The device manager is ready for use
        if (status == EmpaStatus.READY) {
            updateLabel(statusLabel, status.name() + " - Turn on your device");
            // Start scanning
            deviceManager.startScanning();
        // The device manager has established a connection
        } else if (status == EmpaStatus.CONNECTED) {

            // Stop streaming after STREAMING_TIME
            runOnUiThread(new Runnable() {
                @Override
                public void run() {

                    dataCnt.setVisibility(View.VISIBLE);
                    handler.postDelayed(runnable, 1000);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            // Disconnect device
                            deviceManager.disconnect();
                            handler.removeCallbacksAndMessages(null);

                        }
                    }, STREAMING_TIME);

                }
            });
        // The device manager disconnected from a device
        } else if (status == EmpaStatus.DISCONNECTED) {
            updateLabel(deviceNameLabel, "");
        }
    }

    @Override
    //PPG Sensor measures blood volume pulse to determine heart rate
    public void didReceiveBVP(float bvp, double timestamp) {

        updateLabel(bvpLabel, "" + bvp);

    }

    @Override
    //EDA Sensor - nervous system arousal
    public void didReceiveGSR(float gsr, double timestamp) {

        updateLabel(edaLabel, "" + gsr);
        if(sendGsr == true) {
            sendGsr = false;
            postedGsr = true;
            gsrValues[indexGsr] = gsr;
            indexGsr++;
            params.put("gsr", Float.toString(gsr));
            didVolleyPost();
        }
    }

    @Override
    //Inter-beat-interval timing
    public void didReceiveIBI(float ibi, double timestamp) {

        updateLabel(ibiLabel, "" + ibi);
        start = true;
        if(send == true) {
            send = false;
            posted = true;
            ibiValues[index] = ibi;
            index++;
            params.put("ibi", Float.toString(ibi));
            didVolleyPost();
        }

    }

    //send IBI every second
    public Runnable runnable = new Runnable() {
        @Override
        public void run() {

            if(start == true){
                send = true;
                if(posted == false){
                    ibiValues[index] = ibiValues[index-1];
                    params.put("ibi", Float.toString(ibiValues[index-1]));
                    didVolleyPost();
                    index++;
                }
                posted = false;

                sendGsr = true;
//                if(postedGsr == false){
//                    gsrValues[indexGsr] = gsrValues[indexGsr-1];
//                    params.put("gsr", Float.toString(gsrValues[indexGsr-1]));
//                    didVolleyPost();
//                    indexGsr++;
//                }
                postedGsr = false;
            }
                handler.postDelayed(this, 1000);

        }
    };

    @Override
    public void didReceiveAcceleration(int x, int y, int z, double timestamp) {

        updateLabel(accel_xLabel, "" + x);
        updateLabel(accel_yLabel, "" + y);
        updateLabel(accel_zLabel, "" + z);

    }

    @Override
    public void didReceiveBatteryLevel(float battery, double timestamp) {
        updateLabel(batteryLabel, String.format("%.0f %%", battery * 100));
    }

    @Override
    public void didReceiveTemperature(float temp, double timestamp) {
        updateLabel(temperatureLabel, "" + temp);
    }

    /**
     * Created by Raina on 6/20/16.
     */
    public static class MySingleton {

        private static MySingleton mInstance;
        private RequestQueue queue;
        private static Context mCtx;

        private MySingleton(Context context) {
            mCtx = context;
            queue = getRequestQueue();
        }

        public static synchronized MySingleton getInstance(Context context) {
            if (mInstance == null) {
                mInstance = new MySingleton(context);
            }
            return mInstance;
        }

        public RequestQueue getRequestQueue() {
            if (queue == null) {
                queue = Volley.newRequestQueue(mCtx.getApplicationContext());
            }
            return queue;
        }

        public <T> void addToRequestQueue(Request<T> req) {
            getRequestQueue().add(req);
        }

    }

    public class CustomRequest extends Request<JSONObject>{

        private Listener<JSONObject> listener;
        private Map<String, String> params;

        public CustomRequest(String url, Map<String, String> params,
                             Listener<JSONObject> responseListener, ErrorListener errorListener) {

            super(Method.GET, url, errorListener);
            this.listener = responseListener;
            this.params = params;
        }

        public CustomRequest(int method, String url, Map<String, String> params,
                             Listener<JSONObject> responseListener, ErrorListener errorListener) {

            super(method, url, errorListener);
            this.listener = responseListener;
            this.params = params;
        }

        @Override
        protected Map<String, String> getParams() throws com.android.volley.AuthFailureError {
            return params;
        };

        @Override
        protected void deliverResponse(JSONObject response) {
            listener.onResponse(response);
        }

        @Override
        protected Response<JSONObject> parseNetworkResponse(NetworkResponse response) {
            try {
                String jsonString = new String(response.data,
                        HttpHeaderParser.parseCharset(response.headers));
                return Response.success(new JSONObject(jsonString),
                        HttpHeaderParser.parseCacheHeaders(response));
            } catch (UnsupportedEncodingException e) {
                return Response.error(new ParseError(e));
            } catch (JSONException je) {
                return Response.error(new ParseError(je));
            }
        }

    }

    //posting data to the server
    public void didVolleyPost(){

        String url = "http://128.237.197.221:8080";

        MySingleton.getInstance(this.getApplicationContext()).
                getRequestQueue();

        CustomRequest jsObjRequest = new CustomRequest(Method.POST, url, params, new Response.Listener<JSONObject>() {

            @Override
            public void onResponse(JSONObject response) {

                Toast.makeText(MainActivity.this, response.toString(), Toast.LENGTH_LONG).show();

            }
        }, new Response.ErrorListener() {

            @Override
            public void onErrorResponse(VolleyError error) {
                Toast.makeText(MainActivity.this, error.toString(), Toast.LENGTH_LONG).show();
            }
        });

        MySingleton.getInstance(this).addToRequestQueue(jsObjRequest);

    }

    // Update a label with some text, making sure this is run in the UI thread
    private void updateLabel(final TextView label, final String text) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                label.setText(text);
            }
        });
    }


}