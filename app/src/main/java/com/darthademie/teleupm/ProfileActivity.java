package com.darthademie.teleupm;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.darthademie.teleupm.databinding.ActivityProfileBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Configuramos la Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mi Perfil");
        }

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        mDatabase = FirebaseDatabase.getInstance().getReference();

        if (currentUser != null) {
            String uid = currentUser.getUid();
            loadUserProfile(uid);
        } else {
            Toast.makeText(this, "No se ha iniciado sesión", Toast.LENGTH_SHORT).show();
            finish();
        }

        binding.btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            Toast.makeText(this, "Sesión cerrada", Toast.LENGTH_SHORT).show();
            // Volver a la pantalla de Login y limpiar la pila de Activities
            android.content.Intent intent = new android.content.Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadUserProfile(String uid) {
        mDatabase.child("usuarios").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String nombre = dataSnapshot.child("nombre").getValue(String.class);
                    String apellidos = dataSnapshot.child("apellidos").getValue(String.class);
                    String direccion = dataSnapshot.child("direccion").getValue(String.class);

                    binding.tvProfileNombre.setText(nombre != null ? nombre : "No disponible");
                    binding.tvProfileApellidos.setText(apellidos != null ? apellidos : "No disponible");
                    binding.tvProfileDireccion.setText(direccion != null ? direccion : "No disponible");
                } else {
                    Toast.makeText(ProfileActivity.this, "El perfil no existe en la base de datos.", Toast.LENGTH_SHORT).show();
                    binding.tvProfileNombre.setText("-");
                    binding.tvProfileApellidos.setText("-");
                    binding.tvProfileDireccion.setText("-");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("ProfileActivity", "Error al cargar perfil: " + databaseError.getMessage());
                Toast.makeText(ProfileActivity.this, "Error al cargar datos del perfil.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
