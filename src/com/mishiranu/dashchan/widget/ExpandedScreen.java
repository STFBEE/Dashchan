package com.mishiranu.dashchan.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.util.AnimationUtils;
import com.mishiranu.dashchan.util.FlagUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashMap;

public class ExpandedScreen implements RecyclerScrollTracker.OnScrollListener,
		WindowControlFrameLayout.OnApplyWindowPaddingsListener {
	private static final int LOLLIPOP_DIM_COLOR = 0x4d000000;

	private final boolean expandingEnabled;
	private final boolean fullScreenLayoutEnabled;
	private final int initialActionBarHeight;

	private final Handler handler = new Handler();
	private final Activity activity;
	private final Rect insets = new Rect();

	private final View rootView;
	private final View toolbarView;
	private final View drawerInterlayer;
	private final View drawerContent;
	private final View drawerHeader;

	private View actionModeView;
	private View statusGuardView;

	private boolean drawerOverToolbarEnabled;
	private final LinkedHashMap<View, RecyclerView> contentViews = new LinkedHashMap<>();

	private ValueAnimator foregroundAnimator;
	private boolean foregroundAnimatorShow;

	private static final int STATE_SHOW = 0x00000001;
	private static final int STATE_ACTION_MODE = 0x00000002;
	private static final int STATE_LOCKED = 0x00000004;

	private final HashSet<String> lockers = new HashSet<>();
	private int stateFlags = STATE_SHOW;

	private final int slopShiftSize;
	private final int lastItemLimit;
	private final int minItemsCount;

	public static class PreThemeInit {
		private final boolean expandingEnabled;
		private final boolean fullScreenLayoutEnabled;
		private final int initialActionBarHeight;
		private final Activity activity;

		public PreThemeInit(Activity activity, boolean enabled) {
			expandingEnabled = enabled;
			if (C.API_LOLLIPOP) {
				fullScreenLayoutEnabled = true;
			} else if (C.API_KITKAT) {
				Resources resources = activity.getResources();
				int resId = resources.getIdentifier("config_enableTranslucentDecor", "bool", "android");
				fullScreenLayoutEnabled = resId != 0 && resources.getBoolean(resId);
			} else {
				fullScreenLayoutEnabled = false;
			}
			initialActionBarHeight = obtainActionBarHeight(activity);
			this.activity = activity;
			Window window = activity.getWindow();
			if (!fullScreenLayoutEnabled && enabled) {
				window.requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
			}
		}

		public Init initAfterTheme() {
			if (fullScreenLayoutEnabled) {
				Window window = activity.getWindow();
				window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
						View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
			}
			return new Init(expandingEnabled, fullScreenLayoutEnabled, initialActionBarHeight, activity);
		}
	}

	public static class Init {
		private final boolean expandingEnabled;
		private final boolean fullScreenLayoutEnabled;
		private final int initialActionBarHeight;
		private final Activity activity;

		private Init(boolean expandingEnabled, boolean fullScreenLayoutEnabled,
				int initialActionBarHeight, Activity activity) {
			this.expandingEnabled = expandingEnabled;
			this.fullScreenLayoutEnabled = fullScreenLayoutEnabled;
			this.initialActionBarHeight = initialActionBarHeight;
			this.activity = activity;
		}
	}

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public ExpandedScreen(Init init, View rootView, View toolbarView, FrameLayout drawerInterlayer,
			FrameLayout drawerParent, View drawerContent, View drawerHeader) {
		expandingEnabled = init.expandingEnabled;
		fullScreenLayoutEnabled = init.fullScreenLayoutEnabled;
		initialActionBarHeight = init.initialActionBarHeight;
		activity = init.activity;
		Window window = activity.getWindow();
		if (fullScreenLayoutEnabled) {
			if (C.API_LOLLIPOP) {
				int statusBarColor = window.getStatusBarColor() | Color.BLACK;
				int navigationBarColor = window.getNavigationBarColor() | Color.BLACK;
				window.setStatusBarColor(Color.TRANSPARENT);
				window.setNavigationBarColor(Color.TRANSPARENT);
				contentForeground = new LollipopContentForeground(statusBarColor, navigationBarColor);
				statusBarContentForeground = new LollipopStatusBarForeground(statusBarColor);
				statusBarDrawerForeground = new LollipopDrawerForeground();
			} else if (C.API_KITKAT) {
				contentForeground = new KitKatContentForeground();
				statusBarContentForeground = null;
				statusBarDrawerForeground = null;
			} else {
				contentForeground = null;
				statusBarContentForeground = null;
				statusBarDrawerForeground = null;
			}
		} else {
			contentForeground = null;
			statusBarContentForeground = null;
			statusBarDrawerForeground = null;
		}

		Resources resources = activity.getResources();
		float density = ResourceUtils.obtainDensity(resources);
		slopShiftSize = (int) (6f * density);
		lastItemLimit = (int) (72f * density);
		minItemsCount = resources.getConfiguration().screenHeightDp / 48;
		this.rootView = rootView;
		this.toolbarView = toolbarView;
		this.drawerInterlayer = drawerInterlayer;
		if (drawerInterlayer != null) {
			drawerInterlayer.setForeground(statusBarContentForeground);
		}
		this.drawerContent = drawerContent;
		this.drawerHeader = drawerHeader;
		if (fullScreenLayoutEnabled) {
			FrameLayout content = activity.findViewById(android.R.id.content);
			WindowControlFrameLayout frameLayout = new WindowControlFrameLayout(activity);
			content.addView(frameLayout, FrameLayout.LayoutParams.MATCH_PARENT,
					FrameLayout.LayoutParams.MATCH_PARENT);
			frameLayout.setOnApplyWindowPaddingsListener(this);
			frameLayout.setBackground(contentForeground);
			if (statusBarDrawerForeground != null && drawerParent != null) {
				drawerParent.setForeground(statusBarDrawerForeground);
			}
		}
		updatePaddings();
	}

	@Override
	public void onApplyWindowPaddings(WindowControlFrameLayout view, Rect rect) {
		Rect newInsets = new Rect(rect);
		if (newInsets.top > initialActionBarHeight) {
			// Fix for KitKat, assuming AB height always > status bar height
			newInsets.top -= initialActionBarHeight;
		}
		if (!insets.equals(newInsets)) {
			insets.set(newInsets);
			updatePaddings();
		}
	}

	// The same value is hardcoded in ActionBarImpl.
	private static final int ACTION_BAR_ANIMATION_TIME = 250;

	private final ForegroundDrawable contentForeground;
	private final ForegroundDrawable statusBarContentForeground;
	private final ForegroundDrawable statusBarDrawerForeground;

	private static abstract class ForegroundDrawable extends Drawable {
		protected int alpha = 0xff;

		public final void applyAlpha(float value) {
			alpha = (int) (0xff * value);
			invalidateSelf();
		}

		@Override
		public final int getOpacity() {
			return 0;
		}

		@Override
		public final void setAlpha(int alpha) {}

		@Override
		public final void setColorFilter(ColorFilter cf) {}
	}

	private class KitKatContentForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();

		@Override
		public void draw(@NonNull Canvas canvas) {
			int statusBarHeight = insets.top;
			if (statusBarHeight > 0 && alpha != 0x00 && alpha != 0xff) {
				// Black while action bar animated
				paint.setColor(Color.BLACK);
				canvas.drawRect(0f, 0f, getBounds().width(), statusBarHeight, paint);
			}
		}
	}

	private class LollipopContentForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();
		private final int statusBarColor;
		private final int navigationBarColor;

		public LollipopContentForeground(int statusBarColor, int navigationBarColor) {
			this.statusBarColor = statusBarColor;
			this.navigationBarColor = navigationBarColor;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			int width = getBounds().width();
			int height = getBounds().height();
			Paint paint = this.paint;
			if (toolbarView == null) {
				int statusBarHeight = insets.top;
				if (statusBarHeight > 0) {
					paint.setColor(LOLLIPOP_DIM_COLOR);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					if (alpha > 0) {
						paint.setColor(statusBarColor);
						paint.setAlpha(alpha);
						canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
					}
				}
			}
			int navigationBarLeft = insets.left;
			int navigationBarRight = insets.right;
			int navigationBarBottom = insets.bottom;
			if (navigationBarLeft > 0) {
				paint.setColor(navigationBarColor);
				canvas.drawRect(0, 0, navigationBarLeft, height, paint);
			}
			if (navigationBarRight > 0) {
				paint.setColor(navigationBarColor);
				canvas.drawRect(width - navigationBarRight, 0, width, height, paint);
			}
			if (navigationBarBottom > 0) {
				paint.setColor(LOLLIPOP_DIM_COLOR);
				canvas.drawRect(0f, height - navigationBarBottom, width, height, paint);
				if (alpha > 0) {
					paint.setColor(navigationBarColor);
					paint.setAlpha(alpha);
					canvas.drawRect(0f, height - navigationBarBottom, width, height, paint);
				}
			}
		}
	}

	private class LollipopStatusBarForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();
		private final int statusBarColor;

		public LollipopStatusBarForeground(int statusBarColor) {
			this.statusBarColor = statusBarColor;
		}

		@Override
		public void draw(@NonNull Canvas canvas) {
			int width = getBounds().width();
			Paint paint = this.paint;
			int statusBarHeight = insets.top;
			if (statusBarHeight > 0) {
				paint.setColor(LOLLIPOP_DIM_COLOR);
				canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				if (alpha > 0) {
					paint.setColor(statusBarColor);
					paint.setAlpha(alpha);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				}
			}
		}
	}

	private class LollipopDrawerForeground extends ForegroundDrawable {
		private final Paint paint = new Paint();

		@Override
		public void draw(@NonNull Canvas canvas) {
			if (drawerOverToolbarEnabled && toolbarView != null) {
				int width = getBounds().width();
				int statusBarHeight = insets.top;
				if (statusBarHeight > 0) {
					paint.setColor(LOLLIPOP_DIM_COLOR);
					canvas.drawRect(0f, 0f, width, statusBarHeight, paint);
				}
			}
		}
	}

	private class ForegroundAnimatorListener implements ValueAnimator.AnimatorListener,
			ValueAnimator.AnimatorUpdateListener {
		private final boolean show;

		public ForegroundAnimatorListener(boolean show) {
			this.show = show;
		}

		@Override
		public void onAnimationUpdate(ValueAnimator animation) {
			float alpha = (float) animation.getAnimatedValue();
			if (contentForeground != null) {
				contentForeground.applyAlpha(alpha);
			}
			if (statusBarContentForeground != null) {
				statusBarContentForeground.applyAlpha(alpha);
			}
			if (statusBarDrawerForeground != null) {
				statusBarDrawerForeground.applyAlpha(alpha);
			}
			if (toolbarView != null) {
				toolbarView.setAlpha(alpha);
			}
		}

		@Override
		public void onAnimationStart(Animator animation) {}

		@Override
		public void onAnimationEnd(Animator animation) {
			if (toolbarView != null && !show) {
				activity.getActionBar().hide();
			}
			foregroundAnimator = null;
		}

		@Override
		public void onAnimationCancel(Animator animation) {}

		@Override
		public void onAnimationRepeat(Animator animation) {}
	}

	private boolean lastTranslucent = false;

	@TargetApi(Build.VERSION_CODES.KITKAT)
	private void setState(int state, boolean value) {
		if (expandingEnabled) {
			boolean oldShow, newShow, oldActionMode, newActionMode;
			newShow = oldShow = checkState(STATE_SHOW);
			newActionMode = oldActionMode = checkState(STATE_ACTION_MODE);
			if (state == STATE_SHOW) {
				newShow = value;
			}
			if (state == STATE_ACTION_MODE) {
				newActionMode = value;
			}
			stateFlags = FlagUtils.set(stateFlags, state, value);
			if (fullScreenLayoutEnabled && C.API_KITKAT && !C.API_LOLLIPOP) {
				boolean wasDisplayed = oldShow || oldActionMode;
				boolean willDisplayed = newShow || newActionMode;
				if (wasDisplayed == willDisplayed) {
					return;
				}
				boolean translucent = !willDisplayed;
				if (lastTranslucent != translucent) {
					lastTranslucent = translucent;
					Window window = activity.getWindow();
					if (translucent) {
						window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
								| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
					} else {
						window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
								| WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
					}
				}
			}
		}
	}

	private boolean checkState(int state) {
		return FlagUtils.get(stateFlags, state);
	}

	private void applyShowActionBar(boolean show) {
		ActionBar actionBar = activity.getActionBar();
		if (fullScreenLayoutEnabled) {
			boolean showing = isActionBarShowing();
			ValueAnimator foregroundAnimator = ExpandedScreen.this.foregroundAnimator;
			if (foregroundAnimator != null) {
				foregroundAnimator.cancel();
			}
			if (showing != show) {
				if (toolbarView != null) {
					actionBar.show();
				}
				foregroundAnimator = ValueAnimator.ofFloat(show ? 0f : 1f, show ? 1f : 0f);
				ForegroundAnimatorListener listener = new ForegroundAnimatorListener(show);
				foregroundAnimator.setInterpolator(AnimationUtils.ACCELERATE_DECELERATE_INTERPOLATOR);
				foregroundAnimator.setDuration(ACTION_BAR_ANIMATION_TIME);
				foregroundAnimator.addListener(listener);
				foregroundAnimator.addUpdateListener(listener);
				foregroundAnimator.start();
				ExpandedScreen.this.foregroundAnimator = foregroundAnimator;
				foregroundAnimatorShow = show;
			}
		}
		if (toolbarView == null) {
			if (show) {
				actionBar.show();
			} else {
				actionBar.hide();
			}
		}
	}

	private boolean isActionBarShowing() {
		if (!activity.getActionBar().isShowing()) {
			return false;
		}
		if (toolbarView != null && foregroundAnimator != null) {
			return foregroundAnimatorShow;
		}
		return true;
	}

	private boolean enqueuedShowState = true;
	private long lastShowStateChanged;

	private final Runnable showStateRunnable = () -> {
		if (enqueuedShowState != isActionBarShowing()) {
			boolean show = enqueuedShowState;
			setState(STATE_SHOW, show);
			applyShowActionBar(show);
			lastShowStateChanged = SystemClock.elapsedRealtime();
			updatePaddings();
		}
	};

	public void addLocker(String name) {
		lockers.add(name);
		if (!checkState(STATE_LOCKED)) {
			setLocked(true);
		}
	}

	public void removeLocker(String name) {
		lockers.remove(name);
		if (lockers.size() == 0 && checkState(STATE_LOCKED)) {
			setLocked(false);
		}
	}

	private void setLocked(boolean locked) {
		setState(STATE_LOCKED, locked);
		if (locked) {
			setShowActionBar(true, false);
		}
	}

	private void setShowActionBar(boolean show, boolean delayed) {
		// In Lollipop ActionMode isn't depending from Toolbar (android:windowActionModeOverlay == true in theme)
		if (toolbarView == null && checkState(STATE_ACTION_MODE) || checkState(STATE_LOCKED)) {
			show = true;
		}
		if (enqueuedShowState != show) {
			enqueuedShowState = show;
			handler.removeCallbacks(showStateRunnable);
			long t = SystemClock.elapsedRealtime() - lastShowStateChanged;
			if (show != isActionBarShowing()) {
				if (!delayed) {
					showStateRunnable.run();
				} else if (t >= ACTION_BAR_ANIMATION_TIME + 200) {
					handler.post(showStateRunnable);
				} else {
					handler.postDelayed(showStateRunnable, t);
				}
			}
		}
	}

	private final RecyclerScrollTracker recyclerScrollTracker = new RecyclerScrollTracker(this);

	public void addContentView(View view) {
		RecyclerView recyclerView = null;
		if (view instanceof ExpandedFrameLayout) {
			recyclerView = ((ExpandedFrameLayout) view).getRecyclerView();
		}
		contentViews.put(view, recyclerView);
		if (expandingEnabled) {
			if (recyclerView != null) {
				recyclerView.addOnScrollListener(recyclerScrollTracker);
			}
		}
		updatePaddings();
		setShowActionBar(true, true);
	}

	public void removeContentView(View view) {
		RecyclerView recyclerView = contentViews.remove(view);
		if (recyclerView != null && expandingEnabled) {
			recyclerView.removeOnScrollListener(recyclerScrollTracker);
		}
	}

	public void setDrawerOverToolbarEnabled(boolean drawerOverToolbarEnabled) {
		this.drawerOverToolbarEnabled = drawerOverToolbarEnabled;
		updatePaddings();
	}

	private static final int[] ATTRS_ACTION_BAR_SIZE = {android.R.attr.actionBarSize};

	private static int obtainActionBarHeight(Context context) {
		TypedArray typedArray = context.obtainStyledAttributes(ATTRS_ACTION_BAR_SIZE);
		int actionHeight = typedArray.getDimensionPixelSize(0, 0);
		typedArray.recycle();
		return actionHeight;
	}

	public void updatePaddings() {
		if (expandingEnabled || fullScreenLayoutEnabled) {
			int actionBarHeight = obtainActionBarHeight(activity);
			int statusBarHeight = insets.top;
			int leftNavigationBarHeight = insets.left;
			int rightNavigationBarHeight = insets.right;
			int bottomNavigationBarHeight = insets.bottom;
			if (rootView != null) {
				ViewUtils.setNewMargin(rootView, leftNavigationBarHeight, null, rightNavigationBarHeight, null);
			}
			if (drawerInterlayer != null) {
				ViewUtils.setNewPadding(drawerInterlayer, null, statusBarHeight, null, bottomNavigationBarHeight);
			}
			for (LinkedHashMap.Entry<View, RecyclerView> entry : contentViews.entrySet()) {
				View view = entry.getKey();
				RecyclerView recyclerView = entry.getValue();
				if (recyclerView != null) {
					ViewUtils.setNewPadding(recyclerView, null, statusBarHeight + actionBarHeight,
							null, bottomNavigationBarHeight);
				}
				if (view instanceof ExpandedFrameLayout) {
					ExpandedFrameLayout layout = (ExpandedFrameLayout) view;
					int childCount = layout.getChildCount();
					for (int i = 0; i < childCount; i++) {
						View child = layout.getChildAt(i);
						if (child != recyclerView) {
							ViewUtils.setNewPadding(child, null, statusBarHeight + actionBarHeight,
									null, bottomNavigationBarHeight);
						}
					}
				} else {
					ViewUtils.setNewMargin(view, null, statusBarHeight + actionBarHeight,
							null, bottomNavigationBarHeight);
				}
			}
			if (actionModeView != null) {
				ViewUtils.setNewMargin(actionModeView, leftNavigationBarHeight, null, rightNavigationBarHeight, null);
			}
			if (drawerContent != null) {
				int paddingTop = C.API_LOLLIPOP && drawerOverToolbarEnabled && toolbarView != null
						? statusBarHeight : statusBarHeight + actionBarHeight;
				if (drawerHeader != null) {
					ViewUtils.setNewPadding(drawerHeader, null, paddingTop, null, null);
					ViewUtils.setNewPadding(drawerContent, null, 0, null, bottomNavigationBarHeight);
				} else {
					ViewUtils.setNewPadding(drawerContent, null, paddingTop, null, bottomNavigationBarHeight);
				}
			}
			if (contentForeground != null) {
				contentForeground.invalidateSelf();
			}
			if (statusBarContentForeground != null) {
				statusBarContentForeground.invalidateSelf();
			}
			if (statusBarDrawerForeground != null) {
				statusBarDrawerForeground.invalidateSelf();
			}
		}
	}

	private boolean scrollingDown;

	@Override
	public void onScroll(ViewGroup view, int scrollY, int totalItemCount, boolean first, boolean last) {
		if (Math.abs(scrollY) > slopShiftSize) {
			scrollingDown = scrollY > 0;
		}
		boolean hide = false;
		if (scrollingDown) {
			if (totalItemCount > minItemsCount) {
				// List can be overscrolled when it shows first item including list top padding
				// top <= 0 means that list is not overscrolled
				if (!first || view.getChildAt(0).getTop() <= 0) {
					if (last) {
						View lastView = view.getChildAt(view.getChildCount() - 1);
						if (view.getHeight() - view.getPaddingBottom() - lastView.getBottom() + lastItemLimit < 0) {
							hide = true;
						}
					} else {
						hide = true;
					}
				}
			}
		}
		setShowActionBar(!hide, true);
	}

	public void setActionModeState(boolean actionMode) {
		if (actionMode && fullScreenLayoutEnabled && actionModeView == null) {
			// ActionModeBar view has lazy initialization
			int actionModeBarId = activity.getResources().getIdentifier("action_mode_bar", "id", "android");
			actionModeView = actionModeBarId != 0 ? activity.findViewById(actionModeBarId) : null;
			updatePaddings();
		}
		if (!actionMode && fullScreenLayoutEnabled && C.API_MARSHMALLOW && !C.API_Q
				&& activity.getWindow().hasFeature(Window.FEATURE_NO_TITLE)) {
			// Fix marshmallow bug with hidden action bar and action mode overlay
			if (statusGuardView == null) {
				View decorView = activity.getWindow().getDecorView();
				try {
					Field statusGuardField = decorView.getClass().getDeclaredField("mStatusGuard");
					statusGuardField.setAccessible(true);
					statusGuardView = (View) statusGuardField.get(decorView);
				} catch (Exception e) {
					// Ignore
				}
			}
			if (statusGuardView != null) {
				statusGuardView.post(statusGuardHideRunnable);
			}
		}
		setState(STATE_ACTION_MODE, actionMode);
		if (!actionMode && checkState(STATE_LOCKED) && !isActionBarShowing()) {
			// Restore action bar
			enqueuedShowState = false;
			setShowActionBar(true, true);
		}
	}

	private final Runnable statusGuardHideRunnable = () -> statusGuardView.setVisibility(View.GONE);
}
