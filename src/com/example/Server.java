package com.example;


import java.io.IOException;
import java.io.InputStream;

import java.io.OutputStream;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import com.fasterxml.jackson.databind.ObjectMapper;

import net.sf.json.JSONObject;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Server
 */
public class Server {
    private static ServerSocket server = null;
    private static Socket ss = null;
    /**
     * Client Collection
     */
    private static Map<String, ServerThread> serverThreadMap = new HashMap<String, ServerThread>();

    
    public static void main(String[] args) {
        server();
    }

    /**
     * Ordinary Server Connection
     */
    private static void server() {
        try {
            
            server = new ServerSocket(5678);
            System.out.println("server start！");
            while (true) {
                //Create a Receive Interface
                ss = server.accept();
                //Start a new client listener thread
                new ServerThread(server, ss).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ss.close();
                server.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Internal class threads, which start a corresponding listening thread every time a new client is connected
     */
    @SuppressWarnings("Duplicates")
    private static class ServerThread extends Thread {
        ServerSocket server = null;
        Socket socket = null;
        InputStream is = null;
        OutputStream os = null;
        String clientName = null;
        boolean alive = true;

        private Connection con;
        private Statement sql;
        private String uri = "jdbc:mysql://39.96.18.40:3306/IMChatDB?characterEncoding=utf8&serverTimezone=GMT%2B8&useSSL=false";

        String to = null;
        List<byte[]> msgList = new ArrayList<>();
        
        List<String> fidList = new ArrayList<>();
        
        public ServerThread() {
        }

        ServerThread(ServerSocket server, Socket socket) {
            this.socket = socket;
            this.server = server;
        }

        @Override
        public void run() {
            //receive data
            try {
                is = socket.getInputStream();
                //send
                os = socket.getOutputStream();
                //Buffer zone
                byte[] b = new byte[1024];
                
                while (alive) {
                    //Receiving messages from clients
                    
                	int length = -1;
//                    if(serverThreadMap.get(to)!=null && msgList!=null) {
//                    	for (byte[] msgbyte:msgList) {
//                    		
//							serverThreadMap.get(to).os.write(new String(msgbyte).getBytes());
//								                               
//                    	}
//                    	msgList.clear();
//                    }
                    length = is.read(b);
                    if (length != -1) {
                        //Text message
                        String message = new String(b, 0, length);
                        
                        
                        
                        //JSON to HashMap
                        HashMap hashMap = new ObjectMapper().readValue(message, HashMap.class);

                        //Message type
                        String type = (String) hashMap.get("type");

                        
                        
                        //new connection
                        if ("OPEN".equals(type)) {
                            clientName = (String) hashMap.get("clientName");
                            String password = (String) hashMap.get("password");
                            try {
                    			con = DriverManager.getConnection(uri,"root","123456");
                    			String condition = "select * from user where uid = '"+clientName+"' and upassword = '"+password+"'";
                    			sql = con.prepareStatement(condition);
                    			ResultSet rSet = sql.executeQuery(condition);
                    			if(rSet.next()) {
                    				String condition2 = "update user set ustate='1' where uid='"+clientName+"'";
                    				con.prepareStatement(condition2);
                    				//Add client to collection container
                    				String condition3 = "SELECT fname,fid FROM friend WHERE uid = '"+clientName+"' ORDER BY fname";
                    				sql = con.prepareStatement(condition3);
                    				ResultSet rSet1 = sql.executeQuery(condition3);
                    				
                    				
                    				
                    				//String result1 = "{\"type\":\"OPEN\",\"clientName\":\"" + clientName + "\"}";
                    				String userId = null;
                    				String fname = null;
                    				String fid = null;
                    				int i = 0;
                    				JSONObject jsonObject = new JSONObject();
                    				jsonObject.put("type","OPEN");
                    				jsonObject.put("clientName",clientName);
                    				jsonObject.put("result","success");
                    				while(rSet1.next()) {						
                    					userId = "userId"+String.valueOf(i);
                    					fname = rSet1.getString("fname");
                    					fid = rSet1.getString("fid");
                    					fidList.add(fid);
                    					jsonObject.put(userId, fid);
                    					jsonObject.put(fid, fname);
            							i++;
            						}
                    				
                    				String condition4 = "SELECT uname FROM user WHERE uid = '"+clientName+"'";
                    				sql = con.prepareStatement(condition4);
                    				ResultSet rSet4 = sql.executeQuery(condition4);
                    				String name = null;
                    				if(rSet4.next()) {
                    					name = rSet4.getString("uname");
                   					
                    				}
                    				jsonObject.put("userName",name);                   			              				
                    				jsonObject.put("userNum",String.valueOf(i));
                    				String result = jsonObject.toString();
    //                				String result = "{\"type\":\"OPEN\",\"clientName\":\"" + clientName + "\",\"result\":\"success\"}";
                                    serverThreadMap.put(clientName, this);
                                    serverThreadMap.get(clientName).os.write(result.getBytes());
                                    
                                    for(String s:fidList) {
                                    	if(serverThreadMap.get(s) != null) {
                                    		String longined ="{\"type\":\"MESSAGE\",\"chat\":\"LONGINED\",\"from\":\""+name+"\",\"to\":\"" + s + "\"}";
                                    		serverThreadMap.get(s).os.write(longined.getBytes());
                                    	}
                                    }
                                 
                                    System.out.println(clientName + "Successful connection！");
                                    System.out.println("Current number of clients：" + serverThreadMap.size());
                    				
                    			}else {
                    				String result = "{\"type\":\"OPEN\",\"clientName\":\"" + clientName + "\",\"result\":\"error\"}";
                    				serverThreadMap.put(clientName, this);
                                    serverThreadMap.get(clientName).os.write(result.getBytes());
                                    serverThreadMap.remove(clientName);
                    			}
                            } catch (SQLException e) {
                    			// TODO Auto-generated catch block
                    			e.printStackTrace();
                    		}finally {
                    			try {
									con.close();
								} catch (SQLException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
                    		}
                            
                        }

                        //CLOSE
                        if ("CLOSE".equals(type)) {
                        	alive = false;
                        	try {
								con = DriverManager.getConnection(uri,"root","123456");
								String condition2 = "update user set ustate='0' where uid='"+clientName+"'";
								con.prepareStatement(condition2);
								System.err.println(clientName + "Exit the connection and close the listener thread！");
							} catch (SQLException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}finally {
                    			try {
									con.close();
								} catch (SQLException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
                    		}                            
                        }

                        //Text message
                        if ("MESSAGE".equals(type)) {
                            String msg = (String) hashMap.get("message");                       	
                            
                            String chat = (String) hashMap.get("chat");
                            //Group chat (radio)
                            if ("GROUP".equals(chat)) {
                                //Traverse the container and forward messages to each object in the container
                                for (ServerThread st : serverThreadMap.values()) {
                                    //Send data to other clients
                                    if (st != this) {
                                        st.os.write(new String(b, 0, length).getBytes());
                                    }
                                }

                                //Background printing
                                System.out.println(clientName + "Say to everyone：" + msg);
                            }
                            //Private chat
                            if ("PRIVATE".equals(chat)) {      
                            	to = (String) hashMap.get("to");    
                            	msgList.add(b);
                            	if(serverThreadMap.get(to)!=null) {
	                                	for (byte[] msgbyte:msgList) {
	                                		try {
												serverThreadMap.get(to).os.write(new String(msgbyte, 0, length).getBytes());
											} catch (IOException e) {
												// TODO Auto-generated catch block
												e.printStackTrace();
											}
	                                		System.out.println(clientName + "said " + to + " that：" + msg);
	                                	}
	                                	msgList.clear();	                                		                                
								}			
                                
 //                               serverThreadMap.get(to).os.write(new String(b, 0, length).getBytes());
                                //Background printing
                                
                            }
                        }
                      
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("with" + clientName + "Connection interruption, forced to close listening threads！");
            } finally {
                try {
                    serverThreadMap.remove(clientName);
                    System.out.println("Current number of clients：" + serverThreadMap.size());
                    os.close();
                    is.close();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
