# Sign-in: production checklist

The app code handles email and phone sign-in, but Firebase refuses both in a release build unless the
project is set up for that build. Do each step once per Firebase project.

## 1. Turn the providers on

Firebase console → Authentication → Sign-in method:

- **Email/Password**: Enabled. Without it, email sign-in fails with `ERROR_OPERATION_NOT_ALLOWED`
  (the app now shows "This sign-in option is not available right now (ERROR_OPERATION_NOT_ALLOWED)").
- **Phone**: Enabled.

## 2. Phone auth needs the Blaze plan and India allowed

- Firebase console → Usage and billing: the project must be on **Blaze**. On Spark, SMS fails with
  `BILLING_NOT_ENABLED`.
- Authentication → Settings → **SMS region policy**: allow India (`IN`), or leave the policy off.

## 3. Register every signing key (the usual reason it works in debug but not in production)

`app/google-services.json` currently lists one SHA-1, `99:5B:BC:…:1B:B0`, which is the debug keystore.
A Play Store install is signed with a different key, so Firebase rejects phone sign-in with
`ERROR_APP_NOT_AUTHORIZED` / `ERROR_INVALID_APP_CREDENTIAL`, and Google sign-in fails too.

Add **both SHA-1 and SHA-256** of each key under Project settings → Your apps → Android
(`com.ojashealing.family`) → Add fingerprint:

| Key | Where to get it |
| --- | --- |
| Play App Signing key (what users install) | Play Console → Test and release → App integrity → App signing |
| Upload key (`my-upload-key.jks`, used for APKs installed outside Play) | `keytool -list -v -keystore my-upload-key.jks -alias upload` |
| Debug key (already added) | `keytool -list -v -keystore debug.keystore -alias androiddebugkey -storepass android` |

**APKs built by CI** are signed with `debug.keystore` restored from the `DEBUG_KEYSTORE_B64` repo secret.
If that secret is missing, CI signs with a throwaway key, so Google and phone sign-in fail in that APK. The
"Check signing fingerprint against Firebase" step in each run's summary prints the APK's SHA-1/SHA-256 and
says whether it matches `google-services.json`. Fix it by adding the secret
(`base64 -w0 debug.keystore`, the keystore whose SHA-1 is registered). Debug builds also show the APK's
SHA-1 under the error when sign-in fails for this reason.

Then download the new `google-services.json` and replace `app/google-services.json`, so Google
sign-in gets an OAuth client for the release key.

## 4. Play Integrity

Google Cloud console for project `ojas-family-app` → APIs & services → enable **Play Integrity API**.
Firebase uses it to confirm the app before sending an SMS. Without it, Firebase falls back to a
reCAPTCHA page in the browser, which works but adds a step.

## 5. Check it on a real device

Install from a Play internal-testing track (signed with the Play key, like real users):

1. Phone: type `98765 43210` (no `+91`) → an OTP arrives → it is read automatically or can be typed.
2. Email: create an account, sign out, sign in again; try a wrong password and expect
   "Email or password is incorrect".

If something fails, the message on screen ends with a Firebase code such as `(ERROR_APP_NOT_AUTHORIZED)`,
and the same failure is logged in Crashlytics (non-fatal, log line `auth step: …`). Match the code to
the steps above.

For testing without SMS cost, add test numbers under Authentication → Sign-in method → Phone →
Phone numbers for testing.
