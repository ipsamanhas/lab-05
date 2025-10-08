package com.example.lab5_starter;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CityDialogFragment.CityDialogListener {

    private Button addCityButton;
    private ListView cityListView;
    private FloatingActionButton fabDelete;

    private ArrayList<City> cityArrayList;
    private ArrayAdapter<City> cityArrayAdapter;

    private FirebaseFirestore db;
    private CollectionReference citiesRef;

    // keep track of the currently selected city (single-select delete)
    private City selectedCity = null;

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
        cityListView = findViewById(R.id.listviewCities);
        fabDelete = findViewById(R.id.fabDelete);

        // Adapter
        cityArrayList = new ArrayList<>();
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
            if (value != null && !value.isEmpty()){
                cityArrayList.clear();
                for (QueryDocumentSnapshot snapshot: value){
                    String name = snapshot.getString("name");
                    String province = snapshot.getString("province");
                    cityArrayList.add(new City(name, province));
                }
                cityArrayAdapter.notifyDataSetChanged();

                // if the previously-selected city was deleted, clear the selection
                if (selectedCity != null) {
                    boolean stillThere = false;
                    for (City c : cityArrayList) {
                        if (c.getName().equals(selectedCity.getName())) {
                            stillThere = true;
                            break;
                        }
                    }
                    if (!stillThere) {
                        selectedCity = null;
                        fabDelete.setVisibility(View.GONE);
                    }
                }
            } else {
                cityArrayList.clear();
                cityArrayAdapter.notifyDataSetChanged();
                selectedCity = null;
                fabDelete.setVisibility(View.GONE);
            }
        });

        // Add City
        addCityButton.setOnClickListener(view -> {
            CityDialogFragment cityDialogFragment = new CityDialogFragment();
            cityDialogFragment.show(getSupportFragmentManager(),"Add City");
        });

        // Select for edit OR delete
        cityListView.setOnItemClickListener((adapterView, view, i, l) -> {
            selectedCity = cityArrayAdapter.getItem(i);
            // show FAB only when an item is selected
            fabDelete.setVisibility(View.VISIBLE);

            // open details dialog
            CityDialogFragment cityDialogFragment = CityDialogFragment.newInstance(selectedCity);
            cityDialogFragment.show(getSupportFragmentManager(),"City Details");
        });

        // Delete selected city when FAB tapped
        fabDelete.setOnClickListener(v -> {
            if (selectedCity == null) {
                Toast.makeText(this, "Select a city to delete", Toast.LENGTH_SHORT).show();
                return;
            }
            // Delete from Firestore; snapshot listener updates the UI
            citiesRef.document(selectedCity.getName()).delete()
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this, "Deleted " + selectedCity.getName(), Toast.LENGTH_SHORT).show();
                        selectedCity = null;
                        fabDelete.setVisibility(View.GONE);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    // ===== Dialog callbacks =====

    @Override
    public void updateCity(City city, String title, String year) {
        // capture old document id *before* mutating
        String oldName = city.getName();

        // update local object & UI
        city.setName(title);
        city.setProvince(year);
        cityArrayAdapter.notifyDataSetChanged();

        // if the document id (name) changed, delete the old doc
        if (!oldName.equals(title)) {
            citiesRef.document(oldName).delete();
        }

        // write/replace the new/updated doc
        citiesRef.document(title).set(new City(title, year));
    }

    @Override
    public void addCity(City city){
        // write full object; snapshot listener will refresh UI
        DocumentReference docRef = citiesRef.document(city.getName());
        docRef.set(city);
    }
}
