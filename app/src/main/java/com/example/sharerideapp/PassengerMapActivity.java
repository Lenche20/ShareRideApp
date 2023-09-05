package com.example.sharerideapp;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import static com.google.android.gms.location.places.Places.GeoDataApi;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.sharerideapp.databinding.ActivityPassengerMapBinding;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.widget.AutocompleteSupportFragment;
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PassengerMapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    public LocationRequest mLocationRequest;
    private Button mLogout, mRequest, mSettings, mSchedule;
    private LatLng pickUpLocation;
    private Boolean requestFlag = false; // boolean za request, dali ima ili nema request za ride
    private Marker pickUpMarker;
    private String destination;
    private LatLng destinationLatLng;
    private LinearLayout mDriverInfo;
    private TextView mDriverName, mDriverPhone, mDriverCar;


    private ActivityPassengerMapBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPassengerMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()    //se povikuva mapata
                .findFragmentById(R.id.map);
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PassengerMapActivity.this,new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
        }else {
            mapFragment.getMapAsync(this); //se startuva mapata
        }
        destinationLatLng = new LatLng(0.0, 0.0);

        mDriverInfo = (LinearLayout) findViewById(R.id.driverInfo);
        mDriverName = (TextView) findViewById(R.id.driverName);
        mDriverPhone = (TextView) findViewById(R.id.driverPhone);
        mDriverCar = (TextView) findViewById(R.id.driverCar);

        mLogout = (Button) findViewById(R.id.logout);  //sledniov kod e za log out
        mRequest = (Button) findViewById(R.id.request);  //sledniov kod e za requesting a ride
        mSettings = (Button) findViewById(R.id.settings);  //sledniov kod e za settings delot
//        mSchedule = (Button) findViewById(R.id.schedule);  //sledniov kod e za scheduling a ride

        mLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut(); //se odlogira userot, funkcija od firebase e ova
                Intent intent = new Intent(PassengerMapActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        mRequest.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(requestFlag){ // boolean za request, ima request za ride ako ovoj flag e true
                    requestFlag = false; //false znaci deka nema aktiven request za ride
                    geoQuery.removeAllListeners(); //da ne slusha listenerot za querinja (za lokacija na najblizok vozac sto bara podole vo funkcijata za closestDriver)
                    driverLocationRef.removeEventListener(driverLocationRefListener); //se trga ovaa referenca, za da moze nova referenca da se stavi so ova ima, za drug ride request

                    if(driverFound!=null){
                        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("rideRequest");
                        driverRef.setValue(true); //vrednosta na driverFoundId se stava da e true i toa znaci deka ke se izbrishe toa dete od bazata
                        driverFoundID = null;
                    }
                    driverFound = false; //prebrishuvas vrednost na driverFound
                    radius = 1;  //prebrishuvas prethodna vrednost na radius, da zapocne od 1 pak
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid(); //trga values od bazata
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("rideRequests"); // se odi nazacki za da se napravi undo na se sto e napraveno za da se vnesat podatocite
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.removeLocation(userId);

                    if(pickUpMarker !=null){
                        pickUpMarker.remove();//trganje na markerot za pickUp lokacijata
                    }
                    if(mDriverMarker !=null){
                        mDriverMarker.remove();//trganje na markerot za lokacijata na vozacot
                    }
                    mDriverInfo.setVisibility(View.GONE); //da ne se gleda infoto koga ima veke ne e ovoj patnik za ovoj vozac
                    mDriverName.setText(""); //prebrishuva informacii za toj patnik vo bazata kaj vozacot
                    mDriverCar.setText("");
                    mDriverPhone.setText("");

                    mRequest.setText("Request a Ride");

                }else { //zapocni request ako flagot e false
                    requestFlag=true;
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("rideRequests");

                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId,new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude())); //pravi child vo rideRequest sto ke ima userID i lokacijata

                    pickUpLocation = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    pickUpMarker = mMap.addMarker(new MarkerOptions().position(pickUpLocation).title("Your PickUp location")); //marker za imeto na pick-up lokacijata da ima na mapara
                    mRequest.setText("Getting your Driver..."); //poraka za passenger-ot
                    getClosestDriver();
                }
            }
        });
        mSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(PassengerMapActivity.this, PassengerSettingsActivity.class);
                startActivity(intent);
                return; //nema finish, zasto celo vreme e aktivna (nad mapata e ova)
            }
        });

        //ova e za da moze da se zeme stringot sto ke go vnese user-ot
        // Initialize the AutocompleteSupportFragment.
        AutocompleteSupportFragment autocompleteFragment = (AutocompleteSupportFragment)
                getSupportFragmentManager().findFragmentById(R.id.autocomplete_fragment);

        // Specify the types of place data to return.
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME));

        // Set up a PlaceSelectionListener to handle the response.
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(@NonNull Place place) {
                // TODO: Get info about the selected place.
                destination = place.getName().toString();
//                destinationLatLng = place.getLatLng();
           }
            @Override
            public void onError(@NonNull Status status) {
                // TODO: Handle the error.
            }
        });

//        PlaceAutocompleteFragment()
//        GeoDataApi.getAutocompletePredictions();
    }
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
        mLastLocation = location;
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(11)); //mapata ke se menuva kako sto se dvizi userot
    }
    @Override
    public void onConnected(@Nullable Bundle bundle) { //koga mapata e povikana i se e podgotveno da pocne so rabota
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(PassengerMapActivity.this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST_CODE);
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

                }         else{
                    Toast.makeText(getApplicationContext(), "please provide the permission", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }
    private int radius = 1;
    private Boolean driverFound = false; //flag za dali e najden driver
    private String driverFoundID;
    GeoQuery geoQuery;
    private void getClosestDriver(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("driversAvailable");  //gi zema driversAvailable
        GeoFire geoFire = new GeoFire(driverLocation);

        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickUpLocation.latitude, pickUpLocation.longitude), radius); //radius okolu lokacijata na patnikot ke se napravi (long i latitude na toj sto pravi rideRequest
        geoQuery.removeAllListeners(); //za da se izbrishe prethodniot listener ako ne e najden driver vo toj radius i da moze od novo da slusha za vo nov radius
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() { //se dodava nov listener za toa query
            @Override
            public void onKeyEntered(String key, GeoLocation location) { //sekogas koga ke se najde driver vo radius se povikuva ovaa funkcija i se zema key (id-to) i lokacijata na driverot
                if(!driverFound && requestFlag){ //ako ne e najden  dosega driver i ima aktiven request za ride
                    driverFound = true; // pishi deka e najden driver vo toj radius
                    driverFoundID = key;

                    //mu kazuvame na vozacot koj patnik treba da go vozi
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID).child("rideRequest"); //novo dete (child) se pravi vo Drivers vo firebase dazata
                    String passengerId = FirebaseAuth.getInstance().getCurrentUser().getUid(); //se zema id-to na passenger-ot koj bara ride
                    HashMap map = new HashMap();
                    map.put("passengerRideId", passengerId);
                    map.put("destination", destination);
//                    map.put("destinationLat", destinationLatLng.latitude);
//                    map.put("destinationLng", destinationLatLng.longitude);
                    driverRef.updateChildren(map); //se stava id-to na patnikot koj bara ride vo Driverot koj e najblisku do toj patnik

                    getDriverLocation(); //se zema lokacijata na driverot za patnikot
                    getDriverInfo();
                    mRequest.setText("Looking for Driver Location..."); //za da znae patnikot deka e najden driver
                }
            }
            @Override
            public void onKeyExited(String key) {
            }
            @Override
            public void onKeyMoved(String key, GeoLocation location) {
            }
            @Override
            public void onGeoQueryReady() { // koga ke se povika ovaa funkcija, znaci deka ne sme nashe driver vo toj radius
                if(!driverFound && requestFlag){ //ako uste ne e najde driver vo toj radius i ima aktiven request za ride
                    radius++; //togas zgolemi go radiusot za 1
                    getClosestDriver(); //se povikuva sebe si, za odnovo da bara
                }
            }
            @Override
            public void onGeoQueryError(DatabaseError error) {
            }
        });
    }
    private Marker mDriverMarker; // marker za lokacijata na vozacot
    DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationRefListener;

    private void getDriverLocation(){ //mu kazuvame na patnikot kade e negoviot vozac
        driverLocationRef = FirebaseDatabase.getInstance().getReference().child("driversAvailable").child(driverFoundID).child("l"); //  zema lokacija od site drivers koi se slobodni
        driverLocationRefListener = driverLocationRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) { //sekogas koga ke se smeni lokacijata se povikuva funkcijava
                if(snapshot.exists() && requestFlag){
                    List<Object> map = (List<Object>) snapshot.getValue(); //stava se od snapshotot od podatoci vo lista koja ke moze da ja koristime
                    double locationLat = 0;
                    double locationLong = 0;
                    mRequest.setText("Driver is Found");//za da znae patnikot deka e najden driver
                    if(map.get(0)!=null){   // 0 e key na latitude vo firebase bazata i ne treba da e null, tuku da postoi
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1)!=null){   // 1 e key na longitude vo firebase bazata i ne treba da e null, tuku da postoi
                        locationLong = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat,locationLong);
                    if(mDriverMarker!=null){ //ako ne e prv pat da se pokaze marker
                        mDriverMarker.remove(); //trgni go toj marker sto postoi sega (za da stavis nov posle)
                    }
                    Location loc1 = new Location(""); //lokacijata na patnikot
                    loc1.setLatitude(pickUpLocation.latitude);
                    loc1.setLongitude(pickUpLocation.longitude);
                    Location loc2 = new Location(""); //lokacijata na vozacot
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);
                    float distance = loc1.distanceTo(loc2); //Location klasata si ima funkcija .distance za 2 lokacii

                    if (distance<100){
                        mRequest.setText("Your Driver is Here! The Distance is " + String.valueOf(distance)); //ako e blisku vozacot do patnikot
                    }else{  //ako e daleku vozacot od patnikot,
                        mRequest.setText("Your Driver is Found: The Distance is " + String.valueOf(distance)); //distance e vozdushna razliika vo metri
                    }
                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Your Driver is Here"));
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }
    private void getDriverInfo(){
        mDriverInfo.setVisibility(View.VISIBLE); //da se gleda infoto koga ima assigned patnik za ovoj vozac
        DatabaseReference mPassengerDatabase = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverFoundID);
        mPassengerDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists() && snapshot.getChildrenCount()>0){
                    Map<String, Object> map = (Map<String, Object>) snapshot.getValue();
                    if(map.get("name")!=null){ //ako postoi ova dete - name
                        mDriverName.setText(map.get("name").toString());
//                        mDriverName.setText(snapshot.child("name").getValue().toString());
                    }
                    if(map.get("phone")!=null){ //ako postoi ova dete - phone
                        mDriverPhone.setText(map.get("phone").toString());
                    }
                    if(map.get("car")!=null){ //ako postoi ova dete - phone
                        mDriverCar.setText(map.get("car").toString());
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }
    @Override
    protected void onStop() {  //za da znaeme koga userot izlegol od appot (back, home, exit kopcinja..)
        super.onStop();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data); // This is important

        // Your custom handling for onActivityResult goes here
    }

}