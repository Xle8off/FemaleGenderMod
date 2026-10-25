package com.wildfire.client.discord;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Discord local IPC only. All pipe/socket I/O runs on owned daemon workers. */
public final class DiscordIpc implements AutoCloseable {
 private static final int MAX=64*1024;
 private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"SpringPlus-discord-writer");t.setDaemon(true);return t;});
 private final ExecutorService reader=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"SpringPlus-discord-reader");t.setDaemon(true);return t;});
 private final Consumer<String> join;
 interface Connector {Endpoint connect()throws IOException;}
 private final Connector connector;
 private volatile Endpoint endpoint;private volatile JsonObject activity;private volatile boolean closed,ready,published;private volatile String status="Подключаемся к Discord…";
 private volatile long handshakeUntil,pongs;private String last="";private long nonce,lastPing;
 public DiscordIpc(){this(null,DiscordIpc::connect);}
 public DiscordIpc(Consumer<String> join){this(join,DiscordIpc::connect);}
 DiscordIpc(Consumer<String> join,Connector connector){this.join=join;this.connector=connector;scheduler.scheduleWithFixedDelay(this::pump,0,5,TimeUnit.SECONDS);}
 public String status(){return status;}
 long pongs(){return pongs;}
 public void activity(JsonObject value){activity=value.deepCopy();}
 private void pump(){if(closed)return;try{
  if(endpoint==null){Endpoint next=connector.connect();if(closed){next.close();return;}endpoint=next;ready=false;published=false;last="";handshakeUntil=System.nanoTime()+10_000_000_000L;lastPing=System.nanoTime();status="Подключаемся к Discord…";var hello=new JsonObject();hello.addProperty("v",1);hello.addProperty("client_id",PresenceSettings.APPLICATION_ID);next.write(0,hello);reader.execute(()->read(next));return;}
  if(!ready){if(System.nanoTime()>handshakeUntil){status="Discord не ответил · переподключаемся";drop(endpoint);}return;}
  if(System.nanoTime()-lastPing>15_000_000_000L){endpoint.writeBytes(3,new byte[]{'{','}'});lastPing=System.nanoTime();}
  JsonObject current=activity;if(current!=null&&!current.toString().equals(last)){var args=new JsonObject();args.addProperty("pid",ProcessHandle.current().pid());args.add("activity",current);send("SET_ACTIVITY",null,args);last=current.toString();published=true;}
 }catch(Exception error){drop(endpoint);status="Discord не найден · повторим подключение";}}
 private void send(String cmd,String event,JsonObject args)throws IOException{var message=new JsonObject();message.addProperty("cmd",cmd);message.addProperty("nonce",Long.toString(++nonce));if(event!=null)message.addProperty("evt",event);if(args!=null)message.add("args",args);Endpoint current=endpoint;if(current!=null)current.write(1,message);}
 private void read(Endpoint source){try{while(!closed&&endpoint==source){Frame frame=source.read();if(frame.op()==2){status="Discord закрыл канал · переподключаемся";break;}if(frame.op()==4){pongs++;continue;}if(frame.op()==3){source.writeBytes(4,frame.data());continue;}if(frame.op()!=1)continue;var object=JsonParser.parseString(new String(frame.data(),StandardCharsets.UTF_8)).getAsJsonObject();String event=object.has("evt")&&!object.get("evt").isJsonNull()?object.get("evt").getAsString():"";
   if(event.equals("READY")){scheduler.execute(()->{if(closed||endpoint!=source)return;try{if(join!=null)send("SUBSCRIBE","ACTIVITY_JOIN",null);ready=true;status="Подключено к Discord";pump();}catch(IOException e){drop(source);}});}
   else if(event.equals("ACTIVITY_JOIN")&&object.has("data")){var data=object.getAsJsonObject("data");if(join!=null&&data.has("secret")&&PresenceSettings.JOIN.equals(data.get("secret").getAsString()))join.accept(PresenceSettings.JOIN);}
   else if(event.equals("ERROR")){var data=object.has("data")&&object.get("data").isJsonObject()?object.getAsJsonObject("data"):new JsonObject();String reason=data.has("message")&&data.get("message").isJsonPrimitive()?PresenceActivity.clean(data.get("message").getAsString(),96):"неверные параметры активности";status="Discord отклонил запрос: "+reason;}
   else if(event.isEmpty()&&object.has("cmd")&&object.get("cmd").getAsString().equals("SET_ACTIVITY")){status="Подключено к Discord";}
  }}catch(Exception error){if(!closed&&endpoint==source)status="Ошибка канала Discord: "+error.getClass().getSimpleName();}finally{if(!closed)drop(source);}}
 private synchronized void drop(Endpoint source){if(source==null)return;if(endpoint==source){endpoint=null;ready=false;if(!closed&&status.equals("Подключено к Discord"))status="Связь с Discord прервана · переподключаемся";}try{source.close();}catch(IOException ignored){}}
 @Override public void close(){if(closed)return;closed=true;Endpoint current=endpoint;scheduler.execute(()->{if(current!=null)try{if(published){var args=new JsonObject();args.addProperty("pid",ProcessHandle.current().pid());args.add("activity",JsonNull.INSTANCE);send("SET_ACTIVITY",null,args);}}catch(IOException ignored){}finally{drop(current);}});scheduler.shutdown();reader.shutdown();Thread.ofVirtual().name("SpringPlus-discord-close").start(()->{try{if(!scheduler.awaitTermination(1,TimeUnit.SECONDS)){drop(current);scheduler.shutdownNow();}}catch(InterruptedException e){Thread.currentThread().interrupt();}drop(current);reader.shutdownNow();});}
 public record Frame(int op,byte[] data){}
 private static byte[] exact(InputStream in,int length)throws IOException{byte[] bytes=new byte[length];int offset=0;while(offset<length){int read=in.read(bytes,offset,length-offset);if(read<0)throw new EOFException();if(read==0)throw new IOException("IPC read made no progress");offset+=read;}return bytes;}
 public static Frame readFrame(InputStream in)throws IOException{byte[] header=exact(in,8);var b=ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);int op=b.getInt(),length=b.getInt();if(length<0||length>MAX)throw new IOException("IPC frame limit");return new Frame(op,exact(in,length));}
 public static byte[] frame(int op,byte[] data)throws IOException{if(data.length>MAX)throw new IOException("IPC frame limit");return ByteBuffer.allocate(8+data.length).order(ByteOrder.LITTLE_ENDIAN).putInt(op).putInt(data.length).put(data).array();}
 static final class Endpoint implements Closeable {
  final Closeable owner;final InputStream in;final OutputStream out;
  Endpoint(Closeable owner,InputStream in,OutputStream out){this.owner=owner;this.in=in;this.out=out;}
  Frame read()throws IOException{return readFrame(in);}
  synchronized void write(int op,JsonObject object)throws IOException{writeBytes(op,object.toString().getBytes(StandardCharsets.UTF_8));}
  synchronized void writeBytes(int op,byte[] bytes)throws IOException{out.write(frame(op,bytes));out.flush();}
  public void close()throws IOException{owner.close();}
 }
 /** A synchronous Windows pipe must not have a pending blocking ReadFile when we
  * need to write SUBSCRIBE/SET_ACTIVITY on the same handle. JDK available() uses
  * PeekNamedPipe; read only bytes already present and never issue zero-byte reads. */
 static InputStream windowsInput(InputStream raw){return new InputStream(){
  public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}
  public int read(byte[] b,int offset,int length)throws IOException{
   Objects.checkFromIndexSize(offset,length,b.length);if(length==0)return 0;
   long until=System.nanoTime()+45_000_000_000L;
   while(true){int available=raw.available();if(available>0)return raw.read(b,offset,Math.min(length,available));
    if(System.nanoTime()>until)throw new java.net.SocketTimeoutException("Discord IPC read timeout");
    try{Thread.sleep(25);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("Discord IPC stopped");}
   }
  }
 };}
 private static Endpoint connect()throws IOException {
  boolean windows=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win");
  var roots=new LinkedHashSet<String>();for(String key:List.of("XDG_RUNTIME_DIR","TMPDIR","TMP","TEMP")){String value=System.getenv(key);if(value!=null&&!value.isBlank())roots.add(value);}roots.add("/tmp");
  for(int i=0;i<10;i++){
   if(windows){try{var pipe=new RandomAccessFile("\\\\.\\pipe\\discord-ipc-"+i,"rw");return new Endpoint(pipe,windowsInput(new FileInputStream(pipe.getFD())),new FileOutputStream(pipe.getFD()));}catch(IOException ignored){}}
   else for(String root:roots){var path=Path.of(root,"discord-ipc-"+i);if(!Files.exists(path))continue;SocketChannel socket=null;try{socket=SocketChannel.open(StandardProtocolFamily.UNIX);socket.connect(UnixDomainSocketAddress.of(path));SocketChannel connected=socket;InputStream in=new InputStream(){public int read()throws IOException{byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0]&255;}public int read(byte[] b,int off,int len)throws IOException{return connected.read(ByteBuffer.wrap(b,off,len));}};OutputStream out=new OutputStream(){public void write(int value)throws IOException{write(new byte[]{(byte)value});}public void write(byte[] b,int off,int len)throws IOException{var buffer=ByteBuffer.wrap(b,off,len);while(buffer.hasRemaining())connected.write(buffer);}};return new Endpoint(socket,in,out);}catch(IOException e){if(socket!=null)socket.close();}}
  }throw new IOException("Discord IPC unavailable");
 }
}
