package com.source.aero.aerogroundstation;


import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.NavigationView;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import com.leinardi.android.speeddial.SpeedDialActionItem;
import com.leinardi.android.speeddial.SpeedDialView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.annotations.PolylineOptions;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.source.aero.aerogroundstation.Bluetooth.BluetoothConstantsInterface;
import com.source.aero.aerogroundstation.Bluetooth.BluetoothDevices;
import com.source.aero.aerogroundstation.Bluetooth.BluetoothService;
import com.source.aero.aerogroundstation.ContainerClasses.*;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {
    private static final String TAG = "MainActivity";
    String configuration;

    private DatabaseHelper mDatabaseHelper;
    private String dbName = "AeroDB";

    //Mapbox elements
    private MapView mapView;
    private MapboxMap map;

    private int MAX_POINTS = 100;
    private boolean mRecording;

    protected Marker planeMarker;
    protected Marker lastPosition;
    protected Polyline planePath;
    protected IconFactory factory;
    protected Bitmap icon;

    //Ui Elements
    BottomNavigationView bottomNavigationView;
    SpeedDialView speedDialView;
    DrawerLayout drawerLayout;
    Spinner spinner;
    NavigationView navigationView;
    ImageButton statusTabButton;
    ArrayList<LatLng> points;

    private boolean sessionCreated;

    protected String recordingSession;
    private int waypointID;

    boolean dropped;

    //Bluetooth Elements
    //Request Codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    boolean bluetoothDisplayed = false;

    private TextView currentAltitude;
    private TextView currentPayload;
    private TextView currentDropAltitude;
    private TextView currentSpeed;
    private TextView currentTimeToTarget;
    private TextView currentDistanceToTarget;


    private ListView logView;
    private EditText editTextView;
    private Button sendButton;
    private ArrayAdapter<String> logArrayAdapter;
    private TextView.OnEditorActionListener writeListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
            if (i == EditorInfo.IME_NULL && keyEvent.getAction() == KeyEvent.ACTION_UP) {
                String data = textView.getText().toString();
                send(data);
            }
            return true;
        }
    };

    private BluetoothAdapter bluetoothAdapter = null;
    private BluetoothService bluetoothService = null;
    private StringBuffer dataBuffer;
    private int discoveryTime = 300;
    private String connectedDevice = null;

    private Vehicles vehicleManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this, getResources().getString(R.string.mapboxToken));
        setContentView(R.layout.activity_main);
        getSupportActionBar().hide();
        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(this);

        //Initializing UI Elements
        initBottomNavigationBar();
        initSpeedDial();
        initNavigationDrawer(); //Needs to be called before spinner
        initSpinner();
        initStatusTab();

        //Points for creating polyline
        points = new ArrayList<>();
        mRecording = false;
        boolean droppped = false;

        sessionCreated = false;

        recordingSession = null;
        waypointID = 0;

        mDatabaseHelper = new DatabaseHelper(this, dbName);

        //Creating Factory and Icon ONCE to avoid lag in updatePlane()
        factory = IconFactory.getInstance(MainActivity.this);
        icon = factory.fromResource(R.drawable.ic_plane).getBitmap();

        //Set configuration
        Intent intent = getIntent();
        configuration = intent.getStringExtra("CONFIGURATION");

        //Bluetooth Setup
        //Get local bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        logView = (ListView) findViewById(R.id.bluetooth_messageView);
        editTextView = (EditText) findViewById(R.id.bluetooth_sendMsgEditTextView);
        sendButton = (Button) findViewById(R.id.bluetooth_sendMsgButton);

        initTextDisplay();

        vehicleManager = new Vehicles();

        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
            }
        });
    }

    private void initTextDisplay()
    {
        currentAltitude = (TextView) findViewById(R.id.currentAltitude);
        currentAltitude.setText("N/A");

        currentPayload = (TextView) findViewById(R.id.currentPayload);
        currentPayload.setText("N/A");

        currentDropAltitude = (TextView) findViewById(R.id.currentDropAltitude);
        currentDropAltitude.setText("N/A");

        currentSpeed = (TextView) findViewById(R.id.currentSpeed);
        currentSpeed.setText("N/A");

        currentTimeToTarget = (TextView) findViewById(R.id.currentTimeToTarget);
        currentTimeToTarget.setText("N/A");

        currentDistanceToTarget = (TextView) findViewById(R.id.currentDistanceToTarget);
        currentDistanceToTarget.setText("N/A");
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.map = mapboxMap;
    }

    //Make bluetooth menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_bluetoothmain, menu);
        return true;
    }

    //Bluetooth options
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secureConnectOption: {
                Intent intent = new Intent(this, BluetoothDevices.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecureConnectOption: {
                Intent intent = new Intent(this, BluetoothDevices.class);
                startActivityForResult(intent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.makeDiscoverableOption: {
                makeDiscoverable();
                return true;
            }
        }
        return false;
    }


    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();

        //Bluetooth
        //Request for bluetooth to be enabled
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(bluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
        else if (bluetoothService == null) {
            setup();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();

        //Bluetooth
        if (bluetoothService != null) {
            if (bluetoothService.getState() == BluetoothService.STATE_NONE) {
                bluetoothService.start();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();

        //Bluetooth
        if (bluetoothService != null) {
            bluetoothService.stop();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    //Performs speed dial initialization
    private void initSpeedDial() {
        speedDialView = findViewById(R.id.mainActivitySpeedDial);
        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.mainActivitySpeedDialAction1, R.drawable.ic_location)
                        .setLabel(getResources().getString(R.string.mainActivitySpeedDialOption1Text))
                        .create()
        );
        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.mainActivitySpeedDialAction2, R.drawable.ic_record)
                        .setLabel(getResources().getString(R.string.mainActivitySpeedDialOption2Text))
                        .create()
        );
        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.mainActivitySpeedDialAction3, R.drawable.ic_bluetoothconnect)
                        .setLabel(getResources().getString(R.string.mainActivitySpeedDialOption3Text))
                        .create()
        );
        speedDialView.addActionItem(
                new SpeedDialActionItem.Builder(R.id.mainActivitySpeedDialAction4, R.drawable.ic_save)
                        .setLabel(getResources().getString(R.string.mainActivitySpeedDialOption4Text))
                        .create()
        );
        //On click listener for speed dial options
        speedDialView.setOnActionSelectedListener(new SpeedDialView.OnActionSelectedListener() {
            @Override
            public boolean onActionSelected(SpeedDialActionItem speedDialActionItem) {
                switch (speedDialActionItem.getId()) {
                    case R.id.mainActivitySpeedDialAction1:
                        speedDialView.hide();
                        bottomNavigationView.setVisibility(View.INVISIBLE);
                        openFragment("CURRENTLOCATION");
                        break;
                    case R.id.mainActivitySpeedDialAction2:
                        speedDialView.close();
                        isRecording(!mRecording);
                        break;
                    case R.id.mainActivitySpeedDialAction3:
                        if (!bluetoothDisplayed) {
                            ((LinearLayout) findViewById(R.id.bluetoothView)).setVisibility(View.VISIBLE);
                            getSupportActionBar().show();
                            ((BottomNavigationView) findViewById(R.id.mainActivityBottomNavigationView)).setVisibility(View.INVISIBLE);
                            bluetoothDisplayed = true;
                        }
                        else {
                            ((LinearLayout) findViewById(R.id.bluetoothView)).setVisibility(View.INVISIBLE);
                            ((BottomNavigationView) findViewById(R.id.mainActivityBottomNavigationView)).setVisibility(View.VISIBLE);
                            getSupportActionBar().hide();
                            bluetoothDisplayed = false;
                        }
                        speedDialView.close();
                        break;
                    case R.id.mainActivitySpeedDialAction4:
                        speedDialView.hide();
                        bottomNavigationView.setVisibility(View.INVISIBLE);
                        openFragment("OFFLINEMAPS");
                        break;
                    default:
                        return true;
                }
                return true;
            }
        });
    }

    //Initialize bottom navigation bar
    private void initBottomNavigationBar() {
        bottomNavigationView = (BottomNavigationView) findViewById(R.id.mainActivityBottomNavigationView);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                getSupportActionBar().hide();
                switch (item.getItemId()) {
                    case R.id.mainActivityBottomNavigationMap:
                        return true;
                    case R.id.mainActivityBottomNavigationPath:
                        //TODO:Open path fragment
                        return true;
                    case R.id.mainActivityBottomNavigationTargets:
                        //TODO:Open targets fragment
                        return true;
                    case R.id.mainActivityBottomNavigationPayload:
                        openFragment("STATUSTAB");
                        return true;
                    case R.id.mainActivityBottomNavigationAttitude:
                        //TODO: Open attitude fragment
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void initNavigationDrawer() {
        drawerLayout = findViewById(R.id.mainActivityDrawerLayout);
        navigationView = findViewById(R.id.mainActivityNavigationView);
        navigationView.setNavigationItemSelectedListener(
                new NavigationView.OnNavigationItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(MenuItem menuItem) {
                        switch (menuItem.getItemId()) {
                            case R.id.navigationDrawerItem1:
                                drawerLayout.closeDrawers();
                                break;
                            case R.id.navigationDrawerItem2:
                                drawerLayout.closeDrawers();
                                break;
                            case R.id.navigationDrawerItem3:
                                drawerLayout.closeDrawers();
                                break;
                            default:
                                return true;
                        }
                        return true;
                    }
                });
    }

    //Initialize spinner element
    public void initSpinner() {
        spinner = (Spinner) findViewById(R.id.mainActivityNavigationSpinner);
        //Using default android spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.mainActivityNavigationSpinnerItems,android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                navigationView.getMenu().clear();
                switch (pos) {
                    case 0:
                        navigationView.inflateMenu(R.menu.main_navigation_calibration);
                        //TODO: Implement spinner functionality
                        break;
                    case 1:
                        navigationView.inflateMenu(R.menu.main_navigation_logs);
                        //TODO: Implement spinner functionality
                        break;
                    default:
                        Log.d(TAG,"Spinner menu error");
                }
            }
            public void onNothingSelected(AdapterView<?> parent) {
                navigationView.inflateMenu(R.menu.main_navigation_calibration);
            }
        });
    }

    //Initialize statustab fragment
    public void initStatusTab() {
        statusTabButton = (ImageButton) findViewById(R.id.mainActivityStatusTab);
        statusTabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openFragment("STATUSTAB");
            }
        });
    }

    //Create fragment on top of main
    public void openFragment(String fragmentType) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        Fragment fragment;

        //Determine desired fragment type
        switch (fragmentType) {
            case "OFFLINEMAPS":
                fragment = new OfflineMaps();
                break;
            case "CURRENTLOCATION":
                fragment = new MapboxLocationHandler();
                break;
            case "STATUSTAB":
                fragment = new StatusTab();
                statusTabButton.setVisibility(View.INVISIBLE);
                break;
            case "MOTORDIALOGUE":
                fragment = new MotorDialogue();
                break;
            default:
                Log.d("MainActivity", "Failed to create fragment");
                return;
        }

        //Add or remove fragment to backstack
        if (fragmentManager.findFragmentByTag(fragmentType) == null) {
            fragmentTransaction.add(R.id.mainActivityFragmentLayout, fragment).addToBackStack(fragmentType).commit();
        }
        else {
            fragment = fragmentManager.findFragmentById(R.id.mainActivityFragmentLayout);
            fragmentTransaction.remove(fragment).commit();
        }
    }

    //------------------------------------------------- Below is the implementation for when the recording identifier is adjusted -----------------------------------------------------------

    public void isRecording(boolean value){

        //Obtain the ID's of the recording identifier that we are to change
        LinearLayout messageColour = (LinearLayout) findViewById(R.id.recordingIdentifier);
        TextView messageRecord = (TextView) findViewById(R.id.recordingText);

        //Below, use the if statement to identify if the status of the recording identifier is to be changed to
        //"recording"

        //EX. If a record button is pressed, "value" should be greater than zero to identify that recording has started
        if (value == true){

            messageColour.setBackgroundDrawable(getResources().getDrawable(R.drawable.green_boarder_white_outline));
            messageRecord.setText("Recording");
            mRecording = true;

            // Create new session for db
            recordingSession = new Date().toString();
            waypointID = 0;
        }

        else{

            messageColour.setBackgroundDrawable(getResources().getDrawable(R.drawable.red_boarder_white_outline));
            messageRecord.setText("Not Recording");
            mRecording = false;
            sessionCreated = false;

        }



    }

    //------------------------------------------------- Below is the implementation for when the display field values need to be adjusted -----------------------------------------------------------

    void updateUI()
    {
        // Update value
        currentAltitude.setText(String.valueOf(vehicleManager.getPlaneData().readPlaneAltitude()));

        // TODO: set glider text on drop of glider
        // currentPayload.setText("glider");

        // // TODO: set drop value on drop
        // currentDropAltitude.setText("0");

        currentSpeed.setText(String.valueOf(vehicleManager.getPlaneData().readPlaneSpeed()));

        // TODO: Calculate distance to target based on plane position and target position
        // TODO: Calculate time based on distance approximated to meters and current speed or change of speed from past lat, lons
        //currentTimeToTarget.setText("0");
        //currentDistanceToTarget.setText("0");

        updatePlane();
    }

    //------------------------------------------------- Below is the implementation of the motor dialogue when a button is selected -----------------------------------------------------------
    // TODO: Implement the functionality of the motor dialogue
    public void motorDialogueSelect(View view)
    {
        int id = view.getId();

        switch(id)
        {

            case R.id.motorOneOpenButton:
                {
                    //Place code here
                    break;
                }

            case R.id.motorOneCloseButton:
            {
                //Place code here
                break;
            }

            case R.id.motorTwoOpenButton:
            {
                //Place code here
                break;
            }

            case R.id.motorTwoCloseButton:
            {
                //Place code here
                break;
            }

            case R.id.motorThreeOpenButton:
            {
                //Place code here
                break;
            }

            case R.id.motorThreeCloseButton:
            {
                //Place code here
                break;
            }

            case R.id.motorFourOpenButton:
            {
                //Place code here
                break;
            }

            case R.id.motorFourCloseButton:
            {
                //Place code here
                break;
            }

            case R.id.motorFiveOpenButton:
            {
                //Place code here
                break;
            }

            case R.id.motorFiveCloseButton:
            {
                //Place code here
                break;
            }

            case R.id.motorSixOpenButton:
            {
                //Place code here
                break;
            }

            case R.id.motorSixCloseButton:
            {
                //Place code here
                break;
            }

            default: break;


        }
    }

    void updatePlane()
    {
        Log.d(TAG, "Plane update");

        if(planeMarker != null)
        {
            planeMarker.remove();
        }

        // TODO: REPLACE YAW WITH HEADING
        Matrix matrix = new Matrix();
        matrix.postRotate((float)vehicleManager.getPlaneData().readPlaneYaw());
        Bitmap rotatedBitmap = Bitmap.createBitmap(icon, 0, 0, icon.getWidth(), icon.getHeight(), matrix, true);
        rotatedBitmap = Bitmap.createScaledBitmap(rotatedBitmap, 40, 50, false);

        //Plane Marker
        LatLng point = new LatLng(vehicleManager.getPlaneData().readPlaneLatitude(), vehicleManager.getPlaneData().readPlaneLongitude());
        Log.d(TAG, "Plane position: " + point.toString());
        planeMarker = map.addMarker(new MarkerOptions()
                .position(point)
                .icon(factory.fromBitmap(rotatedBitmap)));

        //Past Position Markers
        if(points.size() > 2 && (points.size() % 10 == 0)){

            Bitmap circle = factory.fromResource(R.drawable.black_circle).getBitmap();
            circle = Bitmap.createScaledBitmap(circle, 10, 10, false);

            lastPosition = map.addMarker(new MarkerOptions()
                    .position(points.get(points.size()-2))
                    .icon(factory.fromBitmap(circle)));
        }

        planePath = map.addPolyline(new PolylineOptions()
                .addAll(points)
                .color(Color.parseColor("#3bb2d0"))
                .width(1));

//        //From old code, leaving in till we deal with drop
//        wayPointCount++;
//
//        if (dropped) {
//            droppedCount = wayPointCount;
//        }


    }



    void addWaypointToDb(String flightType)
    {
        // do nothing
        if(!sessionCreated){
            Date currentTime = new Date();
            //;Date formattedDate = new Date();
            recordingSession = new SimpleDateFormat("yyyy_MM_dd-HH_mm_ss_z").format(currentTime);
            Log.d("Formatted", recordingSession);
            sessionCreated = true;
        }
        else{
            waypointID += 1;
            boolean g = false;
             // TODO: Add payload drop type
            if(dropped){
                  g =mDatabaseHelper.addWaypoint(recordingSession, waypointID, points.get(points.size()-1).toString(),
                          (float)vehicleManager.getPlaneData().readPlaneAltitude(),
                          (float)vehicleManager.getPlaneData().readPlaneSpeed(),
                          (float)vehicleManager.getPlaneData().readPlaneYaw(),
                          (float)vehicleManager.getPlaneData().readPlaneAltitude(),
                          (float)vehicleManager.getPlaneData().readPlaneRoll(),
                          (float)vehicleManager.getPlaneData().readPlanePitch(),
                          (float)vehicleManager.getPlaneData().readPlaneYaw(),
                          flightType);
                dropped = false;
            }
            else{
                g =mDatabaseHelper.addWaypoint(recordingSession, waypointID, points.get(points.size()-1).toString(),
                        (float)vehicleManager.getPlaneData().readPlaneAltitude(),
                        (float)vehicleManager.getPlaneData().readPlaneSpeed(),
                        (float)vehicleManager.getPlaneData().readPlaneYaw(),
                        (float) 0.0,
                        (float)vehicleManager.getPlaneData().readPlaneRoll(),
                        (float)vehicleManager.getPlaneData().readPlanePitch(),
                        (float)vehicleManager.getPlaneData().readPlaneYaw(),
                        flightType);
            }

            Log.d("Waypointc", String.valueOf(g));

        }
    }

    //Close current fragment on back press
    //Status tab should always be closed
    @Override
    public void onBackPressed() {
        getFragmentManager().popBackStack();
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        Fragment existingFragment = fragmentManager.findFragmentById(R.id.mainActivityFragmentLayout);
        fragmentTransaction.remove(existingFragment).commit();
        speedDialView.show();
        bottomNavigationView.setVisibility(View.VISIBLE);
        statusTabButton.setVisibility(View.VISIBLE);
        getSupportActionBar().hide();
    }

    //Called from fragments that need access to map object
    //TODO: Redesign fragments using Viewmodels
    public MapboxMap passMap() {
        return this.map; }

    //Bluetooth Functions
    private void setup() {
        logArrayAdapter = new ArrayAdapter<String>(this, R.layout.activity_bluetoothlog);
        logView.setAdapter(logArrayAdapter);

        editTextView.setOnEditorActionListener(writeListener);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick (View view) {
                if (null != view) {
                    TextView textView = (TextView) findViewById(R.id.bluetooth_sendMsgEditTextView);
                    String data = textView.getText().toString();
                    send(data);
                }
            }
        });

        //Initialize bluetooth connections
        bluetoothService = new BluetoothService(this, handler);
        dataBuffer = new StringBuffer("");
    }

    private void makeDiscoverable() {
        if (bluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, discoveryTime);
            startActivity(intent);
        }
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    //Send data
    private void send(String data) {
        Log.d(TAG, "Sending data...");
        
        // Lat/Lon multiplied by 1000000 to remove decimals
        // Test message with target GPS coordinates pointing to ACEB building for testing gnd station -> onboard
        ////                                  start type  lat         lon         calibrate rssi drop gliders motors
        byte[] testMessage;
        //Check configuration to determine message
        if (configuration.equals("DEBUG")) {
            testMessage = hexStringToByteArray("0a0004029035D0FB27D200010201020000000100020003000400050006000700080009000a000b000c000d000e000f0000ff");
        }
        else {
            testMessage = hexStringToByteArray("0a0000029035D0FB27D200010201020000000100020003000400050006000700080009000a000b000c000d000e000f0000aa");
        }

        //Check device is connected
        if (bluetoothService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.bluetooth_notConnectedToast, Toast.LENGTH_SHORT).show();
            return;
        }

        if (data.length() > 0) {
            byte[] send = data.getBytes();

            //bluetoothService.write(send);
            bluetoothService.write(testMessage);

            dataBuffer.setLength(0);
            editTextView.setText(dataBuffer);
        }
    }
    
    // Thiss function takes in a string that represents an incoming bluetooth msg from the HC-05
    private void readIncomingBluetoothData(byte[] data) {
            Log.d(TAG, "Incoming bluetooth data string preparing to be parsed");

            // Get first and last byte of the message
            byte startByte = data[0];
            byte endByte = data[data.length - 1];
            
            // Print out the two msg type bytes
            Log.d(TAG, "Incoming bluetooth data string msg type bytes: " + Byte.valueOf(data[0]) + " "  + Byte.valueOf(data[1]));
                
            // Check if start and stop bytes match message definition
            if((Byte.compare(startByte, (byte) 10) == 0) && (Byte.compare(endByte, (byte) 255) == 0)) {
                // Wrap data in ByteBuffer so we can parse the data easily
                ByteBuffer msgBuffer = ByteBuffer.wrap(data);  // BIG ENDIAN BY DEFAULT
                messageParser parser = new messageParser(msgBuffer);

                Log.d(TAG, "Parser result: " + parser.toString());

                switch (parser.whoIsThisMesssageFor)
                {
                    case 1:
                    case 4:
                    {
                        // PLANE
                        Log.d(TAG, "Message came from plane");


                        vehicleManager.updatePlane(parser);

                        LatLng planePoint =  new LatLng(vehicleManager.getPlaneData().readPlaneLatitude(), vehicleManager.getPlaneData().readPlaneLongitude());
                        if(points.size() > MAX_POINTS)
                        {
                            points.remove(0);
                        }
                        points.add(planePoint);

                        // Check if recording to add to db
                        if(mRecording)
                        {
                            addWaypointToDb("Plane");
                        }

                        updateUI();
                        break;
                    }
                    case 2:
                    {
                        // GLIDER 1
                        Log.d(TAG, "Message came from glider1");
                        vehicleManager.updateGliderOne(parser);

                        // Check if recording to add to db
                        if(mRecording)
                        {
                            addWaypointToDb("Glider 1");
                        }

                        break;
                    }
                    case 3:
                    {
                        // GLIDER 2
                        Log.d(TAG, "Message came from glider2");
                        vehicleManager.updateGliderTwo(parser);

                        // Check if recording to add to db
                        if(mRecording)
                        {
                            addWaypointToDb("Glider 2");
                        }
                        break;
                    }
                }
            }
    }

    private final Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case BluetoothConstantsInterface.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            //setStatus(getString(R.string.bluetooth_titleConnectedTo, connectedDevice));
                            logArrayAdapter.clear();
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            //setStatus(R.string.bluetooth_titleConnectedTo);
                            break;
                        case BluetoothService.STATE_LISTENING:
                        case BluetoothService.STATE_NONE:
                            //setStatus(R.string.bluetooth_titleNotConnectedTo);
                            break;
                    }
                    break;
                case BluetoothConstantsInterface.MESSAGE_WRITE:
                    byte[] writeBuffer = (byte[]) msg.obj;
                    String writeData = new String(writeBuffer);
                    logArrayAdapter.add("Me: " + writeData);
                    break;
                case BluetoothConstantsInterface.MESSAGE_READ:
                    byte[] readBuffer = (byte[]) msg.obj;

                    String readData = new String(readBuffer, 0, msg.arg1);

                    // Print bytes received and how many we received
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < readBuffer.length; ++j){
                        sb.append(String.format("%02X ", readBuffer[j]));
                    }
                    Log.d(TAG, "VALID PACKET LEN: : " + String.valueOf(readBuffer.length) + " CONTENTS: " + sb.toString());

                    if(readData.length() > 0){
                        readIncomingBluetoothData(readBuffer);
                    }

                    logArrayAdapter.add(connectedDevice + ": " + "New msg");
                    break;
                case BluetoothConstantsInterface.MESSAGE_DEVICE_NAME:
                    connectedDevice = msg.getData().getString(BluetoothConstantsInterface.DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + connectedDevice, Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothConstantsInterface.MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(BluetoothConstantsInterface.TOAST),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    setup();
                }
                else {
                    Toast.makeText(this, R.string.bluetooth_btNotEnabledToast, Toast.LENGTH_SHORT).show();
                }
        }
    }

    private void connectDevice(Intent data, boolean secure) {
        String address = data.getExtras().getString(BluetoothDevices.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        bluetoothService.connect(device, secure);
    }
}