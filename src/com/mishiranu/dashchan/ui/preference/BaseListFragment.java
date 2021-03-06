package com.mishiranu.dashchan.ui.preference;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;

public abstract class BaseListFragment extends Fragment {
	private RecyclerView recyclerView;
	private View emptyView;
	private TextView emptyText;

	@Override
	public final View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		FrameLayout view = new FrameLayout(container.getContext());
		view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT));
		recyclerView = new PaddedRecyclerView(view.getContext());
		recyclerView.setId(android.R.id.list);
		recyclerView.setMotionEventSplittingEnabled(false);
		recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
		recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), this::configureDivider));
		view.addView(recyclerView, 0, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT));
		inflater.inflate(R.layout.widget_error, view);
		emptyView = view.findViewById(R.id.error);
		emptyText = view.findViewById(R.id.error_text);
		emptyView.setVisibility(View.GONE);
		if (!C.API_LOLLIPOP) {
			float density = ResourceUtils.obtainDensity(recyclerView);
			ViewUtils.setNewPadding(recyclerView, (int) (16f * density), null, (int) (16f * density), null);
		}
		return view;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		recyclerView = null;
		emptyView = null;
		emptyText = null;
	}

	public RecyclerView getRecyclerView() {
		return recyclerView;
	}

	public FrameLayout getContentView() {
		return (FrameLayout) getView();
	}

	public void setErrorText(CharSequence text) {
		emptyText.setText(text);
		emptyView.setVisibility(StringUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
	}

	protected DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		return configuration.need(true);
	}
}
