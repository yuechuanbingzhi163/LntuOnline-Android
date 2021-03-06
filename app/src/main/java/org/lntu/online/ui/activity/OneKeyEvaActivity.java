package org.lntu.online.ui.activity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.lntu.online.R;
import com.melnykov.fab.FloatingActionButton;

import org.lntu.online.model.api.ApiClient;
import org.lntu.online.model.api.BackgroundCallback;
import org.lntu.online.model.entity.CourseEvaInfo;
import org.lntu.online.shared.LoginShared;
import org.lntu.online.ui.adapter.OneKeyEvaAdapter;
import org.lntu.online.ui.base.BaseActivity;
import org.lntu.online.util.ShipUtils;
import org.lntu.online.util.ToastUtils;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class OneKeyEvaActivity extends BaseActivity {

    @InjectView(R.id.toolbar)
    protected Toolbar toolbar;

    @InjectView(R.id.one_key_eva_layout_content)
    protected ViewGroup layoutContent;

    @InjectView(R.id.one_key_eva_icon_loading)
    protected View iconLoading;

    @InjectView(R.id.one_key_eva_icon_empty)
    protected View iconEmpty;

    @InjectView(R.id.one_key_eva_icon_loading_anim)
    protected View iconLoadingAnim;

    @InjectView(R.id.one_key_eva_tv_load_failed)
    protected TextView tvLoadFailed;

    @InjectView(R.id.one_key_eva_recycler_view)
    protected RecyclerView recyclerView;

    @InjectView(R.id.one_key_eva_fab)
    protected FloatingActionButton fab;

    private OneKeyEvaAdapter adapter;
    private List<CourseEvaInfo> infoList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_one_key_eva);
        ButterKnife.inject(this);

        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Animation dataLoadAnim = AnimationUtils.loadAnimation(this, R.anim.data_loading);
        dataLoadAnim.setInterpolator(new LinearInterpolator());
        iconLoadingAnim.startAnimation(dataLoadAnim);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        infoList = new ArrayList<>();
        adapter = new OneKeyEvaAdapter(this, infoList);
        recyclerView.setAdapter(adapter);

        fab.attachToRecyclerView(recyclerView);

        startNetwork();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startNetwork() {
        ApiClient.with(this).apiService.getCourseEvaInfoList(LoginShared.getLoginToken(this), new BackgroundCallback<List<CourseEvaInfo>>(this) {

            @Override
            public void handleSuccess(List<CourseEvaInfo> infoList, Response response) {
                if (infoList.size() == 0) {
                    showIconEmptyView("暂时没有评课信息。");
                } else {
                    OneKeyEvaActivity.this.infoList.clear();
                    OneKeyEvaActivity.this.infoList.addAll(infoList);
                    adapter.notifyDataSetChanged();
                    layoutContent.setVisibility(View.VISIBLE);
                    iconLoading.setVisibility(View.GONE);
                    iconEmpty.setVisibility(View.GONE);
                }
            }

            @Override
            public void handleFailure(String message) {
                showIconEmptyView(message);
            }

        });
    }

    private void showIconEmptyView(String message) {
        iconLoading.setVisibility(View.GONE);
        iconEmpty.setVisibility(View.VISIBLE);
        tvLoadFailed.setText(message);
    }

    @OnClick(R.id.one_key_eva_icon_empty)
    protected void onBtnIconEmptyClick() {
        iconLoading.setVisibility(View.VISIBLE);
        iconEmpty.setVisibility(View.GONE);
        startNetwork();
    }

    @OnClick(R.id.one_key_eva_fab)
    protected void onBtnFabClick() {
        int n = 0;
        for (CourseEvaInfo info : infoList) {
            if (!info.isDone()) {
                n++;
            }
        }
        if (n <= 0) { //不需要评估
            new MaterialDialog.Builder(this)
                    .title("提示")
                    .content("您的课程都已经评价完成了。")
                    .contentColorRes(R.color.text_color_primary)
                    .positiveText("确定")
                    .positiveColorRes(R.color.color_primary)
                    .show();
        } else { //需要评估
            new MaterialDialog.Builder(this)
                    .title("提示")
                    .content("" +
                            "您有" + n + "门课程需要评价。只有所有的课程完成评价之后，才能够正常查询成绩信息。点击【评价】按钮将会授权应用为您自动全部评价为好评。您也可以通过浏览器登录教务在线手动评价。\n\n" +
                            "您是否授权应用为您自动评价课程呢？")
                    .contentColorRes(R.color.text_color_primary)
                    .positiveText("评价")
                    .positiveColorRes(R.color.color_primary)
                    .negativeText("取消")
                    .negativeColorRes(R.color.text_color_primary)
                    .callback(new MaterialDialog.ButtonCallback() {

                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            MaterialDialog progressDialog = new MaterialDialog.Builder(OneKeyEvaActivity.this)
                                    .content(R.string.networking)
                                    .progress(true, 0)
                                    .cancelListener(new DialogInterface.OnCancelListener() {

                                        @Override
                                        public void onCancel(DialogInterface dialog) {
                                            ToastUtils.with(OneKeyEvaActivity.this).show("评课任务已停止");
                                        }

                                    })
                                    .build();
                            progressDialog.setCanceledOnTouchOutside(false);
                            progressDialog.show();
                            startEvaCourse(0, progressDialog);
                        }

                    })
                    .show();
        }
    }

    private void startEvaCourse(final int current, final MaterialDialog progressDialog) {
        if (current == infoList.size()) { //评价已经完成
            progressDialog.dismiss();
            int n = 0;
            for (CourseEvaInfo info : infoList) {
                if (!info.isDone()) {
                    n++;
                }
            }
            if (n <= 0) { //不需要评估
                new MaterialDialog.Builder(this)
                        .title("提示")
                        .content("您的课程都已经评价完成了。\n不给我们一个好评吗？")
                        .contentColorRes(R.color.text_color_primary)
                        .positiveText("给好评")
                        .positiveColorRes(R.color.color_primary)
                        .negativeText("不评价")
                        .negativeColorRes(R.color.text_color_primary)
                        .callback(new MaterialDialog.ButtonCallback() {

                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                ShipUtils.appStore(OneKeyEvaActivity.this);
                            }

                            @Override
                            public void onNegative(MaterialDialog dialog) {
                                ToastUtils.with(OneKeyEvaActivity.this).show("呜呜呜~~~o(>_<)o");
                            }

                        })
                        .show();
            } else { //需要评估
                new MaterialDialog.Builder(this)
                        .title("提示")
                        .content("您有" + n + "门课程评价失败，您可以再试一次。")
                        .contentColorRes(R.color.text_color_primary)
                        .positiveText("确定")
                        .positiveColorRes(R.color.color_primary)
                        .show();
            }
        } else { //没评完
            final CourseEvaInfo info = infoList.get(current);
            if (info.isDone()) { //不需要评价，跳过
                startEvaCourse(current + 1, progressDialog);
            } else { //需要评价
                progressDialog.setContent("正在评价：" + info.getName());
                ApiClient.with(this).apiService.doCourseEva(LoginShared.getLoginToken(this), info.getEvaKey(), new Callback<Void>() {

                    @Override
                    public void success(Void nothing, Response response) {
                        if (progressDialog.isShowing()) {
                            info.setDone(true);
                            adapter.notifyItemChanged(current);
                            startEvaCourse(current + 1, progressDialog);
                        }
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        if (progressDialog.isShowing()) {
                            startEvaCourse(current + 1, progressDialog);
                        }
                    }

                });
            }
        }
    }

}
