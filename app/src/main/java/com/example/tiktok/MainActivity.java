package com.example.tiktok;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color; // 新增：用于颜色
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable; // 新增：用于文字变化监听
import android.text.InputFilter; // 新增：用于字数限制
import android.text.TextUtils; // 新增：用于文字处理
import android.text.TextWatcher; // 新增：用于文字监听
import android.util.Log;
import android.util.TypedValue; // 新增：用于尺寸转换
import android.view.Gravity; // 新增：用于对齐
import android.view.View; // 新增：用于View操作
import android.view.ViewGroup; // 新增
import android.widget.Button;
import android.widget.EditText; // 新增
import android.widget.FrameLayout; // 新增：用于图片容器
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
    private Button btnAddImage; // 提升为成员变量，方便在排序时定位
    private ActivityResultLauncher<String> pickImageLauncher;

    // --- 【新增】文字编辑相关的变量 ---
    private EditText etDescription;
    private TextView tvWordCount; // 新增：字数显示控件
    private LinearLayout llHotTopics;
    private final int MAX_DESC_LENGTH = 200; // 字数限制常量

    // 本地写死的数据源
    private final String[] friendList = {"Alice", "Bob", "Charlie", "David", "Emma", "张三"};
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

        // 绑定控件（确保 XML 中有对应 ID）
        ivCover = findViewById(R.id.iv_cover);
        llImageContainer = findViewById(R.id.ll_image_container);
        View  btnEditCover = findViewById(R.id.btn_edit_cover);
        btnAddImage = findViewById(R.id.btn_add_image); // 初始化加号按钮

        // --- 【新增】绑定并初始化文字编辑功能 ---
        etDescription = findViewById(R.id.et_description); // 对应 XML 中的 id
        tvWordCount = findViewById(R.id.tv_word_count);     // 新增的字数统计控件
        llHotTopics = findViewById(R.id.ll_hot_topics);   // 对应 XML 中的 id

        initImagePicker();
        // --- 【新增】初始化定位权限启动器 ---
        initLocationPermissionLauncher();

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

        initTextEditingLogic(); // 初始化 @ 和 # 按钮逻辑
        initHotTopics();        // 初始化热门话题列表（已修复显示问题）
        initWordCountLogic();   // 初始化字数统计逻辑
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

    // ================== 【新增】文字编辑与统计逻辑 ==================

    /**
     * 初始化字数统计和限制
     */
    private void initWordCountLogic() {
        if (etDescription == null || tvWordCount == null) return;

        // 1. 设置最大输入长度限制
        etDescription.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_DESC_LENGTH)});

        // 2. 监听输入内容变化
        etDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 更新字数显示
                int length = s.length();
                tvWordCount.setText(length + "/" + MAX_DESC_LENGTH);

                // 如果字数达到上限，改变颜色提示
                if (length >= MAX_DESC_LENGTH) {
                    tvWordCount.setTextColor(Color.RED);
                } else {
                    tvWordCount.setTextColor(Color.parseColor("#888888"));
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

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

    // --- 【修改】使用 FrameLayout 包装图片和操作按钮 ---
    private void addImageToContainer(Uri imageUri) {
        // 1. 创建 FrameLayout 容器
        FrameLayout frameLayout = new FrameLayout(this);

        int size = (int) (80 * getResources().getDisplayMetrics().density);
        int margin = (int) (12 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(size, size);
        frameParams.setMargins(0, 0, margin, 0);
        frameLayout.setLayoutParams(frameParams);

        // 2. 创建图片内容 (底图)
        ImageView imageView = new ImageView(this);
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        imageView.setLayoutParams(imgParams);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageURI(imageUri);

        // 3. 创建删除按钮 (右上角)
        ImageView btnDelete = new ImageView(this);
        // 大小 20dp
        int iconSize = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams deleteParams = new FrameLayout.LayoutParams(iconSize, iconSize);
        deleteParams.gravity = Gravity.TOP | Gravity.RIGHT; // 右上角
        deleteParams.setMargins(0, 4, 4, 0); // 稍微留点边距
        btnDelete.setLayoutParams(deleteParams);
        // 使用系统自带的关闭图标，或者你可以换成红色背景的X
        btnDelete.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        btnDelete.setBackgroundColor(Color.parseColor("#80000000")); // 半透明黑底，看清楚点

        // 删除事件
        btnDelete.setOnClickListener(v -> {
            llImageContainer.removeView(frameLayout); // 移除整个容器
            Toast.makeText(this, "图片已删除", Toast.LENGTH_SHORT).show();
        });

        // 4. 创建移动控制栏 (底部，包含左移和右移)
        LinearLayout moveControlLayout = new LinearLayout(this);
        moveControlLayout.setOrientation(LinearLayout.HORIZONTAL);
        moveControlLayout.setBackgroundColor(Color.parseColor("#80000000")); // 半透明背景
        FrameLayout.LayoutParams moveParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (int)(24 * getResources().getDisplayMetrics().density)); // 高度24dp
        moveParams.gravity = Gravity.BOTTOM;
        moveControlLayout.setLayoutParams(moveParams);

        // 左移按钮
        ImageView btnLeft = new ImageView(this);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        btnLeft.setLayoutParams(btnParams);
        btnLeft.setImageResource(android.R.drawable.ic_media_previous); // 系统向左箭头
        btnLeft.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnLeft.setOnClickListener(v -> moveViewWrapper(frameLayout, -1)); // 向前移

        // 右移按钮
        ImageView btnRight = new ImageView(this);
        btnRight.setLayoutParams(btnParams);
        btnRight.setImageResource(android.R.drawable.ic_media_next); // 系统向右箭头
        btnRight.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnRight.setOnClickListener(v -> moveViewWrapper(frameLayout, 1)); // 向后移

        moveControlLayout.addView(btnLeft);
        moveControlLayout.addView(btnRight);

        // 5. 将所有控件添加到 FrameLayout 容器
        frameLayout.addView(imageView);       // 底层
        frameLayout.addView(btnDelete);       // 上层右上
        frameLayout.addView(moveControlLayout); // 上层底部

        // 6. 添加到主容器
        // 找到加号按钮的索引
        int addBtnIndex = llImageContainer.indexOfChild(btnAddImage);
        if (addBtnIndex == -1) addBtnIndex = llImageContainer.getChildCount();

        llImageContainer.addView(frameLayout, addBtnIndex);
    }

    // --- 【修改】移动 View 容器的逻辑 ---
    private void moveViewWrapper(View view, int direction) {
        int currentIndex = llImageContainer.indexOfChild(view);
        int targetIndex = currentIndex + direction;
        int addBtnIndex = llImageContainer.indexOfChild(btnAddImage);

        // 边界检查：
        // 1. targetIndex < 0: 已经是第一张
        // 2. targetIndex >= addBtnIndex: 不能移到+号后面
        if (targetIndex < 0) {
            Toast.makeText(this, "已经是第一张了", Toast.LENGTH_SHORT).show();
            return;
        }
        if (targetIndex >= addBtnIndex) {
            Toast.makeText(this, "已经是最后一张了", Toast.LENGTH_SHORT).show();
            return;
        }

        // 移动：先移除，再在目标位置添加
        llImageContainer.removeView(view);
        llImageContainer.addView(view, targetIndex);
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