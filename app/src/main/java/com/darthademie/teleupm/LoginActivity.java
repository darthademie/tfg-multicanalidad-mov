package com.darthademie.teleupm;

import static java.lang.System.exit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.darthademie.teleupm.databinding.ActivityLoginBinding;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private static final String PREFS_NAME = "LoginPrefs";
    private static final String PREF_REMEMBER_ME = "remember_me";
    private static final String PREF_SAVED_EMAIL = "saved_email";

    private ActivityLoginBinding binding;

    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Configurar Google Sign-In
        String webClientId = "";
        try {
            webClientId = getString(R.string.default_web_client_id);
        } catch (Exception e) {
            exit(1);
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        // Lógica de "Recuérdame": Recuperar datos de SharedPreferences
        loadRememberedUser();

        // Botón de login con correo y contraseña
        binding.btnLogin.setOnClickListener(v -> {
            String email = binding.etEmail.getText() != null ? binding.etEmail.getText().toString().trim() : "";
            String password = binding.etPassword.getText() != null ? binding.etPassword.getText().toString().trim() : "";

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(LoginActivity.this, "Por favor, completa todos los campos.", Toast.LENGTH_SHORT).show();
                return;
            }

            loginWithEmail(email, password);
        });

        // Botón de login con Google
        binding.btnGoogleLogin.setOnClickListener(v -> signInWithGoogle());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Verificar si el usuario ya ha iniciado sesión previamente
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToMainActivity();
        }
    }

    /**
     * Carga el correo guardado en SharedPreferences si la casilla "Recuérdame" estaba marcada.
     */
    private void loadRememberedUser() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean isRemembered = prefs.getBoolean(PREF_REMEMBER_ME, false);
        if (isRemembered) {
            String savedEmail = prefs.getString(PREF_SAVED_EMAIL, "");
            binding.etEmail.setText(savedEmail);
            binding.cbRememberMe.setChecked(true);
        }
    }

    /**
     * Guarda o elimina el usuario en SharedPreferences según el estado del CheckBox "Recuérdame".
     */
    private void saveRememberMePreference(String email) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (binding.cbRememberMe.isChecked()) {
            editor.putBoolean(PREF_REMEMBER_ME, true);
            editor.putString(PREF_SAVED_EMAIL, email);
        } else {
            editor.putBoolean(PREF_REMEMBER_ME, false);
            editor.remove(PREF_SAVED_EMAIL);
        }
        editor.apply();
    }

    /**
     * Inicia sesión utilizando correo y contraseña en Firebase Auth.
     */
    private void loginWithEmail(String email, String password) {
        saveRememberMePreference(email);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(LoginActivity.this, "¡Inicio de sesión correcto!", Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    } else {
                        String errorMsg = task.getException() != null ?
                                task.getException().getLocalizedMessage() : "Error de autenticación.";
                        Toast.makeText(LoginActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Abre el selector de cuentas de Google para iniciar sesión.
     */
    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Resultado devuelto al iniciar sesión con Google
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null && account.getIdToken() != null) {
                    firebaseAuthWithGoogle(account.getIdToken());
                } else {
                    Toast.makeText(this, "No se pudo obtener la cuenta de Google.", Toast.LENGTH_SHORT).show();
                }
            } catch (ApiException e) {
                Log.w(TAG, "Error en Google Sign-In: código " + e.getStatusCode(), e);
                Toast.makeText(this, "Error al conectar con Google (código: " + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Autenticar en Firebase usando el token obtenido de Google.
     */
    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null && user.getEmail() != null) {
                            saveRememberMePreference(user.getEmail());
                        }
                        Toast.makeText(LoginActivity.this, "¡Bienvenido!", Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    } else {
                        String errorMsg = task.getException() != null ?
                                task.getException().getLocalizedMessage() : "Error de autenticación con Google.";
                        Toast.makeText(LoginActivity.this, "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
