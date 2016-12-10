package com.socket;

import java.io.*;
import java.net.*;


/*
 * 
 * 这里SocketServer完全不用实现Runnable接口，直接使用服务线程类ServerThread 
 * 一客户一线程
 */
public class SocketServer implements Runnable {

	public ServerThread clients[];
	public ServerSocket server = null;
	public Thread thread = null;
	public int clientCount = 0, port = 13000;
	public ServerFrame ui;
	public Database db;

	public SocketServer(ServerFrame frame) {

		clients = new ServerThread[50];
		ui = frame;
		db = new Database(ui.filePath);

		try {
			server = new ServerSocket(port);
			port = server.getLocalPort();
			ui.jTextArea1
					.append("Server startet. IP : " + InetAddress.getLocalHost() + ", Port : " + server.getLocalPort());
			// 启动线程
			start();
		} catch (IOException ioe) {
			ui.jTextArea1.append("Can not bind to port : " + port + "\nRetrying");
			ui.RetryStart(0);
		}
	}

	public SocketServer(ServerFrame frame, int Port) {

		clients = new ServerThread[50];
		ui = frame;
		port = Port;
		db = new Database(ui.filePath);

		try {
			server = new ServerSocket(port);
			port = server.getLocalPort();
			ui.jTextArea1
					.append("Server startet. IP : " + InetAddress.getLocalHost() + ", Port : " + server.getLocalPort());
			start();
		} catch (IOException ioe) {
			ui.jTextArea1.append("\nCan not bind to port " + port + ": " + ioe.getMessage());
		}
	}

	public void run() {
		while (thread != null) {
			try {
				ui.jTextArea1.append("\nWaiting for a client ...");
				addThread(server.accept());
			} catch (Exception ioe) {
				ui.jTextArea1.append("\nServer accept error: \n");
				ui.RetryStart(0);
			}
		}
	}

	/**
	 * 启动线程
	 */
	public void start() {
		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}

	/**
	 * 停止线程
	 */
	@SuppressWarnings("deprecation")
	public void stop() {
		if (thread != null) {
			thread.stop();
			thread = null;
		}
	}

	/**
	 * 根据下标id查找用户进程
	 * @param ID
	 * @return
	 */
	private int findClient(int ID) {
		for (int i = 0; i < clientCount; i++) {
			if (clients[i].getID() == ID) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * 同步处理消息
	 * 
	 * @param ID
	 * @param msg
	 */

	public synchronized void handle(int ID, Message msg) {
		if (msg.content.equals(".bye")) {
			Announce("signout", "SERVER", msg.sender);
			remove(ID);
		} else {
			if (msg.type.equals("login")) {
				if (findUserThread(msg.sender) == null) { // 当前线程中没有该用户线程
					if (db.checkLogin(msg.sender, msg.content)) { //
						clients[findClient(ID)].username = msg.sender;
						clients[findClient(ID)].send(new Message("login", "SERVER", "TRUE", msg.sender));
						Announce("newuser", "SERVER", msg.sender);
						SendUserList(msg.sender);
					} else {
						clients[findClient(ID)].send(new Message("login", "SERVER", "FALSE", msg.sender));
					}
				} else {
					clients[findClient(ID)].send(new Message("login", "SERVER", "FALSE", msg.sender));
				}
			} else if (msg.type.equals("message")) {
				if (msg.recipient.equals("All")) {
					Announce("message", msg.sender, msg.content);
				} else {
					findUserThread(msg.recipient).send(new Message(msg.type, msg.sender, msg.content, msg.recipient));
					clients[findClient(ID)].send(new Message(msg.type, msg.sender, msg.content, msg.recipient));
				}
			} else if (msg.type.equals("test")) {	//测试
				clients[findClient(ID)].send(new Message("test", "SERVER", "OK", msg.sender));
			} else if (msg.type.equals("signup")) {		//注册
				if (findUserThread(msg.sender) == null) {	//线程池中没有该线程
					if (!db.userExists(msg.sender)) {	//用户不存在
						db.addUser(msg.sender, msg.content);
						clients[findClient(ID)].username = msg.sender;
						//服务器发送消息“用户注册成功”
						clients[findClient(ID)].send(new Message("signup", "SERVER", "TRUE", msg.sender));	
						//服务器发送消息“登录成功”（注册成功后直接登录）
						clients[findClient(ID)].send(new Message("login", "SERVER", "TRUE", msg.sender));
						Announce("newuser", "SERVER", msg.sender);
						SendUserList(msg.sender);
					} else {
						clients[findClient(ID)].send(new Message("signup", "SERVER", "FALSE", msg.sender));
					}
				} else {
					clients[findClient(ID)].send(new Message("signup", "SERVER", "FALSE", msg.sender));
				}
			} else if (msg.type.equals("upload_req")) {
				if (msg.recipient.equals("All")) {
					clients[findClient(ID)]
							.send(new Message("message", "SERVER", "Uploading to 'All' forbidden", msg.sender));
				} else {
					findUserThread(msg.recipient)
							.send(new Message("upload_req", msg.sender, msg.content, msg.recipient));
				}
			} else if (msg.type.equals("upload_res")) {
				if (!msg.content.equals("NO")) {
					String IP = findUserThread(msg.sender).socket.getInetAddress().getHostAddress();
					findUserThread(msg.recipient).send(new Message("upload_res", IP, msg.content, msg.recipient));
				} else {
					findUserThread(msg.recipient)
							.send(new Message("upload_res", msg.sender, msg.content, msg.recipient));
				}
			}
		}
	}

	public void Announce(String type, String sender, String content) {
		Message msg = new Message(type, sender, content, "All");
		for (int i = 0; i < clientCount; i++) {
			clients[i].send(msg);
		}
	}

	public void SendUserList(String toWhom) {
		for (int i = 0; i < clientCount; i++) {
			findUserThread(toWhom).send(new Message("newuser", "SERVER", clients[i].username, toWhom));
		}
	}

	/**
	 * 根据用户名查找线程是否存在
	 * @param usr
	 * @return
	 */
	public ServerThread findUserThread(String usr) {
		for (int i = 0; i < clientCount; i++) {
			if (clients[i].username.equals(usr)) {
				return clients[i];
			}
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public synchronized void remove(int ID) { // 为什么要同步移除client（互斥）
		int pos = findClient(ID);
		if (pos >= 0) {
			ServerThread toTerminate = clients[pos];
			ui.jTextArea1.append("\nRemoving client thread " + ID + " at " + pos);
			if (pos < clientCount - 1) {
				for (int i = pos + 1; i < clientCount; i++) {
					clients[i - 1] = clients[i];
				}
			}
			clientCount--;
			try {
				toTerminate.close();
			} catch (IOException ioe) {
				ui.jTextArea1.append("\nError closing thread: " + ioe);
			}
			toTerminate.stop();
		}
	}

	/**
	 * 添加一个线程
	 * 
	 * @param socket
	 */
	private void addThread(Socket socket) {
		if (clientCount < clients.length) {
			ui.jTextArea1.append("\nClient accepted: " + socket);
			clients[clientCount] = new ServerThread(this, socket);
			try {
				clients[clientCount].open();
				clients[clientCount].start();	//启动一个用户线程
				clientCount++;
			} catch (IOException ioe) {
				ui.jTextArea1.append("\nError opening thread: " + ioe);
			}
		} else {
			ui.jTextArea1.append("\nClient refused: maximum " + clients.length + " reached.");
		}
	}
}



/**
 * 服务器处理用户的线程类（用户线程）
 * @author walkerwang
 *
 */
class ServerThread extends Thread {

	public SocketServer server = null;
	public Socket socket = null;
	public int ID = -1;
	public String username = "";	//线程用户名
	public ObjectInputStream streamIn = null;
	public ObjectOutputStream streamOut = null;
	public ServerFrame ui;

	public ServerThread(SocketServer _server, Socket _socket) {
		super();
		server = _server;
		socket = _socket;
		ID = socket.getPort();
		ui = _server.ui;
	}
	
	/**
	 * 发送消息（客户端可以接收到）
	 * @param msg
	 */
	public void send(Message msg) {
		try {
			streamOut.writeObject(msg);
			streamOut.flush();
		} catch (IOException ex) {
			System.out.println("Exception [SocketClient : send(...)]");
		}
	}

	public int getID() {
		return ID;
	}

	@SuppressWarnings("deprecation")
	public void run() {
		ui.jTextArea1.append("\nServer Thread " + ID + " running.");
		while (true) {
			try {
				Message msg = (Message) streamIn.readObject();
				server.handle(ID, msg);
			} catch (Exception ioe) {
				System.out.println(ID + " ERROR reading: " + ioe.getMessage());
				server.remove(ID);
				stop();
			}
		}
	}

	//用户读和写操作是否需要重新开启两个线程
	public void open() throws IOException {
		streamOut = new ObjectOutputStream(socket.getOutputStream());
		streamOut.flush();
		streamIn = new ObjectInputStream(socket.getInputStream());
	}

	public void close() throws IOException {
		if (socket != null)
			socket.close();
		if (streamIn != null)
			streamIn.close();
		if (streamOut != null)
			streamOut.close();
	}
}

