package id.cyberarmy.authenticator;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.client.android.Intents;
import com.google.zxing.integration.android.IntentIntegrator;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements  ActionMode.Callback {
    private ArrayList<Entry> entries;
    private EntriesAdapter adapter;
    private FloatingActionButton fab;

    private int itemIndex;

    private Handler handler;
    private Runnable handlerTask;

    private static final int PERMISSIONS_REQUEST_CAMERA = 42;

    private void doScanQRCode(){
        new IntentIntegrator(MainActivity.this)
                .setCaptureActivity(CaptureActivityAnyOrientation.class)
                .setOrientationLocked(false)
                .initiateScan();
    }

    private void scanQRCode(){
        // check Android 6 permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            doScanQRCode();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, PERMISSIONS_REQUEST_CAMERA);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
       if(requestCode == PERMISSIONS_REQUEST_CAMERA) {
           if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
               // permission was granted
               doScanQRCode();
           } else {
               Snackbar.make(fab, R.string.msg_camera_permission, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                   @Override
                   public void onDismissed(Snackbar snackbar, int event) {
                       super.onDismissed(snackbar, event);

                       if (entries.isEmpty()) {
                           showNoAccount();
                       }
                   }
               }).show();
           }
       }
       else {
           super.onRequestPermissionsResult(requestCode, permissions, grantResults);
       }
    }

    private Entry nextSelection = null;
    private void showNoAccount(){
        Snackbar noAccountSnackbar = Snackbar.make(fab, R.string.no_accounts, Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.button_add, view -> scanQRCode());
        noAccountSnackbar.show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        //getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        //Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        //setSupportActionBar(toolbar);

        fab = findViewById(R.id.action_scan);
        fab.setOnClickListener(view -> scanQRCode());

        final ListView listView = findViewById(R.id.listView);
        final ProgressBar progressBar = findViewById(R.id.progressBar);
        final TextView currentAccount = findViewById(R.id.currentAccount);
        final TextView currentOTP = findViewById(R.id.currentOTP);

        entries = SettingsHelper.load(this);

        adapter = new EntriesAdapter();
        adapter.setEntries(entries);

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            nextSelection = entries.get(i);
            itemIndex = i;
        });

        listView.setOnItemLongClickListener((adapterView, view, i, l) -> {
            nextSelection = entries.get(i);
            startActionMode(MainActivity.this);
            return true;
        });

        currentOTP.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("copied", currentOTP.getText());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });

        if(entries.isEmpty()){
            showNoAccount();
        }

        handler = new Handler();
        handlerTask = new Runnable()
        {
            @Override
            public void run() {
                int progress =  (int) (System.currentTimeMillis() / 1000) % 30 ;
                progressBar.setProgress(progress*100);

                ObjectAnimator animation = ObjectAnimator.ofInt(progressBar, "progress", (progress+1)*100);
                animation.setDuration(1000);
                animation.setInterpolator(new LinearInterpolator());
                animation.start();

                for(int i =0;i < adapter.getCount(); i++){
                    adapter.getItem(i).setCurrentOTP(TOTPHelper.generate(adapter.getItem(i).getSecret()));
                    //Toast.makeText(MainActivity.this, TOTPHelper.generate(adapter.getItem(i).getSecret())+"", Toast.LENGTH_SHORT).show();
                }

                //Toast.makeText(MainActivity.this, TOTPHelper.generate(adapter.getItem(itemIndex).getSecret()), Toast.LENGTH_SHORT).show();
                if(adapter.getCount() != 0){
                    String str = TOTPHelper.generate(adapter.getItem(itemIndex).getSecret());
                    String[] equalStr = new String [2];
                    int temp = 0;

                    for(int i = 0; i < 6; i = i+3) {
                        //Dividing string in n equal part using substring()
                        String part = str.substring(i, i+3);
                        equalStr[temp] = part;
                        temp++;
                    }
                    currentAccount.setText(adapter.getItem(itemIndex).getLabel());
                    currentOTP.setText(equalStr[0]+" "+equalStr[1]);
                    //currentOTP.setText(TOTPHelper.generate(adapter.getItem(itemIndex).getSecret()));
                }
                else {
                    currentOTP.setText("000 000");
                }

                adapter.notifyDataSetChanged();
                handler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();

        handler.post(handlerTask);
    }

    @Override
    public void onPause() {
        super.onPause();

        handler.removeCallbacks(handlerTask);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == IntentIntegrator.REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            try {
                Entry e = new Entry(intent.getStringExtra(Intents.Scan.RESULT));
                e.setCurrentOTP(TOTPHelper.generate(e.getSecret()));

                //add entries

                entries.add(e);
                SettingsHelper.store(this, entries);

                adapter.notifyDataSetChanged();

                Snackbar.make(fab, R.string.msg_account_added, Snackbar.LENGTH_LONG).show();
            } catch (Exception e) {
                Snackbar.make(fab, R.string.msg_invalid_qr_code, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar snackbar, int event) {
                        super.onDismissed(snackbar, event);

                        if(entries.isEmpty()){
                            showNoAccount();
                        }
                    }
                }).show();

                return;
            }
        }

        if(entries.isEmpty()){
            showNoAccount();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if(id == R.id.action_about){
            WebView view = (WebView) LayoutInflater.from(this).inflate(R.layout.dialog_about, null);
            view.loadUrl("file:///android_res/raw/about.html");
            new AlertDialog.Builder(this).setView(view).show();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.menu_edit, menu);

        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        adapter.setCurrentSelection(nextSelection);
        adapter.notifyDataSetChanged();
        actionMode.setTitle(adapter.getCurrentSelection().getLabel());

        return true;
    }

    @Override
    public boolean onActionItemClicked(final ActionMode actionMode, MenuItem menuItem) {
        int id = menuItem.getItemId();

        if (id == R.id.action_delete) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(getString(R.string.alert_remove) + adapter.getCurrentSelection().getLabel() + "?");
            alert.setMessage(R.string.msg_confirm_delete);

            alert.setPositiveButton(R.string.button_remove, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    entries.remove(adapter.getCurrentSelection());

                    Snackbar.make(fab, R.string.msg_account_removed, Snackbar.LENGTH_LONG).setCallback(new Snackbar.Callback() {
                        @Override
                        public void onDismissed(Snackbar snackbar, int event) {
                            super.onDismissed(snackbar, event);

                            if (entries.isEmpty()) {
                                showNoAccount();
                            }
                        }
                    }).show();

                    actionMode.finish();
                }
            });

            alert.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    actionMode.finish();
                }
            });

            alert.show();

            return true;
        }
        else if (id == R.id.action_edit) {
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.alert_rename);

            final EditText input = new EditText(this);
            input.setText(adapter.getCurrentSelection().getLabel());
            alert.setView(input);

            alert.setPositiveButton(R.string.button_save, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    adapter.getCurrentSelection().setLabel(input.getEditableText().toString());
                    actionMode.finish();
                }
            });

            alert.setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    actionMode.finish();
                }
            });

            alert.show();

            return true;
        }

        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        adapter.setCurrentSelection(null);
        adapter.notifyDataSetChanged();

        SettingsHelper.store(this, entries);
    }
}