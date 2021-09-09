package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.gson.JsonSyntaxException;

import net.kdt.pojavlaunch.customcontrols.CustomControls;
import net.kdt.pojavlaunch.customcontrols.LayoutConverter;
import net.kdt.pojavlaunch.utils.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An activity dedicated to importing control files.
 */
public class ImportControlActivity extends Activity {

    private Uri mUriData;
    private boolean mHasIntentChanged = true;
    private volatile boolean mIsFileVerified = false;

    private EditText mEditText;

    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.initContextConstants(getApplicationContext());

        setContentView(R.layout.import_control_layout);
        mEditText = findViewById(R.id.editText_import_control_file_name);
    }

    /**
     * Override the previous loaded intent
     * @param intent the intent used to replace the old one.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        if(intent != null) setIntent(intent);
        mHasIntentChanged = true;
    }

    /**
     * Update all over again if the intent changed.
     */
    @Override
    protected void onPostResume() {
        super.onPostResume();
        if(!mHasIntentChanged) return;
        mIsFileVerified = false;
        getUriData();
        //Set the name of the file in the editText.
        String editTextString = mUriData.toString().replaceAll("%..", "/");
        editTextString = editTextString.substring(editTextString.lastIndexOf('/') + 1);
        mEditText.setText(trimFileName(editTextString));
        mHasIntentChanged = false;

        //Import and verify thread
        //Kill the app if the file isn't valid.
        new Thread(() -> {
            importControlFile("TMP_IMPORT_FILE");

            if(verify())mIsFileVerified = true;
            else runOnUiThread(() -> {
                Toast.makeText(
                        ImportControlActivity.this,
                        "Invalid or corrupted file",
                        Toast.LENGTH_SHORT).show();
                finishAndRemoveTask();
            });
        }).start();
    }

    /**
     * Start the import.
     * @param view the view which called the function
     */
    public void startImport(View view) {
        String fileName = trimFileName(mEditText.getText().toString());
        //Step 1 check for suffixes.
        if(!isFileNameValid(fileName)){
            Toast.makeText(this, "Invalid name or file already exists", Toast.LENGTH_SHORT).show();
            return;
        }
        if(!mIsFileVerified){
            Toast.makeText(this, "The file is being verified, please wait and retry", Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(getApplicationContext(), "Starting importation", Toast.LENGTH_SHORT).show();

        new File(Tools.CTRLMAP_PATH + "/TMP_IMPORT_FILE.json").renameTo(new File(Tools.CTRLMAP_PATH + "/" + fileName + ".json"));
        finishAndRemoveTask();
    }

    /**
     * Copy a the file from the Intent data with a provided name into the controlmap folder.
     * @param fileName The file name to use.
     * @return whether the file was successfully imported
     */
    private boolean importControlFile(String fileName){
        InputStream is;
        try {
            is = getContentResolver().openInputStream(mUriData);

            OutputStream os = new FileOutputStream(Tools.CTRLMAP_PATH + "/" + fileName + ".json");
            byte[] buffer = new byte[1024];
            while(is.read(buffer) != -1)
                os.write(buffer);

            os.close();
            is.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Tell if the clean version of the filename is valid.
     * @param fileName the string to test
     * @return whether the filename is valid
     */
    private static boolean isFileNameValid(String fileName){
        fileName = trimFileName(fileName);

        if(fileName.isEmpty()) return false;
        if (FileUtils.exists(Tools.CTRLMAP_PATH + "/" + fileName + ".json")) return false;

        return true;
    }

    /**
     * Remove or undesirable chars from the string
     * @param fileName The string to trim
     * @return The trimmed string
     */
    private static String trimFileName(String fileName){
        return fileName
                .replace(".json", "")
                .replaceAll("%..", "/")
                .replace("/", "")
                .replace("\\", "")
                .trim();
    }

    /**
     * Tries to get an Uri from the various sources
     */
    private void getUriData(){
        mUriData = getIntent().getData();
        if(mUriData != null) return;
        try {
            mUriData = getIntent().getClipData().getItemAt(0).getUri();
        }catch (Exception ignored){}
    }

    /**
     * Verify if the control file is valid
     * @return Whether the control file is valid
     */
    private static boolean verify(){
        try{
            String jsonLayoutData = Tools.read(Tools.CTRLMAP_PATH + "/TMP_IMPORT_FILE.json");
            JSONObject layoutJobj = new JSONObject(jsonLayoutData);
            return layoutJobj.has("version") && layoutJobj.has("mControlDataList");

        }catch (JSONException | IOException e) {
            e.printStackTrace();
            return false;
        }

    }

}
