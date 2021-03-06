/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.ip.tcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ServerSocketFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.serializer.DefaultDeserializer;
import org.springframework.core.serializer.DefaultSerializer;
import org.springframework.integration.Message;
import org.springframework.integration.MessageChannel;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.PollableChannel;
import org.springframework.integration.ip.tcp.connection.AbstractConnectionFactory;
import org.springframework.integration.ip.tcp.connection.HelloWorldInterceptorFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactory;
import org.springframework.integration.ip.tcp.connection.TcpConnectionInterceptorFactoryChain;
import org.springframework.integration.ip.tcp.connection.TcpNetClientConnectionFactory;
import org.springframework.integration.ip.tcp.connection.TcpNioClientConnectionFactory;
import org.springframework.integration.ip.tcp.serializer.ByteArrayCrLfSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayLengthHeaderSerializer;
import org.springframework.integration.ip.tcp.serializer.ByteArrayStxEtxSerializer;
import org.springframework.integration.ip.util.SocketTestUtils;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.support.MessageBuilder;

/**
 * @author Gary Russell
 * @author Artem Bilan
 * @since 2.0
 */
public class TcpSendingMessageHandlerTests {

	private static final Log logger = LogFactory.getLog(TcpSendingMessageHandlerTests.class);


	private void readFully(InputStream is, byte[] buff) throws IOException {
		for (int i = 0; i < buff.length; i++) {
			buff[i] = (byte) is.read();
		}
	}

	@Test
	public void testNetCrLf() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("Reply" + (++i) + "\r\n").getBytes();
						socket.getOutputStream().write(b);
					}
				}
				catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply1", new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply2", new String((byte[]) mOut.getPayload()));
		done.set(true);
	}

	@Test
	public void testNetCrLfClientMode() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("Reply" + (++i) + "\r\n").getBytes();
						socket.getOutputStream().write(b);
					}
				}
				catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(Integer.MAX_VALUE);
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.setClientMode(true);
		handler.setRetryInterval(10000);
		handler.afterPropertiesSet();
		handler.start();
		adapter.start();
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply1", new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply2", new String((byte[]) mOut.getPayload()));
		done.set(true);
		handler.stop();
		handler.start();
		handler.stop();
	}

	@Test
	public void testNioCrLf() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("Reply" + (++i) + "\r\n").getBytes();
						socket.getOutputStream().write(b);
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Set<String> results = new HashSet<String>();
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add(new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add(new String((byte[]) mOut.getPayload()));
		assertTrue(results.remove("Reply1"));
		assertTrue(results.remove("Reply2"));
		done.set(true);
	}

	@Test
	public void testNetStxEtx() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("\u0002Reply" + (++i) + "\u0003").getBytes();
						socket.getOutputStream().write(b);
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ByteArrayStxEtxSerializer serializer = new ByteArrayStxEtxSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply1", new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply2", new String((byte[]) mOut.getPayload()));
		done.set(true);
	}

	@Test
	public void testNioStxEtx() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("\u0002Reply" + (++i) + "\u0003").getBytes();
						socket.getOutputStream().write(b);
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ByteArrayStxEtxSerializer serializer = new ByteArrayStxEtxSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Set<String> results = new HashSet<String>();
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add(new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add(new String((byte[]) mOut.getPayload()));
		assertTrue(results.remove("Reply1"));
		assertTrue(results.remove("Reply2"));
		done.set(true);
	}

	@Test
	public void testNetLength() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[8];
						readFully(socket.getInputStream(), b);
						if (!"\u0000\u0000\u0000\u0004Test".equals(new String(b))) {
							throw new RuntimeException("Bad Data");
						}
						b = ("\u0000\u0000\u0000\u0006Reply" + (++i)).getBytes();
						socket.getOutputStream().write(b);
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ByteArrayLengthHeaderSerializer serializer = new ByteArrayLengthHeaderSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply1", new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply2", new String((byte[]) mOut.getPayload()));
		done.set(true);
	}

	@Test
	public void testNioLength() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						byte[] b = new byte[8];
						readFully(socket.getInputStream(), b);
						if (!"\u0000\u0000\u0000\u0004Test".equals(new String(b))) {
							throw new RuntimeException("Bad Data");
						}
						b = ("\u0000\u0000\u0000\u0006Reply" + (++i)).getBytes();
						socket.getOutputStream().write(b);
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ByteArrayLengthHeaderSerializer serializer = new ByteArrayLengthHeaderSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Set<String> results = new HashSet<String>();
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add(new String((byte[]) mOut.getPayload()));
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add(new String((byte[]) mOut.getPayload()));
		assertTrue(results.remove("Reply1"));
		assertTrue(results.remove("Reply2"));
		done.set(true);
	}

	@Test
	public void testNetSerial() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						ois.readObject();
						ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						oos.writeObject("Reply" + (++i));
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ccf.setSerializer(new DefaultSerializer());
		ccf.setDeserializer(new DefaultDeserializer());
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply1", mOut.getPayload());
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply2", mOut.getPayload());
		done.set(true);
	}

	@Test
	public void testNioSerial() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						ois.readObject();
						ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						oos.writeObject("Reply" + (++i));
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ccf.setSerializer(new DefaultSerializer());
		ccf.setDeserializer(new DefaultDeserializer());
		ccf.setSoTimeout(10000);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Set<String> results = new HashSet<String>();
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add((String) mOut.getPayload());
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		results.add((String) mOut.getPayload());
		assertTrue(results.remove("Reply1"));
		assertTrue(results.remove("Reply2"));
		done.set(true);
	}

	@Test
	public void testNetSingleUseNoInbound() throws Exception  {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final Semaphore semaphore = new Semaphore(0);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					for (int i = 0; i < 2; i++) {
						Socket socket = server.accept();
						semaphore.release();
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						semaphore.release();
						socket.close();
					}
					server.close();
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		ccf.setSingleUse(true);
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		assertTrue(semaphore.tryAcquire(4, 10000, TimeUnit.MILLISECONDS));
		done.set(true);
		ccf.stop();
	}

	@Test
	public void testNioSingleUseNoInbound() throws Exception  {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final Semaphore semaphore = new Semaphore(0);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					for (int i = 0; i < 2; i++) {
						Socket socket = server.accept();
						semaphore.release();
						byte[] b = new byte[8];
						readFully(socket.getInputStream(), b);
						semaphore.release();
						socket.close();
					}
					server.close();
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(5000);
		ccf.start();
		ccf.setSingleUse(true);
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test.1").build());
		handler.handleMessage(MessageBuilder.withPayload("Test.2").build());
		assertTrue(semaphore.tryAcquire(4, 10000, TimeUnit.MILLISECONDS));
		done.set(true);
		ccf.stop();
	}

	@Test
	public void testNetSingleUseWithInbound() throws Exception  {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final Semaphore semaphore = new Semaphore(0);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					for (int i = 1; i < 3; i++) {
						Socket socket = server.accept();
						semaphore.release();
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("Reply" + i + "\r\n").getBytes();
						socket.getOutputStream().write(b);
						socket.close();
					}
					server.close();
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		ccf.setSingleUse(true);
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		assertTrue(semaphore.tryAcquire(2, 10000, TimeUnit.MILLISECONDS));
		Set<String> replies = new HashSet<String>();
		for (int i = 0; i < 2; i++) {
			Message<?> mOut = channel.receive(10000);
			assertNotNull(mOut);
			replies.add(new String((byte[])mOut.getPayload()));
		}
		assertTrue(replies.remove("Reply1"));
		assertTrue(replies.remove("Reply2"));
		done.set(true);
		ccf.stop();
	}

	@Test
	public void testNioSingleUseWithInbound() throws Exception  {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final Semaphore semaphore = new Semaphore(0);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					for (int i = 1; i < 3; i++) {
						Socket socket = server.accept();
						semaphore.release();
						byte[] b = new byte[6];
						readFully(socket.getInputStream(), b);
						b = ("Reply" + i + "\r\n").getBytes();
						socket.getOutputStream().write(b);
						socket.close();
					}
					server.close();
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.start();
		ccf.setSingleUse(true);
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		assertTrue(semaphore.tryAcquire(2, 10000, TimeUnit.MILLISECONDS));
		Set<String> replies = new HashSet<String>();
		for (int i = 0; i < 2; i++) {
			Message<?> mOut = channel.receive(10000);
			assertNotNull(mOut);
			replies.add(new String((byte[])mOut.getPayload()));
		}
		assertTrue(replies.remove("Reply1"));
		assertTrue(replies.remove("Reply2"));
		done.set(true);
		ccf.stop();
	}

	@Test
	public void testNioSingleUseWithInboundMany() throws Exception  {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final Semaphore semaphore = new Semaphore(0);
		final AtomicBoolean done = new AtomicBoolean();
		final List<Socket> serverSockets = new ArrayList<Socket>();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port, 100);
					latch.countDown();
					for (int i = 0; i < 100; i++) {
						Socket socket = server.accept();
						serverSockets.add(socket);
						semaphore.release();
						byte[] b = new byte[9];
						readFully(socket.getInputStream(), b);
						b = ("Reply" + i + "\r\n").getBytes();
						socket.getOutputStream().write(b);
						socket.close();
					}
					server.close();
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ByteArrayCrLfSerializer serializer = new ByteArrayCrLfSerializer();
		ccf.setSerializer(serializer);
		ccf.setDeserializer(serializer);
		ccf.setSoTimeout(10000);
		ccf.setSingleUse(true);
		ccf.setTaskExecutor(Executors.newFixedThreadPool(100));
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		int i = 0;
		try {
			for (i = 100; i < 200; i++) {
				handler.handleMessage(MessageBuilder.withPayload("Test" + i).build());
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception at " + i);
		}
		assertTrue(semaphore.tryAcquire(100, 20000, TimeUnit.MILLISECONDS));
		Set<String> replies = new HashSet<String>();
		for (i = 100; i < 200; i++) {
			Message<?> mOut = channel.receive(20000);
			assertNotNull(mOut);
			replies.add(new String((byte[])mOut.getPayload()));
		}
		for (i = 0; i < 100; i++) {
			assertTrue("Reply" + i + " missing", replies.remove("Reply" + i));
		}
		done.set(true);
		ccf.stop();
	}

	@Test
	public void testNetNegotiate() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					while (true) {
						ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						Object in = null;
						ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						if (i == 0) {
							in = ois.readObject();
							logger.debug("read object: " + in);
							oos.writeObject("world!");
							ois = new ObjectInputStream(socket.getInputStream());
							oos = new ObjectOutputStream(socket.getOutputStream());
							in = ois.readObject();
							logger.debug("read object: " + in);
							oos.writeObject("world!");
							ois = new ObjectInputStream(socket.getInputStream());
							oos = new ObjectOutputStream(socket.getOutputStream());
						}
						in = ois.readObject();
						oos.writeObject("Reply" + (++i));
					}
				}
				catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ccf.setSerializer(new DefaultSerializer());
		ccf.setDeserializer(new DefaultDeserializer());
		ccf.setSoTimeout(10000);
		TcpConnectionInterceptorFactoryChain fc = new TcpConnectionInterceptorFactoryChain();
		fc.setInterceptors(new TcpConnectionInterceptorFactory[]
		     {new HelloWorldInterceptorFactory(),
		      new HelloWorldInterceptorFactory()});
		ccf.setInterceptorFactoryChain(fc);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		Message<?> mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply1", mOut.getPayload());
		mOut = channel.receive(10000);
		assertNotNull(mOut);
		assertEquals("Reply2", mOut.getPayload());
		done.set(true);
	}

	@Test
	public void testNioNegotiate() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 100;
					while (true) {
						ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
						Object in;
						ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
						if (i == 100) {
							in = ois.readObject();
							logger.debug("read object: " + in);
							oos.writeObject("world!");
							ois = new ObjectInputStream(socket.getInputStream());
							oos = new ObjectOutputStream(socket.getOutputStream());
							Thread.sleep(500);
						}
						in = ois.readObject();
						oos.writeObject("Reply" + (i++));
					}
				} catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ccf.setSerializer(new DefaultSerializer());
		ccf.setDeserializer(new DefaultDeserializer());
		ccf.setSoTimeout(10000);
		TcpConnectionInterceptorFactoryChain fc = new TcpConnectionInterceptorFactoryChain();
		fc.setInterceptors(new TcpConnectionInterceptorFactory[] {new HelloWorldInterceptorFactory()});
		ccf.setInterceptorFactoryChain(fc);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		TcpReceivingChannelAdapter adapter = new TcpReceivingChannelAdapter();
		adapter.setConnectionFactory(ccf);
		QueueChannel channel = new QueueChannel();
		adapter.setOutputChannel(channel);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		for (int i = 0; i < 1000; i++) {
			handler.handleMessage(MessageBuilder.withPayload("Test").build());
		}
		Set<String> results = new TreeSet<String>();
		for (int i = 0; i < 1000; i++) {
			Message<?> mOut = channel.receive(10000);
			assertNotNull(mOut);
			results.add((String) mOut.getPayload());
		}
		logger.debug("results: " + results);
		for (int i = 100; i < 1100; i++) {
			assertTrue("Missing Reply" + i, results.remove("Reply" + i));
		}
		done.set(true);
	}

	@Test
	public void testNetNegotiateSingleNoListen() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					int i = 0;
					ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
					Object in = null;
					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					if (i == 0) {
						in = ois.readObject();
						logger.debug("read object: " + in);
						oos.writeObject("world!");
						ois = new ObjectInputStream(socket.getInputStream());
						oos = new ObjectOutputStream(socket.getOutputStream());
						in = ois.readObject();
						logger.debug("read object: " + in);
						oos.writeObject("world!");
						ois = new ObjectInputStream(socket.getInputStream());
						oos = new ObjectOutputStream(socket.getOutputStream());
					}
					in = ois.readObject();
					oos.writeObject("Reply" + (++i));
					socket.close();
					server.close();
				}
				catch (Exception e) {
					if (!done.get()) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNetClientConnectionFactory("localhost", port);
		ccf.setSerializer(new DefaultSerializer());
		ccf.setDeserializer(new DefaultDeserializer());
		ccf.setSoTimeout(10000);
		TcpConnectionInterceptorFactoryChain fc = new TcpConnectionInterceptorFactoryChain();
		fc.setInterceptors(new TcpConnectionInterceptorFactory[]
            		     {new HelloWorldInterceptorFactory(),
               		      new HelloWorldInterceptorFactory()});
		ccf.setInterceptorFactoryChain(fc);
		ccf.setSingleUse(true);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		done.set(true);
	}

	@Test
	public void testNioNegotiateSingleNoListen() throws Exception {
		final int port = SocketTestUtils.findAvailableServerSocket();
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicBoolean done = new AtomicBoolean();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			public void run() {
				int i = 0;
				try {
					ServerSocket server = ServerSocketFactory.getDefault().createServerSocket(port);
					latch.countDown();
					Socket socket = server.accept();
					ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
					Object in = null;
					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					if (i == 0) {
						in = ois.readObject();
						logger.debug("read object: " + in);
						oos.writeObject("world!");
						ois = new ObjectInputStream(socket.getInputStream());
						oos = new ObjectOutputStream(socket.getOutputStream());
						in = ois.readObject();
						logger.debug("read object: " + in);
						oos.writeObject("world!");
						ois = new ObjectInputStream(socket.getInputStream());
						oos = new ObjectOutputStream(socket.getOutputStream());
					}
					in = ois.readObject();
					oos.writeObject("Reply" + (++i));
					socket.close();
					server.close();
				} catch (Exception e) {
					if (i == 0) {
						e.printStackTrace();
					}
				}
			}
		});
		AbstractConnectionFactory ccf = new TcpNioClientConnectionFactory("localhost", port);
		ccf.setSerializer(new DefaultSerializer());
		ccf.setDeserializer(new DefaultDeserializer());
		ccf.setSoTimeout(10000);
		TcpConnectionInterceptorFactoryChain fc = new TcpConnectionInterceptorFactoryChain();
		fc.setInterceptors(new TcpConnectionInterceptorFactory[]
            		     {new HelloWorldInterceptorFactory(),
               		      new HelloWorldInterceptorFactory()});
		ccf.setInterceptorFactoryChain(fc);
		ccf.setSingleUse(true);
		ccf.start();
		TcpSendingMessageHandler handler = new TcpSendingMessageHandler();
		handler.setConnectionFactory(ccf);
		assertTrue(latch.await(10, TimeUnit.SECONDS));
		handler.handleMessage(MessageBuilder.withPayload("Test").build());
		done.set(true);
	}

	@Test
	public void testOutboundChannelAdapterWithinChain() throws Exception {
		ApplicationContext ctx = new ClassPathXmlApplicationContext(
				"TcpOutboundChannelAdapterWithinTests-context.xml", this.getClass());
		AbstractConnectionFactory ccf = ctx.getBean("ccf", AbstractConnectionFactory.class);
//		TODO Lifecycle#start() isn't invoked within chain...
		ccf.start();
		MessageChannel channelAdapterWithinChain = ctx.getBean("tcpOutboundChannelAdapterWithinChain", MessageChannel.class);
		PollableChannel inbound = ctx.getBean("inbound", PollableChannel.class);
		String testPayload = "Hello, world!";
		channelAdapterWithinChain.send(new GenericMessage<String>(testPayload));
		Message<?> m = inbound.receive(1000);
		assertNotNull(m);
		assertEquals(testPayload, new String((byte[]) m.getPayload()));
	}

}
