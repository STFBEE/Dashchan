package com.mishiranu.dashchan.ui.preference;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import chan.content.ChanConfiguration;
import chan.content.ChanManager;
import chan.util.StringUtils;
import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.UpdaterActivity;
import com.mishiranu.dashchan.content.async.AsyncManager;
import com.mishiranu.dashchan.content.async.ReadUpdateTask;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.ui.ActivityHandler;
import com.mishiranu.dashchan.ui.FragmentHandler;
import com.mishiranu.dashchan.ui.preference.core.CheckPreference;
import com.mishiranu.dashchan.util.AndroidUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ToastUtils;
import com.mishiranu.dashchan.util.ViewUtils;
import com.mishiranu.dashchan.widget.DividerItemDecoration;
import com.mishiranu.dashchan.widget.SimpleViewHolder;
import com.mishiranu.dashchan.widget.ThemeEngine;
import com.mishiranu.dashchan.widget.ViewFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class UpdateFragment extends BaseListFragment implements ActivityHandler, AsyncManager.Callback {
	private static final String VERSION_TITLE_RELEASE = "Release";

	private static final String EXTRA_UPDATE_DATA_MAP = "updateDataMap";
	private static final String EXTRA_UPDATE_DOWNLOAD_ERROR = "updateDownloadError";
	private static final String EXTRA_TARGET_PREFIX = "target_";

	private static final String TASK_READ_UPDATES = "readUpdates";

	private ReadUpdateTask.UpdateDataMap updateDataMap;
	private ErrorItem updateDownloadError;

	private View progressView;

	private static final class ListItem {
		public final String extensionName;
		public final String title;
		public final boolean enabled;
		public final boolean installed;

		public String target;
		public int targetIndex;
		public String warning;

		public static ListItem create(String extensionName, boolean enabled, boolean installed) {
			String title = ChanConfiguration.get(extensionName).getTitle();
			if (title == null) {
				title = extensionName;
			}
			return new ListItem(extensionName, title, enabled, installed);
		}

		public ListItem(String extensionName, String title, boolean enabled, boolean installed) {
			this.extensionName = extensionName;
			this.title = title;
			this.enabled = enabled;
			this.installed = installed;
		}

		public boolean isHeader() {
			return StringUtils.isEmpty(extensionName);
		}

		public boolean willBeInstalled() {
			return !isHeader() && (installed && targetIndex > 0 || !installed && targetIndex >= 0);
		}

		public void setTarget(Context context, List<ReadUpdateTask.UpdateItem> updateItems, int targetIndex) {
			this.targetIndex = targetIndex;
			if (installed && targetIndex > 0 || !installed && targetIndex >= 0) {
				ReadUpdateTask.UpdateItem updateItem = updateItems.get(targetIndex);
				String target = updateItem.title;
				if (context != null) {
					target = context.getString(R.string.__enumeration_format, target, updateItem.name);
					if (updateItem.length > 0) {
						target = context.getString(R.string.__enumeration_format, target,
								StringUtils.formatFileSize(updateItem.length, false));
					}
				}
				this.target = target;
			} else if (targetIndex == 0) {
				target = context != null ? context.getString(R.string.keep_current_version) : null;
			} else {
				target = context != null ? context.getString(R.string.dont_install) : null;
			}
		}
	}

	public UpdateFragment() {}

	public UpdateFragment(ReadUpdateTask.UpdateDataMap updateDataMap) {
		Bundle args = new Bundle();
		args.putParcelable(EXTRA_UPDATE_DATA_MAP, updateDataMap);
		setArguments(args);
	}

	private void updateTitle() {
		int count = 0;
		if (updateDataMap != null) {
			Adapter adapter = (Adapter) getRecyclerView().getAdapter();
			for (ListItem listItem : adapter.listItems) {
				if (listItem.willBeInstalled()) {
					count++;
				}
			}
		}
		((FragmentHandler) requireActivity()).setTitleSubtitle(count <= 0 ? getString(R.string.updates__genitive)
				: ResourceUtils.getColonString(getResources(), R.string.updates__genitive, count), null);
	}

	private boolean isUpdateDataProvided() {
		Bundle args = getArguments();
		return args != null && args.containsKey(EXTRA_UPDATE_DATA_MAP);
	}

	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		FrameLayout content = getContentView();
		ProgressBar progressBar = new ProgressBar(content.getContext());
		ThemeEngine.applyStyle(progressBar);
		content.addView(progressBar, FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
		((FrameLayout.LayoutParams) progressBar.getLayoutParams()).gravity = Gravity.CENTER;
		progressBar.setVisibility(View.GONE);
		progressView = progressBar;
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		progressView = null;
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		setHasOptionsMenu(true);
		if (isUpdateDataProvided()) {
			updateDataMap = requireArguments().getParcelable(EXTRA_UPDATE_DATA_MAP);
		} else {
			updateDataMap = savedInstanceState != null
					? savedInstanceState.getParcelable(EXTRA_UPDATE_DATA_MAP) : null;
			updateDownloadError = savedInstanceState != null
					? savedInstanceState.getParcelable(EXTRA_UPDATE_DOWNLOAD_ERROR) : null;
			if (updateDownloadError != null) {
				setErrorText(updateDownloadError.toString());
			} else if (updateDataMap == null) {
				AsyncManager.get(this).startTask(TASK_READ_UPDATES, this, null, false);
				progressView.setVisibility(View.VISIBLE);
			}
		}
		Adapter adapter = new Adapter(getRecyclerView().getContext(), this::onItemClick);
		getRecyclerView().setAdapter(adapter);
		if (updateDataMap != null) {
			adapter.listItems = buildData(requireContext(), updateDataMap, savedInstanceState);
		}
		updateTitle();
	}

	@Override
	public void onTerminate() {
		AsyncManager.get(this).cancelTask(TASK_READ_UPDATES, this);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);

		RecyclerView recyclerView = getRecyclerView();
		if (recyclerView != null) {
			Adapter adapter = (Adapter) recyclerView.getAdapter();
			for (ListItem listItem : adapter.listItems) {
				outState.putInt(EXTRA_TARGET_PREFIX + listItem.extensionName, listItem.targetIndex);
			}
		}
		if (!isUpdateDataProvided()) {
			outState.putParcelable(EXTRA_UPDATE_DATA_MAP, updateDataMap);
			outState.putParcelable(EXTRA_UPDATE_DOWNLOAD_ERROR, updateDownloadError);
		}
	}

	@Override
	protected DividerItemDecoration.Configuration configureDivider
			(DividerItemDecoration.Configuration configuration, int position) {
		return ((Adapter) getRecyclerView().getAdapter()).configureDivider(configuration, position);
	}

	private static boolean checkVersionValid(ReadUpdateTask.UpdateItem updateItem, int minVersion, int maxVersion) {
		return updateItem.ignoreVersion || updateItem.version >= minVersion && updateItem.version <= maxVersion;
	}

	private static boolean compareForUpdates(ReadUpdateTask.UpdateItem installedUpdateItem,
			ReadUpdateTask.UpdateItem newUpdateItem) {
		return newUpdateItem.code > installedUpdateItem.code ||
				!StringUtils.equals(newUpdateItem.name, installedUpdateItem.name);
	}

	private static ListItem handleAddListItem(Context context, List<ReadUpdateTask.UpdateItem> updateItems,
			String extensionName, Bundle savedInstanceState, int minVersion, int maxVersion,
			boolean installed, String warningUnsupported) {
		ListItem listItem = ListItem.create(extensionName, updateItems.size() >= (installed ? 2 : 1), installed);
		int targetIndex = savedInstanceState != null ? savedInstanceState
				.getInt(EXTRA_TARGET_PREFIX + extensionName, -1) : -1;
		if (targetIndex < 0) {
			if (installed) {
				ReadUpdateTask.UpdateItem installedExtensionData = updateItems.get(0);
				if (checkVersionValid(installedExtensionData, minVersion, maxVersion)) {
					targetIndex = 0;
				}
				for (int i = 1; i < updateItems.size(); i++) {
					ReadUpdateTask.UpdateItem newUpdateItem = updateItems.get(i);
					if (checkVersionValid(newUpdateItem, minVersion, maxVersion)) {
						// targetIndex < 0 - means installed version is not supported
						if (targetIndex < 0 || VERSION_TITLE_RELEASE.equals(newUpdateItem.title)
								&& compareForUpdates(installedExtensionData, newUpdateItem)) {
							targetIndex = i;
							break;
						}
					}
				}
				if (targetIndex < 0) {
					targetIndex = 0;
					listItem.warning = warningUnsupported;
				}
			}
		} else {
			// Restore state
			if (!checkVersionValid(updateItems.get(targetIndex), minVersion, maxVersion)) {
				listItem.warning = warningUnsupported;
			}
		}
		listItem.setTarget(context, updateItems, targetIndex);
		return listItem;
	}

	private static List<ListItem> buildData(Context context,
			ReadUpdateTask.UpdateDataMap updateDataMap, Bundle savedInstanceState) {
		ArrayList<ListItem> listItems = new ArrayList<>();
		String warningUnsupported = context != null ? context.getString(R.string.unsupported_version) : null;
		HashSet<String> handledExtensionNames = new HashSet<>();
		int minVersion;
		int maxVersion;
		{
			List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(ChanManager.EXTENSION_NAME_CLIENT, true);
			ListItem listItem = new ListItem(ChanManager.EXTENSION_NAME_CLIENT,
					context != null ? AndroidUtils.getApplicationLabel(context) : null,
					updateItems.size() >= 2, true);
			int targetIndex = savedInstanceState != null ? savedInstanceState.getInt(EXTRA_TARGET_PREFIX
					+ listItem.extensionName, -1) : -1;
			if (targetIndex < 0) {
				targetIndex = 0;
				for (int i = 1; i < updateItems.size(); i++) {
					ReadUpdateTask.UpdateItem newUpdateItem = updateItems.get(i);
					if (VERSION_TITLE_RELEASE.equals(newUpdateItem.title) &&
							compareForUpdates(updateItems.get(0), newUpdateItem)) {
						targetIndex = 1;
						break;
					}
				}
			}
			listItem.setTarget(context, updateItems, targetIndex);
			ReadUpdateTask.UpdateItem currentAppUpdateItem = updateItems.get(targetIndex);
			minVersion = currentAppUpdateItem.minVersion;
			maxVersion = currentAppUpdateItem.version;
			listItems.add(listItem);
		}
		handledExtensionNames.add(ChanManager.EXTENSION_NAME_CLIENT);
		ChanManager manager = ChanManager.getInstance();
		for (String chanName : manager.getAvailableChanNames()) {
			List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(chanName, true);
			if (updateItems != null) {
				ListItem listItem = handleAddListItem(context, updateItems, chanName,
						savedInstanceState, minVersion, maxVersion, true, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(chanName);
			}
		}
		for (ChanManager.ExtensionItem extensionItem : manager.getExtensionItems()) {
			if (extensionItem.type == ChanManager.ExtensionItem.Type.LIBRARY) {
				List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionItem.extensionName, true);
				if (updateItems != null) {
					ListItem listItem = handleAddListItem(context, updateItems, extensionItem.extensionName,
							savedInstanceState, minVersion, maxVersion, true, warningUnsupported);
					listItems.add(listItem);
					handledExtensionNames.add(extensionItem.extensionName);
				}
			}
		}
		List<String> updateExtensionNames = new ArrayList<>(updateDataMap.extensionNames(true));
		Collections.sort(updateExtensionNames);
		for (String extensionName : updateExtensionNames) {
			if (!handledExtensionNames.contains(extensionName)) {
				List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionName, true);
				ListItem listItem = handleAddListItem(context, updateItems, extensionName, savedInstanceState,
						minVersion, maxVersion, true, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(extensionName);
			}
		}
		List<String> installExtensionNames = new ArrayList<>(updateDataMap.extensionNames(false));
		Collections.sort(installExtensionNames);
		boolean headerAdded = false;
		for (String extensionName : installExtensionNames) {
			if (!handledExtensionNames.contains(extensionName)) {
				if (!headerAdded) {
					if (context != null) {
						listItems.add(new ListItem("", context.getString(R.string.available__plural), false, false));
					}
					headerAdded = true;
				}
				List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionName, false);
				ListItem listItem = handleAddListItem(context, updateItems, extensionName, savedInstanceState,
						minVersion, maxVersion, false, warningUnsupported);
				listItems.add(listItem);
				handledExtensionNames.add(extensionName);
			}
		}
		return listItems;
	}

	private void onItemClick(ListItem listItem) {
		ArrayList<String> targets = new ArrayList<>();
		ArrayList<String> repositories = new ArrayList<>();
		int targetIndex;
		List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(listItem.extensionName, listItem.installed);
		if (listItem.installed) {
			targets.add(getString(R.string.keep_current_version));
			repositories.add(null);
			for (ReadUpdateTask.UpdateItem updateItem : updateItems.subList(1, updateItems.size())) {
				targets.add(updateItem.title);
				repositories.add(updateItem.repository);
			}
			targetIndex = listItem.targetIndex;
		} else {
			targets.add(getString(R.string.dont_install));
			repositories.add(null);
			for (ReadUpdateTask.UpdateItem updateItem : updateItems) {
				targets.add(updateItem.title);
				repositories.add(updateItem.repository);
			}
			targetIndex = listItem.targetIndex + 1;
		}
		TargetDialog dialog = new TargetDialog(listItem.extensionName, listItem.title,
				targets, repositories, targetIndex);
		dialog.show(getChildFragmentManager(), TargetDialog.TAG);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
		menu.add(0, R.id.menu_download, 0, R.string.download_files)
				.setIcon(((FragmentHandler) requireActivity()).getActionBarIcon(R.attr.iconActionDownload))
				.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		menu.add(0, R.id.menu_check_on_start, 0, R.string.check_on_startup).setCheckable(true);
	}

	@Override
	public void onPrepareOptionsMenu(@NonNull Menu menu) {
		long length = 0;
		RecyclerView recyclerView = getRecyclerView();
		if (updateDataMap != null && recyclerView != null) {
			Adapter adapter = (Adapter) recyclerView.getAdapter();
			for (ListItem listItem : adapter.listItems) {
				if (listItem.willBeInstalled()) {
					length += updateDataMap.get(listItem.extensionName, listItem.installed)
							.get(listItem.targetIndex).length;
				}
			}
		}
		String downloadTitle = getString(R.string.download_files);
		if (length > 0) {
			downloadTitle = getString(R.string.__enumeration_format, downloadTitle,
					StringUtils.formatFileSize(length, false));
		}
		menu.findItem(R.id.menu_download).setTitle(downloadTitle);
		menu.findItem(R.id.menu_check_on_start).setChecked(Preferences.isCheckUpdatesOnStart());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_download: {
				ArrayList<UpdaterActivity.Request> requests = new ArrayList<>();
				if (updateDataMap != null) {
					Adapter adapter = (Adapter) getRecyclerView().getAdapter();
					for (ListItem listItem : adapter.listItems) {
						if (listItem.willBeInstalled()) {
							ReadUpdateTask.UpdateItem updateItem = updateDataMap
									.get(listItem.extensionName, listItem.installed).get(listItem.targetIndex);
							if (updateItem.source != null) {
								requests.add(new UpdaterActivity.Request(listItem.extensionName,
										updateItem.name, Uri.parse(updateItem.source)));
							}
						}
					}
				}
				if (!requests.isEmpty()) {
					MessageDialog.create(this, getString(R.string.update_reminder__sentence), true);
					UpdaterActivity.startUpdater(requests);
				} else {
					ToastUtils.show(requireContext(), R.string.no_available_updates);
				}
				return true;
			}
			case R.id.menu_check_on_start: {
				Preferences.setCheckUpdatesOnStart(!item.isChecked());
				break;
			}
		}
		return false;
	}

	private static void handleListItemValidity(ReadUpdateTask.UpdateDataMap updateDataMap,
			ListItem listItem, int minVersion, int maxVersion, String warningUnsupported) {
		boolean valid = true;
		if (listItem.targetIndex >= 0) {
			ReadUpdateTask.UpdateItem updateItem = updateDataMap
					.get(listItem.extensionName, listItem.installed).get(listItem.targetIndex);
			valid = checkVersionValid(updateItem, minVersion, maxVersion);
		}
		listItem.warning = valid ? null : warningUnsupported;
	}

	private static void onTargetChanged(Context context, Adapter adapter,
			ReadUpdateTask.UpdateDataMap updateDataMap, ListItem listItem) {
		String warningUnsupported = context.getString(R.string.unsupported_version);
		ListItem applicationListItem = adapter.listItems.get(0);
		if (!ChanManager.EXTENSION_NAME_CLIENT.equals(applicationListItem.extensionName)) {
			throw new IllegalStateException();
		}
		ReadUpdateTask.UpdateItem applicationUpdateItem = updateDataMap
				.get(ChanManager.EXTENSION_NAME_CLIENT, true).get(applicationListItem.targetIndex);
		int minVersion = applicationUpdateItem.minVersion;
		int maxVersion = applicationUpdateItem.version;
		if (ChanManager.EXTENSION_NAME_CLIENT.equals(listItem.extensionName)) {
			for (ListItem invalidateListItem : adapter.listItems) {
				if (!invalidateListItem.isHeader()) {
					handleListItemValidity(updateDataMap, invalidateListItem,
							minVersion, maxVersion, warningUnsupported);
				}
			}
		} else {
			handleListItemValidity(updateDataMap, listItem, minVersion, maxVersion, warningUnsupported);
		}
	}

	private void onTargetSelected(String extensionName, int targetIndex) {
		Adapter adapter = (Adapter) getRecyclerView().getAdapter();
		for (int i = 0; i < adapter.listItems.size(); i++) {
			ListItem listItem = adapter.listItems.get(i);
			if (extensionName.equals(listItem.extensionName)) {
				List<ReadUpdateTask.UpdateItem> updateItems = updateDataMap.get(extensionName, listItem.installed);
				if (!listItem.installed) {
					targetIndex--;
				}
				if (listItem.targetIndex != targetIndex) {
					listItem.setTarget(requireContext(), updateItems, targetIndex);
					onTargetChanged(requireContext(), adapter, updateDataMap, listItem);
					adapter.notifyDataSetChanged();
					requireActivity().invalidateOptionsMenu();
					updateTitle();
				}
				break;
			}
		}
	}

	private static class ReadUpdateHolder extends AsyncManager.Holder implements ReadUpdateTask.Callback {
		@Override
		public void onReadUpdateComplete(ReadUpdateTask.UpdateDataMap updateDataMap, ErrorItem errorItem) {
			storeResult(updateDataMap, errorItem);
		}
	}

	@Override
	public AsyncManager.Holder onCreateAndExecuteTask(String name, HashMap<String, Object> extra) {
		ReadUpdateHolder holder = new ReadUpdateHolder();
		ReadUpdateTask task = new ReadUpdateTask(requireContext(), holder);
		task.executeOnExecutor(ReadUpdateTask.THREAD_POOL_EXECUTOR);
		return holder.attach(task);
	}

	@Override
	public void onFinishTaskExecution(String name, AsyncManager.Holder holder) {
		ReadUpdateTask.UpdateDataMap updateDataMap = holder.nextArgument();
		ErrorItem errorItem = holder.nextArgument();
		progressView.setVisibility(View.GONE);
		if (updateDataMap != null) {
			this.updateDataMap = updateDataMap;
			Adapter adapter = (Adapter) getRecyclerView().getAdapter();
			adapter.listItems = buildData(requireContext(), updateDataMap, null);
			adapter.notifyDataSetChanged();
			updateTitle();
		} else {
			if (errorItem == null) {
				errorItem = new ErrorItem(ErrorItem.Type.UNKNOWN);
			}
			updateDownloadError = errorItem;
			setErrorText(errorItem.toString());
		}
	}

	@Override
	public void onRequestTaskCancel(String name, Object task) {
		((ReadUpdateTask) task).cancel();
	}

	private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
		private enum ViewType {ITEM, HEADER}

		private interface Callback extends ListViewUtils.ClickCallback<ListItem, RecyclerView.ViewHolder> {
			void onItemClick(ListItem listItem);

			@Override
			default boolean onItemClick(RecyclerView.ViewHolder holder,
					int position, ListItem listItem, boolean longClick) {
				onItemClick(listItem);
				return true;
			}
		}

		private final Callback callback;
		private final CheckPreference checkPreference;

		public List<ListItem> listItems = Collections.emptyList();

		public Adapter(Context context, Callback callback) {
			this.callback = callback;
			checkPreference = new CheckPreference(context, "", false, "title", "summary");
		}

		public DividerItemDecoration.Configuration configureDivider
				(DividerItemDecoration.Configuration configuration, int position) {
			ListItem current = listItems.get(position);
			ListItem next = listItems.size() > position + 1 ? listItems.get(position + 1) : null;
			return configuration.need(!current.isHeader() && (next == null || !next.isHeader() || C.API_LOLLIPOP));
		}

		@Override
		public int getItemCount() {
			return listItems.size();
		}

		@Override
		public int getItemViewType(int position) {
			return (listItems.get(position).isHeader() ? ViewType.HEADER : ViewType.ITEM).ordinal();
		}

		@NonNull
		@Override
		public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			switch (ViewType.values()[viewType]) {
				case ITEM: {
					CheckPreference.CheckViewHolder viewHolder = checkPreference.createViewHolder(parent);
					ViewUtils.setSelectableItemBackground(viewHolder.view);
					viewHolder.view.setTag(viewHolder);
					return ListViewUtils.bind(new SimpleViewHolder(viewHolder.view), false, listItems::get, callback);
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
					CheckPreference.CheckViewHolder viewHolder = (CheckPreference.CheckViewHolder)
							holder.itemView.getTag();
					checkPreference.setValue(listItem.willBeInstalled());
					checkPreference.setEnabled(listItem.enabled);
					checkPreference.bindViewHolder(viewHolder);
					viewHolder.title.setText(listItem.title);
					if (listItem.warning != null) {
						SpannableString spannable = new SpannableString(listItem.target + "\n" + listItem.warning);
						int length = spannable.length();
						spannable.setSpan(new ForegroundColorSpan(ResourceUtils.getColor(holder.itemView.getContext(),
								R.attr.colorTextError)), length - listItem.warning.length(), length,
								SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
						viewHolder.summary.setText(spannable);
					} else {
						viewHolder.summary.setText(listItem.target);
					}
					break;
				}
				case HEADER: {
					((TextView) holder.itemView).setText(listItem.title);
					break;
				}
			}
		}
	}

	public static class TargetDialog extends DialogFragment implements DialogInterface.OnClickListener {
		private static final String TAG = TargetDialog.class.getName();

		private static final String EXTRA_EXTENSION_NAME = "extensionName";
		private static final String EXTRA_TITLE = "title";
		private static final String EXTRA_TARGETS = "targets";
		private static final String EXTRA_REPOSITORIES = "repositories";
		private static final String EXTRA_INDEX = "index";

		public TargetDialog() {}

		public TargetDialog(String extensionName, String title, ArrayList<String> targets,
				ArrayList<String> repositories, int index) {
			Bundle args = new Bundle();
			args.putString(EXTRA_EXTENSION_NAME, extensionName);
			args.putString(EXTRA_TITLE, title);
			args.putStringArrayList(EXTRA_TARGETS, targets);
			args.putStringArrayList(EXTRA_REPOSITORIES, repositories);
			args.putInt(EXTRA_INDEX, index);
			setArguments(args);
		}

		@NonNull
		@Override
		public AlertDialog onCreateDialog(Bundle savedInstanceState) {
			int index = requireArguments().getInt(EXTRA_INDEX);
			List<String> targets = requireArguments().getStringArrayList(EXTRA_TARGETS);
			List<String> repositories = requireArguments().getStringArrayList(EXTRA_REPOSITORIES);
			CharSequence[] titles = new CharSequence[targets.size()];
			FrameLayout referenceParent = new FrameLayout(requireContext());
			TextView referenceSubtitle = ((ViewFactory.TwoLinesViewHolder)
					ViewFactory.makeTwoLinesListItem(referenceParent).getTag()).text2;
			for (int i = 0; i < titles.length; i++) {
				String target = targets.get(i);
				String repository = repositories.get(i);
				if (StringUtils.isEmpty(repository)) {
					titles[i] = target;
				} else {
					SpannableStringBuilder builder = new SpannableStringBuilder(target);
					builder.append('\n');
					builder.append(repository);
					int from = builder.length() - repository.length();
					int to = builder.length();
					builder.setSpan(new ForegroundColorSpan(referenceSubtitle.getTextColors().getDefaultColor()),
							from, to, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.setSpan(new AbsoluteSizeSpan((int) (referenceSubtitle.getTextSize() + 0.5f)),
							from, to, SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					titles[i] = builder;
				}
			}
			return new AlertDialog.Builder(requireContext())
					.setTitle(requireArguments().getString(EXTRA_TITLE))
					.setSingleChoiceItems(titles, index, this)
					.setNegativeButton(android.R.string.cancel, null).create();
		}

		@Override
		public void onClick(DialogInterface dialog, int which) {
			dismiss();
			((UpdateFragment) getParentFragment())
					.onTargetSelected(requireArguments().getString(EXTRA_EXTENSION_NAME), which);
		}
	}

	public static int checkNewVersions(ReadUpdateTask.UpdateDataMap updateDataMap) {
		int count = 0;
		List<ListItem> listItems = buildData(null, updateDataMap, null);
		for (ListItem listItem : listItems) {
			if (listItem.willBeInstalled()) {
				count++;
			}
		}
		return count;
	}
}
