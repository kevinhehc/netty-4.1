# netty 源码中文注释

Channel: Channel是 Java NIO 的一个基本构造。可以看作是传入或传出数据的载体。因此，它可以被打开或关闭，连接或者断开连接。

NioSocketChannel: 已经建立的连接，进行数据读写用的。

NioServerSocketChannel: 服务端用于接受客户端的连接的通道。

EventLoop: 定义了Netty的核心抽象，用来处理连接的生命周期中所发生的事件，在内部，将会为每个Channel分配一个EventLoop，实际内部有单个线程

NioEventLoop: 对 channel 进行轮训处理的具体实现。

EventLoopGroup: 是一个 EventLoop 池，包含很多的 EventLoop。

Bootstrap:   是客户端的引导类，Bootstrap 在调用 bind()（连接UDP）和 connect()（连接TCP）方法时，
会新创建一个 Channel，仅创建一个单独的、没有父 Channel 的 Channel 来实现所有的网络交换。

ServerBootstrap: 是服务端的引导类，ServerBootstrap 在调用 bind() 方法时会创建一个 ServerChannel 来接受来自客户端的连接，
并且该 ServerChannel 管理了多个子 Channel 用于同客户端之间的通信。

ChannelHandler: 对 Channel 中数据的处理器，这些处理器可以是系统本身定义好的编解码器，也可以是用户自定义的。
这些处理器会被统一添加到一个 ChannelPipeline 的对象中，然后按照添加的顺序对 Channel 中的数据进行依次处理。


ChannelPipeline: 把 ChannelHandler 串起来，进行串行的调用，责任链模式。

ChannelFuture:  Netty 中所有的 I/O 操作都是异步的，即操作不会立即得到返回结果，
所以 Netty 中定义了一个 ChannelFuture 对象作为这个异步操作的“代言人”，表示异步操作本身。
如果想获取到该异步操作的返回值，可以通过该异步操作对象的addListener() 方法为该异步操作添加监 NIO 网络编程框架 Netty 听器，
为其注册回调：当结果出来后马上调用执行。

ChannelPromise: promise，其实跟前端的 promise 是类似的逻辑，在 ChannelFuture 之上加了可以主动设置成功和失败的处理

# 其他
编译过程中，报：io.netty.util.collection包不存在解决方法：

cd common

mvn clean compile -Dcheckstyle.skip=true

![Build project](https://github.com/netty/netty/workflows/Build%20project/badge.svg)

# Netty Project

Netty is an asynchronous event-driven network application framework for rapid development of maintainable high performance protocol servers & clients.

## Links

* [Web Site](https://netty.io/)
* [Downloads](https://netty.io/downloads.html)
* [Documentation](https://netty.io/wiki/)
* [@netty_project](https://twitter.com/netty_project)
* [Official Discord server](https://discord.gg/q4aQ2XjaCa)

## How to build

For the detailed information about building and developing Netty, please visit [the developer guide](https://netty.io/wiki/developer-guide.html).  This page only gives very basic information.

You require the following to build Netty:

* Latest stable [OpenJDK 8](https://adoptium.net/)
* Latest stable [Apache Maven](https://maven.apache.org/)
* If you are on Linux or MacOS, you need [additional development packages](https://netty.io/wiki/native-transports.html) installed on your system, because you'll build the native transport.

Note that this is build-time requirement.  JDK 5 (for 3.x) or 6 (for 4.0+ / 4.1+) is enough to run your Netty-based application.

## Branches to look

Development of all versions takes place in each branch whose name is identical to `<majorVersion>.<minorVersion>`.  For example, the development of 3.9 and 4.1 resides in [the branch '3.9'](https://github.com/netty/netty/tree/3.9) and [the branch '4.1'](https://github.com/netty/netty/tree/4.1) respectively.

## Usage with JDK 9+

Netty can be used in modular JDK9+ applications as a collection of automatic modules. The module names follow the
reverse-DNS style, and are derived from subproject names rather than root packages due to historical reasons. They
are listed below:

 * `io.netty.all`
 * `io.netty.buffer`
 * `io.netty.codec`
 * `io.netty.codec.dns`
 * `io.netty.codec.haproxy`
 * `io.netty.codec.http`
 * `io.netty.codec.http2`
 * `io.netty.codec.memcache`
 * `io.netty.codec.mqtt`
 * `io.netty.codec.redis`
 * `io.netty.codec.smtp`
 * `io.netty.codec.socks`
 * `io.netty.codec.stomp`
 * `io.netty.codec.xml`
 * `io.netty.common`
 * `io.netty.handler`
 * `io.netty.handler.proxy`
 * `io.netty.resolver`
 * `io.netty.resolver.dns`
 * `io.netty.transport`
 * `io.netty.transport.epoll` (`native` omitted - reserved keyword in Java)
 * `io.netty.transport.kqueue` (`native` omitted - reserved keyword in Java)
 * `io.netty.transport.unix.common` (`native` omitted - reserved keyword in Java)
 * `io.netty.transport.rxtx`
 * `io.netty.transport.sctp`
 * `io.netty.transport.udt`



Automatic modules do not provide any means to declare dependencies, so you need to list each used module separately
in your `module-info` file.
