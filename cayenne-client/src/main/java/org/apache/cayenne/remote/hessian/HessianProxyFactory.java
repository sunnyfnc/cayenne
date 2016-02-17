/*****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ****************************************************************/

package org.apache.cayenne.remote.hessian;

import com.caucho.hessian.client.HessianConnectionException;
import com.caucho.hessian.client.HessianProxy;
import com.caucho.hessian.client.HessianRuntimeException;
import com.caucho.hessian.io.AbstractHessianInput;
import com.caucho.hessian.io.AbstractHessianOutput;
import com.caucho.hessian.io.HessianProtocolException;
import com.caucho.hessian.io.HessianRemoteObject;
import com.caucho.services.server.AbstractSkeleton;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.graph.*;
import org.apache.cayenne.map.ClientEntityResolver;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.remote.BootstrapMessage;
import org.apache.cayenne.remote.QueryMessage;
import org.apache.cayenne.remote.RemoteSession;
import org.apache.cayenne.remote.SyncMessage;
import org.apache.cayenne.util.PersistentObjectList;
import org.apache.cayenne.util.PersistentObjectMap;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;
import org.w3c.dom.Node;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.WeakHashMap;
import java.util.logging.Level;

/**
 * A proxy factory that handles HTTP sessions.
 *
 * @since 1.2
 */
class HessianProxyFactory extends com.caucho.hessian.client.HessianProxyFactory {

	static FSTConfiguration fstConfiguration = FSTConfiguration.createDefaultConfiguration();

	static {
		fstConfiguration.registerClass(ArrayList.class, HashMap.class, Integer.class,
				Timestamp.class, Date.class, Long.class, SyncMessage.class, NodePropertyChangeOperation.class,
				ArcCreateOperation.class, ArcDeleteOperation.class, CompoundDiff.class, NodeCreateOperation.class,
				NodeDeleteOperation.class, GraphDiff.class, NodeIdChangeOperation.class, PersistentObjectList.class,
				PersistentObjectMap.class, RemoteSession.class, QueryMessage.class, BootstrapMessage.class,
				ClientEntityResolver.class, DataMap.class, ObjectId.class, DataRow.class);
	}

	static final String SESSION_COOKIE_NAME = "JSESSIONID";

	private HessianConnection clientConnection;

	HessianProxyFactory(HessianConnection clientConnection) {
		this.clientConnection = clientConnection;
	}

	@Override
	protected URLConnection openConnection(URL url) throws IOException {
		URLConnection connection = super.openConnection(url);

		// unfortunately we can't read response cookies without completely reimplementing
		// 'HessianProxy.invoke()'. Currently (3.0.13) it doesn't allow to cleanly
		// intercept response... so extract session id from the RemoteSession....

		// add session cookie
		RemoteSession session = clientConnection.getSession();
		if (session != null && session.getSessionId() != null) {
			connection.setRequestProperty("Cookie", SESSION_COOKIE_NAME
					+ "="
					+ session.getSessionId());
		}

		return connection;
	}

	@Override
	public Object create(Class api, String urlName) throws MalformedURLException {
		return create(api, urlName, Thread.currentThread().getContextClassLoader());
	}

	@Override
	public Object create(Class api, String urlName, ClassLoader loader) throws MalformedURLException {
		if (api == null)
			throw new NullPointerException("api must not be null for HessianProxyFactory.create()");

		URL url = new URL(urlName);
		InvocationHandler handler = new MyHessianProxy(this, url);

		return Proxy.newProxyInstance(loader, new Class[]{api, HessianRemoteObject.class}, handler);
	}

	static class MyHessianProxy implements InvocationHandler {
		protected final HessianProxyFactory factory;
		protected final URL url;
		protected boolean fstClassLoaderConfigured = false;

		private final WeakHashMap<Method, String> _mangleMap
				= new WeakHashMap<Method, String>();

		public MyHessianProxy(HessianProxyFactory hessianProxyFactory, URL url) {
			this.factory = hessianProxyFactory;
			this.url = url;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if (!fstClassLoaderConfigured) {
				fstConfiguration.setClassLoader(Thread.currentThread().getContextClassLoader());
				fstClassLoaderConfigured = true;
			}

			String mangleName;

			synchronized (_mangleMap) {
				mangleName = _mangleMap.get(method);
			}

			if (mangleName == null) {
				String methodName = method.getName();
				Class[] params = method.getParameterTypes();

				// equals and hashCode are special cased
				if (methodName.equals("equals")
						&& params.length == 1 && params[0].equals(Object.class)) {
					Object value = args[0];
					if (value == null || !Proxy.isProxyClass(value.getClass()))
						return false;

					HessianProxy handler = (HessianProxy) Proxy.getInvocationHandler(value);

					return url.equals(handler.getURL());
				} else if (methodName.equals("hashCode") && params.length == 0)
					return url.hashCode();
				else if (methodName.equals("getHessianType"))
					return proxy.getClass().getInterfaces()[0].getName();
				else if (methodName.equals("getHessianURL"))
					return url.toString();
				else if (methodName.equals("toString") && params.length == 0)
					return "MyHessianProxy[" + url + "]";

				if (!factory.isOverloadEnabled())
					mangleName = method.getName();
				else
					mangleName = mangleName(method);

				synchronized (_mangleMap) {
					_mangleMap.put(method, mangleName);
				}
			}

			InputStream is = null;
			URLConnection conn;
			HttpURLConnection httpConn = null;

			try {

				conn = sendRequest(mangleName, args);

				if (conn instanceof HttpURLConnection) {
					httpConn = (HttpURLConnection) conn;
					int code = 500;

					try {
						code = httpConn.getResponseCode();
					} catch (Exception e) {
						// ignore
					}

					if (code != 200) {
						final StringBuilder sb = new StringBuilder();
						int ch;

						try {
							is = httpConn.getInputStream();

							if (is != null) {
								while ((ch = is.read()) >= 0)
									sb.append((char) ch);

								is.close();
							}

							is = httpConn.getErrorStream();
							if (is != null) {
								while ((ch = is.read()) >= 0)
									sb.append((char) ch);
							}
						} catch (FileNotFoundException e) {
							throw new HessianConnectionException("MyHessianProxy cannot connect to '" + url, e);
						} catch (IOException e) {
							if (is == null)
								throw new HessianConnectionException(code + ": " + e, e);
							else
								throw new HessianConnectionException(code + ": " + sb, e);
						}

						if (is != null)
							is.close();

						throw new HessianConnectionException(code + ": " + sb.toString());
					}
				}

				is = conn.getInputStream();

				/*AbstractHessianInput in = _factory.getHessianInput(is);

				in.startReply();

				Object value = in.readObject(method.getReturnType());*/
				//final FSTObjectInput in = fstConfiguration.getObjectInput(is);
				//Object value = in.readObject(method.getReturnType());
				Object value = fstConfiguration.decodeFromStream(is);

				if (value instanceof InputStream) {
					value = new ResultInputStream(httpConn, is, (InputStream) value);
					is = null;
					httpConn = null;
				}
				if (value instanceof Throwable) {
					throw (Throwable) value;
				}

				return value;
			} catch (HessianProtocolException e) {
				throw new HessianRuntimeException(e);
			} finally {
				try {
					if (is != null)
						is.close();
				} catch (Exception e) {
					log.log(Level.FINE, e.toString(), e);
				}

				try {
					if (httpConn != null)
						httpConn.disconnect();
				} catch (Exception e) {
					log.log(Level.FINE, e.toString(), e);
				}
			}
		}

		protected String mangleName(Method method) {
			Class[] param = method.getParameterTypes();

			if (param == null || param.length == 0)
				return method.getName();
			else
				return AbstractSkeleton.mangleName(method, false);
		}

		protected URLConnection sendRequest(String methodName, Object[] args)
				throws IOException {

			URLConnection conn = factory.openConnection(url);
			boolean isValid = false;

			try {
				// Used chunked mode when available, i.e. JDK 1.5.
				if (factory.isChunkedPost() && conn instanceof HttpURLConnection) {
					HttpURLConnection httpConn = (HttpURLConnection) conn;

					httpConn.setChunkedStreamingMode(8 * 1024);
				}

				OutputStream os;
				try {
					os = new BufferedOutputStream(conn.getOutputStream());
				} catch (Exception e) {
					throw new HessianRuntimeException(e);
				}

				/*AbstractHessianOutput out = factory.getHessianOutput(os);

				out.call(methodName, args);
				out.flush();*/

				final FSTObjectOutput out = fstConfiguration.getObjectOutput(os);
				out.writeStringUTF(methodName);
				if (args != null) {
					for (Object arg : args) {
						out.writeObject(arg);
					}
				}

				out.flush();

				isValid = true;

				return conn;
			} finally {
				if (!isValid && conn instanceof HttpURLConnection)
					((HttpURLConnection) conn).disconnect();
			}
		}

		public URL getUrl() {
			return url;
		}
	}


	static class ResultInputStream extends InputStream {
		private HttpURLConnection _conn;
		private InputStream _connIs;
		private InputStream _hessianIs;

		ResultInputStream(HttpURLConnection conn,
		                  InputStream is,
		                  InputStream hessianIs) {
			_conn = conn;
			_connIs = is;
			_hessianIs = hessianIs;
		}

		public int read()
				throws IOException {
			if (_hessianIs != null) {
				int value = _hessianIs.read();

				if (value < 0)
					close();

				return value;
			} else
				return -1;
		}

		public int read(byte[] buffer, int offset, int length)
				throws IOException {
			if (_hessianIs != null) {
				int value = _hessianIs.read(buffer, offset, length);

				if (value < 0)
					close();

				return value;
			} else
				return -1;
		}

		public void close()
				throws IOException {
			HttpURLConnection conn = _conn;
			_conn = null;

			InputStream connIs = _connIs;
			_connIs = null;

			InputStream hessianIs = _hessianIs;
			_hessianIs = null;

			try {
				if (hessianIs != null)
					hessianIs.close();
			} catch (Exception e) {
				log.log(Level.FINE, e.toString(), e);
			}

			try {
				if (connIs != null) {
					connIs.close();
				}
			} catch (Exception e) {
				log.log(Level.FINE, e.toString(), e);
			}

			try {
				if (conn != null) {
					conn.disconnect();
				}
			} catch (Exception e) {
				log.log(Level.FINE, e.toString(), e);
			}
		}
	}

	static class MyAbstractHessianInput extends AbstractHessianInput {

		final InputStream is;
		final FSTObjectInput objectInput;

		private String _method;

		MyAbstractHessianInput(InputStream is) {
			this.is = is;
			this.objectInput = fstConfiguration.getObjectInput(is);
		}

		@Override
		public String getMethod() {
			return _method;
		}

		@Override
		public int readCall() throws IOException {
			return 0;
		}

		@Override
		public String readHeader() throws IOException {
			return null;
		}

		@Override
		public String readMethod() throws IOException {
			final String res = objectInput.readStringAsc();
			this._method = res;
			return res;
		}

		@Override
		public void startCall() throws IOException {
			readMethod();
		}

		@Override
		public void completeCall() throws IOException {
		}

		@Override
		public Object readReply(Class expectedClass) throws Throwable {
			return objectInput.readObject(expectedClass);
		}

		@Override
		public void startReply() throws Throwable {

		}

		@Override
		public void completeReply() throws IOException {

		}

		@Override
		public boolean readBoolean() throws IOException {
			return false;
		}

		@Override
		public void readNull() throws IOException {

		}

		@Override
		public int readInt() throws IOException {
			return objectInput.readInt();
		}

		@Override
		public long readLong() throws IOException {
			return objectInput.readLong();
		}

		@Override
		public double readDouble() throws IOException {
			return objectInput.readDouble();
		}

		@Override
		public long readUTCDate() throws IOException {
			return objectInput.readLong();
		}

		@Override
		public String readString() throws IOException {
			return objectInput.readStringUTF();
		}

		@Override
		public Node readNode() throws IOException {
			return null;
		}

		@Override
		public Reader getReader() throws IOException {
			return null;
		}

		@Override
		public InputStream readInputStream() throws IOException {
			return null;
		}

		@Override
		public byte[] readBytes() throws IOException {
			return new byte[0];
		}

		@Override
		public Object readObject(Class expectedClass) throws IOException {
			return null;
		}

		@Override
		public Object readObject() throws IOException {
			return null;
		}

		@Override
		public Object readRemote() throws IOException {
			return null;
		}

		@Override
		public Object readRef() throws IOException {
			return null;
		}

		@Override
		public int addRef(Object obj) throws IOException {
			return 0;
		}

		@Override
		public void setRef(int i, Object obj) throws IOException {

		}

		@Override
		public int readListStart() throws IOException {
			return 0;
		}

		@Override
		public int readLength() throws IOException {
			return 0;
		}

		@Override
		public int readMapStart() throws IOException {
			return 0;
		}

		@Override
		public String readType() throws IOException {
			return null;
		}

		@Override
		public boolean isEnd() throws IOException {
			return false;
		}

		@Override
		public void readEnd() throws IOException {

		}

		@Override
		public void readMapEnd() throws IOException {

		}

		@Override
		public void readListEnd() throws IOException {

		}
	}

	static class MyAbstractHessianOutput extends AbstractHessianOutput {

		final OutputStream os;

		MyAbstractHessianOutput(OutputStream os) {
			this.os = os;
		}

		@Override
		public void startCall() throws IOException {

		}

		@Override
		public void startCall(String method) throws IOException {

		}

		@Override
		public void writeHeader(String name) throws IOException {

		}

		@Override
		public void writeMethod(String method) throws IOException {

		}

		@Override
		public void completeCall() throws IOException {

		}

		@Override
		public void writeBoolean(boolean value) throws IOException {

		}

		@Override
		public void writeInt(int value) throws IOException {

		}

		@Override
		public void writeLong(long value) throws IOException {

		}

		@Override
		public void writeDouble(double value) throws IOException {

		}

		@Override
		public void writeUTCDate(long time) throws IOException {

		}

		@Override
		public void writeNull() throws IOException {

		}

		@Override
		public void writeString(String value) throws IOException {

		}

		@Override
		public void writeString(char[] buffer, int offset, int length) throws IOException {

		}

		@Override
		public void writeBytes(byte[] buffer) throws IOException {

		}

		@Override
		public void writeBytes(byte[] buffer, int offset, int length) throws IOException {

		}

		@Override
		public void writeByteBufferStart() throws IOException {

		}

		@Override
		public void writeByteBufferPart(byte[] buffer, int offset, int length) throws IOException {

		}

		@Override
		public void writeByteBufferEnd(byte[] buffer, int offset, int length) throws IOException {

		}

		@Override
		public void writeRef(int value) throws IOException {

		}

		@Override
		public boolean removeRef(Object obj) throws IOException {
			return false;
		}

		@Override
		public boolean replaceRef(Object oldRef, Object newRef) throws IOException {
			return false;
		}

		@Override
		public boolean addRef(Object object) throws IOException {
			return false;
		}

		@Override
		public void writeObject(Object object) throws IOException {

		}

		@Override
		public boolean writeListBegin(int length, String type) throws IOException {
			return false;
		}

		@Override
		public void writeListEnd() throws IOException {

		}

		@Override
		public void writeMapBegin(String type) throws IOException {

		}

		@Override
		public void writeMapEnd() throws IOException {

		}

		@Override
		public void writeRemote(String type, String url) throws IOException {

		}
	}
}
