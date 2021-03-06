package com.aries.library.fast;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.aries.library.fast.delegate.FastRefreshDelegate;
import com.aries.library.fast.delegate.FastTitleDelegate;
import com.aries.library.fast.i.ActivityFragmentControl;
import com.aries.library.fast.i.IFastRefreshView;
import com.aries.library.fast.i.IFastTitleView;
import com.aries.library.fast.i.SwipeBackControl;
import com.aries.library.fast.manager.LoggerManager;
import com.aries.library.fast.module.activity.FastMainActivity;
import com.aries.library.fast.module.activity.FastRefreshLoadActivity;
import com.aries.library.fast.module.activity.FastTitleActivity;
import com.aries.library.fast.module.fragment.FastRefreshLoadFragment;
import com.aries.library.fast.module.fragment.FastTitleFragment;
import com.aries.library.fast.module.fragment.FastTitleRefreshLoadFragment;
import com.aries.library.fast.util.FastStackUtil;
import com.aries.library.fast.util.FastUtil;
import com.aries.library.fast.util.SizeUtil;
import com.aries.ui.helper.navigation.NavigationViewHelper;
import com.aries.ui.helper.status.StatusViewHelper;
import com.aries.ui.util.DrawableUtil;
import com.aries.ui.util.FindViewUtil;
import com.aries.ui.util.RomUtil;
import com.aries.ui.view.tab.CommonTabLayout;
import com.aries.ui.view.title.TitleBarView;

import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import cn.bingoogolapple.swipebacklayout.BGAKeyboardUtil;
import cn.bingoogolapple.swipebacklayout.BGASwipeBackHelper;

/**
 * @Author: AriesHoo on 2018/7/30 13:48
 * @E-Mail: AriesHoo@126.com
 * Function: Activity/Fragment生命周期
 * Description:
 * 1、2018-7-2 09:29:54 新增继承{@link FastMainActivity}的Activity虚拟导航栏功能
 * 2、2018-11-29 11:49:46 {@link #setStatusBar(Activity)}增加topView background 空判断
 * 3、2018-11-29 11:50:58 {@link #onActivityDestroyed(Activity)} 出栈方法调用{@link FastStackUtil#pop(Activity, boolean)} 第二个参数设置为false避免因Activity状态切换进入生命周期造成状态无法保存问题
 * 4、2019-3-25 14:27:33 新增下拉刷新功能处理及管理并删除原Fragment生命周期注销方法
 */
public class FastLifecycleCallbacks extends FragmentManager.FragmentLifecycleCallbacks implements Application.ActivityLifecycleCallbacks {

    private String TAG = getClass().getSimpleName();
    private ActivityFragmentControl mActivityFragmentControl;
    private Application.ActivityLifecycleCallbacks mActivityLifecycleCallbacks;
    private FragmentManager.FragmentLifecycleCallbacks mFragmentLifecycleCallbacks;
    private SwipeBackControl mSwipeBackControl;


    @Override
    public void onActivityCreated(final Activity activity, final Bundle savedInstanceState) {
        LoggerManager.i(TAG, "onActivityCreated:" + activity.getClass().getSimpleName() + ";contentView:" + FastUtil.getRootView(activity));
        getControl();

        //统一Activity堆栈管理
        FastStackUtil.getInstance().push(activity);
        //统一横竖屏操作
        if (mActivityFragmentControl != null) {
            mActivityFragmentControl.setRequestedOrientation(activity);
        }
        //统一Fragment生命周期处理
        if (activity instanceof FragmentActivity) {
            FragmentManager fragmentManager = ((FragmentActivity) activity).getSupportFragmentManager();
            fragmentManager.registerFragmentLifecycleCallbacks(this, true);
            if (mFragmentLifecycleCallbacks != null) {
                fragmentManager.registerFragmentLifecycleCallbacks(mFragmentLifecycleCallbacks, true);
            }
        }
        //设置滑动返回
        if (!(activity instanceof BGASwipeBackHelper.Delegate)) {
            setSwipeBack(activity);
        }
        //回调给开发者实现自己应用逻辑
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivityCreated(activity, savedInstanceState);
        }
    }

    @Override
    public void onActivityStarted(Activity activity) {
        View contentView = FastUtil.getRootView(activity);
        LoggerManager.i(TAG, "onActivityStarted:" + activity.getClass().getSimpleName() + ";contentView:" + contentView);
        boolean isSet = activity.getIntent().getBooleanExtra(FastConstant.IS_SET_CONTENT_VIEW_BACKGROUND, false);
        if (!isSet) {
            setContentViewBackground(FastUtil.getRootView(activity), activity.getClass());
        }
        //设置状态栏
        setStatusBar(activity);
        //设置虚拟导航栏功能
        setNavigationBar(activity);
        //配置下拉刷新
        if (activity instanceof IFastRefreshView
                && !(FastRefreshLoadActivity.class.isAssignableFrom(activity.getClass()))
                && !activity.getIntent().getBooleanExtra(FastConstant.IS_SET_REFRESH_VIEW, false)) {
            IFastRefreshView refreshView = (IFastRefreshView) activity;
            if (contentView != null
                    || refreshView.getContentView() != null) {
                FastRefreshDelegate delegate = new FastRefreshDelegate(
                        refreshView.getContentView() != null ? refreshView.getContentView() : contentView,
                        (IFastRefreshView) activity);
                String keyDelegate = UUID.randomUUID().toString();
                activity.getIntent().putExtra(FastConstant.KEY_FAST_DELEGATE, keyDelegate);
                FastDelegateManager.getInstance().putFastRefreshDelegate(keyDelegate, delegate);
                activity.getIntent().putExtra(FastConstant.IS_SET_REFRESH_VIEW, true);
            }
        }
        //设置TitleBarView
        if (activity instanceof IFastTitleView
                && !(FastTitleActivity.class.isAssignableFrom(activity.getClass()))
                && !activity.getIntent().getBooleanExtra(FastConstant.IS_SET_TITLE_BAR_VIEW, false)
                && contentView != null) {
            new FastTitleDelegate(contentView, (IFastTitleView) activity, activity.getClass());
            activity.getIntent().putExtra(FastConstant.IS_SET_TITLE_BAR_VIEW, true);
        }
        //回调开发者处理
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivityStarted(activity);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        LoggerManager.i(TAG, "onActivityResumed:" + activity.getClass().getSimpleName());
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivityResumed(activity);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        LoggerManager.i(TAG, "onActivityPaused:" + activity.getClass().getSimpleName() + ";isFinishing:" + activity.isFinishing());
        //Activity销毁前的时机需要关闭软键盘-在onActivityStopped及onActivityDestroyed生命周期内已无法关闭
        if (activity.isFinishing()) {
            BGAKeyboardUtil.closeKeyboard(activity);
        }
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivityPaused(activity);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {
        LoggerManager.i(TAG, "onActivityStopped:" + activity.getClass().getSimpleName() + ";isFinishing:" + activity.isFinishing());
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivityStopped(activity);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        LoggerManager.i(TAG, "onActivitySaveInstanceState:" + activity.getClass().getSimpleName());
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivitySaveInstanceState(activity, outState);
        }
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        //横竖屏会重绘将状态重置
        if (activity.getIntent() != null) {
            activity.getIntent().putExtra(FastConstant.IS_SET_STATUS_VIEW_HELPER, false);
            activity.getIntent().putExtra(FastConstant.IS_SET_NAVIGATION_VIEW_HELPER, false);
            activity.getIntent().putExtra(FastConstant.IS_SET_CONTENT_VIEW_BACKGROUND, false);
            activity.getIntent().putExtra(FastConstant.IS_SET_REFRESH_VIEW, false);
            activity.getIntent().putExtra(FastConstant.IS_SET_TITLE_BAR_VIEW, false);
        }
        LoggerManager.i(TAG, "onActivityDestroyed:" + activity.getClass().getSimpleName() + ";isFinishing:" + activity.isFinishing());
        FastStackUtil.getInstance().pop(activity, false);

        //清除下拉刷新代理FastRefreshDelegate
        String keyDelegate = activity.getIntent().getStringExtra(FastConstant.KEY_FAST_DELEGATE);
        FastDelegateManager.getInstance().removeFastRefreshDelegate(keyDelegate);
        if (mActivityLifecycleCallbacks != null) {
            mActivityLifecycleCallbacks.onActivityDestroyed(activity);
        }
    }

    @Override
    public void onFragmentViewCreated(FragmentManager fm, Fragment f, View v, Bundle savedInstanceState) {
        super.onFragmentViewCreated(fm, f, v, savedInstanceState);
        boolean isSet = f.getArguments() != null ? f.getArguments().getBoolean(FastConstant.IS_SET_CONTENT_VIEW_BACKGROUND, false) : false;
        if (!isSet) {
            setContentViewBackground(v, f.getClass());
        }
        //刷新功能处理
        if (f instanceof IFastRefreshView
                && !(FastRefreshLoadFragment.class.isAssignableFrom(f.getClass()))) {
            IFastRefreshView refreshView = (IFastRefreshView) f;
            String keyDelegate = UUID.randomUUID().toString();
            f.getArguments().putString(FastConstant.KEY_FAST_DELEGATE, keyDelegate);
            FastRefreshDelegate delegate = new FastRefreshDelegate(
                    refreshView.getContentView() != null ? refreshView.getContentView() : f.getView(),
                    refreshView);
            FastDelegateManager.getInstance().putFastRefreshDelegate(keyDelegate, delegate);
        }
        //设置TitleBarView
        if (f instanceof IFastTitleView
                && !(FastTitleFragment.class.isAssignableFrom(f.getClass()))
                && !(FastTitleRefreshLoadFragment.class.isAssignableFrom(f.getClass()))
                && v != null) {
            new FastTitleDelegate(v, (IFastTitleView) f, f.getClass());
        }
    }

    @Override
    public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
        super.onFragmentViewDestroyed(fm, f);
        if (f.getArguments() != null) {
            f.getArguments().putBoolean(FastConstant.IS_SET_CONTENT_VIEW_BACKGROUND, false);
            String keyDelegate = f.getArguments().getString(FastConstant.KEY_FAST_DELEGATE);
            FastDelegateManager.getInstance().removeFastRefreshDelegate(keyDelegate);
        }
    }

    /**
     * 实时获取回调
     */

    private void getControl() {
        mSwipeBackControl = FastManager.getInstance().getSwipeBackControl();
        mActivityFragmentControl = FastManager.getInstance().getActivityFragmentControl();
        if (mActivityFragmentControl == null) {
            return;
        }
        mActivityLifecycleCallbacks = mActivityFragmentControl.getActivityLifecycleCallbacks();
        mFragmentLifecycleCallbacks = mActivityFragmentControl.getFragmentLifecycleCallbacks();
    }

    /**
     * 回调设置Activity/Fragment背景
     *
     * @param v
     * @param cls
     */
    private void setContentViewBackground(View v, Class<?> cls) {
        if (mActivityFragmentControl != null && v != null) {
            mActivityFragmentControl.setContentViewBackground(v, cls);
        }
    }

    /**
     * 设置滑动返回相关
     *
     * @param activity
     */
    private void setSwipeBack(final Activity activity) {
        LoggerManager.i(TAG, activity + getClass().getSimpleName() + ":设置Activity滑动返回");
        //需设置activity window背景为透明避免滑动过程中漏出背景也可减少背景层级降低过度绘制
        activity.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        final BGASwipeBackHelper swipeBackHelper = new BGASwipeBackHelper(activity, new BGASwipeBackHelper.Delegate() {
            @Override
            public boolean isSupportSwipeBack() {
                return mSwipeBackControl != null ? mSwipeBackControl.isSwipeBackEnable(activity) : true;
            }

            @Override
            public void onSwipeBackLayoutSlide(float slideOffset) {
                LoggerManager.i(TAG, "onSwipeBackLayoutCancel");
                if (mSwipeBackControl != null) {
                    mSwipeBackControl.onSwipeBackLayoutSlide(activity, slideOffset);
                }
            }

            @Override
            public void onSwipeBackLayoutCancel() {
                LoggerManager.i(TAG, "onSwipeBackLayoutCancel");
                if (mSwipeBackControl != null) {
                    mSwipeBackControl.onSwipeBackLayoutCancel(activity);
                }
            }

            @Override
            public void onSwipeBackLayoutExecuted() {
                //设置退出动画-确保效果准确
                if (activity == null || activity.isFinishing()) {
                    return;
                }
                BGAKeyboardUtil.closeKeyboard(activity);
                activity.finish();
                activity.overridePendingTransition(0, R.anim.bga_sbl_activity_swipeback_exit);
                if (mSwipeBackControl != null) {
                    mSwipeBackControl.onSwipeBackLayoutExecuted(activity);
                }
            }
        })
                //设置滑动背景
                .setShadowResId(R.drawable.bga_sbl_shadow)
                //底部导航条是否悬浮在内容上设置过NavigationViewHelper可以不用设置该属性
                .setIsNavigationBarOverlap(true)
                .setIsShadowAlphaGradient(true);
        //用于全局控制
        if (mSwipeBackControl != null) {
            mSwipeBackControl.setSwipeBack(activity, swipeBackHelper);
        }
    }

    private void setStatusBar(Activity activity) {
        boolean isSet = activity.getIntent().getBooleanExtra(FastConstant.IS_SET_STATUS_VIEW_HELPER, false);
        if (isSet) {
            return;
        }
        TitleBarView titleBarView = FindViewUtil.getTargetView(activity.getWindow().getDecorView(), TitleBarView.class);
        //不包含TitleBarView处理
        if (titleBarView == null && !(activity instanceof FastMainActivity)) {
            View topView = getTopView(FastUtil.getRootView(activity));
            LoggerManager.i(TAG, "其它三方库设置状态栏沉浸");
            StatusViewHelper statusViewHelper = StatusViewHelper.with(activity)
                    .setControlEnable(true)
                    .setPlusStatusViewEnable(false)
                    .setTransEnable(true)
                    .setTopView(topView);
            if (topView != null && topView.getBackground() != null) {
                Drawable drawable = topView.getBackground().mutate();
                statusViewHelper.setStatusLayoutDrawable(drawable);
            }
            boolean isInit = mActivityFragmentControl != null ? mActivityFragmentControl.setStatusBar(activity, statusViewHelper, topView) : true;
            if (isInit) {
                statusViewHelper.init();
                activity.getIntent().putExtra(FastConstant.IS_SET_STATUS_VIEW_HELPER, true);
            }
        }
    }

    /**
     * 设置全局虚拟导航栏功能
     *
     * @param activity
     */
    private void setNavigationBar(Activity activity) {
        boolean isSet = activity.getIntent().getBooleanExtra(FastConstant.IS_SET_NAVIGATION_VIEW_HELPER, false);
        if (isSet) {
            return;
        }
        LoggerManager.i(TAG, "setNavigationBars:设置虚拟导航栏");
        View bottomView = FastUtil.getRootView(activity);
        //继承FastMainActivity底部View处理
        if (FastMainActivity.class.isAssignableFrom(activity.getClass())) {
            CommonTabLayout tabLayout = FindViewUtil.getTargetView(bottomView, CommonTabLayout.class);
            if (tabLayout != null) {
                bottomView = tabLayout;
            }
        }
        Drawable drawableTop = ContextCompat.getDrawable(activity, R.color.colorLineGray);
        DrawableUtil.setDrawableWidthHeight(drawableTop, SizeUtil.getScreenWidth(), SizeUtil.dp2px(0.5f));
        //设置虚拟导航栏控制
        NavigationViewHelper navigationViewHelper = NavigationViewHelper.with(activity)
                .setLogEnable(BuildConfig.DEBUG)
                //是否控制虚拟导航栏true 后续属性有效--第一优先级
                .setControlEnable(true)
                //是否全透明导航栏优先级第二--同步设置setNavigationViewColor故注意调用顺序
                //华为的半透明和全透明类似
                .setTransEnable(isTrans())
                //是否增加假的NavigationView用于沉浸至虚拟导航栏遮住
                .setPlusNavigationViewEnable(isTrans())
                //设置是否控制底部输入框--默认属性
                .setControlBottomEditTextEnable(true)
                //设置最下边View用于增加paddingBottom--建议activity 根布局
                .setBottomView(bottomView)
                //影响setPlusNavigationViewEnable(true)单个条件
                //或者(setPlusNavigationViewEnable(false)&&setControlEnable(true))--两个前置条件
                //半透明默认设置102
                .setNavigationViewColor(Color.argb(isTrans() ? 0 : 102, 0, 0, 0))
                //setPlusNavigationViewEnable(true)才有效注意与setNavigationViewColor调用顺序
//                .setNavigationViewDrawable(mContext.getResources().getDrawable(R.drawable.img_bg_login))
                //setPlusNavigationViewEnable(true)有效
                .setNavigationViewDrawableTop(drawableTop)
                .setNavigationLayoutColor(Color.WHITE);
        boolean isInit = mActivityFragmentControl != null ? mActivityFragmentControl.setNavigationBar(activity, navigationViewHelper, bottomView) : true;
        if (isInit) {
            activity.getIntent().putExtra(FastConstant.IS_SET_NAVIGATION_VIEW_HELPER, true);
            navigationViewHelper.init();
        }
    }

    /**
     * 是否全透明-华为4.1以上可根据导航栏位置颜色设置导航图标颜色
     *
     * @return
     */
    protected boolean isTrans() {
        return RomUtil.isEMUI() && (RomUtil.getEMUIVersion().compareTo("EmotionUI_4.1") > 0);
    }

    /**
     * 获取Activity 顶部View(用于延伸至状态栏下边)
     *
     * @param target
     * @return
     */
    private View getTopView(View target) {
        if (target != null && target instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) target;
            if (group.getChildCount() > 0) {
                target = ((ViewGroup) target).getChildAt(0);
            }
        }
        return target;
    }
}
