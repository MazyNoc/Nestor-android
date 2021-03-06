package nu.annat.autohome;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import com.google.android.material.tabs.TabLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import com.google.firebase.iid.FirebaseInstanceId;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nu.annat.autohome.api.All;
import nu.annat.autohome.api.Unit;
import nu.annat.autohome.databinding.ActivityMainBinding;
import nu.annat.autohome.rest.Api;
import nu.annat.autohome.rest.Server;
import nu.annat.autohome.ui.LayoutPageViewer;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    private MainActivityPresenter presenter;
    private Api service;
    private ActivityMainBinding binding;
    private Integer currentPage = null;

    private Map<String, Bitmap> bitmaps = new HashMap<>();
    private boolean coldStart;
    private int retries = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Preferences.init(this);

        FirebaseInstanceId.getInstance().getToken();

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        if (savedInstanceState == null) coldStart = true;
        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        //presenter = new MainActivityPresenter(this);

        service = Server.getInstance().getService();
        if (savedInstanceState != null) {
            currentPage = savedInstanceState.getInt("page");
        }
        loadImages();

        binding.viewpager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                String id = ((LayoutPageViewer) binding.viewpager.getAdapter()).getPageId(position);
                Preferences.setLastPageId(id);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        setupNotificationChannels();
        getWindow().getDecorView().setBackgroundColor(ResourcesCompat.getColor(getResources(), R.color.windowBackground, null));
    }

    @Override
    protected void onStart() {
        super.onStart();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("page", binding.viewpager.getCurrentItem());
    }

    private void refresh() {
        service.getAll().enqueue(new Callback<All>() {
            @Override
            public void onResponse(Call<All> call, Response<All> response) {
                updateData(response.body());
                retries = 0;
            }

            @Override
            public void onFailure(Call<All> call, Throwable t) {
                Log.e(TAG, "Failure", t);
                if (retries++ < 5) {
                    refresh();
                } else {
                    Toast.makeText(MainActivity.this, "To many errors", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadImages() {
        synchronized (this) {
            File cacheDir = new File(getCacheDir(), "img");
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                    bitmaps.put(file.getName(), bitmap);
                }
            }
        }
    }

    private File saveImage(String filename, InputStream stream) {
        synchronized (this) {
            try {
                File cacheDir = new File(getCacheDir(), "img");
                cacheDir.mkdir();
                File f = new File(cacheDir, filename);
                f.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(f);

                final byte[] b = new byte[8192];
                for (int r; (r = stream.read(b)) != -1; ) {
                    fileOutputStream.write(b, 0, r);
                }
                fileOutputStream.close();
                return f;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private void updateData(All body) {

        Storage.getInstance().addSensors(body.getUnits());
        body.getUnits().forEach(unit -> unit.setImage(bitmaps.get(unit.imageId)));
        downloadImages(body.getUnits());

        ViewPager viewpager = binding.viewpager;
        TabLayout tabLayout = binding.tabLayout;

        tabLayout.setupWithViewPager(viewpager, true);

        LayoutPageViewer layoutPageViewer = new LayoutPageViewer(viewpager, LayoutInflater.from(viewpager.getContext()), body, coldStart);
        viewpager.setAdapter(layoutPageViewer);

        int currentItem = ((LayoutPageViewer) binding.viewpager.getAdapter()).getPosition(Preferences.getLastPageId());// = currentPage != null ? currentPage : viewpager.getCurrentItem();
        viewpager.setCurrentItem(currentItem);
        currentPage = null;

        coldStart = false;

//
//		Layout layout = body.layouts.get(0);
//		LayoutWorker layoutWorker = new LayoutWorker();
//		new LayoutPresenter(findViewById(R.id.root), layout, layoutWorker);

        //presenter.setData(sensors);
        //presenter.setData2(body.scenes);
    }

    private void downloadImages(final List<Unit> units) {
            for (Unit unit : units) {
                if (unit.imageId != null) {
                    if(TextUtils.isEmpty(unit.imageId))
                        continue;
                    AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                    try {
                        OkHttpClient client = Server.getInstance().client;
                        okhttp3.Response execute = client.newCall(new Request.Builder()
                            .get()
                            .url("http://192.168.1.100:5443/img/" + unit.imageId)
                            .build()).execute();
                        InputStream stream = execute.body().byteStream();
                        File file = saveImage(unit.imageId, stream);
                        unit.setImage(BitmapFactory.decodeFile(file.getAbsolutePath()));
                    } catch (Throwable t) {
                        Log.e(TAG, "run: error", t);
                    }
                });
            }
        }
    }

    private void setupNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            String name = "Info";
            String desc = "Information from Hauto";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel notificationChannel = new NotificationChannel("info", name, importance);
            notificationChannel.setDescription(desc);
            notificationChannel.setShowBadge(false);
            notificationChannel.enableLights(true);
            notificationChannel.enableVibration(true);
            notificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
