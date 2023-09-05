package com.example.sharerideapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;

import android.location.LocationListener;
import android.location.LocationManager;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.sharerideapp.databinding.ActivityDriverMapBinding;



import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationRequest;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;
import java.util.Map;
import java.util.Objects;


public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    public LocationRequest mLocationRequest;
    private FusedLocationProviderClient mFusedLocationClient;
    private Button mLogout, mSettings;
    private String passengerId = "", destination;
    private LatLng destinationLatlng, pickUpLatLng;
    private Boolean loggingOutFlag = false;
    private LinearLayout mPassengerInfo;
    private TextView mPassengerName, mPassengerPhone, mPassengerDestination;
    private ActivityDriverMapBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriverMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()    //se povikuva mapata
                .findFragmentById(R.id.map);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else {
            mapFragment.getMapAsync(this); //se startuva mapata
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mPassengerInfo = (LinearLayout) findViewById(R.id.passengerInfo);
        mPassengerName = (TextView) findViewById(R.id.passengerName);
        mPassengerPhone = (TextView) findViewById(R.id.passengerPhone);
        mPassengerDestination = (TextView) findViewById(R.id.passengerDestination);

        mSettings = (Button) findViewById(R.id.settings);
        mLogout = (Button) findViewById(R.id.logout);  //sledniov kod e za log out
        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loggingOutFlag = true;
                logoutDriver(); //trganje od driversAvailable od baza

                FirebaseAuth.getInstance().signOut(); //se odlogira userot, funkcija od firebase e ova
                Intent intent = new Intent(DriverMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DriverMapActivity.this, DriverSettingsActivity.class);
                startActivity(intent);
                return;
            }
        });
        getAssignedPassenger();
    }
    private void getAssignedPassenger(){ //ovaa funkcija se povikuva koga patnik ke request-ne ride, i koga ovoj driver ke se najde kako najblizok do toj patnik
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid(); //se zema id-to na passenger-ot koj bara ride
        DatabaseReference assignedPassengerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("rideRequest").child("passengerRideId"); //novo dete (child) se pravi vo driver-ot koj ke bide nekov vozac vo firebase dazata
        assignedPassengerRef.addValueEventListener(new ValueEventListener() { //ova e listener sto sekogas koga driverot e najaven ke slusha
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){ //false e ako e trgnato deteto sto gi cuva ovie podatoci
                    passengerId = snapshot.getValue().toString();
                    getAssignedPassengerPickUpLocation();
                    getAssignedPassengerInfo();
                    getAssignedPassengerDestination();

                }else{ //ova else se povikuva sekoga koga customerRideId se trga od id-to na vozacot
                    //tuka e trigerot sto mu kazuva na vozacot deka e otkazan negoviot ride request
                    passengerId = ""; //na null se vrakja id-to na patnikot
                    if(mPickUpMarker!=null){
                        mPickUpMarker.remove();
                    }
                    if(assignedPassengerPickUpLocationRefListener!=null) {
                        assignedPassengerPickUpLocationRef.removeEventListener(assignedPassengerPickUpLocationRefListener); //se trga listener-ot
                    }
                    mPassengerInfo.setVisibility(View.GONE); //da ne se gleda infoto koga ima veke ne e ovoj patnik za ovoj vozac
                    mPassengerName.setText(""); //prebrishuva informacii za toj patnik vo bazata kaj vozacot
                    mPassengerDestination.setText("Destination: -- ");
                    mPassengerPhone.setText("");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
    private Marker mPickUpMarker;
    private DatabaseReference assignedPassengerPickUpLocationRef;
    private ValueEventListener assignedPassengerPickUpLocationRefListener;
    private void getAssignedPassengerPickUpLocation(){ //za zemanje na lokacijata na patnikot
        assignedPassengerPickUpLocationRef = FirebaseDatabase.getInstance().getReference().child("rideRequests").child(passengerId).child("l"); //novo dete so IDto na najdeniot driver koe ja ima negovata lokacija se pravi vo DriversWorking - koe i toa se sozdava ako go nema dosega vo firebase dazata
        assignedPassengerPickUpLocationRefListener = assignedPassengerPickUpLocationRef.addValueEventListener(new ValueEventListener() { //ova e listener sto sekogas koga driverot e najaven ke slusha
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && !passengerId.equals("")){ //ako ne e prazno deteto na id-to na vozacot
                    List<Object> map = (List<Object>) snapshot.getValue(); //koristime lista zasto se brojki
                    double locationLat = 0; //ova e lokacijata na patnikot
                    double locationLong = 0;
                    if(map.get(0)!=null){   // 0 e key na latitude vo firebase bazata i ne treba da e null, tuku da postoi
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1)!=null){   // 1 e key na longitude vo firebase bazata i ne treba da e null, tuku da postoi
                        locationLong = Double.parseDouble(map.get(1).toString());
                    }
                    pickUpLatLng = new LatLng(locationLat,locationLong);
                    mPickUpMarker = mMap.addMarker(new MarkerOptions().position(pickUpLatLng).title("The PickUp Location"));
//                    getRouteToMarker(pickUpLatLng);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }  
    private void getAssignedPassengerDestination(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid(); //se zema id-to na passenger-ot koj bara ride
        DatabaseReference assignedPassengerRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverId).child("rideRequest").child("destination"); //novo dete (child) se pravi vo driver-ot koj ke bide nekov vozac vo firebase dazata
        assignedPassengerRef.addListenerForSingleValueEvent(new ValueEventListener() { //ova e listener sto sekogas koga driverot e najaven ke slusha
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){ //false e ako e trgnato deteto sto gi cuva ovie podatoci
//                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
//                    if(map.get("destination")!=null){
//                        destination = map.get("destination").toString();
                    destination = snapshot.getValue().toString();
                    mPassengerDestination.setText("Destination: "+destination);
                    }else{ //ke se povika koga patnikot povikal vozac ama ne stavil destinacija
                        mPassengerDestination.setText("Destination: -- ");
                    }
//                    Double destinationLat = 0.0;
//                    Double destinationLng = 0.0;
//                    if(map.get("destinationLat") != null){
//                        destinationLat = Double.valueOf(map.get("destinationLat").toString());
//                    }
//                    if(map.get("destinationLng") != null){
//                        destinationLng = Double.valueOf(map.get("destinationLng").toString());
//                        destinationLatlng = new LatLng(destinationLat, destinationLng);
//                    }
                }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
    private void getAssignedPassengerInfo(){
        mPassengerInfo.setVisibility(View.VISIBLE); //da se gleda infoto koga ima assigned patnik za ovoj vozac
        DatabaseReference mPassengerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Passengers").child(passengerId);
        mPassengerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if(map.get("name")!=null){ //ako postoi ova dete - name
                        mPassengerName.setText(map.get("name").toString());
                    }
                    if(map.get("phone")!=null){ //ako postoi ova dete - phone
                        mPassengerPhone.setText(map.get("phone").toString());
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
//    private void getRouteToMarker(LatLng pickUpLatLng){
//        if(pickUpLatLng != null && mLastLocation != null){
//            Routing routing = new Routing.Builder()
//                    .travelMode(AbstractRouting.TravelMode.DRIVING)
//                    .withListener(this)
//                    .alternativeRoutes(false)
//                    .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()), pickupLatLng)
//                    .build();
//            routing.execute();
//        }
//    }

    @Override
    public void onMapReady(GoogleMap googleMap) {  //proveruva koga e loadirana mapata
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }
    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi (LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }
    @Override
    public void onLocationChanged(@NonNull Location location) { //ovaa funkcija se povikuva sekoja sekunda, onConnection i GetMapReady gi podgotvuvaat rabotite za da raboti ovaa f-ja
        if(getApplicationContext()!=null){
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11)); //mapata ke se menuva kako sto se dvizi userot

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); //of firebase go zema id-to na userot koj e currently logged in
            DatabaseReference refAvailable = FirebaseDatabase.getInstance().getReference("driversAvailable"); //vo ova ke bidat site drivers koi se currently available
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driversWorking"); //vo ova ke bidat site drivers koi se currently working

            GeoFire geoFireAvailable = new GeoFire(refAvailable); //reference kade da se zacuva datata (userId)
            GeoFire geoFireWorking = new GeoFire(refWorking); //reference kade da se zacuva datata (userId)

            switch (passengerId){ //switch megju working i available da e driverot
                case "": //ako passengerId e prazno - nema passengers assigned to this driver
                    geoFireWorking.removeLocation(userId);
                    geoFireAvailable.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude())); //tuka (vo userId) ke se zacuva infoto (latitude i longitude) za ovoj current user
                    break;
                default:
                    geoFireAvailable.removeLocation(userId);
                    geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(), location.getLongitude())); //tuka (vo userId) ke se zacuva infoto (latitude i longitude) za ovoj current user
                    break;
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) { //koga mapata e povikana i se e podgotveno da pocne so rabota

        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(DriverMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this); //triger za refresh na lokacijata
    }
    @Override
    public void onConnectionSuspended(int i) {
    }
    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }
    final int LOCATION_REQUEST_CODE = 1;
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case LOCATION_REQUEST_CODE:
                if(grantResults.length>0&&grantResults[0] ==PackageManager.PERMISSION_GRANTED){
                    SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()    //se povikuva mapata
                            .findFragmentById(R.id.map);
                    mapFragment.getMapAsync(this); //se startuva mapata

                } else{
                    Toast.makeText(getApplicationContext(), "please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }
    private void logoutDriver(){ //trganje od driversAvailable od baza
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); //id na userot koj e currently logged in
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAvailable"); //vo ova ke bidat site drivers koi se currently available

        GeoFire geoFire = new GeoFire(ref); //reference kade da se zacuva datata (userId)
        geoFire.removeLocation(userId); // ke go izvadime toj userId da ne bide veke dete na DriverAvailable
    }
    @Override
    protected void onStop() {  //za da znaeme koga userot izlegol od appot (back, home, exit kopcinja..)
        super.onStop();
        if(!loggingOutFlag){ //ako stisnal na logout kopceto ne go pravi slednoto, zasto inace se ubiva aktivnosta pa se vika onStop
            logoutDriver();
        }
       }
}