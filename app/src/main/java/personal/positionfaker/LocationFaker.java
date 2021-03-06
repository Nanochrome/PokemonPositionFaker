package personal.positionfaker;

/**
 * Created by Nanochrome on 23-Jul-16.
 */

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.File;
import java.util.ArrayList;

public class LocationFaker extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private GoogleMap mMap;
    private Context mContext;
    private MockedLocationProvider mMockedLocationProvider;
    final String providerName = "MyFancyGPSProvider";
    private MarkerOptions myPositionMarker;
    private MarkerOptions endPositionMarker;
    private Marker myPositionAtMap;
    private Marker endPostionAtMap;

    private double mLatitude = 0;
    private double mLongtitude = 0;

    private SharedPreferences sharedPreferences;
    private SharedPreferences.Editor editor;

    private ImageButton pokemonPosition;
    private ImageButton teleportPosition;
    private ImageButton coordinatesPosition;
    private ImageButton directionOrRoute;
    private ImageButton onOffButton;
    private ImageButton speedButton;

    private Polyline line;
    private ArrayList<LatLng> route = new ArrayList<LatLng>();

    private GoogleApiClient googleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = this;
        setContentView(R.layout.activity_location_picker);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        pokemonPosition = (ImageButton) findViewById(R.id.pokemonLocation);
        teleportPosition = (ImageButton) findViewById(R.id.teleportLocation);
        coordinatesPosition = (ImageButton) findViewById(R.id.coordinatsLocation);
        directionOrRoute = (ImageButton) findViewById(R.id.route_direction);
        onOffButton = (ImageButton) findViewById(R.id.on_off_button);
        speedButton = (ImageButton) findViewById(R.id.changeSpeed);

        mMockedLocationProvider = new MockedLocationProvider(this, mContext, providerName);

        googleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(AppIndex.API).build();

        sharedPreferences = getSharedPreferences("PokemonGoCoordinates", MODE_WORLD_READABLE);
        editor = sharedPreferences.edit();
//        editor.clear();
        if (sharedPreferences.getAll().isEmpty()) {
            editor.putBoolean("moduleOn", false);
            editor.putBoolean("isRoute", false);
            editor.putInt("speed", 10);
            editor.apply();
        }

        mMockedLocationProvider.setSpeed(sharedPreferences.getInt("speed", 10));

        File theSharedPrefsFile = new File("data/data/personal.positionfaker/shared_prefs/PokemonGoCoordinates.xml");
//        Log.i("TAG", ""+ theSharedPrefsFile.exists());
        theSharedPrefsFile.setReadable(true, false);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setMapToolbarEnabled(false);

        if (checkPermission()) {
            mMap.setMyLocationEnabled(true);
        }

        myPositionMarker = new MarkerOptions().position(new LatLng(mLatitude, mLongtitude))
                .title("Here is your spoofed position");
        myPositionMarker.icon(BitmapDescriptorFactory.fromBitmap(resizeMapIcons("pokeball_icon", 50, 50))).anchor(0.5f, 0.5f);
        myPositionAtMap = mMap.addMarker(myPositionMarker);

        endPositionMarker = new MarkerOptions().title("Your end distination")
                .position(new LatLng(mLatitude, mLongtitude));
        endPostionAtMap = mMap.addMarker(endPositionMarker);
        endPostionAtMap.setVisible(false);

        pokemonPosition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mMap.getCameraPosition().zoom < 15) {
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(mLatitude, mLongtitude), 15));
                } else {
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(new LatLng(mLatitude, mLongtitude)));
                }
            }
        });

        teleportPosition.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMyLocation(endPositionMarker.getPosition().latitude, endPositionMarker.getPosition().longitude);
                updateMyLocation();
                if (getIsRoute()) {
                    route.clear();
                    line.setPoints(route);
                    route.add(getMyLocation());
                    isMoving = false;
                    endPostionAtMap.setVisible(false);
                    mMockedLocationProvider.stopTimer();
                }
            }
        });

        // Endnu en knap som dog gør det muligt at skrive koordinater direkte ind.
        coordinatesPosition.setOnClickListener(new View.OnClickListener() {
            @TargetApi(Build.VERSION_CODES.M)
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(LocationFaker.this);
                builder.setTitle("Input destination");
                LinearLayout linearLayout = new LinearLayout(mContext);
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                final EditText inputLatitude = new EditText(mContext);
                final EditText inputLongtitude = new EditText(mContext);
                inputLatitude.setHint("Latitude");
                inputLongtitude.setHint("Longtitude");
                inputLatitude.setTextColor(getResources().getColor(R.color.darkText));
                inputLongtitude.setTextColor(getResources().getColor(R.color.darkText));
                inputLatitude.setHintTextColor(getResources().getColor(R.color.darkText_brighter));
                inputLongtitude.setHintTextColor(getResources().getColor(R.color.darkText_brighter));
                inputLatitude.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                inputLongtitude.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                linearLayout.addView(inputLatitude);
                linearLayout.addView(inputLongtitude);
                builder.setView(linearLayout);
                builder.setPositiveButton("GO!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (inputLatitude.getText().toString().length() > 0 && inputLongtitude.getText().toString().length() > 0) {
                            double mInputLatitude = Double.parseDouble(inputLatitude.getText().toString());
                            double mInputLongtitude = Double.parseDouble(inputLongtitude.getText().toString());
                            setGoalLocation(new LatLng(mInputLatitude, mInputLongtitude));
                        } else {
                            dialog.cancel();
                        }
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        });

        speedButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(LocationFaker.this);
                builder.setTitle("Change walkingspeed");
                LinearLayout linearLayout = new LinearLayout(mContext);
                linearLayout.setOrientation(LinearLayout.VERTICAL);
                linearLayout.setPadding(10, 10, 10, 10);
                final SeekBar speedBar = new SeekBar(mContext);
                final TextView speedBarInfo = new TextView(mContext);

                speedBar.setMax(10);
                speedBar.setProgress(sharedPreferences.getInt("speed", 10));
                speedBarInfo.setText("Speed: " + speedBar.getProgress() + "km/h");
                speedBarInfo.setGravity(Gravity.CENTER_HORIZONTAL);

                speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        try {
                            speedBarInfo.setText("Speed: " + progress + "km/h");
                        } catch (Exception e) {
                            Toast.makeText(mContext, "Error", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });

                linearLayout.addView(speedBar);
                linearLayout.addView(speedBarInfo);
                builder.setView(linearLayout);
                builder.setPositiveButton("GO!", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        editor.putInt("speed", speedBar.getProgress());
                        mMockedLocationProvider.setSpeed(speedBar.getProgress());
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        });


        line = mMap.addPolyline(new PolylineOptions()
                .width(10)
                .color(getResources().getColor(R.color.colorPrimary)));

        if (sharedPreferences.getBoolean("isRoute", false)) {
            directionOrRoute.setImageResource(R.drawable.route);
            isMoving = false;
        } else {
            directionOrRoute.setImageResource(R.drawable.direction);
        }

        directionOrRoute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editor.putBoolean("isRoute", (!getIsRoute()));
                editor.apply();
                if (getIsRoute()) {
                    directionOrRoute.setImageResource(R.drawable.route);
                    isMoving = false;
                } else {
                    directionOrRoute.setImageResource(R.drawable.direction);
                    route.clear();
                    line.setPoints(route);
                }
                endPostionAtMap.setVisible(false);
                mMockedLocationProvider.stopTimer();
            }
        });

        setOnOffButtonImage(sharedPreferences.getBoolean("moduleOn", false));

        onOffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean module_on = sharedPreferences.getBoolean("moduleOn", false);
                try {
                    boolean newBool = (!module_on);
                    editor.putBoolean("moduleOn", newBool);
                    editor.apply();
//                    Log.i("TAG ", "Vi skifter moduleOn til "+ newBool);
                    setOnOffButtonImage(!module_on);
                } catch (Exception e) {
                    Toast.makeText(mContext, "Error", Toast.LENGTH_SHORT).show();
                }

            }
        });


        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                setGoalLocation(latLng);
            }
        });
    }

    public void setOnOffButtonImage(boolean moduleOn) {
        if (moduleOn) {
            onOffButton.setImageResource(R.drawable.on_icon);
        } else {
            onOffButton.setImageResource(R.drawable.off_icon);
        }
    }

    public void setGoalLocation(LatLng latLng) {
        endPositionMarker.position(latLng);
        endPostionAtMap.setPosition(endPositionMarker.getPosition());
        endPostionAtMap.setVisible(true);
        if (getIsRoute()) {
            setRoutePartGoalLocation(latLng);
        } else {
            mMockedLocationProvider.moveToLocation(latLng.latitude, latLng.longitude);
        }
    }

    private boolean isMoving = false;

    public void setRoutePartGoalLocation(LatLng latLng) {
        route.add(latLng);
        line.setPoints(route);
        if (!isMoving) {
            route.add(0, getMyLocation());
            indexOnRoute = 1;
            mMockedLocationProvider.moveToLocation(route.get(1).latitude, route.get(1).longitude);
            isMoving = true;
        }
    }

    private int indexOnRoute;

    public boolean getIsRoute() {
        return sharedPreferences.getBoolean("isRoute", false);
    }

    public boolean sendMeToNextRoutePoint() {
        if (route.size() == indexOnRoute) {
            mMockedLocationProvider.moveToLocation(getMyLocation().latitude, getMyLocation().latitude);
            isMoving = false;
            route.clear();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    line.setPoints(route);
                    endPostionAtMap.setVisible(false);
                }
            });
            route.add(getMyLocation());
            return true;
        }
        if (route.size() > indexOnRoute) {
            mMockedLocationProvider.moveToLocation(route.get(indexOnRoute).latitude, route.get(indexOnRoute).longitude);
            indexOnRoute++;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (indexOnRoute > 2) {
                        route.remove(indexOnRoute - 3);
                        indexOnRoute--;
                    }
                    line.setPoints(route);
                }
            });
        }
        return false;
    }

    public void goalReached() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                endPostionAtMap.setVisible(false);
            }
        });
    }

    public void setMyLocation(double lat, double lon) {
        mLatitude = lat;
        mLongtitude = lon;
        editor.putString("latitude", "" + mLatitude);
        editor.putString("longtitude", "" + mLongtitude);
        editor.apply();
        upDatePolyLine();
//        Log.i("TAG", "setMyLocation");
    }

    public void upDatePolyLine() {
        if (getIsRoute()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (!route.isEmpty()) {
                        route.remove(0);
                        route.add(0, getMyLocation());
                        line.setPoints(route);
                    }
                }
            });
        }
    }

    public LatLng getMyLocation() {
        return myPositionMarker.getPosition();
    }

    public void updateMyLocation() {
        myPositionMarker.position(new LatLng(mLatitude, mLongtitude));
        myPositionAtMap.setPosition(getMyLocation());
    }

    public Bitmap resizeMapIcons(String iconName, int width, int height) {
        Bitmap imageBitmap = BitmapFactory.decodeResource(getResources(), getResources().getIdentifier(iconName, "drawable", getPackageName()));
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(imageBitmap, width, height, false);
        return resizedBitmap;
    }

    public boolean checkPermission() {
        return !((ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        googleApiClient.disconnect();
//        new File("/data/data/personal.positionfaker/shared_prefs/PokemonGoCoordinates.xml").setReadable(true, false);
    }

    @Override
    public void onPause() {
        super.onPause();
//        new File("/data/data/personal.positionfaker/shared_prefs/PokemonGoCoordinates.xml").setReadable(true, false);
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (checkPermission()) {
            Location mLastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
            if (mLastLocation != null) {
                setMyLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                updateMyLocation();
            }
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }


}