package com.anur.core.coordinate.apis.driver

import com.anur.config.InetSocketAddressConfiguration
import com.anur.core.coordinate.apis.recovery.LeaderClusterRecoveryManager
import com.anur.core.coordinate.apis.fetch.LeaderCoordinateManager
import com.anur.core.coordinate.model.RequestProcessor
import com.anur.core.elect.ElectMeta
import com.anur.core.struct.coordinate.CommitResponse
import com.anur.core.struct.coordinate.Commiter
import com.anur.core.struct.coordinate.FetchResponse
import com.anur.core.struct.coordinate.Fetcher
import com.anur.core.struct.coordinate.RecoveryReporter
import com.anur.core.struct.coordinate.Register
import com.anur.core.struct.coordinate.RegisterResponse
import com.anur.util.ChannelManager
import com.anur.io.hanalog.log.LogManager
import io.netty.channel.Channel
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.function.Consumer

/**
 * Created by Anur IjuoKaruKas on 2019/7/10
 *
 * 主节点协调器的业务分发处理中心
 */
object LeaderApisHandler {

    private val logger = LoggerFactory.getLogger(LeaderApisHandler::class.java)

    /**
     * 在集群选主成功后，先来一波日志恢复
     */
    fun handleRecoveryRequest(msg: ByteBuffer, channel: Channel) {
        val recoveryReporter = RecoveryReporter(msg)
        val serverName = ChannelManager.getInstance(ChannelManager.ChannelType.COORDINATE)
            .getChannelName(channel)
        val GAO = recoveryReporter.getCommited()

        logger.debug("收到来自协调节点 {} 的 RecoveryReport 请求 {} ", serverName, GAO)
        LeaderClusterRecoveryManager.receive(serverName, GAO)
    }

    /**
     * 协调子节点向主节点请求 Fetch 消息，主节点需返回当前可提交 GAO (canCommit)
     */
    fun handleFetchRequest(msg: ByteBuffer, channel: Channel) {
        val fetcher = Fetcher(msg)
        val serverName = ChannelManager.getInstance(ChannelManager.ChannelType.COORDINATE)
            .getChannelName(channel)

        logger.debug("收到来自协调节点 {} 的 Fetch 请求 {} ", serverName, fetcher.fetchGAO)

        // TODO 虽然当前类叫做 Leader Api 管理，但是集群恢复阶段，Follower 也会收到 FetchRequest，这里做特殊处理
        // 所以如果不是集群恢复阶段, 当leader收到 fetchRequest, 需要发送 COMMIT 类型的消息, 内容为当前 canCommit 的 GAO
        // 同时, 在集群成员收到COMMIT 消息时,需要回复一个 COMMIT RESPONSE,表明自己的 fetch 进度
        if (ElectMeta.isLeader) {
            val canCommit = LeaderCoordinateManager.fetchReport(serverName, fetcher.fetchGAO)

            ApisManager.send(serverName, Commiter(canCommit), RequestProcessor(
                Consumer { byteBuffer ->
                    val commitResponse = CommitResponse(byteBuffer)
                    LeaderCoordinateManager.commitReport(serverName, commitResponse.commitGAO)
                },
                null))
        }

        // 为什么要。next，因为 fetch 过来的是客户端最新的 GAO 进度，而获取的要从 GAO + 1开始
        val fetchDataInfo = LogManager.getAfter(fetcher.fetchGAO.next())
        if (fetchDataInfo == null) logger.debug("对于 fetch 请求 ${fetcher.fetchGAO}， 返回为空")


        ApisManager.send(serverName, FetchResponse(fetchDataInfo), RequestProcessor.REQUIRE_NESS)
    }

    /**
     * 协调子节点向父节点注册自己
     */
    fun handleRegisterRequest(msg: ByteBuffer, channel: Channel) {
        val register = Register(msg)
        logger.info("协调节点 {} 已注册到本节点", register.getServerName())
        ChannelManager.getInstance(ChannelManager.ChannelType.COORDINATE)
            .register(register.getServerName(), channel)
        ApisManager.send(register.getServerName(), RegisterResponse(InetSocketAddressConfiguration.getServerName()), RequestProcessor.REQUIRE_NESS)
    }
}