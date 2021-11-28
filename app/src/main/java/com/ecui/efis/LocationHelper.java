package com.ecui.efis;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

public class LocationHelper implements LocationListener {
    private Context context;
    private LocationManager locationManager;

    public LocationHelper(Context _context){
        context=_context;
    }

    public Location getLocation(){
        locationManager=(LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGPSEnabled=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if(!isGPSEnabled){
            Toast.makeText(context,"GPS FAILED!",Toast.LENGTH_SHORT).show();
            return null;
        }
        else{
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(context,"NO GPS PERMISSION!",Toast.LENGTH_LONG).show();
                return null;
            }

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,100,1,this);
            return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {

    }
}
