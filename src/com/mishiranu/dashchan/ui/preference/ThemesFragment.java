package com.mishiranu.dashchan.ui.preference;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanLocator;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.http.HttpRequest;
import chan.util.CommonUtils;
import chan.util.StringUtils;
import com.mishiranu.dashchan.BuildConfig;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.FileHolder;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.Preference;
import com.mishiranu.dashchan.util.DialogMenu;
import com.mishiranu.dashchan.util.IOUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.Log;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ThemesFragment extends BaseListFragment implements ActivityHandler, AsyncManager.Callback {
	private static final String EXTRA_AVAILABLE_THEMES = "availableThemes";
	private static final String TASK_READ_THEMES = "readThemes";

	private List<JSONObject> availableJsonThemes = Collections.emptyList();

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		((FragmentHandler) requireActivity()).setTitleSubtitle(getString(R.string.themes), null);
		RecyclerView recyclerView = getRecyclerView();
		recyclerView.setAdapter(new Adapter(recyclerView.getContext(), (theme, installed, longClick) -> {
			if (longClick) {
				String json;
				try {
					json = theme.toJsonObject().toString(4);
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
				new ContextMenuDialog(theme.name, json, installed && !theme.builtIn)
						.show(getChildFragmentManager(), ContextMenuDialog.class.getName());
			} else {
				installTheme(theme, installed);
			}
			return true;
		}));

		ArrayList<String> availableThemes = savedInstanceState != null
				? savedInstanceState.getStringArrayList(EXTRA_AVAILABLE_THEMES) : null;
		if (availableThemes == null) {
			AsyncManager.get(this).startTask(TASK_READ_THEMES, this, null, false);
		} else {
			availableJsonThemes = new ArrayList<>();
			for (String string : availableThemes) {
				try {
					availableJsonThemes.add(new JSONObject(string));
				} catch (JSONException e) {
					throw new RuntimeException(e);
				}
			}
		}
		updateThemes();
	}

	@Override
	public void onTerminate() {
		AsyncManager.get(this).cancelTask(TASK_READ_THEMES, this);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		if (availableJsonThemes != null) {
			ArrayList<String> availableThemes = new ArrayList<>();
			for (JSONObject jsonObject : this.availableJsonThemes) {
				availableThemes.add(jsonObject.toString());
			}
			outState.putStringArrayList(EXTRA_AVAILABLE_THEMES, availableThemes);
		}
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_add_theme, 0, R.string.add_theme)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionAddRule))
				.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_add_theme: {
				// Check Android supports "application/json" MIME-type
				String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("json");
				if (StringUtils.isEmpty(mimeType) || "application/octet-stream".equals(mimeType)) {
					mimeType = "*/*";
				}
				// SHOW_ADVANCED to show folder navigation
				Intent intent = new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE)
						.setType(mimeType).putExtra("android.content.extra.SHOW_ADVANCED", true);
				startActivityForResult(intent, C.REQUEST_CODE_ATTACH);
				return true;
			}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK) {
			switch (requestCode) {
				case C.REQUEST_CODE_ATTACH: {
					Uri uri = data.getData();
					FileHolder fileHolder = uri != null ? FileHolder.obtain(requireContext(), uri) : null;
					if (fileHolder != null) {
						ByteArrayOutputStream output = new ByteArrayOutputStream();
						boolean success;
						try (InputStream input = FileHolder.obtain(requireContext(), uri).openInputStream()) {
							IOUtils.copyStream(input, output);
							success = true;
						} catch (IOException e) {
							Log.persistent().stack(e);
							success = false;
						}
						byte[] array = output.toByteArray();
						if (success && array.length > 0) {
							JSONObject jsonObject;
							try {
								jsonObject = new JSONObject(new String(array));
							} catch (JSONException e) {
								jsonObject = null;
							}
							ThemeEngine.Theme theme = jsonObject != null
									? ThemeEngine.parseTheme(requireContext(), jsonObject) : null;
							if (theme != null) {
								installTheme(theme, false);
							} else {
								ToastUtils.show(requireContext(), R.string.invalid_data_format);
							}
						}
					}
					break;
				}
			}
		}
	}

	@Override
	protected DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		return ((Adapter) getRecyclerView().getAdapter()).configureDivider(configuration, position);
	}

	private void updateThemes() {
		ArrayList<ListItem> listItems = new ArrayList<>();
		boolean installedAdded = false;
		for (ThemeEngine.Theme theme : ThemeEngine.getThemes()) {
			if (!theme.builtIn && !installedAdded) {
				listItems.add(new ListItem(null, false, getString(R.string.installed__plural)));
				installedAdded = true;
			}
			listItems.add(new ListItem(theme, true, null));
		}
		ArrayList<ThemeEngine.Theme> availableThemes = new ArrayList<>();
		if (availableJsonThemes != null) {
			for (JSONObject jsonObject : availableJsonThemes) {
				ThemeEngine.Theme theme = ThemeEngine.parseTheme(requireContext(), jsonObject);
				if (theme != null) {
					availableThemes.add(theme);
				}
			}
			Collections.sort(availableThemes);
		}
		if (!availableThemes.isEmpty()) {
			listItems.add(new ListItem(null, false, getString(R.string.available__plural)));
			for (ThemeEngine.Theme theme : availableThemes) {
				listItems.add(new ListItem(theme, false, null));
			}
		}
		Adapter adapter = (Adapter) getRecyclerView().getAdapter();
		adapter.listItems = listItems;
		adapter.notifyDataSetChanged();
	}

	private void installTheme(ThemeEngine.Theme theme, boolean installed) {
		if (!installed) {
			if (ThemeEngine.addTheme(theme)) {
				updateThemes();
			} else {
				ToastUtils.show(requireContext(), R.string.no_access);
				return;
			}
		}
		if (!installed || !theme.name.equals(Preferences.getTheme())) {
			Preferences.setTheme(theme.name);
			requireActivity().recreate();
		}
	}

	private void deleteTheme(String name) {
		if (ThemeEngine.deleteTheme(name)) {
			updateThemes();
			if (name.equals(Preferences.getTheme())) {
				requireActivity().recreate();
			}
		}
	}

	@Override
	public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
		ReadThemesTask task = new ReadThemesTask();
		task.executeOnExecutor(ReadThemesTask.THREAD_POOL_EXECUTOR);
		return task.getHolder();
	}

	@Override
	public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
		List<JSONObject> themes = holder.nextArgument();
		ErrorItem errorItem = holder.nextArgument();
		if (errorItem != null) {
			ToastUtils.show(requireContext(), errorItem);
		} else {
			availableJsonThemes = themes != null ? themes : Collections.emptyList();
			updateThemes();
		}
	}

	@Override
	public void onRequestTaskCancel(String name, Object task) {
		((ReadThemesTask) task).cancel();
	}

	private static class ListItem {
		public final ThemeEngine.Theme theme;
		public final boolean installed;
		public final String title;

		private ListItem(ThemeEngine.Theme theme, boolean installed, String title) {
			this.theme = theme;
			this.installed = installed;
			this.title = title;
		}
	}

	private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
			implements ListViewUtils.ClickCallback<Void, RecyclerView.ViewHolder> {
		private enum ViewType {ITEM, HEADER}

		private interface Callback {
			boolean onThemeClick(ThemeEngine.Theme theme, boolean installed, boolean longClick);
		}

		private static class ItemViewHolder extends RecyclerView.ViewHolder {
			public final Preference.Runtime.IconViewHolder holder;

			public ItemViewHolder(Preference.Runtime.IconViewHolder iconViewHolder) {
				super(iconViewHolder.view);
				ViewUtils.setSelectableItemBackground(itemView);
				this.holder = iconViewHolder;
				iconViewHolder.summary.setVisibility(View.GONE);
			}
		}

		private final Callback callback;
		private final Preference.Runtime<?> iconPreference;

		private List<ListItem> listItems = Collections.emptyList();

		public Adapter(Context context, Callback callback) {
			this.callback = callback;
			iconPreference = new Preference.Runtime<>(context, "", null, "title", p -> null);
		}

		public DividerItemDecoration.Configuration configureDivider
				(DividerItemDecoration.Configuration configuration, int position) {
			ListItem current = listItems.get(position);
			ListItem next = listItems.size() > position + 1 ? listItems.get(position + 1) : null;
			if (C.API_LOLLIPOP) {
				return configuration.need(next != null && next.title != null);
			} else {
				return configuration.need(current.title == null && (next == null || next.title == null));
			}
		}

		@Override
		public int getItemCount() {
			return listItems.size();
		}

		@Override
		public int getItemViewType(int position) {
			return (listItems.get(position).title != null ? ViewType.HEADER : ViewType.ITEM).ordinal();
		}

		@Override
		public boolean onItemClick(RecyclerView.ViewHolder holder, int position, Void nothing, boolean longClick) {
			ListItem listItem = listItems.get(position);
			return callback.onThemeClick(listItem.theme, listItem.installed, longClick);
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (ViewType.values()[viewType]) {
				case ITEM: {
					return ListViewUtils.bind(new ItemViewHolder(iconPreference.createIconViewHolder(parent)),
							true, null, this);
				}
				case HEADER: {
					return new SimpleViewHolder(ViewFactory.makeListTextHeader(parent));
				}
				default: {
					throw new IllegalStateException();
				}
			}
		}

		@Override
		public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
			ListItem listItem = listItems.get(position);
			switch (ViewType.values()[holder.getItemViewType()]) {
				case ITEM: {
					Preference.Runtime.IconViewHolder viewHolder = ((ItemViewHolder) holder).holder;
					viewHolder.icon.setImageDrawable(listItem.theme.createThemeChoiceDrawable());
					viewHolder.title.setText(listItem.theme.name);
					break;
				}
				case HEADER: {
					((TextView) holder.itemView).setText(listItem.title);
					break;
				}
			}
		}
	}

	public static class ContextMenuDialog extends DialogFragment {
		private static final String EXTRA_NAME = "name";
		private static final String EXTRA_JSON = "json";
		private static final String EXTRA_CAN_DELETE = "canDelete";

		public ContextMenuDialog() {}

		public ContextMenuDialog(String name, String json, boolean canDelete) {
			Bundle args = new Bundle();
			args.putString(EXTRA_NAME, name);
			args.putString(EXTRA_JSON, json);
			args.putBoolean(EXTRA_CAN_DELETE, canDelete);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			String name = requireArguments().getString(EXTRA_NAME);
			DialogMenu dialogMenu = new DialogMenu(requireContext());
			dialogMenu.add(R.string.save, () -> {
				DownloadService.Binder binder = ((FragmentHandler) requireActivity()).getDownloadBinder();
				if (binder != null) {
					String json = requireArguments().getString(EXTRA_JSON);
					binder.downloadStorage(new ByteArrayInputStream(json.getBytes()),
							null, null, null, null, name + ".json", false, true);
				}
			});
			if (requireArguments().getBoolean(EXTRA_CAN_DELETE)) {
				dialogMenu.add(R.string.delete, () -> {
					ThemesFragment themesFragment = (ThemesFragment) getParentFragment();
					themesFragment.getView().post(() -> themesFragment
							.deleteTheme(requireArguments().getString(EXTRA_NAME)));
				});
			}
			return dialogMenu.create();
		}
	}

	private static class ReadThemesTask extends AsyncManager.SimpleTask<Void, Void, Boolean> {
		private final HttpHolder holder = new HttpHolder();

		private ArrayList<JSONObject> themes;
		private ErrorItem errorItem;

		@Override
		protected Boolean doInBackground(Void... params) {
			ArrayList<JSONObject> themes = new ArrayList<>();
			try (HttpHolder holder = this.holder) {
				Uri uri = ChanLocator.getDefault().setScheme(Uri.parse(BuildConfig.URI_THEMES));
				int redirects = 0;
				while (redirects++ < 5) {
					JSONObject jsonObject = new HttpRequest(uri, holder).read().getJsonObject();
					if (jsonObject == null) {
						errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
						return false;
					}
					String redirect = CommonUtils.optJsonString(jsonObject, "redirect");
					if (redirect != null) {
						uri = ReadUpdateTask.normalizeUri(Uri.parse(redirect), uri);
						continue;
					}
					JSONArray jsonArray = jsonObject.getJSONArray("themes");
					for (int i = 0; i < jsonArray.length(); i++) {
						themes.add(jsonArray.getJSONObject(i));
					}
					this.themes = themes;
					return true;
				}
				errorItem = new ErrorItem(ErrorItem.Type.EMPTY_RESPONSE);
				return false;
			} catch (HttpException e) {
				errorItem = e.getErrorItemAndHandle();
				holder.disconnect();
				return false;
			} catch (JSONException e) {
				errorItem = new ErrorItem(ErrorItem.Type.INVALID_RESPONSE);
				return false;
			}
		}

		@Override
		protected void onStoreResult(AsyncManager.Holder holder, Boolean result) {
			holder.storeResult(themes, errorItem);
		}

		@Override
		public void cancel() {
			cancel(true);
			holder.interrupt();
		}
	}
}
