package com.anur.core.coordinate.operator;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.anur.config.InetSocketAddressConfiguration;
import com.anur.core.coordinate.apis.driver.RequestHandlePool;
import com.anur.core.coordinate.model.CoordinateRequest;
import com.anur.core.struct.base.AbstractStruct;
import com.anur.util.ChannelManager;
import com.anur.util.ChannelManager.ChannelType;
import com.anur.util.HanabiExecutors;
import com.anur.util.ShutDownHooker;
import com.anur.io.coordinate.server.CoordinateServer;
import com.anur.core.struct.OperationTypeEnum;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

/**
 * Created by Anur IjuoKaruKas on 2/12/2019
 *
 * 集群内通讯、协调服务器操作类服务端，负责协调相关的业务
 */
public class CoordinateServerOperator implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(CoordinateServerOperator.class);

    private volatile static CoordinateServerOperator INSTANCE;

    /**
     * 关闭本服务的钩子
     */
    private ShutDownHooker serverShutDownHooker;

    /**
     * 启动latch
     */
    private CountDownLatch initialLatch = new CountDownLatch(2);

    /**
     * 选举服务端，需要常驻
     */
    private CoordinateServer coordinateServer;

    /**
     * 如何去消费消息
     */
    private static BiConsumer<ChannelHandlerContext, ByteBuffer> SERVER_MSG_CONSUMER = (ctx, msg) -> {
        int sign = 0;
        try {
            sign = msg.getInt(AbstractStruct.TypeOffset);
        } catch (Exception e) {
            e.printStackTrace();
        }
        OperationTypeEnum typeEnum = OperationTypeEnum.parseByByteSign(sign);
        if (typeEnum == null) {
            logger.error("一定是哪里有bug，收到的消息 sign 为：" + sign);
        } else {
            RequestHandlePool.INSTANCE.receiveRequest(new CoordinateRequest(msg, typeEnum, ctx.channel()));
        }
    };

    /**
     * 需要在 channelPipeline 上挂载什么
     */
    private static Consumer<ChannelPipeline> PIPE_LINE_ADDER = c -> c.addFirst(new UnRegister());

    /**
     * Coordinate 断开连接时，需要从 ChannelManager 移除管理
     */
    static class UnRegister extends ChannelInboundHandlerAdapter {

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            super.channelInactive(ctx);
            ChannelManager.getInstance(ChannelType.COORDINATE)
                          .unRegister(ctx.channel());
        }
    }

    /**
     * 协调器的服务端是个纯单例，没什么特别的
     */
    public static CoordinateServerOperator getInstance() {
        if (INSTANCE == null) {
            synchronized (CoordinateServerOperator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new CoordinateServerOperator();
                    INSTANCE.init();

                    Thread thread = new Thread(INSTANCE);
                    thread.setName("CoordinateServerOperator");
                    thread.setPriority(10);
                    HanabiExecutors.INSTANCE.execute(thread);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 初始化Elector
     */
    public void init() {
        this.serverShutDownHooker = new ShutDownHooker(String.format("终止协调服务器的套接字接口 %s 的监听！", InetSocketAddressConfiguration.INSTANCE.getServerCoordinatePort()));
        this.coordinateServer = new CoordinateServer(InetSocketAddressConfiguration.INSTANCE.getServerCoordinatePort(),
            serverShutDownHooker, SERVER_MSG_CONSUMER, PIPE_LINE_ADDER);
        initialLatch.countDown();
    }

    public void start() {
        initialLatch.countDown();
    }

    /**
     * 优雅地关闭选举服务器
     */
    public void shutDown() {
        serverShutDownHooker.shutdown();
    }

    @Override
    public void run() {
        try {
            initialLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.info("协调服务器正在启动...");
        coordinateServer.start();
    }
}
