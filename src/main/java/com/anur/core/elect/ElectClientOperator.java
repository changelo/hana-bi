package com.anur.core.elect;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.anur.config.InetSocketAddressConfigHelper.HanabiNode;
import com.anur.core.coder.Coder;
import com.anur.core.coder.Coder.DecodeWrapper;
import com.anur.core.elect.model.VotesResponse;
import com.anur.core.util.HanabiExecutors;
import com.anur.core.util.ShutDownHooker;
import com.anur.io.elect.client.ElectClient;
import io.netty.channel.ChannelHandlerContext;

/**
 * Created by Anur IjuoKaruKas on 2/2/2019
 *
 * 选举服务器操作类客户端，负责选举相关的业务
 */
@SuppressWarnings("ALL")
public class ElectClientOperator implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(ElectServerOperator.class);

    private volatile static Map<HanabiNode, ElectClientOperator> SERVER_INSTANCE_MAPPER = new HashMap<>();

    /**
     * 关闭本服务的钩子
     */
    private ShutDownHooker serverShutDownHooker;

    /**
     * 启动latch
     */
    private CountDownLatch initialLatch = new CountDownLatch(2);

    /**
     * 选举客户端
     */
    private ElectClient electClient;

    /**
     * 要连接的节点的信息
     */
    private HanabiNode hanabiNode;

    /**
     * 如何消费消息
     */
    private static BiConsumer<ChannelHandlerContext, String> CLIENT_MSG_CONSUMER = (ctx, msg) -> {
        DecodeWrapper decodeWrapper = Coder.decode(msg);
        VotesResponse votesResponse;
        switch (decodeWrapper.protocolEnum) {
        case VOTES_RESPONSE:
            votesResponse = (VotesResponse) decodeWrapper.object;
            ElectOperator.getInstance()
                         .receiveVotes(votesResponse);

        default:
            break;
        }
    };

    /**
     * 选举服务器的客户端需要连接多个服务端，所以这里使用map来管理
     */
    public static ElectClientOperator getInstance(HanabiNode hanabiNode) {
        ElectClientOperator electClientOperator = SERVER_INSTANCE_MAPPER.get(hanabiNode);

        if (electClientOperator == null) {
            synchronized (ElectClientOperator.class) {
                electClientOperator = SERVER_INSTANCE_MAPPER.get(hanabiNode);
                if (electClientOperator == null) {
                    electClientOperator = new ElectClientOperator(hanabiNode);
                    electClientOperator.init();
                    HanabiExecutors.submit(electClientOperator);
                    SERVER_INSTANCE_MAPPER.put(hanabiNode, electClientOperator);
                }
            }
        }
        return SERVER_INSTANCE_MAPPER.get(hanabiNode);
    }

    public ElectClientOperator(HanabiNode hanabiNode) {
        this.hanabiNode = hanabiNode;
    }

    /**
     * 初始化Elector
     */
    private void init() {
        this.serverShutDownHooker = new ShutDownHooker(
            String.format(" ----------------- 终止与选举节点 %s [%s:%s] 的连接 ----------------- ", hanabiNode.getServerName(), hanabiNode.getHost(), hanabiNode.getElectionPort()));
        this.electClient = new ElectClient(hanabiNode.getServerName(), hanabiNode.getHost(), hanabiNode.getElectionPort(), CLIENT_MSG_CONSUMER, this.serverShutDownHooker);
        initialLatch.countDown();
    }

    /**
     * 启动client，没事可以多调用，并不会启动多个连接
     */
    public void start() {
        if (this.serverShutDownHooker.isShutDown()) {// 如果以前就创建过这个client，但是中途关掉了，直接重启即可
            logger.debug("正在重新建立与节点 {} [{}:{}] 的连接...", hanabiNode.getServerName(), hanabiNode.getHost(), hanabiNode.getElectionPort());
            this.serverShutDownHooker.reset();
            HanabiExecutors.submit(this);
        } else {
            initialLatch.countDown();// 如果没创建过，则直接将其启动
        }
    }

    public synchronized void ShutDown() {
        this.serverShutDownHooker.shutdown();
    }

    @Override
    public void run() {
        try {
            initialLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        logger.debug("正在建立与节点 {} [{}:{}] 的连接...", hanabiNode.getServerName(), hanabiNode.getHost(), hanabiNode.getElectionPort());
        electClient.start();
    }
}
