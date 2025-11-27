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
                new ActivityResultContracts.GetContent(),
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
        ImageView imageView = new ImageView(this);
        // 设置宽高为 80dp (需要转换成像素，这里简化处理，实际建议用 dp 转 px 工具)
        int size = (int) (80 * getResources().getDisplayMetrics().density);
        int margin = (int) (12 * getResources().getDisplayMetrics().density);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
        params.setMargins(0, 0, margin, 0);
        imageView.setLayoutParams(params);
        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imageView.setImageURI(imageUri);

        // 添加到容器中，位置在 "btn_add_image" 之前
        // getChildCount()-1 刚好就是加号按钮前面的索引
        int index = llImageContainer.getChildCount() - 1;
        if (index < 0) index = 0; // 防止容器为空时的异常
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