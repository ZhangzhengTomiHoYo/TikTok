package com.example.tiktok;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ImageView ivCover;
    private LinearLayout llImageContainer;
    private ActivityResultLauncher<String> pickImageLauncher;

    // 【修改点1】定义一个布尔变量来记录当前操作是“设置封面”还是“添加图片”
    private boolean isSelectingCover = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 【修改点2】必须先初始化图片选择器，否则 pickImageLauncher 为空
        initImagePicker();

        ivCover = findViewById(R.id.iv_cover);
        llImageContainer = findViewById(R.id.ll_image_container);
        Button btnEditCover = findViewById(R.id.btn_edit_cover);
        Button btnAddImage = findViewById(R.id.btn_add_image);

        // 编辑封面
        btnEditCover.setOnClickListener(v -> {
            isSelectingCover = true; // 标记为封面
            checkPermissionAndPickImage();
        });

        // 添加图片
        btnAddImage.setOnClickListener(v -> {
            isSelectingCover = false; // 标记为普通图片
            checkPermissionAndPickImage();
        });
    }

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
                        } else {
                            addImageToContainer(uri);
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
        int index = llImageContainer.getChildCount() - 1;
        // 4.3 异常处理：如果容器为空（getChildCount ()=0），index 会是 -1，此时设为 0，避免添加 View 时索引越界。
        if (index < 0) index = 0; // 防止容器为空时的异常
        // 4.4 addView(View, index)：把新创建的 ImageView 插入到 LinearLayout 的指定索引位置；
        //因为 llImageContainer 被 HorizontalScrollView 包裹，所以新增的图片会自动支持横向滚动（HorizontalScrollView 的核心作用就是让内部的 LinearLayout 可以横向滑动）。
        llImageContainer.addView(imageView, index);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限申请成功，直接打开相册
                pickImageLauncher.launch("image/*");
            } else {
                Toast.makeText(this, "请开启相册权限", Toast.LENGTH_SHORT).show();
            }
        }
    }
}