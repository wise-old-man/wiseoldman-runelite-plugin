package net.wiseoldman.web;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Set;

import java.util.concurrent.TimeUnit;
import net.runelite.api.WorldType;
import net.runelite.client.RuneLiteProperties;
import net.wiseoldman.WomUtilsPlugin;
import net.wiseoldman.beans.GroupErrorCode;
import net.wiseoldman.beans.GroupInfoWithMemberships;
import net.wiseoldman.beans.NameChangeEntry;
import net.wiseoldman.beans.ParticipantWithStanding;
import net.wiseoldman.beans.RoleIndex;
import net.wiseoldman.beans.WomStatus;
import net.wiseoldman.beans.ParticipantWithCompetition;
import net.wiseoldman.beans.GroupMemberAddition;
import net.wiseoldman.beans.Member;
import net.wiseoldman.beans.PlayerInfo;
import net.wiseoldman.beans.WomPlayerUpdate;
import net.wiseoldman.events.WomOngoingPlayerCompetitionsFetched;
import net.wiseoldman.events.WomRequestFailed;
import net.wiseoldman.events.WomUpcomingPlayerCompetitionsFetched;
import net.wiseoldman.WomUtilsConfig;
import net.wiseoldman.events.WomGroupSynced;
import java.awt.Color;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MessageNode;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.EventBus;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class WomClient
{
	private OkHttpClient okHttpClient;

	private Gson gson;

	@Inject
	private Client client;

	@Inject
	private WomUtilsConfig config;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	private static final Color SUCCESS = new Color(170, 255, 40);
	public final Color ERROR = new Color(204, 66, 66);

	private static final DecimalFormat NUMBER_FORMAT = new DecimalFormat("#.##");

	private final WomUtilsPlugin plugin;
	private final String leagueError = " You are currently in a League world. Your group configurations might be for the main game.";

	private final String userAgent;

	private ArrayList<Member> clanMembers;
	private Set<RoleIndex> roleOrders;
	public boolean isSyncing = false;

	@Inject
	public WomClient(Gson gson, WomUtilsPlugin plugin, Client client, OkHttpClient okHttpClient)
	{
		this.gson = gson.newBuilder()
			.setDateFormat(DateFormat.FULL, DateFormat.FULL)
			.create();

		this.plugin = plugin;
		this.client = client;

		this.okHttpClient = okHttpClient.newBuilder()
			.connectTimeout(30, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(30, TimeUnit.SECONDS)
			.build();

		String pluginVersion = WomUtilsPlugin.getPluginVersion();
		String runeliteVersion = RuneLiteProperties.getVersion();

		userAgent = "WiseOldManRuneLitePlugin/" + pluginVersion + " " +
			"RuneLite/" + runeliteVersion;
	}

	public void submitNameChanges(NameChangeEntry[] changes)
	{
		Request request = createRequest(changes, HttpMethod.POST, "names", "bulk");
		sendRequest(request);
		log.info("Submitted {} name changes to WOM", changes.length);
	}

	void sendRequest(Request request)
	{
		sendRequest(request, r -> {
		});
	}

	void sendRequest(Request request, Consumer<Response> consumer)
	{
		sendRequest(request, new WomCallback(consumer));
	}

	void sendRequest(Request request, Consumer<Response> consumer, Consumer<Exception> exceptionConsumer)
	{
		sendRequest(request, new WomCallback(consumer, exceptionConsumer));
	}

	void sendRequest(Request request, Callback callback)
	{
		okHttpClient.newCall(request).enqueue(callback);
	}

	private Request createRequest(Object payload, String... pathSegments)
	{
		return createRequest(payload, HttpMethod.POST, pathSegments);
	}

	private Request createRequest(Object payload, HttpMethod httpMethod, String... pathSegments)
	{
		HttpUrl url = buildUrl(pathSegments);
		RequestBody body = RequestBody.create(
			MediaType.parse("application/json; charset=utf-8"),
			gson.toJson(payload)
		);

		Request.Builder requestBuilder = new Request.Builder()
			.header("User-Agent", userAgent)
			.url(url);

		if (httpMethod == HttpMethod.PUT)
		{
			return requestBuilder.put(body).build();
		}
		else if (httpMethod == HttpMethod.DELETE)
		{
			return requestBuilder.delete(body).build();
		}


		return requestBuilder.post(body).build();
	}

	private Request createRequest(String... pathSegments)
	{
		HttpUrl url = buildUrl(pathSegments);
		return new Request.Builder()
			.header("User-Agent", userAgent)
			.url(url)
			.build();
	}

	private HttpUrl buildUrl(String[] pathSegments)
	{
		HttpUrl.Builder urlBuilder = new HttpUrl.Builder()
			.scheme("https")
			.host("api.wiseoldman.net")
			.addPathSegment(this.plugin.worldType.contains(WorldType.SEASONAL) ? "league" : "v2");

		for (String pathSegment : pathSegments)
		{
			if (pathSegment.startsWith("?"))
			{
				// A query param
				String[] kv = pathSegment.substring(1).split("=");
				urlBuilder.addQueryParameter(kv[0], kv[1]);
			}
			else
			{
				urlBuilder.addPathSegment(pathSegment);
			}
		}


		return urlBuilder.build();
	}

	public void importGroupMembers()
	{
		if (config.groupId() > 0)
		{
			Request request = createRequest("groups", "" + config.groupId());
			sendRequest(request, this::importMembersCallback);
		}
	}

	private void importMembersCallback(Response response)
	{
		if (!response.isSuccessful())
		{
			return;
		}

		GroupInfoWithMemberships groupInfo = parseResponse(response, GroupInfoWithMemberships.class);
		postEvent(new WomGroupSynced(groupInfo, true));
	}

	private void syncClanMembersCallBack(Response response)
	{
		final String message;

		if (response.isSuccessful())
		{
			GroupInfoWithMemberships data = parseResponse(response, GroupInfoWithMemberships.class);
			postEvent(new WomGroupSynced(data));
			this.isSyncing = false;
		}
		else if (response.code() == 429)
		{
			log.error("wom-utils: reached api limits while syncing clan members");
			this.isSyncing = false;
		}
		else if (response.code() == 403)
		{
			WomStatus data = parseResponse(response, WomStatus.class);

			if (data.getCode() == GroupErrorCode.OPTED_OUT_MEMBERS_FOUND)
			{
				String[] optedOutPlayers = Arrays.stream(data.getData()).map(String::toLowerCase).toArray(String[]::new);
				boolean didRemove = this.clanMembers.removeIf(member -> Arrays.asList(optedOutPlayers).contains(member.getUsername().toLowerCase()));

				// If no players were removed, don't send request so we don't end up in an endless loop
				if (!didRemove)
				{
					return;
				}

				GroupMemberAddition payload = new GroupMemberAddition(config.verificationCode(), this.clanMembers, this.roleOrders);
				Request request = createRequest(payload, HttpMethod.PUT, "groups", "" + config.groupId());
				sendRequest(request, this::syncClanMembersCallBack, this::handleSyncClanMembersException);
			}
			else if (data.getCode() == GroupErrorCode.INCORRECT_VERIFICATION_CODE)
			{
				sendResponseToChat("Incorrect group verification code.", ERROR);
				this.isSyncing = false;
			}
		}
		else
		{
			WomStatus data = parseResponse(response, WomStatus.class);
			log.error("Unhandled error while syncing wom group {}", data.getCode());
			message = "Error: " + data.getMessage() + (this.plugin.worldType.contains(WorldType.SEASONAL) ? leagueError : "");
			sendResponseToChat(message, ERROR);
			this.isSyncing = false;
		}
		plugin.syncButton.setEnabled(this.isSyncing);
	}

	public void handleSyncClanMembersException(Exception e)
	{
		this.isSyncing = false;
		this.plugin.syncButton.setEnabled(false);

		if (e instanceof java.net.SocketTimeoutException)
		{
			sendResponseToChat("Failed to sync clan members due to a timeout. Please try again later.", ERROR);
			log.warn("Sync group members request timed out.");
		}
		else
		{
			log.warn("An unexpected error occurred during group sync request.", e);
		}
	}

	private void playerOngoingCompetitionsCallback(String username, Response response)
	{
		boolean showRetry = true;
		if (response.isSuccessful())
		{
			ParticipantWithStanding[] comps = parseResponse(response, ParticipantWithStanding[].class);
			postEvent(new WomOngoingPlayerCompetitionsFetched(username, comps));
			showRetry = false;
		}
		else if (response.code() == 429)
		{
			log.error("wom-utils: reached api limits while fetching ongoing competitions");
		}
		else
		{
			WomStatus data = parseResponse(response, WomStatus.class);
			String message = "Error: " + data.getMessage();
			sendResponseToChat(message, ERROR);
		}

		if (showRetry)
		{
			eventBus.post(new WomRequestFailed(username, WomRequestType.COMPETITIONS_ONGOING));
		}
	}

	private void playerUpcomingCompetitionsCallback(String username, Response response)
	{
		boolean showRetry = true;
		if (response.isSuccessful())
		{
			ParticipantWithCompetition[] comps = parseResponse(response, ParticipantWithCompetition[].class);
			postEvent(new WomUpcomingPlayerCompetitionsFetched(username, comps));
			showRetry = false;
		}
		else if (response.code() == 429)
		{
			log.error("wom-utils: reached api limits while fetching upcoming competitions");
		}
		else
		{
			WomStatus data = parseResponse(response, WomStatus.class);
			String message = "Error: " + data.getMessage();
			sendResponseToChat(message, ERROR);
		}

		if (showRetry)
		{
			eventBus.post(new WomRequestFailed(username, WomRequestType.COMPETITIONS_UPCOMING));
		}
	}


	private <T> T parseResponse(Response r, Class<T> clazz)
	{
		return parseResponse(r, clazz, false);
	}

	private <T> T parseResponse(Response r, Class<T> clazz, boolean nullIferror)
	{
		if (nullIferror && !r.isSuccessful())
		{
			return null;
		}

		String body;
		try
		{
			body = r.body().string();
		}
		catch (IOException e)
		{
			log.error("Could not read response {}", e.getMessage());
			return null;
		}

		return gson.fromJson(body, clazz);
	}

	private void sendResponseToChat(String message, Color color)
	{
		ChatMessageBuilder cmb = new ChatMessageBuilder();
		cmb.append("[WOM] ");
		cmb.append(color, message);

		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(cmb.build())
			.build());
	}

	public void syncClanMembers(ArrayList<Member> clanMembers, Set<RoleIndex> roleOrders)
	{
		this.clanMembers = clanMembers;
		this.roleOrders = roleOrders;

		GroupMemberAddition payload = new GroupMemberAddition(config.verificationCode(), clanMembers, roleOrders);
		Request request = createRequest(payload, HttpMethod.PUT, "groups", "" + config.groupId());
		sendRequest(request, this::syncClanMembersCallBack, this::handleSyncClanMembersException);
		this.isSyncing = true;
		plugin.syncButton.setEnabled(true);
	}

	public void commandLookup(String username, WomCommand command, ChatMessage chatMessage)
	{
		Request request = createRequest("players", username);
		sendRequest(request, r -> commandCallback(r, command, chatMessage));
	}

	private void commandCallback(Response response, WomCommand command, ChatMessage chatMessage)
	{
		if (!response.isSuccessful())
		{
			return;
		}

		final PlayerInfo info = parseResponse(response, PlayerInfo.class);

		final double time;

		try
		{
			String cmd = command.getCommand();
			if (cmd.equals("!ehp"))
			{
				time = info.getEhp();
			}
			else if (cmd.equals("!ehb"))
			{
				time = info.getEhb();
			}
			else if (cmd.equals("!ttm"))
			{
				time = info.getTtm();
			}
			else
			{
				time = info.getTt200m();
			}
		}
		catch (Throwable e)
		{
			log.warn("{}", e.getMessage());
			return;
		}

		String value = NUMBER_FORMAT.format(time);

		String message = new ChatMessageBuilder()
			.append(ChatColorType.NORMAL)
			.append(command.getMessage())
			.append(ChatColorType.HIGHLIGHT)
			.append(value)
			.append(".")
			.build();

		final MessageNode messageNode = chatMessage.getMessageNode();
		messageNode.setRuneLiteFormatMessage(message);
		client.refreshChat();
	}

	private void postEvent(Object event)
	{
		// Handle callbacks on the client thread
		clientThread.invokeLater(() -> eventBus.post(event));
	}

	public void fetchUpcomingPlayerCompetitions(String username)
	{
		Request request = createRequest("players", username, "competitions", "?status=upcoming");
		sendRequest(request, r -> playerUpcomingCompetitionsCallback(username, r));
	}

	public void fetchOngoingPlayerCompetitions(String username)
	{
		Request request = createRequest("players", username, "competitions", "standings", "?status=ongoing");
		sendRequest(request, r -> playerOngoingCompetitionsCallback(username, r));
	}

	public void updatePlayer(String username, long accountHash)
	{
		if (this.plugin.worldType.contains(WorldType.TOURNAMENT_WORLD))
		{
			return;
		}

		Request request = createRequest(new WomPlayerUpdate(accountHash), "players", username);
		sendRequest(request);
	}

	public CompletableFuture<PlayerInfo> lookupAsync(String username)
	{
		CompletableFuture<PlayerInfo> future = new CompletableFuture<>();
		Request request = createRequest("players", username);
		sendRequest(request, r -> future.complete(parseResponse(r, PlayerInfo.class, true)), future::completeExceptionally);
		return future;
	}

	public CompletableFuture<PlayerInfo> updateAsync(String username)
	{
		CompletableFuture<PlayerInfo> future = new CompletableFuture<>();
		Request request = createRequest(new Object(), "players", username);
		sendRequest(request, r -> future.complete(parseResponse(r, PlayerInfo.class, true)), future::completeExceptionally);
		return future;
	}
}
