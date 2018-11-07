package de.vanappsteer.windowalarmconfig.activities;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Button;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import de.vanappsteer.windowalarmconfig.R;
import de.vanappsteer.windowalarmconfig.adapter.PagerAdapter;
import de.vanappsteer.windowalarmconfig.interfaces.ConfigView;
import de.vanappsteer.windowalarmconfig.models.ConfigModel;
import de.vanappsteer.windowalarmconfig.services.BluetoothDeviceConnectionService;
import de.vanappsteer.windowalarmconfig.services.BluetoothDeviceConnectionService.DeviceConnectionListener;
import de.vanappsteer.windowalarmconfig.util.LoggingUtil;

public class DeviceConfigActivity extends AppCompatActivity {

    public enum Result {
        CANCELLED,
        FAILED,
        SUCCESS
    }

    public static String ACTIVITY_RESULT_KEY_RESULT = "ACTIVITY_RESULT_KEY_RESULT";

    public static final String KEY_CHARACTERISTIC_HASH_MAP = "KEY_CHARACTERISTIC_HASH_MAP";

    public static final UUID BLE_CHARACTERISTIC_DEVICE_RESTART_UUID = UUID.fromString("890f7b6f-cecc-4e3e-ade2-5f2907867f4b");

    private BluetoothDeviceConnectionService mDeviceService;
    private boolean mDeviceServiceBound = false;

    private HashMap<UUID, String> mConfigDescriptionHashMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_config);

        setResult(RESULT_CANCELED);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        Intent intent = getIntent();

        HashMap<UUID, String> characteristicHashMap = (HashMap<UUID, String>) intent.getSerializableExtra(KEY_CHARACTERISTIC_HASH_MAP);
        if (characteristicHashMap == null) {
            characteristicHashMap = new HashMap<>();
        }

        for (Map.Entry<UUID, String> entry : characteristicHashMap.entrySet()) {
            mConfigDescriptionHashMap.put(entry.getKey(), entry.getValue());
        }

        initViews();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Intent intent = new Intent(this, BluetoothDeviceConnectionService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
        mDeviceServiceBound = false;
    }

    private void initViews() {
        TabLayout tabLayout = findViewById(R.id.tabLayout);
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        final ViewPager viewPager = findViewById(R.id.pager);
        final PagerAdapter adapter = new PagerAdapter(getSupportFragmentManager(), tabLayout.getTabCount(), mConfigDescriptionHashMap);
        viewPager.setAdapter(adapter);
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                viewPager.setCurrentItem(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // not implemented
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // not implemented
            }
        });

        Button buttonSave = findViewById(R.id.buttonSave);
        buttonSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mDeviceServiceBound) {
                    Map<UUID, String> map = new HashMap<>();

                    for (int i = 0; i < adapter.getCount(); i++) {
                        ConfigView configView = (ConfigView) adapter.getItem(i);
                        ConfigModel configModel = configView.getModel();
                        map.putAll(configModel.getDataMap());

                        for (Map.Entry<UUID, String> entry : configModel.getDataMap().entrySet()) {
                            LoggingUtil.debug(entry.getKey().toString());
                            LoggingUtil.debug(entry.getValue());
                        }
                    }
                    map.put(BLE_CHARACTERISTIC_DEVICE_RESTART_UUID, "empty value");

                    mDeviceService.writeCharacteristics(map);

                }
                else {
                    // TODO: keep config activity instead and retry?
                    finishWithIntent(Result.FAILED);
                }
            }
        });
        Button buttonCancel = findViewById(R.id.buttonCancel);
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finishWithIntent(Result.CANCELLED);
            }
        });
    }

    private void finishWithIntent(Result result) {

        Intent resultIntent = new Intent();
        resultIntent.putExtra(ACTIVITY_RESULT_KEY_RESULT, result);

        if (result == Result.SUCCESS) {
            setResult(RESULT_OK, resultIntent);
        }
        else {
            setResult(RESULT_CANCELED, resultIntent);
        }

        DeviceConfigActivity.this.finish();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BluetoothDeviceConnectionService.LocalBinder binder = (BluetoothDeviceConnectionService.LocalBinder) service;
            mDeviceService = binder.getService();
            mDeviceService.addDeviceConnectionListener(mDeviceConnectionListener);
            mDeviceServiceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mDeviceServiceBound = false;
        }
    };

    DeviceConnectionListener mDeviceConnectionListener = new DeviceConnectionListener() {

        @Override
        public void onAllCharacteristicsWrote() {
            finishWithIntent(Result.SUCCESS);
        }

        @Override
        public void onDeviceConnectionError(int errorCode) {
            finishWithIntent(Result.FAILED);
        }
    };
}
