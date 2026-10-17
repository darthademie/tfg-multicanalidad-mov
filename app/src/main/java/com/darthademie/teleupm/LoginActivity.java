package com.darthademie.teleupm;

import static java.lang.System.exit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
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
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
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

            boolean isValid = true;

            // Validación de correo electrónico (formato usuario@dominio.extension)
            if (email.isEmpty()) {
                binding.tilEmail.setError("Por favor, introduce tu correo electrónico.");
                isValid = false;
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilEmail.setError("Formato de correo no válido (ejemplo@dominio.com).");
                isValid = false;
            } else {
                binding.tilEmail.setError(null);
            }

            // Validación de contraseña
            if (password.isEmpty()) {
                binding.tilPassword.setError("Por favor, introduce tu contraseña.");
                isValid = false;
            } else {
                binding.tilPassword.setError(null);
            }

            if (!isValid) {
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
                        String errorMsg = getFriendlyFirebaseErrorMessage(task.getException());
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
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
                Log.e(TAG, "Error en Google Sign-In: código " + e.getStatusCode(), e);
                if (e.getStatusCode() == 10) {
                    Toast.makeText(this, "Error 10 (DEVELOPER_ERROR): Fallo con la huella digital de la app. Contacte al administrador", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Error al conectar con Google (código: " + e.getStatusCode() + ")", Toast.LENGTH_SHORT).show();
                }
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
                        Toast.makeText(LoginActivity.this, "¡Bienvenido a TeleUPM!", Toast.LENGTH_SHORT).show();
                        goToMainActivity();
                    } else {
                        String errorMsg = getFriendlyFirebaseErrorMessage(task.getException());
                        Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Traduce las excepciones de Firebase Auth a mensajes amigables en español para el usuario.
     */
    private String getFriendlyFirebaseErrorMessage(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            return "El usuario o la contraseña son incorrectos.";
        } else if (e instanceof FirebaseAuthInvalidUserException) {
            String errorCode = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(errorCode)) {
                return "Esta cuenta de usuario ha sido deshabilitada.";
            }
            return "No existe ninguna cuenta registrada con este correo electrónico.";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "Ya existe una cuenta registrada con este correo electrónico.";
        } else if (e instanceof FirebaseAuthException) {
            FirebaseAuthException authException = (FirebaseAuthException) e;
            switch (authException.getErrorCode()) {
                case "ERROR_INVALID_EMAIL":
                    return "El formato del correo electrónico no es válido.";
                case "ERROR_WRONG_PASSWORD":
                case "ERROR_USER_NOT_FOUND":
                    return "El usuario o la contraseña son incorrectos.";
                case "ERROR_USER_DISABLED":
                    return "Esta cuenta de usuario ha sido deshabilitada.";
                case "ERROR_TOO_MANY_REQUESTS":
                    return "Demasiados intentos fallidos. Por favor, inténtalo más tarde.";
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "El inicio de sesión no está habilitado en Firebase Console.";
                default:
                    return authException.getLocalizedMessage();
            }
        }
        return e != null ? e.getLocalizedMessage() : "Error de autenticación desconocido.";
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
