package com.example.tiktok;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface; // 新增：用于弹窗
import android.content.pm.PackageManager;
import android.graphics.Color; // 新增：用于颜色
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils; // 新增：用于文字处理
import android.util.Log;
import android.util.TypedValue; // 新增：用于尺寸转换
import android.view.Gravity; // 新增：用于对齐
import android.widget.Button;
import android.widget.EditText; // 新增
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;


import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog; // 新增：原生弹窗
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ImageView ivCover;
    private LinearLayout llImageContainer;
    private ActivityResultLauncher<String> pickImageLauncher;

    // --- 【新增】文字编辑相关的变量 ---
    private EditText etDescription;
    private LinearLayout llHotTopics;
    // 本地写死的数据源
    private final String[] friendList = {"Alice", "Bob", "Charlie", "David", "Emma", "老王", "张三"};
    private final String[] hotTopicList = {"#旅行日记", "#美食探店", "#日常生活", "#萌宠时刻", "#技术分享", "#Android开发"};
    // ---------------------------

    // --- 【新增】定位相关的变量 ---
    private TextView tvLocation;
    private ImageView ivClearLocation;
    private LocationManager locationManager;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    // ---------------------------

    private boolean isSelectingCover = false;
    private boolean hasManualCoverSet = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        initImagePicker();
        // --- 【新增】初始化定位权限启动器 ---
        initLocationPermissionLauncher();

        ivCover = findViewById(R.id.iv_cover);
        llImageContainer = findViewById(R.id.ll_image_container);
        Button btnEditCover = findViewById(R.id.btn_edit_cover);
        Button btnAddImage = findViewById(R.id.btn_add_image);

        // --- 【新增】绑定定位UI控件 ---
        tvLocation = findViewById(R.id.tv_location);
        ivClearLocation = findViewById(R.id.iv_clear_location);

        // 点击清除图标重置位置信息
        ivClearLocation.setOnClickListener(v -> {
            tvLocation.setText("添加位置");
            Toast.makeText(MainActivity.this, "位置已清除", Toast.LENGTH_SHORT).show();
        });

        // 点击文字也可以重新获取位置
        tvLocation.setOnClickListener(v -> checkLocationPermissionAndGet());
        // ---------------------------

        // --- 【新增】绑定并初始化文字编辑功能 ---
        etDescription = findViewById(R.id.et_description); // 对应 XML 中的 id
        llHotTopics = findViewById(R.id.ll_hot_topics);   // 对应 XML 中的 id

        initTextEditingLogic(); // 初始化 @ 和 # 按钮逻辑
        initHotTopics();        // 初始化热门话题列表（已修复显示问题）
        // ------------------------------------

        btnEditCover.setOnClickListener(v -> {
            isSelectingCover = true;
            checkPermissionAndPickImage();
        });

        btnAddImage.setOnClickListener(v -> {
            isSelectingCover = false;
            checkPermissionAndPickImage();
        });

        // --- 【新增】页面加载时自动开始获取位置 ---
        checkLocationPermissionAndGet();
    }

    // ================== 【新增】文字编辑功能逻辑 ==================

    /**
     * 初始化 @好友 和 #话题 按钮的点击事件
     */
    private void initTextEditingLogic() {
        // 注意：这里需要确保 XML 里这两个 TextView 的 ID 分别是 at_friend 和 tv_topics
        TextView btnAtFriend = findViewById(R.id.at_friend);
        TextView btnAddTopic = findViewById(R.id.tv_topics);

        // 1. 处理 @ 好友功能
        if (btnAtFriend != null) {
            btnAtFriend.setOnClickListener(v -> {
                // 使用原生 AlertDialog 展示好友列表
                new AlertDialog.Builder(this)
                        .setTitle("选择要提醒的好友")
                        .setItems(friendList, (dialog, which) -> {
                            String selectedFriend = friendList[which];
                            // 将 @Name 插入到输入框
                            appendTagToDescription("@" + selectedFriend + " ");
                        })
                        .show();
            });
        }

        // 2. 处理 # 话题按钮 (手动添加)
        if (btnAddTopic != null) {
            btnAddTopic.setOnClickListener(v -> {
                // 点击只插入 # 符号，让用户自己输入
                appendTagToDescription("#");
            });
        }
    }

    /**
     * 初始化热门话题区域 (动态添加 View)
     * 【修复】增加了 setSingleLine 和 LayoutParams 调整，解决文字换行挤压问题
     */
    private void initHotTopics() {
        if (llHotTopics == null) return;

        // 清除可能存在的旧视图
        llHotTopics.removeAllViews();

        // 遍历本地写死的话题数据
        for (String topic : hotTopicList) {
            TextView tvTopic = new TextView(this);
            tvTopic.setText(topic);
            tvTopic.setTextColor(Color.WHITE);
            tvTopic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13); // 字体稍微改小一点点，更精致

            // 关键修复：强制单行显示，防止出现截图中的换行情况
            tvTopic.setSingleLine(true);
            tvTopic.setEllipsize(TextUtils.TruncateAt.END);

            // 设置背景：深灰色圆角效果（如果 XML 没有定义 drawable，这里用纯色背景代替）
            tvTopic.setBackgroundColor(Color.parseColor("#333333"));

            // 设置内边距 (Padding)：让文字周围有呼吸感，看起来像按钮
            // 左右 12dp, 上下 6dp
            int paddingH = (int) (12 * getResources().getDisplayMetrics().density);
            int paddingV = (int) (6 * getResources().getDisplayMetrics().density);
            tvTopic.setPadding(paddingH, paddingV, paddingH, paddingV);

            tvTopic.setGravity(Gravity.CENTER);

            // 设置布局参数 (LayoutMargin)
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            // 设置右边距，让话题之间有间隔
            params.setMargins(0, 0, (int)(8 * getResources().getDisplayMetrics().density), 0);
            tvTopic.setLayoutParams(params);

            // 点击热门话题，直接上屏
            tvTopic.setOnClickListener(v -> {
                appendTagToDescription(topic + " ");
            });

            // 添加到横向滚动的容器中
            llHotTopics.addView(tvTopic);
        }
    }

    /**
     * 辅助方法：将文本追加到 EditText 中
     */
    private void appendTagToDescription(String textToAppend) {
        if (etDescription != null) {
            etDescription.append(textToAppend);
            // 将光标移动到文本末尾，方便用户继续输入
            etDescription.setSelection(etDescription.getText().length());
        }
    }
    // ========================================================

    // ================== 图片逻辑 (保持不变) ==================
    private void initImagePicker() {
        // 注册 Activity Result 回调
        pickImageLauncher = registerForActivityResult(
                // 参数1：定义要执行的系统操作（这里是 “打开系统相册选择文件”，GetContent() 是 Android 预置的 “选文件” 契约，限定选文件类操作）。
                new ActivityResultContracts.GetContent(),
                // 参数2：操作完成后触发的逻辑（这里是接收相册选中图片的 Uri，判断是设为封面还是添加到容器，完成图片展示）。
                // GetContent() 契约默认返回选中文件的 Uri，所以回调参数 uri 就是该文件的资源标识符，用来定位 / 展示图片。
                uri -> {
                    if (uri != null) {
                        // 【修改点3】根据成员变量判断，而不是去读 Intent
                        if (isSelectingCover) {
                            ivCover.setImageURI(uri);
                            hasManualCoverSet = true; // 用户手动设置了封面
                            Log.d("MainActivity", "用户手动设置封面成功");
                        } else {
                            Log.d("MainActivity", "进入添加图片分支");
                            addImageToContainer(uri);
                            // 如果还没有设置封面，那么第一张图默认为封面
                            Log.d("MainActivity", "检查封面状态");
                            // 如果用户还没有手动设置过封面，则将第一张添加的图片设为封面
                            if (!hasManualCoverSet) {
                                Log.d("MainActivity", "用户未手动设置过封面，将第一张图片设为封面");
                                ivCover.setImageURI(uri);
                                hasManualCoverSet = true; // 设置了封面
                                Log.d("MainActivity", "封面设置成功");
                            } else {
                                Log.d("MainActivity", "用户已手动设置过封面，保留原有封面");
                            }
                        }
                    }
                }
        );
    }

    private void checkPermissionAndPickImage() {
        // 兼容 Android 13 (API 33) 及以上的图片权限
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, 1001);
        } else {
            pickImageLauncher.launch("image/*");
        }
    }

    private void addImageToContainer(Uri imageUri) {
        // 1. 原来的layout中没有imageview，只是一个horscroview，所以要new
        ImageView imageView = new ImageView(this);
        // 2.
        // 2.1 Android 中布局参数（宽高、间距）的单位是像素（px），但设计稿通常用 DP（设备无关像素），不同手机的 DP 和 PX 换算比例不同（比如 1DP 在 1x 屏幕 = 1PX，2x 屏幕 = 2PX）；
        // 2.2 getDisplayMetrics().density：获取当前设备的 DP→PX 缩放比（比如 3.0 代表 1DP=3PX）；
        // 2.3 80DP 转成像素：保证不同分辨率手机上，新增图片的大小一致；12DP 是图片右侧的间距，避免图片挤在一起。
        // 设置宽高为 80dp (需要转换成像素，这里简化处理，实际建议用 dp 转 px 工具)
        int size = (int) (80 * getResources().getDisplayMetrics().density);
        int margin = (int) (12 * getResources().getDisplayMetrics().density);

        // 3.
        // 3.1 LinearLayout.LayoutParams：因为父容器 llImageContainer 是 LinearLayout（包裹在 HorizontalScrollView 里），所以必须用对应的布局参数类；
        // 3.2 LayoutParams(size, size)：设置 ImageView 的宽高为转换后的 80DP 像素；
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        // 3.3 setMargins(左, 上, 右, 下)：只给右侧设 12DP 间距，让图片之间有间隙，更美观。
        params.setMargins(0, 0, margin, 0);
        imageView.setLayoutParams(params);
        // 3.4 CENTER_CROP：图片按比例缩放，填满 ImageView 的宽高，超出部分裁剪，保证图片不变形（如果用默认的 FIT_CENTER 会导致图片拉伸 / 留空白）
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        // 3.5 和设置封面的核心逻辑一致：通过 Uri 定位选中的图片，显示到 ImageView 上；
        imageView.setImageURI(imageUri);

        // 添加到容器中，位置在 "btn_add_image" 之前
        // getChildCount()-1 刚好就是加号按钮前面的索引
        // 4.
        // 4.1 llImageContainer.getChildCount()：获取 LinearLayout 里的子 View 总数（包含 “添加图片” 的加号按钮）；
        // 4.2 -1：因为加号按钮是 LinearLayout 的最后一个子 View，所以插入到 “最后一个位置的前一位”，保证新图片出现在加号左边；
        // 4.2 mod-> 删去-1 让添加按钮始终在最前面
        int index = llImageContainer.getChildCount();
        // 4.3 异常处理：如果容器为空（getChildCount ()=0），index 会是 -1，此时设为 0，避免添加 View 时索引越界。
        // 4.3 mod ->不需要了 因为永远>=0
//        if (index < 0) index = 0; // 防止容器为空时的异常
        // 4.4 addView(View, index)：把新创建的 ImageView 插入到 LinearLayout 的指定索引位置；
        //因为 llImageContainer 被 HorizontalScrollView 包裹，所以新增的图片会自动支持横向滚动（HorizontalScrollView 的核心作用就是让内部的 LinearLayout 可以横向滑动）。
        llImageContainer.addView(imageView, index);
    }

    // ================== 【新增】定位逻辑代码 ==================

    // 1. 初始化权限回调
    private void initLocationPermissionLauncher() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false);
                    Boolean coarseLocationGranted = result.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false);

                    if (fineLocationGranted != null && fineLocationGranted) {
                        // 精确位置已授权
                        getLocation();
                    } else if (coarseLocationGranted != null && coarseLocationGranted) {
                        // 模糊位置已授权
                        getLocation();
                    } else {
                        tvLocation.setText("未获得定位权限");
                        Toast.makeText(this, "需要定位权限才能显示位置", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    // 2. 检查权限并请求
    private void checkLocationPermissionAndGet() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            getLocation();
        } else {
            // 请求定位权限
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    // 3. 获取经纬度核心逻辑
    private void getLocation() {
        try {
            tvLocation.setText("正在定位中...");
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            // 检查GPS是否开启
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                    !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                tvLocation.setText("请开启手机GPS");
                return;
            }

            // 获取位置监听器
            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    // 获取到经纬度
                    double latitude = location.getLatitude();
                    double longitude = location.getLongitude();

                    // 格式化显示 (保留2位小数)
                    String locationStr = String.format("纬度: %.2f, 经度: %.2f", latitude, longitude);
                    tvLocation.setText(locationStr);
                    Log.d("Location", "Location acquired: " + locationStr);

                    // 获取到一次后，移除监听，省电
                    locationManager.removeUpdates(this);
                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {}
                @Override
                public void onProviderEnabled(@NonNull String provider) {}
                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}
            };

            // 请求位置更新 (使用 Network 或 GPS)
            // 权限检查已经在外部做过了，这里用 SecurityException 捕获即可
            List<String> providers = locationManager.getProviders(true);
            String bestProvider = LocationManager.NETWORK_PROVIDER;
            if (providers.contains(LocationManager.GPS_PROVIDER)) {
                bestProvider = LocationManager.GPS_PROVIDER;
            }

            // 请求更新：provider, 最小时间间隔0ms, 最小距离0米, 监听器
            locationManager.requestLocationUpdates(bestProvider, 0, 0, locationListener);

            // 尝试立即获取最后一次已知位置作为兜底
            Location lastKnownLocation = locationManager.getLastKnownLocation(bestProvider);
            if (lastKnownLocation != null) {
                String locationStr = String.format("纬度: %.2f, 经度: %.2f", lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                tvLocation.setText(locationStr);
            }

        } catch (SecurityException e) {
            e.printStackTrace();
            tvLocation.setText("定位权限异常");
        } catch (Exception e) {
            e.printStackTrace();
            tvLocation.setText("定位服务不可用");
        }
    }

    // 这一段是原有的相册权限回调，保持不变
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickImageLauncher.launch("image/*");
            } else {
                Toast.makeText(this, "请开启相册权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}