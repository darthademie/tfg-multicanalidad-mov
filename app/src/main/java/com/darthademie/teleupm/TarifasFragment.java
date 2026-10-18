package com.darthademie.teleupm;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TarifasFragment extends Fragment {

    private static final String ARG_TYPE = "type";
    private boolean isActiveTab;
    private RecyclerView recyclerView;
    private TarifasAdapter adapter;

    private DatabaseReference dbRef;
    private String currentUid;

    private List<Tarifa> allTarifas = new ArrayList<>();
    private Map<String, String> userTarifas = new HashMap<>(); // pushId -> tarifaId

    public static TarifasFragment newInstance(boolean isActiveTab) {
        TarifasFragment fragment = new TarifasFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_TYPE, isActiveTab);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            isActiveTab = getArguments().getBoolean(ARG_TYPE);
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUid = auth.getCurrentUser().getUid();
        }
        dbRef = FirebaseDatabase.getInstance().getReference();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_tarifas, container, false);
        recyclerView = view.findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new TarifasAdapter(this::onTarifaClicked);
        recyclerView.setAdapter(adapter);

        loadData();

        return view;
    }

    private void loadData() {
        if (currentUid == null) return;

        // Fetch all tarifas
        dbRef.child("tarifas").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allTarifas.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Tarifa t = new Tarifa(ds.getKey(), 
                            ds.child("nombre").getValue(String.class), 
                            ds.child("descripcion").getValue(String.class));
                    allTarifas.add(t);
                }
                updateList();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Fetch user active tarifas
        dbRef.child("usuarios").child(currentUid).child("tarifas").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userTarifas.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String tarifaId = ds.getValue(String.class);
                    userTarifas.put(ds.getKey(), tarifaId);
                }
                updateList();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateList() {
        List<Tarifa> displayList = new ArrayList<>();
        for (Tarifa t : allTarifas) {
            boolean hasTarifa = userTarifas.containsValue(t.id);
            if (isActiveTab && hasTarifa) {
                // Find pushId
                for (Map.Entry<String, String> entry : userTarifas.entrySet()) {
                    if (entry.getValue().equals(t.id)) {
                        t.userPushId = entry.getKey();
                        break;
                    }
                }
                displayList.add(t);
            } else if (!isActiveTab && !hasTarifa) {
                displayList.add(t);
            }
        }
        if (adapter != null) {
            adapter.setTarifas(displayList);
        }
    }

    private void onTarifaClicked(Tarifa tarifa) {
        if (currentUid == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        if (isActiveTab) {
            builder.setTitle("Cancelar Tarifa");
            builder.setMessage("¿Deseas cancelar la tarifa " + tarifa.nombre + "?");
            builder.setPositiveButton("Sí", (dialog, which) -> {
                dbRef.child("usuarios").child(currentUid).child("tarifas").child(tarifa.userPushId)
                        .removeValue()
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Se aplicará a partir del siguiente mes", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Inténtelo más tarde", Toast.LENGTH_SHORT).show());
            });
            builder.setNegativeButton("No", null);
        } else {
            builder.setTitle("Asignar Tarifa");
            builder.setMessage("¿Deseas asignar la tarifa " + tarifa.nombre + "?");
            builder.setPositiveButton("Sí", (dialog, which) -> {
                dbRef.child("usuarios").child(currentUid).child("tarifas").push()
                        .setValue(tarifa.id)
                        .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "La operación se ha aplicado con éxito para este mes", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(getContext(), "Inténtelo más tarde", Toast.LENGTH_SHORT).show());
            });
            builder.setNegativeButton("No", null);
        }
        builder.show();
    }
}
