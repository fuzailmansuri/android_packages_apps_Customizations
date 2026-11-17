package com.android.launcher3.smartspace;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.PowerManager;
import android.os.UserManager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.android.launcher3.Launcher;
import com.android.launcher3.CellLayout;
import com.android.launcher3.Insettable;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.WorkspaceLayoutManager;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.views.BaseDragLayer;

/** A fixed first-page header; saved workspace cells remain unchanged. */
public class AtAGlanceView extends FrameLayout implements
        SharedPreferences.OnSharedPreferenceChangeListener, StateListener<LauncherState>, Insettable {
    private static final int HEADER_HEIGHT_DP = 88;
    private static final int HEADER_MARGIN_DP = 16;
    private final Launcher mLauncher;
    private final SmartspaceView mSmartspace;
    private final ViewTreeObserver.OnPreDrawListener mPositionListener = () -> {
        updatePosition();
        return true;
    };
    private final Rect mInsets = new Rect();
    private ViewTreeObserver mObserver;
    private boolean mUsingExistingWidget;
    private boolean mVisible;
    private boolean mNormalState;

    public AtAGlanceView(Launcher launcher) {
        super(launcher);
        mLauncher = launcher;
        mSmartspace = new SmartspaceView(launcher);
        addView(mSmartspace, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public static void attach(Launcher launcher) {
        AtAGlanceView header = new AtAGlanceView(launcher);
        header.setId(R.id.launcher_smartspace);
        BaseDragLayer.LayoutParams params = new BaseDragLayer.LayoutParams(
                LayoutParams.MATCH_PARENT, header.dp(HEADER_HEIGHT_DP));
        params.ignoreInsets = true;
        launcher.getDragLayer().addView(header,
                launcher.getDragLayer().indexOfChild(launcher.getWorkspace()) + 1, params);
        header.setInsets(launcher.getDragLayer().getInsets());
    }

    public static int reservedTopPadding(Context context, int insetTop) {
        return insetTop + Math.round((HEADER_MARGIN_DP + HEADER_HEIGHT_DP)
                * context.getResources().getDisplayMetrics().density);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void setInsets(Rect insets) {
        mInsets.set(insets);
        BaseDragLayer.LayoutParams params = (BaseDragLayer.LayoutParams) getLayoutParams();
        params.topMargin = insets.top + dp(HEADER_MARGIN_DP);
        params.leftMargin = insets.left + mLauncher.getWorkspace().getPaddingLeft();
        params.rightMargin = insets.right + mLauncher.getWorkspace().getPaddingRight();
        setLayoutParams(params);
        updatePosition();
    }

    private void updatePosition() {
        Workspace<?> workspace = mLauncher.getWorkspace();
        CellLayout first = workspace.getScreenWithId(WorkspaceLayoutManager.FIRST_SCREEN_ID);
        if (first != null) {
            setTranslationX(workspace.getLeft() + first.getLeft() - workspace.getScrollX()
                    - mInsets.left - workspace.getPaddingLeft());
        }
        boolean shown = first != null && mNormalState && !mUsingExistingWidget
                && LauncherPrefs.SHOW_SMARTSPACE.get(getContext());
        setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
        setAlpha(workspace.getAlpha());
    }

    private void checkExistingWidget() {
        mUsingExistingWidget = false;
        if (mLauncher.getAppWidgetHolder() == null) return;
        AppWidgetManager manager = AppWidgetManager.getInstance(getContext());
        for (int id : mLauncher.getAppWidgetHolder().getAppWidgetIds()) {
            AppWidgetProviderInfo info = manager.getAppWidgetInfo(id);
            if (info != null && info.provider.getClassName().endsWith("SmartspaceWidgetProvider")) {
                mUsingExistingWidget = true;
                break;
            }
        }
    }

    private final BroadcastReceiver mScreenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateListening();
        }
    };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mNormalState = mLauncher.isInState(LauncherState.NORMAL);
        checkExistingWidget();
        mObserver = getViewTreeObserver();
        mObserver.addOnPreDrawListener(mPositionListener);
        LauncherPrefs.getPrefs(getContext()).registerOnSharedPreferenceChangeListener(this);
        mLauncher.getStateManager().addStateListener(this);
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        getContext().registerReceiver(mScreenReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        updateListening();
    }

    @Override
    protected void onDetachedFromWindow() {
        LauncherPrefs.getPrefs(getContext()).unregisterOnSharedPreferenceChangeListener(this);
        mLauncher.getStateManager().removeStateListener(this);
        getContext().unregisterReceiver(mScreenReceiver);
        if (mObserver != null && mObserver.isAlive()) {
            mObserver.removeOnPreDrawListener(mPositionListener);
        }
        mObserver = null;
        mSmartspace.setActive(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        mVisible = visible;
        // View's constructor may dispatch visibility before our child is initialized.
        if (mSmartspace != null) updateListening();
    }

    @Override
    public void onStateTransitionStart(LauncherState state) {
        mNormalState = mNormalState || state == LauncherState.NORMAL;
        updateListening();
    }

    @Override
    public void onStateTransitionComplete(LauncherState state) {
        mNormalState = state == LauncherState.NORMAL;
        updateListening();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null || LauncherPrefs.SHOW_SMARTSPACE.getSharedPrefKey().equals(key)) {
            mLauncher.getWorkspace().setInsets(mInsets);
            updateListening();
        }
        if (key == null || key.startsWith("pref_smartspace_")) mSmartspace.refreshSettings();
    }

    public void setResumed(boolean resumed) {
        if (resumed) checkExistingWidget();
        updateListening();
    }

    private void updateListening() {
        updatePosition();
        PowerManager power = getContext().getSystemService(PowerManager.class);
        KeyguardManager keyguard = getContext().getSystemService(KeyguardManager.class);
        UserManager users = getContext().getSystemService(UserManager.class);
        mSmartspace.setActive(isAttachedToWindow() && mVisible && mLauncher.isStarted() && mNormalState
                && !mUsingExistingWidget
                && power != null && power.isInteractive() && users != null && users.isUserUnlocked()
                && keyguard != null && !keyguard.isKeyguardLocked()
                && LauncherPrefs.SHOW_SMARTSPACE.get(getContext()));
    }
}
