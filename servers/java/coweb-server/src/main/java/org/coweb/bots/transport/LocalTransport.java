/**
 * Copyright (c) The Dojo Foundation 2011. All Rights Reserved.
 * Copyright (c) IBM Corporation 2008, 2011. All Rights Reserved.
 */
package org.coweb.bots.transport;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.cometd.bayeux.Message;
import org.cometd.bayeux.server.ConfigurableServerChannel;
import org.cometd.bayeux.server.ServerChannel;
import org.cometd.bayeux.server.ServerSession;

import org.coweb.SessionHandler;
import org.coweb.bots.Bot;
import org.coweb.bots.Proxy;

public class LocalTransport extends Transport implements Proxy {
	private static final Logger log = Logger.getLogger(LocalTransport.class
			.getName());

	private Bot bot = null;
	private Map<String, ServerSession> clients =
		new HashMap<String, ServerSession>();

	private ArrayList<ServerSession> subscribers =
		new ArrayList<ServerSession>();

	public LocalTransport() {
		super();
	}

	@Override
	public void init() {
		return;
	}

	@Override
	public boolean subscribeUser(ServerSession client, boolean pub)
			throws IOException {
		log.fine("LocalTransport::subscribeUser");

		if (this.bot == null) {
			this.bot = this.getBotInstance();
			if (this.bot == null)
				throw new IOException("unable to locate bot " +
						this.serviceName);
		}

		if (pub) {
			this.bot.onSubscribe((String) client.getAttribute("username"));
			this.subscribers.add(client);
		}

		return true;
	}

	@Override
	public boolean unsubscribeUser(ServerSession client, boolean pub)
			throws IOException {
		log.fine("LocalTransport::unSubscribeUser");

		if (this.bot == null) {
			this.bot = this.getBotInstance();
			if (this.bot == null)
				throw new IOException("unable to locate bot "
						+ this.serviceName);
		}

		if (pub) {
			this.bot.onUnsubscribe((String) client.getAttribute("username"));
			this.subscribers.remove(client);
		}

		return true;

	}

	@Override
	public void shutdown() {
		log.fine("LocalTransport::shutdown");
		this.clients.clear();
		this.subscribers.clear();
		if (this.bot != null)
			this.bot.onShutdown();
	}

	@Override
	public boolean userRequest(ServerSession client, Message message)
			throws IOException {
		log.fine("LocalTransport::userRequest");
		log.fine("message = " + message);

		Map<String, Object> data = message.getDataAsMap();
		Map<String, Object> params = (Map<String, Object>) data.get("value");
		String replyToken = (String) data.get("topic");
		String username = (String) client.getAttribute("username");

		if (this.bot == null) {
			this.bot = this.getBotInstance();
			if (this.bot == null)
				throw new IOException("unable to locate bot "
						+ this.serviceName);
		}

		this.clients.put(replyToken, client);
		this.bot.onRequest(params, replyToken, username);

		return true;
	}

	@Override
	public void reply(Bot bot, String replyToken, Map<String, Object> obj) {

		log.fine("LocalTransport::reply");
		log.fine("reply data = " + obj);
		ServerSession client = this.clients.get(replyToken);

		if (client == null) {
			log.warning("LocalTransport::error " +
					"sending bot reply client not found");
			// TODO send error.
			return;
		}

		HashMap<String, Object> data = new HashMap<String, Object>();
		data.put("value", obj);
		data.put("topic", replyToken);
		data.put("error", false);

		log.fine("LocalTransport::reply");
		log.fine("payload = " + data);
		log.fine("replyToken = " + replyToken);

		client.deliver(this.server, "/service/bot/" + this.serviceName +
				"/response", data, null);
		this.clients.remove(replyToken);
	}

	/**
	 * Actually sends the message from the bot to all subscribers.
	 * @param bot The bot sending the message.
	 * @param obj The message contents.
	 */
	@Override
	public void publish(Bot bot, Map<String, Object> obj) {
		log.fine("LocalTransport::publish");

		HashMap<String, Object> data = new HashMap<String, Object>();
		data.put("value", obj);
		data.put("error", false);

		ServerChannel channel = this.getResponseChannel();
		for (ServerSession client : this.subscribers) {
			client.deliver(this.server, channel.getId(), data, null);
		}
	}

	private Bot getBotInstance() throws IOException {

		String className = (String) this.botConfig.get("class");
		if (className == null)
			throw new IOException("Error parsing bot descriptor");

		try {
			Class<? extends Bot> clazz = Class.forName(className).asSubclass(
					Bot.class);
			bot = clazz.newInstance();
			bot.setProxy(this);
			bot.init();

		} catch (Exception e) {
			throw new IOException("Error attempting to initialize bot "
					+ className + e.toString());
		}

		return bot;
	}

}
