package com.lntu.online.activity;

import org.apache.http.Header;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.GridView;
import android.widget.Toast;

import com.lntu.online.R;
import com.lntu.online.adapter.MainAdapter;
import com.lntu.online.adapter.MainItemClickListener;
import com.lntu.online.http.HttpUtil;
import com.lntu.online.http.NormalAuthListener;
import com.lntu.online.info.AppInfo;
import com.lntu.online.info.NetworkInfo;
import com.lntu.online.model.ClientVersion;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;

public class MainActivity extends ActionBarActivity {

    private Toolbar toolbar;
    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    private GridView gridView;
    private long firstBackKeyTime = 0; //首次返回键按下时间戳

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        drawerLayout = (DrawerLayout) findViewById(R.id.main_drawer_layout);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.menu, R.string.app_name);
        drawerToggle.syncState();
        drawerLayout.setDrawerListener(drawerToggle);
        drawerLayout.setDrawerShadow(R.drawable.navigation_drawer_shadow, GravityCompat.START);

        //GridView
        gridView = (GridView) findViewById(R.id.main_grid_view);
        gridView.setAdapter(new MainAdapter(this));
        gridView.setOnItemClickListener(new MainItemClickListener());
        //checkUpdate
        checkUpdateBackground();
    }

    public void onDrawerItemSelected(View view) {
        switch (view.getId()) {
        case R.id.action_browser: {
            Uri uri = Uri.parse("http://60.18.131.131:11180/academic/index.html");
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
            break;
        }
        case R.id.action_logout:
            showLogoutDialog();
            break;
        case R.id.action_market: {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("market://details?id=" + getPackageName()));
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
            } else {
                intent.setData(Uri.parse("http://zhushou.360.cn/detail/index/soft_id/1964733?recrefer=SE_D_%E8%BE%BD%E5%B7%A5%E5%A4%A7%E6%95%99%E5%8A%A1%E5%9C%A8%E7%BA%BF"));
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "您的手机没有安装应用商店程序", Toast.LENGTH_SHORT).show();
                }
            }
            break;
        }
        case R.id.action_feedback:
            startActivity(new Intent(this, AdviceActivity.class));
            break;
        case R.id.action_share:
            // TODO
            break;
        case R.id.action_update:
            checkUpdate();
            break;
        case R.id.action_settings:
            // TODO
            break;
        case R.id.action_about:
            startActivity(new Intent(this, AboutActivity.class));
            break;
        case R.id.action_help:
            // TODO
            break;
        case R.id.action_exit:
            showExitDialog();
            break;
        }
    }

    @Override
    public void onBackPressed() {
        long secondBackKeyTime = System.currentTimeMillis();
        if (secondBackKeyTime - firstBackKeyTime > 2000) {
            Toast.makeText(this, "再按一次返回桌面", Toast.LENGTH_SHORT).show();
            firstBackKeyTime = secondBackKeyTime;
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (intent.getBooleanExtra("is_goback_login", false)) { //返回登陆页面
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    public void showLogoutDialog() {
        new AlertDialog.Builder(this)
        .setTitle("注销")
        .setMessage("您确定要注销当前用户吗？")
        .setPositiveButton("确定", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.putExtra("gotoMain", true);
                startActivity(intent);
                finish();
            }
        })
        .setNegativeButton("取消", null)
        .show();
    }

    public void showExitDialog() {
        new AlertDialog.Builder(this)    
        .setTitle("退出")
        .setMessage("您确定要退出应用吗？")
        .setPositiveButton("确定", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        })
        .setNegativeButton("取消", null)
        .show();
    }

    private void checkUpdate() {
        RequestParams params = new RequestParams();
        params.put("platform", "android");
        HttpUtil.post(this, NetworkInfo.serverUrl + "version/stable", params, new NormalAuthListener(this) {

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {       
                try {
                    ClientVersion cv = ClientVersion.dao.fromJson(responseString);
                    if (cv.getBuild() > AppInfo.getVersionCode()) { //有更新
                        showUpdateDialog(cv);
                    } else { //无更新
                        showNoUpdateDialog();
                    }
                } catch(Exception e) {
                    String[] msgs = responseString.split("\n");
                    showErrorDialog("提示", msgs[0], msgs[1]);
                }
            }

        });
    }

    private void checkUpdateBackground() {
        RequestParams params = new RequestParams();
        params.put("platform", "android");
        HttpUtil.baseGet(this, NetworkInfo.serverUrl + "version/stable", params, new TextHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {       
                try {
                    ClientVersion cv = ClientVersion.dao.fromJson(responseString);
                    if (cv.getBuild() > AppInfo.getVersionCode()) { //有更新
                        if (cv.isForced()) { //强制更新
                            showForcedUpdateDialog(cv);
                        } else { //非强制更新
                            showUpdateDialog(cv);
                        }
                    }
                } catch(Exception e) {
                    //后台更新检查不提示错误
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                //错误不处理
            }

        });
    }

    public void showUpdateDialog(final ClientVersion cv) {
        new AlertDialog.Builder(this)
        .setTitle("更新提示")
        .setMessage("有新版本：" + cv.getName() + "\n更新日志：\n" + cv.getMessage())
        .setPositiveButton("下载", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Uri uri = Uri.parse(cv.getPublishUrl());
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
            }
        })
        .setNegativeButton("忽略", null)
        .show();
    }

    public void showForcedUpdateDialog(final ClientVersion cv) {
        new AlertDialog.Builder(this)
        .setTitle("更新提示")
        .setMessage("有新版本：" + cv.getName() + "\n更新日志：\n" + cv.getMessage())
        .setPositiveButton("下载", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Uri uri = Uri.parse(cv.getPublishUrl());
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                startActivity(intent);
                finish();
            }
        })
        .setNegativeButton("退出", new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                finish();
            }
        })
        .setCancelable(false)
        .show();
    }

    public void showNoUpdateDialog() {
        new AlertDialog.Builder(this)
        .setTitle("提示")
        .setMessage("当前已是最新版本")
        .setPositiveButton("确定", null)
        .show();
    }

}
