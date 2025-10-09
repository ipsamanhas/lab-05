package com.example.lab5_starter;

import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CityDialogFragment.CityDialogListener {

    private Button addCityButton;
    private ListView cityListView;
    private MaterialButton buttonDelete;   // <-- red bottom button

    private ArrayList<City> cityArrayList;
    private ArrayAdapter<City> cityArrayAdapter;

    private FirebaseFirestore db;
    private CollectionReference citiesRef;

    // delete-mode: after tapping Delete, next city tap deletes that city
    private boolean isDeleteMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Views
        addCityButton = findViewById(R.id.buttonAddCity);
        cityListView  = findViewById(R.id.listviewCities);
        buttonDelete  = findViewById(R.id.buttonDelete);

        // Adapter
        cityArrayList    = new ArrayList<>();
        cityArrayAdapter = new CityArrayAdapter(this, cityArrayList);
        cityListView.setAdapter(cityArrayAdapter);

        // Firestore
        db = FirebaseFirestore.getInstance();
        citiesRef = db.collection("cities");

        // Live sync
        citiesRef.addSnapshotListener((value, error) -> {
            if (error != null) {
                Log.e("Firestore", error.toString());
                return;
            }
            cityArrayList.clear();
            if (value != null && !value.isEmpty()) {
                for (QueryDocumentSnapshot snapshot : value) {
                    String name = snapshot.getString("name");
                    String province = snapshot.getString("province");
                    cityArrayList.add(new City(name, province));
                }
            }
            cityArrayAdapter.notifyDataSetChanged();
        });

        // Add City
        addCityButton.setOnClickListener(view -> {
            CityDialogFragment cityDialogFragment = new CityDialogFragment();
            cityDialogFragment.show(getSupportFragmentManager(), "Add City");
        });

        // Tap list items: delete if armed; otherwise open edit dialog
        cityListView.setOnItemClickListener((adapterView, view, i, l) -> {
            City tapped = cityArrayAdapter.getItem(i);
            if (tapped == null) return;

            if (isDeleteMode) {
                citiesRef.document(tapped.getName()).delete()
                        .addOnSuccessListener(unused -> {
                            Toast.makeText(this, "Deleted " + tapped.getName(), Toast.LENGTH_SHORT).show();
                            setDeleteMode(false);
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                CityDialogFragment cityDialogFragment = CityDialogFragment.newInstance(tapped);
                cityDialogFragment.show(getSupportFragmentManager(), "City Details");
            }
        });

        // Red bottom "Delete" button toggles delete mode
        buttonDelete.setOnClickListener(v -> setDeleteMode(!isDeleteMode));
        setDeleteMode(false); // default
    }

    /** Be able to delete any city of the user's choice */
    private void setDeleteMode(boolean enabled) {
        isDeleteMode = enabled;
        if (enabled) {
            Toast.makeText(this, "Delete mode ON: tap a city to delete. Tap Delete again to cancel.", Toast.LENGTH_SHORT).show();
            buttonDelete.setAlpha(1.0f);
        } else {
            buttonDelete.setAlpha(0.95f);
        }
    }

    // ===== Dialog callbacks =====

    @Override
    public void updateCity(City city, String title, String year) {
        // capture old id BEFORE mutation
        String oldName = city.getName();

        // update local object & UI
        city.setName(title);
        city.setProvince(year);
        cityArrayAdapter.notifyDataSetChanged();

        // if id changed, delete old doc
        if (!oldName.equals(title)) {
            citiesRef.document(oldName).delete();
        }
        // write/replace new doc
        citiesRef.document(title).set(new City(title, year));
    }

    @Override
    public void addCity(City city){
        DocumentReference docRef = citiesRef.document(city.getName());
        docRef.set(city);
    }
}
