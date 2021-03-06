package com.source.aero.aerogroundstation;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageButton;

import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.Polyline;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class FlightPath extends AppCompatActivity implements OnMapReadyCallback {
    private final static String TAG = "FLIGHTPATH";

    private Polyline path;
    private MapView mapView;
    private MapboxMap map;
    protected Marker planeMarker;
    Marker Payload;
    Marker CDA;
    protected Marker lastPosition;
    protected Bitmap icon;

    //UI Elements
    ImageButton forwardButton;
    ImageButton backwardsButton;
    ImageButton playButton;
    DecimalFormat telemetryFormat = new DecimalFormat("#.00");

    //Data elements
    ArrayList<Waypoint> waypoints;
    int currentPoint;
    Bundle data;

    public FlightPath() {
        //Empty constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(this,getResources().getString(R.string.mapboxToken));
        setContentView(R.layout.activity_flight_path);
        mapView = (MapView) findViewById(R.id.flightPathMapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
        mapView.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(MapboxMap mapboxMap) {
                map = mapboxMap;
            }
        });


        //Get waypoints
        //data = getArguments();
        //Retrieve data sent from activity
        /*try {
            //waypoints = (ArrayList<Waypoint>) data.getSerializable("WAYPOINTS");
            //Test data
            waypoints = populate();
        } catch (NullPointerException e) {
            Log.d(TAG,"Couldn't receive waypoints from main activity");
            finish();
        } catch (ClassCastException e) {
            Log.d(TAG,"Data from main activity in wrong format");
            finish();
        }

        //Set current point to first point;
        currentPoint = 0;

        forwardButton = (ImageButton) findViewById(R.id.flightPathForwardButton);
        backwardsButton = (ImageButton) findViewById(R.id.flightPathBackwardsButton);
        playButton = (ImageButton) findViewById(R.id.flightPathPlayButton);*/



        /*forwardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                forward();
            }
        });
        backwardsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                backward();
            }
        });
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
            }
        });*/
    }

    @Override
    public void onMapReady(MapboxMap mapboxMap) {
        this.map = mapboxMap;
    }

    /*public void updateData(Waypoint point) {
        //Initialize textview elements
        TextView altitudeVal = (TextView) findViewById(R.id.flightPathAltitudeVal);
        TextView speedVal = (TextView) findViewById(R.id.flightPathSpeedVal);
        TextView headingVal = (TextView) findViewById(R.id.flightPathHeadingVal);
        TextView dropHeightVal = (TextView) findViewById(R.id.flightPathDropHeightVal);
        TextView rollVal = (TextView) findViewById(R.id.flightPathRollVal);
        TextView pitchVal = (TextView) findViewById(R.id.flightPathPitchVal);
        TextView yawVal = (TextView) findViewById(R.id.flightPathYawVal);

        //Update textviews for current point
        altitudeVal.setText(getString(R.string.flightPathAltitudeFormatString,point.getAltitude()));
        speedVal.setText(getString(R.string.flightPathSpeedFormatString,point.getSpeed()));
        headingVal.setText(getString(R.string.flightPathHeadingFormatString,point.getHeading()));
        dropHeightVal.setText(getString(R.string.flightPathAltitudeFormatString,point.getAltitude()));
        rollVal.setText(getString(R.string.flightPathRollFormatString,point.getRoll()));
        pitchVal.setText(getString(R.string.flightPathPitchFormatString,point.getPitch()));
        yawVal.setText(getString(R.string.flightPathYawFormatString,point.getYaw()));
    }

    public void play() {

    }

    public void forward() {
        if (currentPoint < waypoints.size()-1) {
            currentPoint += 1;
            updateData(waypoints.get(currentPoint));
        }
        else {
            Toast.makeText(this,"End of path reached",Toast.LENGTH_SHORT).show();
        }
    }

    public void backward() {
        if (currentPoint > 0) {
            currentPoint -= 1;
            updateData(waypoints.get(currentPoint));
        } else {
            Toast.makeText(this,"Start of path reached",Toast.LENGTH_SHORT).show();
        }
    }

    public ArrayList<Waypoint> populate() {
        Date date = new Date();
        SimpleDateFormat formatter = new SimpleDateFormat("yyy_MM_dd-HH:mm:ss_z");
        String sessionId = formatter.format(date);
        double altitude = 100;
        double speed = 10;
        double heading = 90;
        String location = "10101010101";
        int sid = 0;
        double drop = 10;
        double gliderDropHeight = 11;
        double roll = 0;
        double pitch = 0;
        double yaw = 0;
        String flight_type = "P";
        ArrayList<Waypoint> testArray = new ArrayList<Waypoint>();

        for (int i = 0; i < 100; i++) {
            altitude += 1;
            speed += 1;
            heading += 1;
            sid += 1;
            drop += 1;
            roll += 1;
            pitch += 1;
            yaw += 1;
            Waypoint point = new Waypoint(sessionId,sid,location,altitude,speed,heading,drop,gliderDropHeight,roll,pitch,yaw);
            testArray.add(point);
        }

        return testArray;

    }*/

}
