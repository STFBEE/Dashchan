package chan.http;

import android.net.Uri;
import android.util.Pair;
import chan.annotation.Public;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;

@Public
public final class HttpRequest {
	@Public
	public interface Preset {}

	public interface HolderPreset extends Preset {
		HttpHolder getHolder();
	}

	public interface TimeoutsPreset extends Preset {
		int getConnectTimeout();
		int getReadTimeout();
	}

	public interface InputListenerPreset extends Preset {
		HttpHolder.InputListener getInputListener();
	}

	public interface OutputListenerPreset extends Preset {
		OutputListener getOutputListener();
	}

	public interface OutputStreamPreset extends Preset {
		OutputStream getOutputStream();
	}

	public interface OutputListener {
		void onOutputProgressChange(long progress, long progressMax);
	}

	@Public
	public interface RedirectHandler {
		@Public
		enum Action {
			@Public CANCEL,
			@Public GET,
			@Public RETRANSMIT;

			private Uri redirectedUri;

			@Public
			public Action setRedirectedUri(Uri redirectedUri) {
				this.redirectedUri = redirectedUri;
				return this;
			}

			public Uri getRedirectedUri() {
				return redirectedUri;
			}

			private void reset() {
				redirectedUri = null;
			}

			public static void resetAll() {
				CANCEL.reset();
				GET.reset();
				RETRANSMIT.reset();
			}
		}

		@Public
		Action onRedirectReached(int responseCode, Uri requestedUri, Uri redirectedUri, HttpHolder holder)
				throws HttpException;

		@Public
		RedirectHandler NONE = (responseCode, requestedUri, redirectedUri, holder) -> Action.CANCEL;

		@Public
		RedirectHandler BROWSER = (responseCode, requestedUri, redirectedUri, holder) -> Action.GET;

		@Public
		RedirectHandler STRICT = (responseCode, requestedUri, redirectedUri, holder) -> {
			switch (responseCode) {
				case HttpURLConnection.HTTP_MOVED_PERM:
				case HttpURLConnection.HTTP_MOVED_TEMP: {
					return Action.RETRANSMIT;
				}
				default: {
					return Action.GET;
				}
			}
		};
	}

	final HttpHolder holder;
	final Uri uri;

	static final int REQUEST_METHOD_GET = 0;
	static final int REQUEST_METHOD_HEAD = 1;
	static final int REQUEST_METHOD_POST = 2;
	static final int REQUEST_METHOD_PUT = 3;
	static final int REQUEST_METHOD_DELETE = 4;

	int requestMethod = REQUEST_METHOD_GET;
	RequestEntity requestEntity;

	boolean successOnly = true;
	RedirectHandler redirectHandler = RedirectHandler.BROWSER;
	HttpValidator validator;
	boolean keepAlive = true;

	HttpHolder.InputListener inputListener;
	OutputListener outputListener;
	OutputStream outputStream;

	int connectTimeout = 15000;
	int readTimeout = 15000;
	int delay = 0;

	ArrayList<Pair<String, String>> headers;
	CookieBuilder cookieBuilder;

	boolean checkRelayBlock = true;

	// TODO CHAN
	// Make this constructor private after updating
	// allchan alphachan anonfm archiverbt arhivach chuckdfwk desustorage diochan exach fiftyfive fourchan fourplebs
	// kropyvach kurisach nulltirech owlchan ponyach ponychan princessluna randomarchive sevenchan shanachan sharechan
	// synch tiretirech tumbach uboachan wizardchan
	// Added: 26.09.20 20:10
	public HttpRequest(Uri uri, HttpHolder holder, Preset preset) {
		if (holder == null && preset instanceof HolderPreset) {
			holder = ((HolderPreset) preset).getHolder();
		}
		if (holder == null) {
			throw new IllegalArgumentException("No HttpHolder provided");
		}
		this.uri = uri;
		this.holder = holder;
		if (preset instanceof TimeoutsPreset) {
			setTimeouts(((TimeoutsPreset) preset).getConnectTimeout(), ((TimeoutsPreset) preset).getReadTimeout());
		}
		if (preset instanceof OutputListenerPreset) {
			setOutputListener(((OutputListenerPreset) preset).getOutputListener());
		}
		if (preset instanceof InputListenerPreset) {
			setInputListener(((InputListenerPreset) preset).getInputListener());
		}
		if (preset instanceof OutputStreamPreset) {
			setOutputStream(((OutputStreamPreset) preset).getOutputStream());
		}
	}

	@Public
	public HttpRequest(Uri uri, HttpHolder holder) {
		this(uri, holder, null);
	}

	@Public
	public HttpRequest(Uri uri, Preset preset) {
		this(uri, null, preset);
	}

	private HttpRequest setMethod(int method, RequestEntity entity) {
		requestMethod = method;
		requestEntity = entity;
		return this;
	}

	@Public
	public HttpRequest setGetMethod() {
		return setMethod(REQUEST_METHOD_GET, null);
	}

	@Public
	public HttpRequest setHeadMethod() {
		return setMethod(REQUEST_METHOD_HEAD, null);
	}

	@Public
	public HttpRequest setPostMethod(RequestEntity entity) {
		return setMethod(REQUEST_METHOD_POST, entity);
	}

	@Public
	public HttpRequest setPutMethod(RequestEntity entity) {
		return setMethod(REQUEST_METHOD_PUT, entity);
	}

	@Public
	public HttpRequest setDeleteMethod(RequestEntity entity) {
		return setMethod(REQUEST_METHOD_DELETE, entity);
	}

	@Public
	public HttpRequest setSuccessOnly(boolean successOnly) {
		this.successOnly = successOnly;
		return this;
	}

	@Public
	public HttpRequest setRedirectHandler(RedirectHandler redirectHandler) {
		if (redirectHandler == null) {
			throw new NullPointerException();
		}
		this.redirectHandler = redirectHandler;
		return this;
	}

	@Public
	public HttpRequest setValidator(HttpValidator validator) {
		this.validator = validator;
		return this;
	}

	@Public
	public HttpRequest setKeepAlive(boolean keepAlive) {
		this.keepAlive = keepAlive;
		return this;
	}

	@Public
	public HttpRequest setTimeouts(int connectTimeout, int readTimeout) {
		if (connectTimeout >= 0) {
			this.connectTimeout = connectTimeout;
		}
		if (readTimeout >= 0) {
			this.readTimeout = readTimeout;
		}
		return this;
	}

	@Public
	public HttpRequest setDelay(int delay) {
		this.delay = delay;
		return this;
	}

	public HttpRequest setInputListener(HttpHolder.InputListener listener) {
		inputListener = listener;
		return this;
	}

	public HttpRequest setOutputListener(OutputListener listener) {
		outputListener = listener;
		return this;
	}

	@Public
	public HttpRequest setOutputStream(OutputStream outputStream) {
		this.outputStream = outputStream;
		return this;
	}

	private HttpRequest addHeader(Pair<String, String> header) {
		if (header != null && header.first != null && header.second != null) {
			if (headers == null) {
				headers = new ArrayList<>();
			}
			headers.add(header);
		}
		return this;
	}

	@Public
	public HttpRequest addHeader(String name, String value) {
		return addHeader(new Pair<>(name, value));
	}

	@Public
	public HttpRequest clearHeaders() {
		headers = null;
		return this;
	}

	@Public
	public HttpRequest addCookie(String name, String value) {
		if (name != null && value != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(name, value);
		}
		return this;
	}

	@Public
	public HttpRequest addCookie(String cookie) {
		if (cookie != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(cookie);
		}
		return this;
	}

	@Public
	public HttpRequest addCookie(CookieBuilder builder) {
		if (builder != null) {
			if (cookieBuilder == null) {
				cookieBuilder = new CookieBuilder();
			}
			cookieBuilder.append(builder);
		}
		return this;
	}

	@Public
	public HttpRequest clearCookies() {
		cookieBuilder = null;
		return this;
	}

	public HttpRequest setCheckRelayBlock(boolean checkRelayBlock) {
		this.checkRelayBlock = checkRelayBlock;
		return this;
	}

	@Public
	public HttpRequest copy() {
		HttpRequest request = new HttpRequest(uri, holder);
		request.setMethod(requestMethod, requestEntity);
		request.setSuccessOnly(successOnly);
		request.setRedirectHandler(redirectHandler);
		request.setValidator(validator);
		request.setKeepAlive(keepAlive);
		request.setInputListener(inputListener);
		request.setOutputListener(outputListener);
		request.setOutputStream(outputStream);
		request.setTimeouts(connectTimeout, readTimeout);
		request.setDelay(delay);
		if (headers != null) {
			request.headers = new ArrayList<>(headers);
		}
		request.addCookie(cookieBuilder);
		request.setCheckRelayBlock(checkRelayBlock);
		return request;
	}

	@Public
	public HttpHolder execute() throws HttpException {
		try {
			HttpClient.getInstance().execute(this);
			return holder;
		} catch (HttpException e) {
			holder.disconnect();
			throw e;
		}
	}

	@Public
	public HttpResponse read() throws HttpException {
		execute();
		try {
			if (requestMethod == REQUEST_METHOD_HEAD) {
				return null;
			}
			return holder.read();
		} catch (HttpException e) {
			holder.disconnect();
			throw e;
		}
	}
}
