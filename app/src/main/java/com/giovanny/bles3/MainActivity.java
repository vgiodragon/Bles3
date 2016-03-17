package com.giovanny.bles3;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private boolean activado;
    public ArrayList<BluetoothDevice> anteriores;

    private ArrayList<BluetoothDevice> arrayBluetoothD;


    private int TiempoCiclo=4000;
    //String url ="http://192.168.20.197:8080/store/";
    String url ="http://52.35.80.115:8080/store/";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        arrayBluetoothD=new ArrayList<>();


    }

    public synchronized Object getArrayBluetoothD() {
        return arrayBluetoothD.clone();
    }

    public void resetArrayBluetoothD() {
        this.arrayBluetoothD.clear();
    }

    private synchronized void addDevice(BluetoothDevice btDevice){
        for(BluetoothDevice actual: arrayBluetoothD){
            if(actual.getAddress().equals(btDevice.getAddress())) {
                return;
            }
        }
        Log.d("Scaner", "_Anadi:" + btDevice.getName());
        arrayBluetoothD.add(btDevice);

    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            addDevice(result.getDevice());
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        activado=true;

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<>();
            }
            anteriores = (ArrayList<BluetoothDevice>) getArrayBluetoothD();
        }

        HiloAlternativo();
    }
    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            Log.d("PAUSE","pausado");
            mLEScanner.stopScan(mScanCallback);
            activado =false;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        activado=false;
        mLEScanner.stopScan(mScanCallback);
    }


    private void HiloAlternativo() {

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (activado) {
                    mLEScanner.startScan(filters,settings,mScanCallback);
                    esperoTiempo(TiempoCiclo/2);
                    mLEScanner.stopScan(mScanCallback);
                    esperoTiempo(TiempoCiclo);
                    new TareaToast().execute();
                }
            }
        }).start();
    }

    private class TareaToast extends AsyncTask<Void,String,Void> {
        ArrayList<BluetoothDevice> nuevos;
        boolean []ban;
        String mensaje;
        public TareaToast(){
            mensaje="";
        }
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            nuevos= (ArrayList<BluetoothDevice>) getArrayBluetoothD();
            ban=new boolean[anteriores.size()];
            Arrays.fill(ban, false);
        }

        @Override
        protected Void doInBackground(Void... params) {
            String ants="";
            for(BluetoothDevice ant: anteriores){
                ants+=ant.getName()+"_";
            }
            Log.d("Scaner","un ciclo ,_"+ants);

            for(BluetoothDevice actual: nuevos){
                int i;
                for(i=0;i<anteriores.size();i++){
                    if(actual.getAddress().equals(anteriores.get(i).getAddress())){
                        ban[i]=true;
                        break;
                    }
                }
                if(i==anteriores.size()){
                    Date date = new Date();
                    publishProgress(actual.getName()+"&"+date+"&"+"Aparecio");//Aparecio(false,ant);
                }
            }

            int j=0;

            for(BluetoothDevice ant: anteriores){
                if(ban[j]==false){
                    Date date = new Date();
                    publishProgress(ant.getName()+"&"+date+"&"+"Desaparecio");//Aparecio(false,ant);
                }
                j++;
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            mensaje+=values[0]+"$$";
            Toast.makeText(getBaseContext(), values[0], Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            anteriores= (ArrayList<BluetoothDevice>) nuevos.clone();
            resetArrayBluetoothD();
            if(!mensaje.equals(""))
                new SendToServer(url+mensaje).execute();
        }
    }

    private class SendToServer extends AsyncTask<String, Void, String> {
        String url;
        public SendToServer(String url){
            this.url=url.replace(" ","_");
        }
        @Override
        protected String doInBackground(String... urls) {
            String respues="";
            try {
                respues= downloadUrl(url);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return respues;
        }
        @Override
        protected void onPostExecute(String result) {
            Log.d("respuesta", "The response is: " + result);
        }
    }

    public String readIt(InputStream stream, int len) throws IOException, UnsupportedEncodingException {
        Reader reader = null;
        reader = new InputStreamReader(stream, "UTF-8");
        char[] buffer = new char[len];
        reader.read(buffer);
        return new String(buffer);
    }

    private String downloadUrl(String myurl) throws IOException {
        InputStream is = null;
        int len = 50;

        try {
            URL url = new URL(myurl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setDoInput(true);

            // Starts the query
            conn.connect();
            int response = conn.getResponseCode();
            Log.d("respuesta", "The response is: " + response);
            is = conn.getInputStream();

            // Convert the InputStream into a string
            String contentAsString = readIt(is, len);
            return contentAsString;

        } finally {
            if (is != null) {
                is.close();
            }
        }
    }

    public void esperoTiempo(int Time){
        try {
            Thread.sleep(Time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
